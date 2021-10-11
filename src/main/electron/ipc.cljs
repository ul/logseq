(ns electron.ipc
  (:require [cljs-bean.core :as bean]
            [promesa.core :as p]
            [frontend.util :as util]))

;; TODO: handle errors
(defn ipc
  [& args]
  (when (util/electron?)
    (p/let
        [response
         (js/fetch "/ipc" #js {:method "POST"
                               :headers #js {"Accept" "application/json"
                                             "Content-Type" "application/json"}
                               :body (js/JSON.stringify (bean/->js args))})
         content-type (.get (.-headers response) "content-type")
         result (if (.startsWith content-type "text/plain")
                  (.text response)
                  (.json response))]
      result)))

(defn invoke
  [channel & args]
  #_(when (util/electron?)
    (p/let [result (js/window.apis.invoke channel (bean/->js args))]
      result)))
