(ns hanrei.integration-test
  "Integration tests for Phase 1 ingest pipeline.
   Tests: API fetch, statute extraction, entity building."
  (:require [clojure.test :refer [deftest is testing]]
            [hanrei.ingest :as ingest]
            [hanrei.pilot :as pilot]))

(deftest statute-extraction
  "Test: extract statute references from opinion text"
  (testing "Extract single statute reference"
    (let [opinion "民法第123条により..."
          refs (ingest/extract-statute-references opinion)]
      (is (> (count refs) 0) "Should extract at least one statute")
      (is (= (:statute-name (first refs)) "民法") "Should identify 民法")))

  (testing "Extract multiple statute references"
    (let [opinion "民法第123条により、刑法第456条は適用されず..."
          refs (ingest/extract-statute-references opinion)]
      (is (>= (count refs) 2) "Should extract multiple statutes")))

  (testing "Handle nil text gracefully"
    (let [refs (ingest/extract-statute-references nil)]
      (is (= refs []) "Should return empty list for nil")))

  (testing "No match in text"
    (let [opinion "This case involves common law principles."
          refs (ingest/extract-statute-references opinion)]
      (is (= refs []) "Should return empty list when no statutes match"))))

(deftest entity-building
  "Test: build Datomic entities from raw API response"
  (testing "Build case entity from minimal raw data"
    (let [raw {:case_id "2026-001"
               :title "Test Case"
               :decision_date "2026-07-18"
               :summary "Test holding"
               :full_text_url "http://example.com"
               :opinion_text "民法第123条により、..."}
          entity (ingest/build-case-entity raw)]
      (is (= (:hanrei/case-id entity) "2026-001") "Should preserve case-id")
      (is (= (:hanrei/case-title entity) "Test Case") "Should preserve title")
      (is (> (count (:hanrei/case-statute entity)) 0) "Should extract statute refs")
      (is (= (:hanrei/source entity) :jpn-supremecourt) "Should set source")))

  (testing "Build case entity with defaults"
    (let [raw {}
          entity (ingest/build-case-entity raw)]
      (is (:hanrei/case-id entity) "Should generate fallback case-id")
      (is (:hanrei/last-verified entity) "Should set last-verified timestamp")))

  (testing "Build judge entity"
    (let [entity (ingest/build-judge-entity "Judge Smith" "jpn-court-001")]
      (is (= (:hanrei/judge-name entity) "Judge Smith") "Should preserve judge name")
      (is (= (:hanrei/judge-court entity) "jpn-court-001") "Should preserve court did")))

  (testing "Build opinion entity"
    (let [entity (ingest/build-opinion-entity
                  {:type "majority" :text "The law is clear..."}
                  "case-123"
                  "judge-456")]
      (is (= (:hanrei/opinion-type entity) :majority) "Should parse opinion type")
      (is (= (:hanrei/opinion-case entity) "case-123") "Should link to case"))))

(deftest phase-1-pipeline
  "Test: Phase 1 ingest pipeline with mock data"
  (testing "Ingest returns proper result structure"
    ;; Note: This test runs with nil datomic-conn (no real DB transact)
    (let [result (ingest/ingest-phase-1 nil {:page-size 10 :max-pages 1})]
      (is (map? result) "Should return a map")
      (is (contains? result :ingested) "Should include :ingested count")
      (is (contains? result :case-count) "Should include :case-count")
      (is (contains? result :opinion-count) "Should include :opinion-count")
      (is (contains? result :timestamp) "Should include :timestamp")))

  (testing "Validation passes on result"
    (let [result (ingest/ingest-phase-1 nil {:page-size 1 :max-pages 1})]
      (is (ingest/validate-ingest nil result) "Validation should pass"))))
