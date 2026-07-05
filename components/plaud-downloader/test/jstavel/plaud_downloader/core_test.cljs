(ns jstavel.plaud-downloader.core-test
  (:require [jstavel.plaud-downloader.core :as sut]
            [cljs.test :refer-macros [deftest is testing]]))

(def mock-html
  (str "<html><body><ul>"
       "<li class=\"file-list-item\" data-file-id=\"abc123def456\">"
       "<div class=\"file-list-item__filename\">2026-07-01 21:46:39</div>"
       "<div class=\"file-list-item__duration-column\">4m 45s</div>"
       "<div class=\"file-list-item__date-column\">2026-07-01 21:46:39</div>"
       "</li>"
       "<li class=\"file-list-item\" data-file-id=\"deadbeef0001\">"
       "<div class=\"file-list-item__filename\">Meeting Notes</div>"
       "<div class=\"file-list-item__duration-column\">12m 10s</div>"
       "<div class=\"file-list-item__date-column\">2026-06-30 10:00:00</div>"
       "</li>"
       "</ul></body></html>"))

(def html-page
  {:html-content mock-html
   :page-url "https://web.plaud.ai"
   :timestamp "2026-07-01T21:46:39.000Z"})

(deftest extract-records-parses-all
  (testing "Full extraction of all records"
    (let [result (sut/extract-records html-page {:parse-strategy :plaud-web-default})]
      (is (not (:error result)) "Should not have top-level error")
      (is (= 2 (count (:records result))) "Should find 2 records")
      (is (= "abc123def456" (:plaud-id (first (:records result)))))
      (is (= "deadbeef0001" (:plaud-id (second (:records result)))))
      (is (= "4m 45s" (:duration (first (:records result)))))
      (is (= "2026-07-01 21:46:39" (:created (first (:records result)))))
      (is (= "Meeting Notes" (:name (second (:records result))))))))

(deftest extract-records-empty-page
  (testing "Empty page returns parse-failed error"
    (let [result (sut/extract-records {:html-content "<html><body></body></html>"
                                        :page-url ""
                                        :timestamp ""}
                                      {:parse-strategy :plaud-web-default})]
      (is (= :parse-failed (:error result)))
      (is (string? (:message result))))))

(deftest extract-records-partial-parse
  (testing "Partial parse when some records have missing data"
    (let [partial-html (str "<html><body><ul>"
                            "<li class=\"file-list-item\">"
                            "<div class=\"file-list-item__filename\"></div>"
                            "<div class=\"file-list-item__duration-column\">5m 00s</div>"
                            "<div class=\"file-list-item__date-column\">2026-01-01 12:00:00</div>"
                            "</li>"
                            "<li class=\"file-list-item\" data-file-id=\"valid-id-999\">"
                            "<div class=\"file-list-item__filename\">Valid Recording</div>"
                            "<div class=\"file-list-item__duration-column\">10m 00s</div>"
                            "<div class=\"file-list-item__date-column\">2026-01-02 12:00:00</div>"
                            "</li>"
                            "</ul></body></html>")
          result (sut/extract-records {:html-content partial-html
                                        :page-url ""
                                        :timestamp ""}
                                      {:parse-strategy :plaud-web-default})]
      (is (= 1 (count (:records result))) "Only 1 valid record")
      (is (= :partial-parse (:error result)))
      (is (= 2 (count (:partial-records result))) "Includes all 2 partial records"))))

(deftest save-page-html-writes-file
  (testing "Save-page-html returns output path and byte count"
    (let [result (sut/save-page-html html-page)]
      (is (string? (:output-path result)))
      (is (number? (:bytes-written result))))))

