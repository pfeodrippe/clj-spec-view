(ns clj-spec-view.content-script
  (:require-macros [chromex.support :refer [runonce]])
  (:require [clj-spec-view.content-script.core :as core]))

(runonce
  (core/init!))
