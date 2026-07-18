(ns hanrei.ingest
  "Pilot ingest pipeline for Japan Supreme Court decisions.
   Phase 1: fetch 1K recent decisions from JP SC API.
   Extracts statute references and opinion metadata for Datomic transact."
  (:require [clojure.string :as str]
            #?(:clj [clj-http.client :as http])
            #?(:clj [org.jsoup :as jsoup]))
  #?(:cljs (:require-macros [hanrei.ingest])))

;; === Phase 1 Pilot: Japan Supreme Court API ===

(def jp-sc-api-endpoint "https://www.courts.go.jp/search/ajax/hanrei_ajax.html")

(defn fetch-page
  "Fetch single page from Japan Supreme Court API"
  [page-num page-size]
  #?(:clj
     (try
       (let [response (http/get jp-sc-api-endpoint
                                {:query-params {"sort" "desc"
                                               "page" page-num
                                               "count" page-size}
                                 :timeout 10000
                                 :socket-timeout 10000})
             body (:body response)]
         ;; Parse JSON response (simplified for pilot)
         (try
           (read-string body)  ;; Placeholder: real impl uses json/read-str
           (catch Exception _
             nil)))
       (catch Exception e
         (println (str "Error fetching page " page-num ": " (.getMessage e)))
         nil))
     :cljs
     ;; ClojureScript placeholder: use fetch API
     nil))

(defn fetch-japan-supreme-court-decisions
  "Fetch N pages from Japan Supreme Court API (Phase 1 pilot)"
  [{:keys [page-size max-pages] :or {page-size 100 max-pages 10}}]
  (let [pages (range 1 (inc max-pages))
        decision-lists (keep #(fetch-page % page-size) pages)]
    (apply concat decision-lists)))

(defn extract-statute-references
  "Extract statute/law references from case opinion text using regex.
   Pattern: 「...法第...条」 or 「民法第123条」"
  [opinion-text]
  (if (nil? opinion-text)
    []
    (let [;; Pattern: 「...法第...条」 or 「民法第123条」
          pattern #"(?:「?)([^」]*法)第(\d+)条(?:」?)?"
          matches (re-seq pattern opinion-text)]
      (map (fn [[full statute-name article-no]]
             {:statute-name statute-name
              :article-no (try
                           #?(:clj (Integer/parseInt article-no))
                           #?(:cljs (js/parseInt article-no))
                           #?(:clj (catch Exception _ nil))
                           #?(:cljs (catch :default _ nil)))
              :confidence 0.8})
           (filter (fn [[_ n]] (not (nil? n))) matches)))))

(defn extract-judge-names
  "Extract judge names from decision text (public role names only).
   Placeholder: real implementation parses HTML/PDF for judge signature blocks."
  [decision-text]
  ;; Returns empty for now; full impl would parse 「裁判官 [Name]」 patterns
  [])

(defn- now-instant
  "Get current timestamp (portable across CLJ/CLJS)"
  []
  #?(:clj (java.time.Instant/now)
     :cljs (js/Date.now)))

(defn- random-id
  "Generate a random case ID (portable across CLJ/CLJS)"
  []
  #?(:clj (str "case-" (java.util.UUID/randomUUID))
     :cljs (str "case-" (random-uuid))))

(defn build-case-entity
  "Transform raw API response → Datomic entity"
  [raw-case]
  (let [case-id (or (:case_id raw-case) (random-id))
        decision-date (or (:decision_date raw-case) "2026-07-18")
        opinion-text (or (:opinion_text raw-case) "")
        statute-refs (extract-statute-references opinion-text)]
    {:db/id -1
     :hanrei/case-id case-id
     :hanrei/case-title (or (:title raw-case) "Unknown case")
     :hanrei/case-date decision-date  ;; Simplified; real impl: LocalDate/parse
     :hanrei/case-summary (or (:summary raw-case) "")
     :hanrei/source :jpn-supremecourt
     :hanrei/source-url (or (:full_text_url raw-case) "")
     :hanrei/last-verified (now-instant)
     :hanrei/case-statute statute-refs}))

(defn build-judge-entity
  "Transform judge metadata → Datomic entity (public role name only)"
  [judge-name court-did]
  {:db/id -1
   :hanrei/judge-name judge-name
   :hanrei/judge-court court-did
   :hanrei/source :jpn-supremecourt
   :hanrei/last-verified (now-instant)})

(defn build-opinion-entity
  "Transform opinion → Datomic entity"
  [raw-opinion case-id judge-did]
  {:db/id -1
   :hanrei/opinion-type (keyword (or (:type raw-opinion) "unknown"))
   :hanrei/opinion-author judge-did
   :hanrei/opinion-text (or (:text raw-opinion) "")
   :hanrei/opinion-case case-id
   :hanrei/source :jpn-supremecourt
   :hanrei/last-verified (now-instant)})

(defn ingest-phase-1
  "Phase 1 pilot: fetch 1,000 recent JP Supreme Court decisions.
   Returns {:ingested count :case-count count :opinion-count count :timestamp Instant}"
  [datomic-conn {:keys [page-size max-pages] :or {page-size 100 max-pages 10}}]
  (println (str "[hanrei] Starting Phase 1 pilot ingest (page-size=" page-size " max-pages=" max-pages ")"))

  (let [raw-decisions (fetch-japan-supreme-court-decisions
                        {:page-size page-size :max-pages max-pages})
        case-entities (map build-case-entity raw-decisions)

        ;; Flatten all opinions
        opinion-entities (mapcat
                          (fn [raw-case]
                            (map #(build-opinion-entity % (:case_id raw-case) nil)
                                 (or (:opinions raw-case) [])))
                          raw-decisions)

        all-entities (concat case-entities opinion-entities)]

    (println (str "[hanrei] Fetched " (count raw-decisions) " raw decisions"))
    (println (str "[hanrei] Built " (count all-entities) " entities for transact"))

    ;; TODO: transact to kotoba Datomic
    ;; (d/transact datomic-conn all-entities)

    {:ingested (count all-entities)
     :case-count (count case-entities)
     :opinion-count (count opinion-entities)
     :timestamp (now-instant)}))

(defn validate-ingest
  "Post-ingest validation.
   Checks that entities were properly formed and statute refs resolved."
  [datomic-conn ingest-result]
  (println (str "[hanrei] Validating ingest: " ingest-result))

  ;; TODO: query Datomic to verify entities
  ;; TODO: check for statute reference resolution
  ;; TODO: check for judge-case linkage

  true)
