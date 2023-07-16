(ns tvt.a7.profedit.frames
  (:require
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.actions :as a]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [tvt.a7.profedit.util :as u]))


(defn make-status-bar []
  (sc/vertical-panel
   :items
   [(sc/separator :orientation :horizontal)
    (w/status)]))


(defn make-menu-file [*state make-frame make-wizard-frame]
  (sc/menu
   :icon (conf/key->icon :actions-group-menu)
   :items
   [(a/act-new! make-wizard-frame *state)
    (a/act-open! make-frame *state)
    (a/act-save! *state)
    (a/act-save-as! *state)
    (a/act-reload! make-frame *state)
    (a/act-import! make-frame *state)
    (a/act-export! *state)]))


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


(defn make-frame-wizard [*w-state content-vec]
  (let [c-sel [:wizard :content-idx]
        get-cur-content-idx #(get-in (deref *w-state) c-sel)
        inc-cur-content-idx! (partial swap! *w-state #(update-in % c-sel inc))
        get-cur-content-map #(nth content-vec (get-cur-content-idx))
        wrap-cur-content #(sc/border-panel
                           :id :content
                           :center ((:cons (get-cur-content-map))))
        reset-content! #(sc/replace! % (sc/select % [:#content])
                                     (wrap-cur-content))
        finalize-cur-content! #((:finalizer (get-cur-content-map))% )
        frame-cons (partial make-frame-wizard *w-state get-cur-content-idx)
        wiz-fs-sel [:wizard :maximized?]
        next-button (sc/button :text ::next-frame
                               :id :next-button
                               :listen
                               [:action
                                (fn [e]
                                  (let [frame (sc/to-root e)]
                                    (finalize-cur-content! e)
                                    (if (select-first-empty-input frame)
                                      (when (prof/status-ok?)
                                        (prof/status-err! ::fill-the-input))
                                      (do (inc-cur-content-idx!)
                                          (reset-content! frame)))))])
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
                                  :vgap 30
                                  :border 5
                                  :center (wrap-cur-content)
                                  :south next-button)
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
        frame (->> (content-cons)
                   (sc/frame
                    :icon (conf/key->icon :icon-frame)
                    :id :frame-main
                    :title ::start-menu-title
                    :on-close (if (System/getProperty "repl") :dispose :exit)
                    :menubar
                    (sc/menubar
                     :items [(make-menu-file *state frame-cons wizard-cons)
                             (make-menu-themes frame-cons)
                             (make-menu-languages frame-cons)])
                    :content))]
    (doseq [fat-label (sc/select frame [:.fat])]
      (sc/config! fat-label :font conf/font-fat))
    (sc/pack! frame)))
