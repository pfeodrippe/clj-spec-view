(ns clj-spec-view.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop go]]
                   [hiccups.core :refer [html]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect get-url]]
            [clojure.string :as string]
            [hiccups.runtime :as hiccupsrt]
            [dommy.core :refer-macros [sel sel1]]
            [domina.xpath :refer [xpath]]
            [domina.events :as de]
            [domina :as d]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [goog.crypt.base64 :as b64]
            [cljsjs.highlight]
            [cljsjs.highlight.langs.clojure]
            [cognitect.transit :as t]))


(def reader (t/reader :json))
(def writer (t/writer :json))


;; Append css to page
(let [style (.createElement js/document "link")]
  (set! (.-rel style) "stylesheet")
  (set! (.-type style) "text/css")
  (set! (.-href style) (get-url "styles/zenburn.min.css"))
  (.appendChild (or (.-head js/document) (.-documentElement js/document)) style))


;; Atom vars
(def repo-specs (atom {}))
;; Credentials
(def credentials (atom {:username ""
                        :password ""}))


;; Views
(def result-id "result")

(defn get-window-offset [pixels off]
  (str (+ pixels off) "px"))

(defn make-styles [page-x page-y]
  {:left (get-window-offset page-x 10)
   :top (get-window-offset page-y 10)
   :position "absolute"
   :background "white"
   :opacity 1.0
   :color "black"})

(defn str-style [info]
  (apply str (map #(let [[kwd val] %]
                     (str (name kwd) ":" val "; "))
                  info)))

(defn create-result-el [text [page-x page-y]]
  (html
   [:div {:id result-id
          :style (str-style (make-styles page-x page-y))}
    [:pre [:code text]]]))


;; Listeners helper functions
(defn select-el!
  [el]
  (let [range (.createRange js/document)
        sel (.getSelection js/window)]
    (.selectNodeContents range el)
    (.removeAllRanges sel)
    (.addRange sel range)))

(defn str->name
  [s]
  ((comp name keyword) s))

(defn filter-spec
  [repo-specs selection]
  (->> repo-specs
       vals
       (apply concat)
       (filter #(= (str->name (:fn %)) selection))
       (map :text)
       first))


;; Listeners
(defn handle-mouse-over!
  [d-e]
  (let [e (.-event_ (.-evt d-e))
        el (.. d-e -evt -target)
        selection (-> el .-innerHTML (string/replace #" " "") str->name)
        spec (filter-spec @repo-specs selection)]
    (when (and (not (string/blank? selection))
               (not (nil? spec)))
      (select-el! el)
      (d/append! (xpath "//body")
                 (create-result-el spec
                                   [(.-pageX e)
                                    (.-pageY e)]))
      (.highlightBlock js/hljs
                       (-> js/document (.querySelector "code"))))))

(defn handle-mouse-out!
  [e]
  (let [result-el (d/by-id result-id)]
    (when-not (d/ancestor? result-el
                           (.-target (.-evt e)))
      (.removeAllRanges (.getSelection js/window))
      (d/destroy! result-el))))

;; TODO: user token, give hints about who has spec
(defn listen-text-selection! []
  (de/listen! js/document :mouseover handle-mouse-over!)
  (de/listen! js/document :mouseout handle-mouse-out!))


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


;; Messages
(defn handle-token! [token]
  (swap! credentials assoc :password token)
  (go (reset! repo-specs
              (-> js/window
                  .-location
                  .-href
                  url->repo
                  collect-fdefs-at-repo
                  <!))))

(defn run-message-loop! [message-channel]
  (log "CONTENT SCRIPT: starting message loop...")
  (go-loop []
    (when-some [message (t/read reader (<! message-channel))]
      (case (:type message)
        :fetch-token-res (handle-token! (:msg message))
        :default nil)
      (recur))
    (log "CONTENT SCRIPT: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port (t/write writer {:type :fetch-token}))
    (run-message-loop! background-port)
    (listen-text-selection!)))


;; Main entry point
(defn init! []
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
