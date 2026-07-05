(ns jstavel.plaud-downloader.core
  (:require ["playwright" :as pw]
            ["fs" :as fs]
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
      (.find (fn [fr] (s/includes? (.-url fr) "web.plaud.ai")))))

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
          timeout-ms (or (:timeout-ms fetch-config) 15000)
          
          _ (await (.goto page base-url (clj->js {:timeout timeout-ms})))
          
          ^js main-frame (find-main-frame page)
          current-page-url (await (.url page))
          timestamp (js/Date. (.toISOString (js/Date.))) ; Generate ISO 8601 timestamp

          _ (reset! atom-page page)
          _ (reset! atom-frame main-frame)]
      
      (await (-> main-frame (.locator "text=/All Files/i") .click))
      (await (wait-for-loading-to-finish main-frame timeout-ms)) ; Pass timeout
      
      {:html-content (await (.content page))
       :page-url current-page-url
       :timestamp timestamp})
    
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


(defn ^:async -main [& _args]
  (let [conn (await (connect-to-browser {:endpoint "http://localhost:9222"}))]
    (if (:context conn)
      (let [fetch-result (await (fetch-audio-records-page conn {:base-url "https://web.plaud.ai" :timeout-ms 30000}))] ; Pass fetch-config
        (if (:html-content fetch-result) ; Changed from :html to :html-content
          (let [output-file "all-files-page.html"]
            (fs/writeFileSync output-file (:html-content fetch-result)) ; Changed from :html to :html-content
            (tap> (str "page with list of audio records was saved at " output-file)))
          (tap> {:msg "Failed to fetch page" :error fetch-result})))
      (tap> {:msg "Failed to connect to browser" :error conn})))
  (tap> {:msg "Zahajuji stahovani stranek"})
  (.on js/process "SIGINT" #(do (js/process.exit 0)))
  nil)

(comment
  (-main)
  @atom-frame
  )
