(ns tvt.a7.profedit.frames
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.actions :as a]
   [tvt.a7.profedit.fio :as fio]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [tvt.a7.profedit.util :as u]
   [tvt.a7.profedit.rosetta :as ros]))


(defn make-status-bar []
  (sc/vertical-panel
   :items
   [(sc/separator :orientation :horizontal)
    (w/status)]))


(defn- mk-menu-file-items [*state make-frame make-wizard-frame]
  [(a/act-new! make-wizard-frame *state)
   (a/act-open! make-frame *state)
   (a/act-save! *state)
   (a/act-save-as! *state)
   (a/act-reload! make-frame *state)
   (a/act-load-zero-xy! *state)
   (a/act-import! make-frame *state)
   (a/act-export! *state)])


(defn make-menu-file [*state make-frame make-wizard-frame]
  (sc/menu :icon (conf/key->icon :actions-group-menu)
           :items (mk-menu-file-items *state make-frame make-wizard-frame)))


(defn make-menu-themes [make-frame]
  (let [at! (fn [t-name t-key] (a/act-theme! make-frame t-name t-key))]
    (sc/menu
     :icon (conf/key->icon :actions-group-theme)
     :items
     [(at! ::action-theme-sol-light :sol-light)
      (at! ::action-theme-sol-dark :sol-dark)
      (at! ::action-theme-real-dark :dark)
      (at! ::action-theme-hi-dark :hi-dark)])))


(defn make-menu-languages [make-frame]
  (sc/menu
   :icon (conf/key->icon :icon-languages)
   :items
   [(a/act-language-en! make-frame)
    (a/act-language-ua! make-frame)]))


(defn- select-first-empty-input [frame]
  (when-let [first-empty (first (filter #(-> %
                                             sc/text
                                             seq
                                             nil?)
                                        (sc/select
                                         frame
                                         [:.nonempty-input])))]
    (sc/request-focus! first-empty)))


(defn- save-new-profile [*state *w-state main-frame-cons]
  (if (w/save-as-chooser *w-state)
    (do (reset! *state (select-keys (deref *w-state) [:profile]))
        (-> (main-frame-cons) sc/pack! sc/show!))
    (reset! fio/*current-fp nil)))


(defn- wizard-finalizer [*state *w-state main-frame-cons]
  (when (and (not (save-new-profile *state *w-state main-frame-cons))
             (prof/status-err?))
    (sc/alert (prof/status-text))
    (recur *state *w-state main-frame-cons)))


;; FIXME: The ugliest function I ever wrote X_X
(defn make-frame-wizard [*state *w-state main-frame-cons content-vec]
  (let [c-sel [:wizard :content-idx]
        get-cur-content-idx #(get-in (deref *w-state) c-sel)

        inc-cur-content-idx! (partial swap! *w-state #(update-in % c-sel inc))

        dec-cur-content-idx! (partial swap! *w-state #(update-in % c-sel dec))

        get-cur-content-map #(let [cur-idx (get-cur-content-idx)]
                               (when (and (>= cur-idx 0)
                                          (< cur-idx (count content-vec)))
                                 (nth content-vec cur-idx)))

        wrap-cur-content #(sc/border-panel
                           :id :content
                           :center ((:cons (get-cur-content-map))))

        reset-content! #(let [c-c (sc/select % [:#content-container])
                              cur-cont (sc/select c-c [:#content])]
                          (sc/replace! c-c cur-cont
                                       (wrap-cur-content)))
        finalize-cur-content! #((:finalizer (get-cur-content-map)) %)

        frame-cons (partial make-frame-wizard
                            *state
                            *w-state
                            main-frame-cons
                            content-vec)
        wiz-fs-sel [:wizard :maximized?]

        can-go-back? #(> (get-cur-content-idx) 0)

        prev-b (sc/button
                :text ::prev-frame
                :id :back-button
                :enabled? (can-go-back?)
                :listen
                [:action
                 (fn [e]
                   (let [frame (sc/to-root e)]
                     (when (can-go-back?)
                       (dec-cur-content-idx!)
                       (sc/invoke-later (reset-content! frame))
                       (sc/config! e :enabled? (can-go-back?)))))])
        next-b (sc/button
                :text ::next-frame
                :listen
                [:action
                 (fn [e]
                   (let [frame (sc/to-root e)]
                     (if (finalize-cur-content! e)
                       (if (select-first-empty-input frame)
                         (when (prof/status-ok?)
                           (prof/status-err! ::fill-the-input))
                         (sc/invoke-later
                          (inc-cur-content-idx!)
                          (if (get-cur-content-map)
                            (do
                              (reset-content! frame)
                              (sc/config! prev-b :enabled? true))
                            (if-not (ros/repeating-speeds?
                                     (:profile (deref *w-state)))
                              (sc/invoke-later
                               (sc/dispose! frame)
                               (wizard-finalizer *state
                                                 *w-state
                                                 main-frame-cons))
                              (do
                                (dec-cur-content-idx!)
                                (reset-content! frame)
                                (prof/status-err!
                                 ::ros/profile-bc-repeating-speeds))))))
                       (reset-content! frame))))])

        frame (sc/frame
               :icon (conf/key->icon :icon-frame)
               :id :frame-main
               :on-close (if (System/getProperty "repl") :dispose :exit)
               :menubar
               (sc/menubar
                :items [(make-menu-themes frame-cons)
                        (make-menu-languages frame-cons)])
               :content (sc/vertical-panel
                         :items [(w/make-banner)
                                 (sc/border-panel
                                  :id :content-container
                                  :size [900 :by 500]
                                  :vgap 30
                                  :border 5
                                  :center (wrap-cur-content)
                                  :south (sc/horizontal-panel
                                          :items [prev-b next-b]))
                                 (make-status-bar)]))]
    (prof/status-ok! "")
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (if (get-in (deref *w-state) wiz-fs-sel)
      (u/maximize! frame)
      (sc/show! (sc/pack! frame)))
    frame))


(defn make-frame-main [*state wizard-cons content-cons]
  (let [frame-cons (partial make-frame-main *state wizard-cons content-cons)
        buttons (mapv #(sc/config! % :name "")
                      (mk-menu-file-items *state frame-cons wizard-cons))
        frame (->> (content-cons)
                   (sc/frame
                    :icon (conf/key->icon :icon-frame)
                    :id :frame-main
                    :title ::start-menu-title
                    :on-close (if (System/getProperty "repl") :dispose :exit)
                    :menubar
                    (sc/menubar
                     :items (into
                             buttons
                             [(sc/separator :orientation :vertical)
                              (make-menu-file *state frame-cons wizard-cons)
                              (make-menu-themes frame-cons)
                              (make-menu-languages frame-cons)]))
                    :content))]
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (sc/pack! frame)))
