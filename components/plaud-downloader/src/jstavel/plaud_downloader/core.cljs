(ns jstavel.plaud-downloader.core
  (:require ["playwright" :as pw]
            ["fs" :as fs]
            ["cheerio" :as cheerio]
            [clojure.string :as s]))

(defonce atom-frame (atom nil))
(defonce atom-page (atom nil))

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

(defn ^:async run-download!
  "Orchestrates the full Plaud download pipeline:
   connect -> fetch page -> save HTML -> extract records.
   Returns {:records [...] :output-path \"...\"} or an error map."
  [remote-debug-port]
  (let [endpoint (str "http://localhost:" remote-debug-port)
        conn (await (connect-to-browser {:endpoint endpoint}))]
    (if (:error conn)
      conn
      (let [page-result (await (fetch-audio-records-page conn {:base-url "https://web.plaud.ai" :timeout-ms 30000}))]
        (if (:error page-result)
          page-result
          (let [save-result (save-page-html page-result)]
            (if (:error save-result)
              (assoc save-result :records [])
              (merge (extract-records page-result {:parse-strategy :plaud-web-default})
                     save-result))))))))

(defn ^:async -main [& _args]
  (let [result (await (run-download! 9222))]
    (tap> (if (:error result)
            {:msg "Download failed" :data result}
            {:msg (str "Download completed - " (count (:records result)) " records")
             :output-path (:output-path result)
             :records (:records result)})))
  (.on js/process "SIGINT" #(js/process.exit 0))
  nil)

(comment
  (-main)
  )
