(ns jstavel.plaud-downloader.core
  (:require ["playwright" :as pw]
            ["fs" :as fs]
            ["cheerio" :as cheerio]
            [clojure.string :as s]))

(defonce atom-frame (atom nil))
(defonce atom-page (atom nil))

(defn- ms->duration-string
  "Convert milliseconds to 'Xm Xs' format.
   Examples: 285000 -> '4m 45s', 53848 -> '53s'"
  [ms]
  (let [total-sec (quot ms 1000)
        minutes   (quot total-sec 60)
        seconds   (rem total-sec 60)]
    (if (zero? minutes)
      (str seconds "s")
      (str minutes "m " seconds "s"))))

(defn- ms->datetime-string
  "Convert Unix milliseconds to 'YYYY-MM-DD HH:MM:SS' string."
  [ms]
  (-> (js/Date. ms)
      .toISOString
      (s/replace #"T" " ")
      (s/replace #"\..*Z?" "")))

(defn- api-record->record
  "Convert a JS API response item to the PlaudAudioRecordsList contract format."
  [^js api-item]
  {:name     (.-filename api-item)
   :duration (ms->duration-string (.-duration api-item))
   :plaud-id (.-id api-item)
   :created  (ms->datetime-string (.-start_time api-item))
   :filesize (.-filesize api-item)})

(defn ^:async connect-to-browser [config]
  (try
    (let [endpoint (or (:endpoint config) "http://localhost:9222")
          ^js browser (await (.connectOverCDP pw/chromium endpoint))
          ^js context (-> browser (.contexts) (aget 0))]
      {:browser browser
       :context context})
    (catch js/Error e
      (let [msg (.-message e)]
        {:error (if (and (string? msg) (s/includes? msg "ECONNREFUSED"))
                  :connection-refused
                  :connection-failed)
         :message (str "Could not connect to browser: " (or msg "unknown error"))}))))

(defn- find-main-frame [page]
  (-> page
      .frames
      (.find (fn [fr] (s/includes? (.url fr) "web.plaud.ai")))))

(defn- ^:async wait-for-loading-to-finish [^js frame timeout-ms]
  (-> frame
      (.locator ".file-list-skeleton-item")
      .first
      (.waitFor (clj->js {:state "hidden" :timeout timeout-ms}))))

(defn ^:async fetch-audio-records-page [connection fetch-config]
  (try
    (let [^js context (:context connection)
          ^js page    (await (.newPage context))
          base-url (or (:base-url fetch-config) "https://web.plaud.ai")
          timeout-ms (or (:timeout-ms fetch-config) 15000)]
      (await (.goto page base-url (clj->js {:timeout timeout-ms})))
      (reset! atom-page page)
      (let [^js main-frame (find-main-frame page)
            current-page-url (await (.url page))
            timestamp (.toISOString (js/Date.))]
        (reset! atom-frame main-frame)
        (await (-> main-frame (.locator "text=/All Files/i") .click))
        (await (wait-for-loading-to-finish main-frame timeout-ms))
        {:html-content (await (.content page))
         :page-url current-page-url
         :timestamp timestamp}))
    (catch js/Error e
      (let [error-message (.-message e)
            error-keyword (cond
                            (s/includes? error-message "Timeout") :timeout
                            ;; Further specific Playwright error messages can be added here if identified
                            :else :fetch-failed)
            captured-page-html (if-let [^js current-page @atom-page]
                                 (try
                                   (await (.content current-page))
                                   (catch js/Error _
                                     nil)) ; Ignore errors during content extraction itself
                                 nil)]
        (merge
         {:error error-keyword
          :message (str "Could not fetch audio records page: " error-message)}
         (when captured-page-html
           {:page-html captured-page-html}))))))

(defn save-page-html
  "Saves HTML content to a file with a timestamped name.
   Input: HtmlPage map with :html-content, :page-url, :timestamp
   Output: SavePageResult with :output-path, :bytes-written or SavePageError"
  [html-page]
  (try
    (let [sanitized-ts (-> (:timestamp html-page)
                           (s/replace #":" "-"))
          output-path (str "plaud-records-" sanitized-ts ".html")
          content (:html-content html-page)]
      (fs/writeFileSync output-path content)
      {:output-path output-path
       :bytes-written (.-length content)})
    (catch js/Error e
      {:error :save-failed
       :message (str "Could not save page: " (.-message e))})))

(defn extract-records
  "Parses HTML content and extracts Plaud audio records.
   Input: HtmlPage + ExtractionPolicy (map with optional :parse-strategy)
   Output: PlaudAudioRecordsList {:records [...]} or ExtractionError"
  [html-page _extraction-policy]
  (try
    (let [$ (.load cheerio (:html-content html-page))
          items (.toArray ^js ($ "li.file-list-item"))
          total (count items)
          all-records (mapv
                        (fn [^js el]
                          {:name (.. ($ el) (find ".file-list-item__filename") (text) (trim))
                           :duration (.. ($ el) (find ".file-list-item__duration-column") (text) (trim))
                           :plaud-id (.attr ^js ($ el) "data-file-id")
                           :created (.. ($ el) (find ".file-list-item__date-column") (text) (trim))})
                        items)
          valid-records (filterv
                          (fn [r]
                            (and (not-empty (:name r))
                                 (not-empty (:plaud-id r))))
                          all-records)
          valid-count (count valid-records)]
      (cond
        (zero? total)
        {:error :parse-failed
         :message "No file-list-item elements found in the page"}

        (not= total valid-count)
        {:records valid-records
         :error :partial-parse
         :message (str "Only " valid-count " out of " total " records were fully parsed")
         :partial-records all-records}

        :else
        {:records valid-records}))
    (catch js/Error e
      {:error :parse-failed
       :message (str "Failed to parse HTML: " (.-message e))})))

(defn ^:async fetch-temp-url
  "Gets a temporary S3 download URL for a Plaud recording.
   Calls the Plaud API from browser context (cookies auto-included).
   Input: Playwright page (on Plaud domain), plaud-id string
   Output: {:plaud-id ... :temp-url ...} or {:error :temp-url-failed}"
  [^js page plaud-id]
  (try
    (let [^js response (await (.evaluate page
                           (str "fetch('https://api-euc1.plaud.ai/file/temp-url/"
                                plaud-id
                                "',{credentials:'include',"
                                "headers:{'edit-from':'web','app-platform':'web'}})"
                                ".then(r => r.json())")))]
      (if (and response (.-temp_url response))
        {:plaud-id plaud-id
         :temp-url (.-temp_url response)}
        {:error   :temp-url-failed
         :message (str "No temp URL for " plaud-id ": "
                       (js/JSON.stringify response))}))
    (catch js/Error e
      {:error   :temp-url-failed
       :message (str "Failed to get temp URL: " (.-message e))})))

(defn ^:async download-file-from-url
  "Downloads a binary file from a URL to the specified output path.
   The URL is a pre-signed S3 URL — no auth needed.
   Uses Node.js fetch to download the binary content.
   Returns {:output-path ... :bytes-written N} or {:error :download-failed}"
  [url output-path]
  (try
    (let [^js response (await (js/fetch url))]
      (if (.-ok response)
        (let [buffer (await (.arrayBuffer response))
              data   (js/Buffer.from buffer)]
          (fs/writeFileSync output-path data)
          {:output-path   output-path
           :bytes-written (.-length data)})
        {:error   :download-failed
         :message (str "HTTP " (.-status response) " " (.-statusText response))}))
    (catch js/Error e
      {:error   :download-failed
       :message (str "Failed to download file: " (.-message e))})))

(defn ^:async download-recording-by-id
  "Downloads a single Plaud recording by its ID.
   Step 1: Get temporary S3 URL via Plaud API.
   Step 2: Download the file to {output-dir}/{plaud-id}.mp3.
   Returns {:plaud-id ... :output-path ... :bytes-written N} or error map."
  [^js page plaud-id output-dir]
  (let [temp-result (await (fetch-temp-url page plaud-id))]
    (if (:error temp-result)
      temp-result
      (let [filename (str plaud-id ".mp3")
            out-path (str output-dir "/" filename)
            dl-result (await (download-file-from-url (:temp-url temp-result) out-path))]
        (if (:error dl-result)
          dl-result
          (assoc dl-result :plaud-id plaud-id))))))

(defn ^:async download-recording
  "Downloads a Plaud recording with duplicate detection.
   Names files by plaud-id: '8acb9405...mp3'.
   Skips download if the file already exists in output-dir.
   Input: page, record map {:plaud-id ...}, output-dir
   Returns {:plaud-id ... :output-path ... :bytes-written N :skipped? bool}"
  [^js page {:keys [plaud-id]} output-dir]
  (let [out-path   (str output-dir "/" plaud-id ".mp3")
        local-stat (try (.statSync fs out-path) (catch js/Error _ nil))]
    (if local-stat
      (do (tap> (str "SKIP " plaud-id " (" (.-size local-stat) " bytes)"))
          {:plaud-id      plaud-id
           :output-path   out-path
           :bytes-written (.-size local-stat)
           :skipped?      true})
      (do (tap> (str "DOWNLOAD " plaud-id " ..."))
          (let [temp-result (await (fetch-temp-url page plaud-id))]
            (if (:error temp-result)
              (do (tap> (str "ERROR temp-url: " (:message temp-result)))
                  temp-result)
              (let [dl-result (await (download-file-from-url (:temp-url temp-result) out-path))]
                (if (:error dl-result)
                  (do (tap> (str "ERROR download: " (:message dl-result)))
                      dl-result)
                  (do (tap> (str "OK " plaud-id " (" (:bytes-written dl-result) " bytes)"))
                      (assoc dl-result :plaud-id plaud-id))))))))))

(declare fetch-records-via-api)

(defn ^:async download-all-new-recordings
  "Downloads all recordings from the Plaud API that don't yet exist locally.
   Gets full record list, checks file existence, downloads missing ones.
   Input: page (on Plaud domain), output-dir
   Returns {:downloaded N :skipped N :errors [...]}"
  [^js page output-dir & {:keys [batch-limit]}]
  (let [result (await (fetch-records-via-api page))]
    (if (:error result)
      result
      (let [total (:total result)]
        (tap> (str "BATCH: " total " total records"
                              (when batch-limit (str " (limit " batch-limit ")"))))
        (loop [remaining  (:records result)
               downloaded 0
               skipped    0
               errors     []]
          (if (or (empty? remaining)
                  (and batch-limit (>= downloaded batch-limit)))
            (do (tap> (str "BATCH DONE: " downloaded " downloaded, " skipped " skipped, " (count errors) " errors"))
                {:total      total
                 :downloaded downloaded
                 :skipped    skipped
                 :errors     errors
                 :stopped-by (when (and batch-limit (>= downloaded batch-limit))
                               :batch-limit)})
            (let [n        (+ downloaded skipped)
                  record   (first remaining)
                  _        (when (zero? (mod n 10))
                              (tap> (str "PROGRESS " n "/" total " (" downloaded " downloaded, " skipped " skipped)")))
                  dl-result (await (download-recording page record output-dir))]
            (if (:error dl-result)
              (recur (rest remaining) downloaded skipped (conj errors dl-result))
              (if (:skipped? dl-result)
                (recur (rest remaining) downloaded (inc skipped) errors)
                (recur (rest remaining) (inc downloaded) skipped errors))))))))))

(defn ^:async fetch-records-via-api
  "Fetches all Plaud audio records via the API from within the browser context.
   The page must be on web.plaud.ai domain (for cookie origin).
   Calls page.evaluate with fetch() — auth cookies are sent automatically.
   Returns {:records [...] :total N} or {:error :api-failed :message \"...\"}"
  [^js page]
  (try
    (tap> "API: fetching all records...")
    (let [^js api-response (await (.evaluate page
                               (str "fetch('https://api-euc1.plaud.ai/file/simple/web"
                                    "?skip=0&limit=99999&is_trash=2"
                                    "&sort_by=start_time&is_desc=true',"
                                    "{credentials:'include',"
                                    "headers:{'edit-from':'web','app-platform':'web'}})"
                                    ".then(r => r.json())")))]
      (if (and api-response (.-data_file_list api-response))
        (let [^js items   (.-data_file_list api-response)
              total      (.-data_file_total api-response)
              records    (mapv api-record->record (array-seq items))]
          {:records records
           :total   total})
        {:error   :api-failed
         :message (str "Unexpected API response: " (js/JSON.stringify api-response))}))
    (catch js/Error e
      {:error   :api-failed
       :message (str "API fetch failed: " (.-message e))})))

(defn ^:async run-download!
  "Orchestrates the full Plaud download pipeline.
   Primary: connect -> navigate to domain -> fetch records via API.
   Fallback: HTML extraction path (fetch page -> save HTML -> extract records).
   Returns {:records [...] :total N} or error map."
  [remote-debug-port]
  (let [endpoint (str "http://localhost:" remote-debug-port)
        conn     (await (connect-to-browser {:endpoint endpoint}))]
    (if (:error conn)
      conn
      (let [^js context (:context conn)
            ^js page    (await (.newPage context))]
        (try
          (await (.goto page "https://web.plaud.ai" (clj->js {:timeout 30000})))
          (reset! atom-page page)
          (let [api-result (await (fetch-records-via-api page))]
            (if (:error api-result)
              (let [page-result (await (fetch-audio-records-page conn
                                            {:base-url   "https://web.plaud.ai"
                                             :timeout-ms 30000}))]
                (if (:error page-result)
                  page-result
                  (let [save-result (save-page-html page-result)]
                    (if (:error save-result)
                      (assoc save-result :records [])
                      (merge (extract-records page-result {:parse-strategy :plaud-web-default})
                             save-result)))))
              (assoc api-result :source :api)))
          (catch js/Error _
            (let [page-result (await (fetch-audio-records-page conn
                                          {:base-url   "https://web.plaud.ai"
                                           :timeout-ms 30000}))]
              (if (:error page-result)
                page-result
                (let [save-result (save-page-html page-result)]
                  (if (:error save-result)
                    (assoc save-result :records [])
                    (merge (extract-records page-result {:parse-strategy :plaud-web-default})
                           save-result)))))))))))

(defn- parse-args
  "Parse CLI arguments. Returns {:plaud-id ... :batch-limit ...}"
  [args]
  (loop [remaining args
         batch-limit nil
         positional  []]
    (if (empty? remaining)
      {:plaud-id    (first positional)
       :batch-limit batch-limit}
      (case (first remaining)
        "--batch-limit" (recur (drop 2 remaining)
                               (js/parseInt (second remaining))
                               positional)
        (recur (rest remaining)
               batch-limit
               (conj positional (first remaining)))))))

(defn ^:async -main [& args]
  (let [{:keys [plaud-id batch-limit]} (parse-args args)
        home        (aget js/process.env "HOME")
        out-dir     (str home "/Downloads")
        conn        (await (connect-to-browser {:endpoint "http://localhost:9222"}))]
    (if (:error conn)
      (tap> (str "ERROR Connection failed: " (pr-str (:error conn))))
      (let [^js ctx  (:context conn)
            ^js page (await (.newPage ctx))]
        (await (.goto page "https://web.plaud.ai" (clj->js {:timeout 15000})))
        (reset! atom-page page)
        (if plaud-id
          (let [result (await (download-recording-by-id page plaud-id out-dir))]
            (tap> (if (:error result)
                               (str "ERROR " (:message result))
                               (str "DOWNLOADED " (:plaud-id result) " -> " (:output-path result)
                                    " (" (:bytes-written result) " bytes)"))))
          (let [{:keys [downloaded skipped errors stopped-by]} (await (download-all-new-recordings page out-dir :batch-limit batch-limit))]
            (tap> (str "BATCH COMPLETE: " downloaded " downloaded, " skipped " skipped"
                                  (when stopped-by " (stopped by batch-limit)")
                                  ", " (count errors) " errors")))))))
  (.on js/process "SIGINT" #(js/process.exit 0))
  nil)

(comment
  (-main)
  )
