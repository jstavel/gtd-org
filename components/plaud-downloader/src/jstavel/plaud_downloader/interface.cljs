(ns jstavel.plaud-downloader.interface
  (:require [jstavel.plaud-downloader.core :as core]))

(defn run-download!
  "Public entry point that follows the contracts defined in docs/contracts/.
   Accepts a map with optional :remote-debug-port (default 9222) and returns
   a promise that resolves to {:records [...] :output-path <string>}."
  ;; AI? take a look at workflow.hcl. Find which contracts are used here
  [opts]
  (core/run-download! (or (:remote-debug-port opts) 9222)))
