;; # 📋 A todo list persisted in datomic local
;; This short [Clerk](https://clerk.vision) notebook shows how to setup [datomic local](https://docs.datomic.com/cloud/datomic-local.html) to work within [application.garden](https://application.garden) projects.
(ns datomic-example
  (:require [datomic.client.api :as d]
            [nextjournal.clerk :as clerk]))

{::clerk/visibility {:code :hide :result :hide}}

(defonce !tasks (atom nil))

(def task-viewer
  {:transform-fn clerk/mark-presented
   :render-fn '(fn [{:as m :task/keys [description completed? id]} _]
                 [:div.mb-1.flex.bg-amber-200.border.border-amber-400.rounded-md.p-2.justify-between
                  [:div.flex
                   [:input.mt-2.ml-3.cursor-pointer {:type :checkbox :checked (boolean completed?)
                                                     :class (str "appearance-none h-4 w-4 rounded bg-amber-300 border border-amber-400 relative"
                                                                 "checked:border-amber-700 checked:bg-amber-700 checked:bg-no-repeat checked:bg-contain")
                                                     :on-change (fn [e]
                                                                  (.catch (nextjournal.clerk.render/clerk-eval {:recompute? true}
                                                                           (list 'update-task! (str id) 'assoc :task/completed? (.. e -target -checked)))
                                                                          (fn [err] (js/console.error err))))}]

                   [:div.text-xl.ml-2.mb-0.font-sans description]]
                  [:button.flex-end.mr-2.text-sm.text-amber-600.font-bold
                   {:on-click #(nextjournal.clerk.render/clerk-eval {:recompute? true} (list 'remove-task (str id)))} "⛌"]])})

(def tasks-viewer
  {:transform-fn (clerk/update-val (comp (partial mapv (partial clerk/with-viewer task-viewer)) deref))
   :render-fn '(fn [coll opts] (into [:div] (nextjournal.clerk.render/inspect-children opts) coll))})

{::clerk/visibility {:code :hide :result :show}}

(clerk/with-viewer
  '(fn [_ _]
     (let [text (nextjournal.clerk.render.hooks/use-state nil)
           ref (nextjournal.clerk.render.hooks/use-ref nil)
           handle-key-press (nextjournal.clerk.render.hooks/use-callback
                             (fn [e]
                               (when (and (= "Enter" (.-key e)) (= (.-target e) @ref) (not-empty @text))
                                 (reset! text nil)
                                 (.catch (nextjournal.clerk.render/clerk-eval {:recompute? true} (list 'add-task @text))
                                         (fn [err] (js/console.error err ))))) [text])]

       (nextjournal.clerk.render.hooks/use-effect
        (fn []
          (.addEventListener js/window "keydown" handle-key-press)
          #(.removeEventListener js/window "keydown" handle-key-press)) [handle-key-press])

       [:div.flex.bg-amber-100.border-amber-200.border.rounded-md.h-10.w-full.pl-8.font-sans.text-xl.mt-2
        [:input.bg-amber-100.focus:outline-none.text-md.w-full
        {:on-change #(reset! text (.. % -target -value))
         :placeholder "Enter some text and press Return…" :ref ref
         :value @text :type "text"}]])) {::clerk/width :wide} nil)

(clerk/with-viewer tasks-viewer {::clerk/width :wide} !tasks)

{::clerk/visibility {:code :show :result :hide}}
;; Start by adding Clerk and datomic dependencies to your `deps.edn` file
;;
;;```clojure
;;{:paths ["notebooks"]
;; :deps
;; {com.datomic/local {:mvn/version "1.0.277"}
;;  io.github.nextjournal/clerk {:git/sha "cbb19fd8f1a9b3b01c9ccb0d43c6dbb4571f3829"}}
;; :aliases
;; {:nextjournal/garden {:exec-fn nextjournal.clerk/serve!
;;                       :exec-args {:index "notebooks/datomic_example.clj"}}}}
;;```
;;
;; Set up your datomic client to use the storage path in `GARDEN_STORAGE` env variable

(def client
  (d/client {:server-type :datomic-local
             :storage-dir (System/getenv "GARDEN_STORAGE")
             :system "garden"}))

;; … and the usual business for managing datomic connection, schema and entities

(d/create-database client {:db-name "todo-datomic"})

(def conn (d/connect client {:db-name "todo-datomic"}))

(def schema
  [{:db/ident :task/description
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :task/id
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident :task/completed?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}

   {:db/ident :task/category
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}])

(d/transact conn {:tx-data schema})

(defn add-task [text]
  (d/transact conn {:tx-data [{:task/id (random-uuid)
                               :task/description text}]}))

(defn tasks []
  (->> (d/q '[:find (pull ?t [:task/id :task/description :task/completed?]) ?txinst
              :where [?t :task/id _ ?txinst]]
            (d/db conn))
       (sort-by second >)
       (mapv first)))

(defn update-task! [id f & args]
  (let [ref [:task/id (parse-uuid id)]
        updated-entity (apply f (d/pull (d/db conn) '[:task/id :task/description :task/completed?] ref) args)]
    (d/transact conn {:tx-data [updated-entity]})
    true))

(defn remove-task [id]
  (d/transact conn {:tx-data [[:db/retractEntity [:task/id (parse-uuid id)]]]}))

^{::clerk/visibility {:code :hide} ::clerk/no-cache true}
(reset! !tasks (tasks))
