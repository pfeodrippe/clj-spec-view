(ns chromex-sample.background.storage
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :as ptcl]
            [chromex.ext.storage :as storage]))

(def local-storage (storage/get-local))

(defn save-local
  [k v]
  (log (str "Saving key " k ""))
  (log v)
  (ptcl/set local-storage (clj->js {k v})))

(defn fetch-local
  [k]
  (go
    (let [[[items] error] (<! (ptcl/get local-storage k))]
      (if error
        (error (str "fetch " k " error:") error)
        (get (js->clj items) k)))))
