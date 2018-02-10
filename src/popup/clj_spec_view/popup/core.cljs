(ns clj-spec-view.popup.core
  (:import [goog.dom query])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [reagent.core :as r]
            [re-com.core :as rc]
            [cognitect.transit :as t]))


;; Transit
(def reader (t/reader :json))
(def writer (t/writer :json))


;; Vars
(def background-port (runtime/connect))
(def token (r/atom ""))


;; Views
(defn main-cpt
  []
  [rc/v-box
   :gap "10px"
   :children
   [[rc/title
     :label "Github Token"
     :style {:font-size "14px"}]
    [rc/input-text
     :model token
     :placeholder "Access Token"
     :style {:font-size "12px"}
     :on-change #(reset! token %)]
    [rc/button
     :label "Save (locally)"
     :class "btn btn-outline-secondary"
     :style {:font-size "12px"}
     :on-click #(post-message! background-port (t/write writer
                                                        {:type :save-token
                                                         :msg @token}))]]])

(defn frame-cpt []
  [rc/scroller
   :v-scroll :auto
   :height "120px"
   :width "270px"
   :padding "10px"
   :style {:background-color "#f6f6ef"}
   :child [main-cpt]])

(defn mountit! []
  (r/render [frame-cpt] (aget (query "#main") 0)))


;; Message loop
(defn handle-token! [tk]
  (reset! token tk))

(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-some [message (t/read reader (<! message-channel))]
      (log message)
      (case (:type message)
        :fetch-token-res (handle-token! (:msg message))
        :default nil)
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! []
  (post-message! background-port (t/write writer {:type :fetch-token}))
  (run-message-loop! background-port))


;; Main entry point
(defn init! []
  (log "POPUP: init")
  (mountit!)
  (connect-to-background-page!))
