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
      ;; -- let block with all-files-button. It will be take by .locator ("text=/All Files/i") AI!
      (let [all-files-button (await (-> @atom-frame (.locator "text=/All files/i")))
            _ (await (.click all-files-button))
            ]
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
