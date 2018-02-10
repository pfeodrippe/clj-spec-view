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


(def reader (t/reader :json))
(def writer (t/writer :json))


;; Vars
(def background-port (runtime/connect))


;; Views
(defn main-cpt
  []
  (let [token (r/atom "")]
    [rc/v-box
     :gap "5px"
     :children
     [[rc/input-text
       :model token
       :placeholder "Access Token"
       :on-change #(reset! token %)]
      [rc/button
       :label "Save (locally)"
       :class "btn btn-outline-secondary"
       :on-click #(post-message! background-port (t/write writer
                                                          {:type :save-token
                                                           :msg @token}))]]]))

(defn frame-cpt []
  [rc/scroller
   :v-scroll :auto
   :height "100px"
   :width "300px"
   :padding "10px"
   :style {:background-color "#f6f6ef"}
   :child [main-cpt]])

(defn mountit []
  (r/render [frame-cpt] (aget (query "#main") 0)))


;; Message loop
(defn process-message! [message]
  (log "POPUP: got message:" message))

(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! []
  (run-message-loop! background-port))


;; Main entry point
(defn init! []
  (log "POPUP: init")
  (mountit)
  (connect-to-background-page!))
