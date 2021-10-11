(ns electron.core
  (:require [electron.handler :as handler]
            [electron.search :as search]
            [electron.utils :refer [*win mac? linux? logger get-win-from-sender]]
            [clojure.string :as string]
            [promesa.core :as p]
            [cljs-bean.core :as bean]
            [electron.fs-watcher :as fs-watcher]
            ["fs-extra" :as fs]
            ["path" :as path]
            ["os" :as os]
            ["fastify" :as fastify]
            ["fastify-static" :as fastify-static]
            ["fastify-websocket" :as fastify-websocket]
            [clojure.core.async :as async]
            [electron.state :as state]
            [electron.git :as git]
            [electron.window :as win]
            [electron.exceptions :as exceptions]
            ["/electron/utils" :as utils]))

(defn main
  []

  (when (search/version-changed?)
    (search/rm-search-dir!))

  (search/ensure-search-dir!)

  (search/open-dbs!)

  (git/auto-commit-current-graph!)

  (doto
   (fastify
    #js {:https
         #js {:key (fs/readFileSync "/Users/ul/Library/Containers/io.tailscale.ipn.macos/Data/cybercraft.ainu-musical.ts.net.key")
              :cert (fs/readFileSync "/Users/ul/Library/Containers/io.tailscale.ipn.macos/Data/cybercraft.ainu-musical.ts.net.crt")}})
    (.register fastify-websocket)
    (.register fastify-static #js {:root js/__dirname :prefix (if dev? "/static/" "/")})
    (.get "/"
          (fn [request reply]
            ^js (.sendFile reply
                           (if dev? "index.html" "electron.html")
                           (if dev? (.dirname path js/__dirname) nil))))
    (.post "/ipc"
           (fn [request reply]
             (let [args-js ^js (.-body request)
                   response
                   (try
                     (let [message (bean/->clj args-js)]
                       (bean/->js (handler/handle nil message)))
                     (catch js/Error e
                       (when-not (contains? #{"mkdir" "stat"} (nth args-js 0))
                         (println "IPC error: " args-js e))
                       e))]
               ^js (.send reply response))))
    (.get "/fs-watcher" #js {:websocket true}
          (fn [connection request]
            (fs-watcher/add-connection connection)
            (.on ^js (.-socket connection) "close" #(fs-watcher/rm-connection connection))))
    (.listen #js {:port 5000 :host "cybercraft.ainu-musical.ts.net"} (fn [err address]))))

(defn start [])
(defn stop [])
