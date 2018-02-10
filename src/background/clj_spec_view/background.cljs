(ns clj-spec-view.background
  (:require-macros [chromex.support :refer [runonce]])
  (:require [clj-spec-view.background.core :as core]))

(runonce
  (core/init!))
