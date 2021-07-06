(ns frontend.handler.events
  (:refer-clojure :exclude [run!])
  (:require [frontend.state :as state]
            [clojure.core.async :as async]
            [frontend.spec :as spec]
            [frontend.ui :as ui]
            [frontend.util :as util :refer [profile]]
            [frontend.config :as config]
            [frontend.handler.notification :as notification]
            [frontend.components.encryption :as encryption]
            [frontend.fs.nfs :as nfs]
            [frontend.db.conn :as conn]
            [frontend.handler.migrate :as migrate]
            [frontend.db-schema :as db-schema]
            [frontend.db :as db]
            [datascript.core :as d]
            ["semver" :as semver]))

;; TODO: should we move all events here?

(defn show-install-error!
  [repo-url title]
  (spec/validate :repos/url repo-url)
  (notification/show!
   [:p.content
    title
    " "
    [:span.mr-2
     (util/format
      "Please make sure that you've installed the logseq app for the repo %s on GitHub. "
      repo-url)
     (ui/button
      "Install Logseq on GitHub"
      :href (str "https://github.com/apps/" config/github-app-name "/installations/new"))]]
   :error
   false))

(defmulti handle first)

(defmethod handle :repo/install-error [[_ repo-url title]]
  (show-install-error! repo-url title))

(defmethod handle :modal/encryption-setup-dialog [[_ repo-url close-fn]]
  (state/set-modal!
   (encryption/encryption-setup-dialog repo-url close-fn)))

(defmethod handle :modal/encryption-input-secret-dialog [[_ repo-url db-encrypted-secret close-fn]]
  (state/set-modal!
   (encryption/encryption-input-secret-dialog
    repo-url
    db-encrypted-secret
    close-fn)))

(defmethod handle :graph/added [[_ repo]]
  ;; add ast/version to db
  (let [conn (conn/get-conn repo false)
        ast-version (d/datoms @conn :aevt :ast/version)]
    (db/set-key-value repo :ast/version db-schema/ast-version))

  ;; markdown convert notification
  (js/setTimeout
   (fn []
     (when (not (:markdown/version (state/get-config)))
       (migrate/show-convert-notification! repo)))
   5000))

(defmethod handle :graph/migrated [[_ repo]]
  (js/setTimeout
   (fn []
     (when (not (:markdown/version (state/get-config)))
       (migrate/show-migrated-notification! repo)))
   5000))

(defn get-local-repo
  []
  (when-let [repo (state/get-current-repo)]
    (when (config/local-db? repo)
      repo)))

(defn ask-permission
  [repo]
  (when-not (util/electron?)
    (fn [close-fn]
      [:div
       [:p
        "Grant native filesystem permission for directory: "
        [:b (config/get-local-dir repo)]]
       (ui/button
        "Grant"
        :class "ui__modal-enter"
        :on-click (fn []
                    (nfs/check-directory-permission! repo)
                    (close-fn)))])))

(defmethod handle :modal/nfs-ask-permission []
  (when-let [repo (get-local-repo)]
    (state/set-modal! (ask-permission repo))))



(defmethod handle :after-db-restore [[_ repos]]
  (mapv (fn [{url :url} repo]
          ;; compare :ast/version
          (let [db (conn/get-conn url)
                ast-version (:v (first (d/datoms db :aevt :ast/version)))]
            (when (and (not= config/local-repo url)
                       (or (nil? ast-version)
                           (. semver lt ast-version db-schema/ast-version)))
              (notification/show!
               [:p.content
                (util/format "DB-schema updated, Please re-index repo [%s]" url)]
               :warning
               false))))
        repos))

(defn run!
  []
  (let [chan (state/get-events-chan)]
    (async/go-loop []
      (let [payload (async/<! chan)]
        (handle payload))
      (recur))
    chan))
