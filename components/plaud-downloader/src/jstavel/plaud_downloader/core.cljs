(ns jstavel.plaud-downloader.core
  (:require ["playwright" :as pw]
            ["fs" :as fs]
            ["cheerio" :as cheerio]
            [portal.api :as p]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            
            [clojure.string :as s]))

;; Funkce pro výpis DOMu podobná tvému Python skriptu
(add-tap p/submit)

;; 2. Otevři Portal pouze v případě, že ještě není otevřený
(when (empty? (p/sessions))
  (p/open))

(defn clean-html [raw-html]
  ;; Načteme surové HTML do cheerio objektu
  (let [$ (.load cheerio raw-html)]
    (doto ($ "html")
      ;; 1. Odstraníme kompletně skripty a inline styly
      (-> (.find "script, style") .remove) 
      ;; 2. Vyčistíme vnitřky SVG, ale necháme samotný tag (kvůli třídám)
      (-> (.find "svg") .empty) 
      ;; 3. Projdeme všechny elementy a smažeme inline 'style' atributy
      (-> (.find "*") (.removeAttr "style")))
    (.html $)))

(defonce page-atom (atom nil))
(defonce frame-atom (atom nil))
(defonce html-atom (atom nil))
(defonce browser-atom (atom nil))
(defonce contexts-atom (atom nil))

(comment
  @frame-atom
  @page-atom
  @contexts-atom
  (tap> @html-atom)
  (reset! frame-atom nil)
  (defn ^:async connect! []
    (try
      (let [browser (await (.connectOverCDP (.-chromium pw) "http://localhost:9222"))
            page (-> browser .contexts (aget 0) .pages (aget 0))
            ]
        (reset! page-atom page)
        (-> pw (.-chromium) (.connectOverCDP "http://localhost:9222") (.then #(reset! browser-atom %)))
        (.-chromium pw)
        (js/console.log pw)
        (reset! browser-atom browser)
        (tap> "✅ PŘIPOJENO K PROHLÍŽEČI!"))
      (catch :default e
        (tap> (str "❌ CHYBA:" e)))))
  
  (-> pw
      .-chromium
      (.connectOverCDP "http://localhost:9222")
      (.then #(reset! browser-atom %)))
  
  (-> @browser-atom
      (.contexts)
      (aget 0)
      (.newPage)
      (.then #(reset! page-atom %))
      )

  (-> @page-atom
      (.goto "https://web.plaud.ai")
      (.then #(tap> "hotovo")))
  
  (tap> "dalsi kolo")
  ;; linka pro detail: https://web.plaud.ai/file/b89275db1902933a507fd5ebbce45b6c
  (go
    (let [main-frame (-> @page-atom
                         .frames
                         (.find (fn [fr] (-> fr .url (clojure.string/includes? "web.plaud.ai")))))]
      (tap> {:msg "Zahajuji stahování a čištění stránek..."})
      (let [raw-html (<p! (.content main-frame))]
        (.writeFileSync fs "main-frame.hml" raw-html)
        (tap> "zapsano do main-frame.html"))
      (let [all-files-btn (.locator main-frame "text=/All Files/i")]
        (<p! (.click all-files-btn))
        )
      (let [raw-html (<p! (.content main-frame))]
        (.writeFileSync fs "all-files-frame.hml" raw-html)
        (tap> "zapsano do all-files-frame.html"))
      )
    )
  
  (.locator @frame-atom "text=/All Files/i")
  
  (-> @page-atom (.frames) (aget 0) (->> (reset! frame-atom)))
  (-> @page-atom .frames (aget 0) (->> (reset! frame-atom)))
  (-> @page-atom (.frames) (aget 0) (->> (reset! frame-atom)))
  (-> @frame-atom (.content) (.then (partial reset! html-atom)))

  (-> @page-atom
      .frames
      alength
      )


  )

(defn ^:async -main [& _args]
  (let [^js browser (await (.connectOverCDP pw/chromium "http://localhost:9222"))
        ^js context (-> browser .contexts (aget 0))
        ^js page (-> context .pages (aget 0))]

    (if (nil? page)
      (println "CHYBA: Nenašel jsem stránku.")
      (do
        (println "Spouštím diagnostiku DOMu...")
        
        ;; Počkáme chvilku, ať se stránka dokreslí
        (await (.waitForTimeout page 2000))
        
        ;; Provedeme dump
        ;;(await (dump-dom-elements page))
        
        (println "Hotovo.")
        (.on js/process "SIGINT" #(do (js/process.exit 0)))))
    
    ;; TADY JE TEN TRIK: 
    ;; Nenecháme funkci skončit. REPL bude běžet, protože Node proces neskončil.
    ;;(js/setInterval (fn [] (js/console.log "Proces běží, REPL je aktivní...")) 60000)
    ))

