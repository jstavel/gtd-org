(ns jstavel.plaud-downloader.interface
  (:require [jstavel.plaud-downloader.core :as core]))

(defn run-download!
  "Public entry point that follows the contracts defined in docs/contracts/.
   Accepts a map with optional :remote-debug-port (default 9222) and returns
   a promise that resolves to {:records [...] :output-path <string>}.
   
   Contracts used:
     BrowserConnectionConfig (input)  - see docs/contracts/browser_connection_config.md
     PlaudAudioRecordsList (output)   - see docs/contracts/plaud_audio_records_list.md
     SavePageResult (output)          - see docs/contracts/save_page_result.md"
  [opts]
  (core/run-download! (or (:remote-debug-port opts) 9222)))
