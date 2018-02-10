(ns clj-spec-view.popup
  (:require-macros [chromex.support :refer [runonce]])
  (:require [clj-spec-view.popup.core :as core]))

(runonce
  (core/init!))
