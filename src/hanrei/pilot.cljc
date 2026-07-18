(ns hanrei.pilot
  "Phase 1 pilot execution and logging orchestrator."
  (:require [hanrei.ingest :as ingest]))

(defn run-phase-1-pilot
  "Execute Phase 1 pilot: fetch 1K decisions from Japan Supreme Court.
   Returns result map with ingested/case-count/opinion-count/timestamp."
  [datomic-conn]
  (println "\n=== hanrei Phase 1 Pilot Execution ===")
  (println (str "Started at: " (java.time.Instant/now)))

  (let [result (ingest/ingest-phase-1 datomic-conn
                                       {:page-size 100 :max-pages 10})]
    (println (str "\n=== Ingest Result ==="))
    (println (str "Total entities ingested: " (:ingested result)))
    (println (str "Cases: " (:case-count result)))
    (println (str "Opinions: " (:opinion-count result)))
    (println (str "Completed at: " (:timestamp result)))

    ;; Validation
    (let [valid? (ingest/validate-ingest datomic-conn result)]
      (println (str "Validation: " (if valid? "PASS" "FAIL")))
      result)))

;; Phase 1 execution log (append-only, timestamp + result)
(def ^:dynamic pilot-log-file "logs/phase-1-pilot.log")

(defn log-pilot-result
  "Write pilot result to log file (append-only, EDN format).
   Each entry is {:timestamp Instant :phase 1 :result {...}}"
  [result]
  #?(:clj
     (let [log-entry {:timestamp (java.time.Instant/now)
                      :phase 1
                      :result result}]
       ;; TODO: write to logs/phase-1-pilot.log as EDN
       (println (str "[hanrei] Logged: " log-entry)))
     :cljs
     ;; ClojureScript version would write to file via Node.js fs or browser localStorage
     (println (str "[hanrei.pilot] ClojureScript: log entry would be " result))))
