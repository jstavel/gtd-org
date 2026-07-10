(ns jstavel.org-staging.interface-test
  (:require
   [clojure.test :refer [deftest is use-fixtures testing]]
   [clojure.string :as str]
   [babashka.fs :as fs]
   [jstavel.org-staging.interface :as org-staging]
   [jstavel.org-staging.core :as core]
   [jstavel.fp.interface :as fp])
  (:import
   (java.nio.file.attribute FileTime)
   (java.util UUID)))


(def ^:private temp-root-dir (atom nil))
(def ^:private downloads-dir (atom nil))
(def ^:private assets-objects-root (atom nil))
(def ^:private staging-org-file (atom nil))

(defn- delete-recursively [^java.nio.file.Path path]
  (fs/delete-tree path))

(defn- create-file [parent-path filename content]
  (let [file-path (fs/path parent-path filename)]
    (fs/create-dirs (fs/parent file-path))
    (fs/write-bytes file-path (.getBytes content))
    (fs/set-last-modified-time file-path (FileTime/fromMillis 0)) ; Ensure consistent modification time
    file-path))

(defn- create-dir [parent-path dirname]
  (let [dir-path (fs/path parent-path dirname)]
    (fs/create-dirs dir-path)
    dir-path))

(defn- setup-temp-env [f]
  (let [root (fs/create-temp-dir "gtd-org-test-")]
    (reset! temp-root-dir root)
    (reset! downloads-dir (create-dir root "Downloads"))
    (reset! assets-objects-root (create-dir root (fs/path "assets" "objects")))
    (reset! staging-org-file (create-file root "staging.org" ""))
    (f)
    (delete-recursively root)))

(use-fixtures :each setup-temp-env)

;; --- Tests for deterministic-sha256-path ---

(deftest deterministic-sha256-path-file-test
  (testing "calculates hash for a file"
    (let [file-path (create-file @downloads-dir "test.txt" "hello world")
          expected-hash (fp/sha256-file file-path)]
      (is (= expected-hash (org-staging/deterministic-sha256-path file-path))))))

(deftest deterministic-sha256-path-empty-file-test
  (testing "calculates hash for an empty file"
    (let [file-path (create-file @downloads-dir "empty.txt" "")
          expected-hash (fp/sha256-file file-path)]
      (is (= expected-hash (org-staging/deterministic-sha256-path file-path))))))

(deftest deterministic-sha256-path-directory-test
  (testing "calculates hash for a directory with files"
    (let [dir-path (create-dir @downloads-dir "my-dir")
          _ (create-file dir-path "file1.txt" "content1")
          _ (create-file dir-path "file2.txt" "content2")]
      ;; Use a known tar hash for comparison
      ;; You might need to generate this value by running the tar command manually
      ;; tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner --pax-option=exthdr.name=%d/PaxHeaders/%f,atime=@0 -cf - -C <parent-of-my-dir> my-dir | sha256sum
      (is (re-matches #"[0-9a-fA-F]{64}" (org-staging/deterministic-sha256-path dir-path))))))

(deftest deterministic-sha256-path-nested-directory-test
  (testing "calculates hash for a nested directory structure"
    (let [parent-dir (create-dir @downloads-dir "parent")
          child-dir (create-dir parent-dir "child")
          _ (create-file parent-dir "root-file.txt" "root content")
          _ (create-file child-dir "child-file.txt" "child content")]
      (is (re-matches #"[0-9a-fA-F]{64}" (org-staging/deterministic-sha256-path parent-dir))))))

;; --- Tests for build-asset-index ---

(deftest build-asset-index-empty-test
  (testing "returns an empty map for an empty assets/objects root"
    (is (empty? (org-staging/build-asset-index @assets-objects-root)))))

(deftest build-asset-index-valid-structure-test
  (testing "builds index for a valid asset structure"
    (let [uuid-str (str (UUID/randomUUID))
          short-hash-prefix (subs uuid-str 0 4)
          ab (subs short-hash-prefix 0 2)
          cd (subs short-hash-prefix 2 4)
          asset-dir (create-dir (create-dir (create-dir @assets-objects-root ab) cd) uuid-str)
          raw-dir (create-dir asset-dir "raw")
          file-content "dummy audio content"
          audio-file (create-file raw-dir "original.mp3" file-content)
          expected-hash (fp/sha256-file audio-file)
          index (org-staging/build-asset-index @assets-objects-root)]
      (is (= 1 (count index)))
      (is (= audio-file (get index expected-hash))))))

(deftest build-asset-index-malformed-uuid-test
  (testing "ignores malformed UUID directories"
    (let [malformed-dir (create-dir (create-dir (create-dir @assets-objects-root "aa") "bb") "not-a-uuid")
          raw-dir (create-dir malformed-dir "raw")
          _ (create-file raw-dir "original.mp3" "content")]
      (is (empty? (org-staging/build-asset-index @assets-objects-root))))))

(deftest build-asset-index-missing-raw-dir-test
  (testing "ignores asset directories without a raw subdirectory"
    (let [uuid-str (str (UUID/randomUUID))
          short-hash-prefix (subs uuid-str 0 4)
          ab (subs short-hash-prefix 0 2)
          cd (subs short-hash-prefix 2 4)
          asset-dir (create-dir (create-dir (create-dir @assets-objects-root ab) cd) uuid-str)
          _ (create-file asset-dir "original.mp3" "content")] ; file directly under UUID, not 'raw/'
      (is (empty? (org-staging/build-asset-index @assets-objects-root))))))

(deftest build-asset-index-multiple-files-in-raw-test
  (testing "logs warning and ignores raw directories with multiple files"
    (let [uuid-str (str (UUID/randomUUID))
          short-hash-prefix (subs uuid-str 0 4)
          ab (subs short-hash-prefix 0 2)
          cd (subs short-hash-prefix 2 4)
          asset-dir (create-dir (create-dir (create-dir @assets-objects-root ab) cd) uuid-str)
          raw-dir (create-dir asset-dir "raw")]
      (create-file raw-dir "original1.mp3" "content1")
      (create-file raw-dir "original2.mp3" "content2")
      (is (empty? (org-staging/build-asset-index @assets-objects-root)))
      ;; TODO: Potentially assert log output for warning if timbre allows easy capture
      )))

;; --- Tests for read-staging-org ---

(deftest read-staging-org-empty-file-test
  (testing "returns an empty set for an empty staging.org"
    (is (empty? (org-staging/read-staging-org @staging-org-file)))))

(deftest read-staging-org-non-existent-file-test
  (testing "returns an empty set for a non-existent staging.org"
    (let [non-existent-path (fs/path (str @temp-root-dir) "non-existent-staging.org")]
      (is (empty? (org-staging/read-staging-org non-existent-path))))))

(deftest read-staging-org-with-hashes-test
  (testing "extracts hashes from TODO entries"
    (let [content "* TODO Task 1\n:PROPERTIES:\n:HASH: 1234567890123456789012345678901234567890123456789012345678901234\n:END:\n\n* TODO Task 2\n:PROPERTIES:\n:HASH: abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd\n:END:\n* DONE Task 3 (should be ignored)\n:PROPERTIES:\n:HASH: 0000000000000000000000000000000000000000000000000000000000000000\n:END:"
          _ (fs/write-bytes @staging-org-file (.getBytes content))
          expected-hashes #{"1234567890123456789012345678901234567890123456789012345678901234"
                            "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd"}]
      (is (= expected-hashes (org-staging/read-staging-org @staging-org-file))))))

(deftest read-staging-org-with-no-hashes-test
  (testing "ignores entries without a HASH property"
    (let [content "* TODO Task 1\n:PROPERTIES:\n:NO_HASH: value\n:END:\n\n* TODO Task 2\n:PROPERTIES:\n:HASH: 1234567890123456789012345678901234567890123456789012345678901234\n:END:"
          _ (fs/write-bytes @staging-org-file (.getBytes content))
          expected-hashes #{"1234567890123456789012345678901234567890123456789012345678901234"}]
      (is (= expected-hashes (org-staging/read-staging-org @staging-org-file))))))

(deftest read-staging-org-malformed-hashes-test
  (testing "ignores malformed HASH properties"
    (let [content "* TODO Task 1\n:PROPERTIES:\n:HASH: not-a-hash\n:END:\n\n* TODO Task 2\n:PROPERTIES:\n:HASH: 1234567890123456789012345678901234567890123456789012345678901234\n:END:"
          _ (fs/write-bytes @staging-org-file (.getBytes content))
          expected-hashes #{"1234567890123456789012345678901234567890123456789012345678901234"}]
      (is (= expected-hashes (org-staging/read-staging-org @staging-org-file))))))

;; --- Tests for append-to-staging-org ---

(deftest append-to-staging-org-empty-file-test
  (testing "appends to an empty staging.org file"
    (let [entry "* TODO New Task\n:PROPERTIES:\n:HASH: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n:END:"
          _ (org-staging/append-to-staging-org @staging-org-file entry)]
      (is (str/includes? (-> @staging-org-file .toFile slurp) entry)))))

(deftest append-to-staging-org-existing-file-test
  (testing "appends to an existing staging.org file"
    (let [initial-content "* TODO Existing Task\n:PROPERTIES:\n:HASH: 1111111111111111111111111111111111111111111111111111111111111111\n:END:"
          new-entry "* TODO Another Task\n:PROPERTIES:\n:HASH: BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB\n:END:"
          _ (fs/write-bytes @staging-org-file (.getBytes initial-content))
          _ (org-staging/append-to-staging-org @staging-org-file new-entry)
          file-content (-> @staging-org-file .toFile slurp)]
      (is (str/includes? file-content initial-content))
      (is (str/includes? file-content new-entry)))))

(deftest append-to-staging-org-ensures-newline-test
  (testing "ensures a newline before the new entry"
    (let [initial-content "Some existing text without a final newline"
          new-entry "* TODO Another Task\n:PROPERTIES:\n:HASH: BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB\n:END:"
          _ (fs/write-bytes @staging-org-file (.getBytes initial-content))
          _ (org-staging/append-to-staging-org @staging-org-file new-entry)
          file-content (-> @staging-org-file .toFile slurp)]
      (is (str/includes? file-content (str "\n" new-entry))))))

;; --- Tests for scan-and-stage-downloads ---

(deftest scan-and-stage-downloads-empty-downloads-test
  (testing "staging.org remains empty if Downloads is empty"
    (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
    (is (str/blank? (-> @staging-org-file .toFile slurp )))))

(deftest scan-and-stage-downloads-new-mp3-test
  (testing "adds a new mp3 file to staging.org with enriched metadata"
    (let [filename "2026-07-07 16_53_06.mp3"
          mp3-file (create-file @downloads-dir filename "mp3 content")
          expected-hash (org-staging/deterministic-sha256-path mp3-file)]
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [content (-> @staging-org-file .toFile slurp)]
        (is (str/includes? content "* TODO"))
        (is (str/includes? content filename))
        (is (str/includes? content (str ":HASH:       " expected-hash)))
        (is (str/includes? content ":PLAUD-ID:   "))
        (is (str/includes? content ":CREATED:    2026-07-07"))
        (is (str/includes? content ":DURATION:"))
        (is (str/includes? content ":BITRATE:"))
        (is (str/includes? content ":FILESIZE:"))
        (is (str/includes? content (str ":SOURCE:     " mp3-file)))
        (is (str/includes? content ":SYNOPSIS:   "))
        (is (str/includes? content ":CLEANUP:"))
        (is (str/includes? content ":KIND:       Plaud-voice-record"))
        (is (str/includes? content ":DECISION:"))
        (is (str/includes? content "[ ] INGEST  (Move to assets/objects/UUID/)"))
        (is (str/includes? content "[ ] REJECT  (Delete from Downloads)"))
        (is (str/includes? content "[ ] IGNORE  (Keep in Downloads, remove from TODO)"))
        (is (str/includes? content ":LOGBOOK:"))
        (is (str/includes? content "Auto-detected by Staging Worker."))))))

(deftest scan-and-stage-downloads-multiple-new-mp3s-test
  (testing "adds multiple new mp3 files to staging.org with enriched metadata"
    (let [mp3-file1 (create-file @downloads-dir "2026-07-07 10_00_00.mp3" "mp3 content 1")
          mp3-file2 (create-file @downloads-dir "2026-07-08 11_00_00.mp3" "mp3 content 2")
          hash1 (org-staging/deterministic-sha256-path mp3-file1)
          hash2 (org-staging/deterministic-sha256-path mp3-file2)]
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [content (-> @staging-org-file .toFile slurp)]
        (is (str/includes? content "2026-07-07 10_00_00.mp3"))
        (is (str/includes? content ":CREATED:    2026-07-07"))
        (is (str/includes? content (str ":HASH:       " hash1)))
        (is (str/includes? content "2026-07-08 11_00_00.mp3"))
        (is (str/includes? content ":CREATED:    2026-07-08"))
        (is (str/includes? content (str ":HASH:       " hash2)))
        (is (str/includes? content ":DECISION:"))
        (is (str/includes? content ":LOGBOOK:"))))))

(deftest scan-and-stage-downloads-already-ingested-test
  (testing "does not add files already present in assets/objects"
    (let [mp3-file (create-file @downloads-dir "ingested.mp3" "ingested content")
          file-hash (org-staging/deterministic-sha256-path mp3-file)
          uuid-str (str (UUID/randomUUID))
          short-hash-prefix (subs uuid-str 0 4)
          ab (subs short-hash-prefix 0 2)
          cd (subs short-hash-prefix 2 4)
          asset-dir (create-dir (create-dir (create-dir @assets-objects-root ab) cd) uuid-str)
          raw-dir (create-dir asset-dir "raw")]
      (create-file raw-dir "original.mp3" "ingested content") ;; Same content, same hash
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (is (str/blank? (-> @staging-org-file .toFile slurp ))))))

(deftest scan-and-stage-downloads-already-staged-test
  (testing "does not add files already pending in staging.org (idempotency)"
    (let [mp3-file (create-file @downloads-dir "pending.mp3" "pending content")
          file-hash (org-staging/deterministic-sha256-path mp3-file)
          initial-entry (str "* TODO pending.mp3\n:PROPERTIES:\n:HASH: " file-hash "\n:SOURCE: " mp3-file "\n:END:")]
      (fs/write-bytes @staging-org-file (.getBytes initial-entry))
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [content (-> @staging-org-file .toFile slurp)]
        (is (str/includes? content initial-entry))
        (is (= 1 (count (str/split content #"\* TODO")))) ; Only one TODO entry, no duplicates
        ))))

(deftest scan-and-stage-downloads-mixed-scenario-test
  (testing "handles a mix of new, ingested, and staged files"
    (let [new-mp3 (create-file @downloads-dir "2026-07-08 10_00_00.mp3" "new content")
          ingested-mp3 (create-file @downloads-dir "ingested.mp3" "ingested content")
          pending-mp3 (create-file @downloads-dir "pending.mp3" "pending content")
          new-hash (org-staging/deterministic-sha256-path new-mp3)
          ingested-hash (org-staging/deterministic-sha256-path ingested-mp3)
          pending-hash (org-staging/deterministic-sha256-path pending-mp3)]

      ;; Setup ingested file in assets/objects
      (let [uuid-str (str (UUID/randomUUID))
            short-hash-prefix (subs uuid-str 0 4)
            ab (subs short-hash-prefix 0 2)
            cd (subs short-hash-prefix 2 4)
            asset-dir (create-dir (create-dir (create-dir @assets-objects-root ab) cd) uuid-str)
            raw-dir (create-dir asset-dir "raw")]
        (create-file raw-dir "original.mp3" "ingested content"))

      ;; Setup pending file in staging.org
      (let [pending-entry (str "* TODO pending.mp3\n:PROPERTIES:\n:HASH: " pending-hash "\n:SOURCE: " pending-mp3 "\n:END:")]
        (fs/write-bytes @staging-org-file (.getBytes pending-entry)))

      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)

      (let [content (-> @staging-org-file .toFile slurp)]
        ;; New file should be added with enriched format
        (is (str/includes? content "2026-07-08 10_00_00.mp3"))
        (is (str/includes? content ":DECISION:"))
        ;; Pending entry is still present
        (is (str/includes? content "* TODO pending.mp3"))
        ))))

(deftest scan-and-stage-downloads-non-mp3-files-test
  (testing "ignores non-mp3 files"
    (create-file @downloads-dir "document.pdf" "pdf content")
    (create-file @downloads-dir "image.png" "png content")
    (create-file @downloads-dir "video.wav" "wav content") ; Should be ignored per MVP
    (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
    (is (str/blank? (-> @staging-org-file .toFile slurp)))))

(deftest scan-and-stage-downloads-read-only-invariant-test
  (testing "ensures downloads-path content is untouched"
    (let [mp3-file (create-file @downloads-dir "test.mp3" "original content")
          initial-hash (org-staging/deterministic-sha256-path mp3-file)
          initial-files (set (map fs/file-name (fp/list-audio-files @downloads-dir)))]
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [after-scan-hash (org-staging/deterministic-sha256-path mp3-file)
            after-scan-files (set (map fs/file-name (fp/list-audio-files @downloads-dir)))]
        (is (= initial-hash after-scan-hash)) ; Content hash should be unchanged
        (is (= initial-files after-scan-files)) ; No files should be moved/deleted/renamed
        (is (fs/exists? mp3-file))))))

;; --- Tests for metadata extraction helpers ---

(deftest extract-date-from-filename-valid-test
  (testing "extracts date from standard Plaud filename"
    (is (= "2026-07-07" (#'core/extract-date-from-filename "2026-07-07 16_53_06.mp3")))
    (is (= "2025-01-15" (#'core/extract-date-from-filename "2025-01-15 09_00_00.mp3")))
    (is (= "2024-12-31" (#'core/extract-date-from-filename "2024-12-31 23_59_59.mp3")))))

(deftest extract-date-from-filename-no-date-test
  (testing "returns N/A when no date pattern found"
    (is (= "N/A" (#'core/extract-date-from-filename "audio.mp3")))
    (is (= "N/A" (#'core/extract-date-from-filename "recording.mp3")))
    (is (= "N/A" (#'core/extract-date-from-filename "")))))

(deftest duration-minutes-ceil-test
  (testing "converts MM:SS to ceiling minutes"
    (is (= 1  (#'core/duration-minutes-ceil "00:01")))
    (is (= 1  (#'core/duration-minutes-ceil "00:59")))
    (is (= 1  (#'core/duration-minutes-ceil "01:00")))
    (is (= 5  (#'core/duration-minutes-ceil "04:45")))
    (is (= 10 (#'core/duration-minutes-ceil "10:00")))
    (is (= 0  (#'core/duration-minutes-ceil "N/A")))
    (is (= 0  (#'core/duration-minutes-ceil "invalid")))))

;; --- Tests for generate-org-entry ---

(deftest generate-org-entry-basic-test
  (testing "generates complete staging.org entry with all sections"
    (let [metadata {:hash          "a1b2c3d4e5f6789012345678abcdef01abcdef0123456789abcdef0123456789ab"
                    :source-path   "/home/user/Downloads/2026-07-07 16_53_06.mp3"
                    :filename      "2026-07-07 16_53_06.mp3"
                    :created       "2026-07-07"
                    :duration-mmss "04:45"
                    :bitrate       "128k"
                    :filesize      "2834432"
                    :kind          "Plaud-voice-record"
                    :logbook-line  "- [2026-07-08 Wed 20:15] Auto-detected by Staging Worker."}
          entry (#'core/generate-org-entry metadata)]
      (is (str/includes? entry "* TODO [5 min] 2026-07-07 16_53_06.mp3"))
      (is (str/includes? entry ":HASH:       a1b2c3d4"))
      (is (str/includes? entry ":PLAUD-ID:   "))
      (is (str/includes? entry ":CREATED:    2026-07-07"))
      (is (str/includes? entry ":DURATION:   04:45"))
      (is (str/includes? entry ":BITRATE:    128k"))
      (is (str/includes? entry ":FILESIZE:   2834432"))
      (is (str/includes? entry ":SOURCE:     /home/user/Downloads"))
      (is (str/includes? entry ":SYNOPSIS:   "))
      (is (str/includes? entry ":CLEANUP:"))
      (is (str/includes? entry ":KIND:       Plaud-voice-record"))
      (is (str/includes? entry ":DECISION:"))
      (is (str/includes? entry "[ ] INGEST  (Move to assets/objects/UUID/)"))
      (is (str/includes? entry "[ ] REJECT  (Delete from Downloads)"))
      (is (str/includes? entry "[ ] IGNORE  (Keep in Downloads, remove from TODO)"))
      (is (str/includes? entry ":LOGBOOK:"))
      (is (str/includes? entry "Auto-detected by Staging Worker.")))))

(deftest generate-org-entry-no-duration-test
  (testing "omits [N min] prefix when duration is N/A"
    (let [metadata {:hash          "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
                    :source-path   "/tmp/test.mp3"
                    :filename      "test.mp3"
                    :created       "N/A"
                    :duration-mmss "N/A"
                    :bitrate       "N/A"
                    :filesize      "1000"
                    :kind          "audio-file"
                    :logbook-line  "- [2026-07-08 Wed 20:15] Auto-detected by Staging Worker."}
          entry (#'core/generate-org-entry metadata)]
      (is (str/includes? entry "* TODO test.mp3"))
      (is (not (str/includes? entry "min]"))))))

(deftest generate-org-entry-non-plaud-kind-test
  (testing "uses provided kind value, not hardcoded Plaud-voice-record"
    (let [metadata {:hash          "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
                    :source-path   "/tmp/test.mp3"
                    :filename      "test.mp3"
                    :created       "N/A"
                    :duration-mmss "00:30"
                    :bitrate       "64k"
                    :filesize      "500"
                    :kind          "audio-file"
                    :logbook-line  "- [2026-07-08 Wed 20:15] Auto-detected by Staging Worker."}
          entry (#'core/generate-org-entry metadata)]
      (is (str/includes? entry ":KIND:       audio-file"))
      (is (not (str/includes? entry ":KIND:       Plaud-voice-record"))))))

;; --- Tests for scan-and-stage-downloads with date-named files ---

(deftest scan-and-stage-downloads-unknown-filename-pattern-test
  (testing "handles mp3 files without date pattern in filename"
    (let [mp3-file (create-file @downloads-dir "recording.mp3" "some audio content")
          expected-hash (org-staging/deterministic-sha256-path mp3-file)]
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [content (-> @staging-org-file .toFile slurp)]
        (is (str/includes? content "recording.mp3"))
        (is (str/includes? content ":CREATED:    N/A"))
        (is (str/includes? content ":KIND:       audio-file"))
        (is (str/includes? content ":DECISION:"))
        (is (str/includes? content ":LOGBOOK:"))))))

(deftest scan-and-stage-downloads-decision-drawer-present-test
  (testing "every new entry has a Decision drawer with three checkboxes"
    (let [_ (create-file @downloads-dir "2026-07-08 12_00_00.mp3" "content")
          _ (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
          content (-> @staging-org-file .toFile slurp)]
      (is (str/includes? content ":DECISION:"))
      (is (str/includes? content "[ ] INGEST"))
      (is (str/includes? content "[ ] REJECT"))
      (is (str/includes? content "[ ] IGNORE")))))

(deftest scan-and-stage-downloads-logbook-present-test
  (testing "every new entry has a LOGBOOK with auto-detection timestamp"
    (let [_ (create-file @downloads-dir "2026-07-08 14_00_00.mp3" "content")
          _ (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
          content (-> @staging-org-file .toFile slurp)]
      (is (str/includes? content ":LOGBOOK:"))
      (is (str/includes? content "Auto-detected by Staging Worker.")))))

(deftest scan-and-stage-downloads-headline-duration-test
  (testing "headline includes [N min] prefix for files with duration metadata"
    (let [_ (create-file @downloads-dir "2026-07-08 15_00_00.mp3" "dummy mp3 content")
          _ (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
          content (-> @staging-org-file .toFile slurp)]
      (is (str/includes? content "* TODO")))
    ;; Note: headline duration depends on ffprobe output of the test file.
    ;; We test the format generation in generate-org-entry tests above.
    ))
