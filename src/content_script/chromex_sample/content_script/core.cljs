(ns chromex-sample.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop go]]
                   [hiccups.core :refer [html]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [clojure.string :as string]
            [hiccups.runtime :as hiccupsrt]
            [dommy.core :refer-macros [sel sel1]]
            [domina.xpath :refer [xpath]]
            [domina.events :as de]
            [domina :as d]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [goog.crypt.base64 :as b64]))


;; Atom vars
(def repo-specs (atom {}))


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
    [:textarea {:rows 10 :cols 80}
     text]]))


(defn listen-text-selection! []
  (de/listen! js/document
              :mouseup
              (fn [d-e]
                (let [e (.-event_ (.-evt d-e))
                      selection (str (.getSelection js/window))
                      spec (->> @repo-specs
                                vals
                                (apply concat)
                                (filter #(= (name (keyword (:fn %))) selection))
                                (map :text)
                                first)]
                  (if (and (not (string/blank? selection))
                           (not (nil? spec)))
                    (d/append! (xpath "//body")
                               (create-result-el spec
                                                 [(.-pageX e)
                                                  (.-pageY e)]))))))
  (de/listen! js/document
              :mousedown
              (fn [e]
                (let [result-el (d/by-id result-id)]
                  (when-not (d/ancestor? result-el
                                         (.-target (.-evt e)))
                    (d/destroy! result-el))))))



;; Credentials
(def credentials (atom {:username ""
                        :password ""}))

;; Github
;; v3 API
(def github-api "https://api.github.com")

(defn build-search-repo-code
  [repo code]
  (str github-api
       "/search/code?q="
       code
       "+repo:"
       repo))

(defn search-repo-code
  [repo code]
  (go (let [response (<! (http/get (build-search-repo-code repo code)
                                   {:with-credentials? false
                                    :basic-auth @credentials}))]
        (:body response))))


(defn decode-url
  [url]
  (go
    (-> (<! (http/get url
                      {:with-credentials? false
                       :basic-auth @credentials}))
        :body
        :content
        b64/decodeString)))


;; Pattern helper functions
(defn re-pos [re s]
  (let [re (js/RegExp. (.-source re) "g")]
    (loop [res {}]
      (if-let [m (.exec re s)]
        (recur (assoc res (.-index m) (first m)))
        res))))

(defn build-reducer-with-delimiters
  [o-dlmt c-dlmt]
  (fn [acc [k v]]
    (if (and (> (:o acc ) 0)
             (= (:o acc ) (:c acc)))
      (reduced (:last-index acc))
      (cond
        (= v o-dlmt) (-> (update acc :o inc)
                         (assoc :last-index k))
        (= v c-dlmt) (-> (update acc :c inc)
                         (assoc :last-index k))))))


(defn read-from-idx
  [idx s o-dlmt c-dlmt]
  (let [open-parens (re-pos (re-pattern (str "\\" o-dlmt)) (subs s idx))
        closed-parens (re-pos (re-pattern (str "\\" c-dlmt)) (subs s idx))]
    (subs s idx (+ idx (inc (reduce (build-reducer-with-delimiters o-dlmt c-dlmt)
                                    {:o 0 :c 0 :last-index -1}
                                    (->> (merge open-parens closed-parens)
                                         (into (sorted-map)))))))))


(defn find-fdefs
  [s]
  (->> (re-pos #"\(s/fdef([^\)]+)\)" s)
       keys
       (map #(read-from-idx % s "(" ")"))))


;; Helper functions
(defn url->repo
  [url]
  (->> (subvec (string/split url "/") 3 5)
       (string/join "/")))


;; Collect specs
(defn collect-fdefs-at-repo
  [repo]
  (go (let [items (:items (<! (search-repo-code repo "clojure.spec.alpha")))
            urls (map :git_url items)
            paths (map :path items)
            collector (atom [])]
        (doseq [url urls]
          (let [file-str (<! (decode-url url))
                raw-fdefs (find-fdefs file-str)
                fdefs (map #(identity {:text %
                                       :fn (-> %
                                               (string/split #" ")
                                               second
                                               (string/replace #"\n" ""))})
                           raw-fdefs)]
            (swap! collector conj fdefs)))
        (zipmap paths @collector))))


                                        ; -- a simple page analysis  --
(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port "hello from CONTENT SCRIPT!")
    (run-message-loop! background-port)

    (go (reset! repo-specs
                (-> js/window
                    .-location
                    .-href
                    url->repo
                    collect-fdefs-at-repo
                    <!)))

    (listen-text-selection!)))

                                        ; -- main entry point --

(defn init! []
  (log "CONTENT SCRIPT: int")
  (connect-to-background-page!))



(comment

  (go
    (cljs.pprint/pprint
     (->> (:items (<! (search-repo-code "pfeodrippe/banka" "clojure.spec.alpha")))
          #_(map keys))))


  (go
    (cljs.pprint/pprint
     (<! (decode-file "https://api.github.com/repositories/101492674/contents/test/banka/boundary/database_test.clj?ref=c9d5cbcdc3919050c9d9f1a95554466224a0d142"))))


  (go (let [response (<! (http/post github-api
                                    {:with-credentials? false
                                     :json-params {"query" viewer-data-query}
                                     :headers {"Authorization"
                                               "Bearer XXXXXXXX"}}))]
        (prn (:status response))
        (prn (:data (:body response)))))

  )
