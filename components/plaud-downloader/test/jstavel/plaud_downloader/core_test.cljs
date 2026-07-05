(ns jstavel.plaud-downloader.core-test
  (:require [jstavel.plaud-downloader.core :as sut]
            ["fs" :as fs]
            ["path" :as path]
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

(def real-page-path
  (path/join "components" "plaud-downloader" "resources" "plaud-downloader" "all-files-page.html"))

(def real-html-content
  (fs/readFileSync real-page-path "utf8"))

(def real-html-page
  {:html-content real-html-content
   :page-url "https://web.plaud.ai"
   :timestamp "2026-07-01T21:46:39.000Z"})

(deftest extract-records-from-real-page
  (testing "Extraction from real all-files-page.html fixture"
    (let [result (sut/extract-records real-html-page {:parse-strategy :plaud-web-default})]
      (is (not (:error result)) "Should have no error")
      (is (= 30 (count (:records result))) "Should find exactly 30 records")
      (let [record-by-id (into {} (map (juxt :plaud-id identity) (:records result)))]
        (is (= "4m 45s" (:duration (get record-by-id "8acb9405f6274428c59c98f02c453b89"))))
        (is (= "2026-07-01 21:46:39" (:name (get record-by-id "8acb9405f6274428c59c98f02c453b89"))))
        (is (= "2026-07-01 21:46:39" (:created (get record-by-id "8acb9405f6274428c59c98f02c453b89"))))

        (is (= "53s" (:duration (get record-by-id "dff75bce0ecb01fb1023eaa4f2666103"))))
        (is (= "2026-06-28 09:42:22" (:name (get record-by-id "dff75bce0ecb01fb1023eaa4f2666103"))))

        (is (= "25m 26s" (:duration (get record-by-id "6b906048380c47be9b49b265f777c00b"))))
        (is (= "2026-06-22 10:29:40" (:name (get record-by-id "6b906048380c47be9b49b265f777c00b"))))))))

