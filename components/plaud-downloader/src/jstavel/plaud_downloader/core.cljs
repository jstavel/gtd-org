(ns jstavel.plaud-downloader.core
  (:require ["playwright" :as pw]
            ["fs" :as fs]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [clojure.string :as s]))

(defonce atom-frame (atom nil))
(defonce atom-page (atom nil))

(defn ^:async -main [& _args]
  (let [^js browser (await (.connectOverCDP pw/chromium "http://localhost:9222"))
        ^js context (-> browser (.contexts) (aget 0))]
    (let [^js page (await (.newPage context))
          _ (await (-> page (.goto "https://web.plaud.ai")))
          main-frame (-> page
                         .frames
                         (.find (fn [fr] (-> fr .url (s/includes? "web.plaud.ai")))))]
      (reset! atom-page page)
      (reset! atom-frame main-frame)
      (let [all-files-button (await (-> @atom-frame (.locator "text=/All Files/i")))
            _ (await (.click all-files-button))
            html-content (await (.content page))]
        ;; -- vytahni "all-file-page.html" to promenne a vsechny retezce nahrad touto promennout AI!
        (fs/writeFileSync "all-files-page.html" html-content)
        (tap> "page with list of audio records was saved at all-files-page.html")
        )
      )
    (tap> {:msg "Zahajuji stahovani stranek"})
    )
  (.on js/process "SIGINT" #(do (js/process.exit 0)))
  )

(comment
  (-main)
  @atom-frame
  )
