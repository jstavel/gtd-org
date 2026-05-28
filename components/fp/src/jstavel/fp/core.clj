(ns jstavel.fp.core
  (:require [clojure.string :as str])
  (:import
   (java.nio.file Files Path FileVisitOption LinkOption)
   (java.util.function Predicate)
   (java.security MessageDigest)))

