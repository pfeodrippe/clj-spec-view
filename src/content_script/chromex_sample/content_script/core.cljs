(ns chromex-sample.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [hiccups.core :refer [html]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [clojure.string :as str]
            [hiccups.runtime :as hiccupsrt]
            [dommy.core :refer-macros [sel sel1]]
            [domina.xpath :refer [xpath]]
            [domina.events :as de]
            [domina :as d]))

; -- a message loop --

(defn process-message! [message]
  (log "CONTENT SCRIPT: got message:" message))

(defn run-message-loop! [message-channel]
  (log "CONTENT SCRIPT: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "CONTENT SCRIPT: leaving message loop")))


;; Custom code
(def result-id "result")


(defn get-window-offset [pixels]
  (str (+ pixels 5) "px"))


(defn make-styles [page-x page-y]
  {:left (get-window-offset page-x)
   :top (get-window-offset page-y)
   :position "absolute"
   :background "white"
   :border "1px solid black"
   :color "black"})


(defn str-style [info]
  (apply str (map #(let [[kwd val] %]
                     (str (name kwd) ":" val "; "))
                  info)))


(defn create-result-el [text [page-x page-y]]
  (html
   [:div {:id result-id
          :style (str-style (make-styles page-x page-y))}
    [:p text]]))


(defn listen-text-selection! []
  (de/listen! js/document :mouseup (fn [d-e]
                                     (let [e (.-event_ (.-evt d-e))
                                           selection (str (.getSelection js/window))]
                                       (if (not (str/blank? selection))
                                         (d/append! (xpath "//body")
                                                    (create-result-el selection [(.-pageX e)
                                                                                 (.-pageY e)]))))))
  (de/listen! js/document :mousedown (fn [e]
                                       (let [result-el (d/by-id result-id)]
                                         (when-not (d/ancestor? result-el
                                                                (.-target (.-evt e)))
                                           (d/destroy! result-el))))))


; -- a simple page analysis  --
(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port "hello from CONTENT SCRIPT!")
    (run-message-loop! background-port)
    (listen-text-selection!)))

; -- main entry point --

(defn init! []
  (log "CONTENT SCRIPT: int")
  (connect-to-background-page!))
