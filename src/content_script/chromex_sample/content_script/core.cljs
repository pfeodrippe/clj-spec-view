(ns chromex-sample.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [dommy.core :refer-macros [sel sel1]]
            [clojure.string :as str]))

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
(defn handle-text
  [text]
  (set! (.-nodeValue text)
        (-> text
            .-nodeValue
            (str/replace #"Danousse" "JESUS"))))

(defn walk-element
  [el]
  (let [t (.-nodeType el)]
    (cond
      (or (= t 1) ;; Element
          (= t 9) ;; Document
          (= t 11)) (loop [child (.-firstChild el)]
                      (when child
                        (walk-element child)
                        (recur (.-nextSibling child))))
      (= t 3) (handle-text el) )))

(defn walk-over-body
  []
  (walk-element (sel1 :body)))


; -- a simple page analysis  --

(defn do-page-analysis! [background-port]
  (let [script-elements (.getElementsByTagName js/document "div")
        script-count (.-length script-elements)
        title (.-title js/document)
        msg (str "CONTENT SCRIPT: document '" title "' contains " script-count " script tags.")]
    (log msg)
    (post-message! background-port msg)))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port "hello from CONTENT SCRIPT!")
    (run-message-loop! background-port)
    (do-page-analysis! background-port)
    (walk-over-body)))

; -- main entry point --

(defn init! []
  (log "CONTENT SCRIPT: int")
  (connect-to-background-page!))
