(ns jstavel.org-staging.interface-test
  (:require
   [clojure.test :refer [deftest is use-fixtures testing]]
   [clojure.string :as str]
   [jstavel.org-staging.interface :as org-staging]
   [jstavel.fp.interface :as fp]
   )
  (:import
   (java.nio.file Files Path Paths LinkOption)
   (java.nio.file.attribute FileTime FileAttribute)
   (java.util UUID)))

(def ^:private temp-root-dir (atom nil))
(def ^:private downloads-dir (atom nil))
(def ^:private assets-objects-root (atom nil))
(def ^:private staging-org-file (atom nil))

(defn- delete-recursively [^Path path]
  (when (Files/exists path (into-array LinkOption []))
    (if (Files/isDirectory path (into-array LinkOption []))
      (with-open [stream (Files/walk path (into-array java.nio.file.FileVisitOption []))]
        (doseq [p (->> (iterator-seq (.iterator stream))
                       (sort (java.util.Comparator/reverseOrder)))]
          (Files/delete p)))
      (Files/delete path))))

(defn- create-file [parent-path filename content]
  (let [^Path file-path (.resolve parent-path filename)]
    (Files/createDirectories (.getParent file-path) (into-array FileAttribute []))
    (Files/write file-path (.getBytes content) (into-array java.nio.file.OpenOption []))
    (Files/setLastModifiedTime file-path (FileTime/fromMillis 0)) ; Ensure consistent modification time
    file-path))

(defn- create-dir [parent-path dirname]
  (let [^Path dir-path (.resolve parent-path dirname)]
    (Files/createDirectories dir-path (into-array FileAttribute []))
    dir-path))

(defn- setup-temp-env [f]
  (let [root (Files/createTempDirectory "gtd-org-test-" (into-array FileAttribute []))]
    (reset! temp-root-dir root)
    (reset! downloads-dir (create-dir root "Downloads"))
    (reset! assets-objects-root (create-dir root (Paths/get "assets" "objects")))
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
    (let [non-existent-path (Paths/get (.toString @temp-root-dir) "non-existent-staging.org")]
      (is (empty? (org-staging/read-staging-org non-existent-path))))))

(deftest read-staging-org-with-hashes-test
  (testing "extracts hashes from TODO entries"
    (let [content "* TODO Task 1\n:PROPERTIES:\n:HASH: 1234567890123456789012345678901234567890123456789012345678901234\n:END:\n\n* TODO Task 2\n:PROPERTIES:\n:HASH: abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd\n:END:\n* DONE Task 3 (should be ignored)\n:PROPERTIES:\n:HASH: 0000000000000000000000000000000000000000000000000000000000000000\n:END:"
          _ (Files/write @staging-org-file (.getBytes content) (into-array java.nio.file.OpenOption []))
          expected-hashes #{"1234567890123456789012345678901234567890123456789012345678901234"
                            "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd"}]
      (is (= expected-hashes (org-staging/read-staging-org @staging-org-file))))))

(deftest read-staging-org-with-no-hashes-test
  (testing "ignores entries without a HASH property"
    (let [content "* TODO Task 1\n:PROPERTIES:\n:NO_HASH: value\n:END:\n\n* TODO Task 2\n:PROPERTIES:\n:HASH: 1234567890123456789012345678901234567890123456789012345678901234\n:END:"
          _ (Files/write @staging-org-file (.getBytes content) (into-array java.nio.file.OpenOption []))
          expected-hashes #{"1234567890123456789012345678901234567890123456789012345678901234"}]
      (is (= expected-hashes (org-staging/read-staging-org @staging-org-file))))))

(deftest read-staging-org-malformed-hashes-test
  (testing "ignores malformed HASH properties"
    (let [content "* TODO Task 1\n:PROPERTIES:\n:HASH: not-a-hash\n:END:\n\n* TODO Task 2\n:PROPERTIES:\n:HASH: 1234567890123456789012345678901234567890123456789012345678901234\n:END:"
          _ (Files/write @staging-org-file (.getBytes content) (into-array java.nio.file.OpenOption []))
          expected-hashes #{"1234567890123456789012345678901234567890123456789012345678901234"}]
      (is (= expected-hashes (org-staging/read-staging-org @staging-org-file))))))

;; --- Tests for append-to-staging-org ---

(deftest append-to-staging-org-empty-file-test
  (testing "appends to an empty staging.org file"
    (let [entry "* TODO New Task\n:PROPERTIES:\n:HASH: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n:END:"
          _ (org-staging/append-to-staging-org @staging-org-file entry)]
      (is (str/includes? (slurp @staging-org-file) entry)))))

(deftest append-to-staging-org-existing-file-test
  (testing "appends to an existing staging.org file"
    (let [initial-content "* TODO Existing Task\n:PROPERTIES:\n:HASH: 1111111111111111111111111111111111111111111111111111111111111111\n:END:"
          new-entry "* TODO Another Task\n:PROPERTIES:\n:HASH: BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB\n:END:"
          _ (Files/write @staging-org-file (.getBytes initial-content) (into-array java.nio.file.OpenOption []))
          _ (org-staging/append-to-staging-org @staging-org-file new-entry)
          file-content (slurp @staging-org-file)]
      (is (str/includes? file-content initial-content))
      (is (str/includes? file-content new-entry)))))

(deftest append-to-staging-org-ensures-newline-test
  (testing "ensures a newline before the new entry"
    (let [initial-content "Some existing text without a final newline"
          new-entry "* TODO Another Task\n:PROPERTIES:\n:HASH: BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB\n:END:"
          _ (Files/write @staging-org-file (.getBytes initial-content) (into-array java.nio.file.OpenOption []))
          _ (org-staging/append-to-staging-org @staging-org-file new-entry)
          file-content (slurp @staging-org-file)]
      (is (str/includes? file-content (str "\n" new-entry))))))

;; --- Tests for scan-and-stage-downloads ---

(deftest scan-and-stage-downloads-empty-downloads-test
  (testing "staging.org remains empty if Downloads is empty"
    (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
    (is (str/blank? (slurp @staging-org-file)))))

(deftest scan-and-stage-downloads-new-mp3-test
  (testing "adds a new mp3 file to staging.org"
    (let [mp3-file (create-file @downloads-dir "audio.mp3" "mp3 content")
          expected-hash (org-staging/deterministic-sha256-path mp3-file)]
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [content (slurp @staging-org-file)]
        (is (str/includes? content "audio.mp3"))
        (is (str/includes? content (str ":HASH: " expected-hash)))
        (is (str/includes? content (str ":SOURCE: " (.toString mp3-file))))))))

(deftest scan-and-stage-downloads-multiple-new-mp3s-test
  (testing "adds multiple new mp3 files to staging.org"
    (let [mp3-file1 (create-file @downloads-dir "audio1.mp3" "mp3 content 1")
          mp3-file2 (create-file @downloads-dir "audio2.mp3" "mp3 content 2")
          hash1 (org-staging/deterministic-sha256-path mp3-file1)
          hash2 (org-staging/deterministic-sha256-path mp3-file2)]
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [content (slurp @staging-org-file)]
        (is (str/includes? content "audio1.mp3"))
        (is (str/includes? content (str ":HASH: " hash1)))
        (is (str/includes? content "audio2.mp3"))
        (is (str/includes? content (str ":HASH: " hash2)))))))

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
      (is (str/blank? (slurp @staging-org-file))))))

(deftest scan-and-stage-downloads-already-staged-test
  (testing "does not add files already pending in staging.org (idempotency)"
    (let [mp3-file (create-file @downloads-dir "pending.mp3" "pending content")
          file-hash (org-staging/deterministic-sha256-path mp3-file)
          initial-entry (str "* TODO pending.mp3\n:PROPERTIES:\n:HASH: " file-hash "\n:SOURCE: " (.toString mp3-file) "\n:END:")]
      (Files/write @staging-org-file (.getBytes initial-entry) (into-array java.nio.file.OpenOption []))
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [content (slurp @staging-org-file)]
        (is (str/includes? content initial-entry))
        (is (= 1 (count (str/split content #"\* TODO")))) ; Only one TODO entry, no duplicates
        ))))

(deftest scan-and-stage-downloads-mixed-scenario-test
  (testing "handles a mix of new, ingested, and staged files"
    (let [new-mp3 (create-file @downloads-dir "new.mp3" "new content")
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
      (let [pending-entry (str "* TODO pending.mp3\n:PROPERTIES:\n:HASH: " pending-hash "\n:SOURCE: " (.toString pending-mp3) "\n:END:")]
        (Files/write @staging-org-file (.getBytes pending-entry) (into-array java.nio.file.OpenOption [])))

      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)

      (let [content (slurp @staging-org-file)]
        ;; Only the new.mp3 should be added
        (is (str/includes? content "new.mp3"))
        (is (str/includes? content (str ":HASH: " new-hash)))
        ;; Ingested and pending should not be duplicated
        (is (not (str/includes? content (str "* TODO ingested.mp3"))))
        (is (str/includes? content (str "* TODO pending.mp3"))) ; Still there from initial setup
        (is (= 2 (count (str/split content #"\* TODO")))) ; Initial pending + new one
        ))))

(deftest scan-and-stage-downloads-non-mp3-files-test
  (testing "ignores non-mp3 files"
    (create-file @downloads-dir "document.pdf" "pdf content")
    (create-file @downloads-dir "image.png" "png content")
    (create-file @downloads-dir "video.wav" "wav content") ; Should be ignored per MVP
    (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
    (is (str/blank? (slurp @staging-org-file)))))

(deftest scan-and-stage-downloads-read-only-invariant-test
  (testing "ensures downloads-path content is untouched"
    (let [mp3-file (create-file @downloads-dir "test.mp3" "original content")
          initial-hash (org-staging/deterministic-sha256-path mp3-file)
          initial-files (set (map #(.getFileName %) (fp/list-audio-files @downloads-dir)))]
      (org-staging/scan-and-stage-downloads @downloads-dir @assets-objects-root @staging-org-file)
      (let [after-scan-hash (org-staging/deterministic-sha256-path mp3-file)
            after-scan-files (set (map #(.getFileName %) (fp/list-audio-files @downloads-dir)))]
        (is (= initial-hash after-scan-hash)) ; Content hash should be unchanged
        (is (= initial-files after-scan-files)) ; No files should be moved/deleted/renamed
        (is (Files/exists mp3-file (into-array LinkOption [])))))))
