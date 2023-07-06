(ns tvt.a7.profedit.ballistic
  (:require
   [tvt.a7.profedit.widgets :as w]
   [tvt.a7.profedit.profile :as prof]
   [tvt.a7.profedit.config :as conf]
   [seesaw.core :as sc]
   [clojure.spec.alpha :as s]
   [seesaw.forms :as sf]
   [j18n.core :as j18n]
   [seesaw.bind :as ssb])
  (:import [java.awt AWTEvent]))


(declare ^:private make-func-coefs)


(defn- bc-type->coef-key [bc-type]
  (bc-type {:g1 :coef-g1 :g7 :coef-g7 :custom :coef-custom}))


(defn- state->bc-type [state]
  (:bc-type (prof/state->cur-prof state)))


(defn- state->bc-coef-sel [state]
  (let [bc-type (state->bc-type state)]
    [:profile (bc-type->coef-key bc-type)]))


(defn- state->bc-coef [state]
  (get-in state (state->bc-coef-sel state)))


(defn regen-func-coefs [*state frame]
  (sc/invoke-later
   (let [op (sc/select frame [:#func-pan-wrap])
         cont (sc/select frame [:#func-pan-cont])
         np (make-func-coefs *state)]
     (sc/replace! cont op np))))


(defn make-bc-type-sel [*state]
  (let [get-deps (fn [*state]
                   (let [state (deref *state)]
                     {:bc-type (state->bc-type state)
                      :count (count (state->bc-coef state))}))
        *last-deps (atom nil)]
    (w/input-sel *state
                 [:bc-type]
                 {:g1 (j18n/resource ::g1)
                  :g7 (j18n/resource ::g7)
                  :custom (j18n/resource ::custom)}
                 ::prof/bc-type
                 :listen
                 [:selection
                  (fn [e]
                    (sc/request-focus! (sc/to-root e))
                    (let [new-deps (get-deps *state)
                          last-deps (deref *last-deps)]
                      (when (not= last-deps new-deps)
                        (regen-func-coefs *state (sc/to-root e))
                        (reset! *last-deps new-deps))))])))


(defn make-g1-g7-row [*state bc-type idx]
  (let [bc-c-key (bc-type->coef-key bc-type)]
    [(sc/label :text ::mv)
     (w/input-num *state [bc-c-key idx :mv] ::prof/mv :columns 5)
     (sc/label :text ::bc)
     (w/input-num *state [bc-c-key idx :bc] ::prof/bc :columns 5)]))


(defn- sync-custom-cofs [*state vpath spec ^AWTEvent e]
  (let [{:keys [min-v max-v fraction-digits]} (meta (s/get-spec spec))
        source (sc/to-widget e)
        new-val (w/str->double (or (sc/value source) "")
                               fraction-digits)]
    (if (and new-val (<= min-v new-val max-v))
      (prof/assoc-in-prof! *state vpath new-val)
      (do (prof/status-err! (format (j18n/resource ::range-error-fmt)
                                    (str min-v)
                                    (str max-v)))
          (sc/value! source (w/val->str (prof/get-in-prof* *state vpath)
                                        fraction-digits))))))


(defn make-custom-row [*state cofs idx]
  (let [mk-units-ma  (sc/text :text ::ma-units
                              :editable? false
                              :focusable? false
                              :margin 0)
        sync-cd (partial sync-custom-cofs
                         *state
                         [:coef-custom idx :cd]
                         ::prof/cd)
        sync-ma (partial sync-custom-cofs
                         *state
                         [:coef-custom idx :ma]
                         ::prof/ma)]
    [(sc/label :text (str "[" (inc idx) "] " (j18n/resource ::cd)))
     (sc/horizontal-panel
      :items [(sc/text :text (:cd (nth cofs idx))
                       :listen [:focus-lost sync-cd]
                       :columns 5)])
     (sc/label :text ::ma)
     (sc/horizontal-panel
      :items [(sc/text :text (:ma (nth cofs idx))
                       :listen [:focus-lost sync-ma]
                       :columns 5)
              mk-units-ma])]))


(defn- make-func-children [*state]
  (let [state (deref *state)
        bc-coef (state->bc-coef state)
        bc-type (state->bc-type state)
        num-rows (count bc-coef)]
    (into [(sf/span (sc/label :text ::coefs :class :fat) 5) (sf/next-line)]
          (mapcat
           (if (= bc-type :custom)
             (partial make-custom-row *state (state->bc-coef @*state))
             (partial make-g1-g7-row *state bc-type)))
          (range 0 num-rows))))


(defn- make-func-coefs [*state]
  (sc/scrollable
   (w/forms-with-bg
    :func-coeficients-panel
    "pref,4dlu,pref,4dlu,pref,4dlu,pref"
    :items (make-func-children *state))
   :id :func-pan-wrap))


(defn- bind-coef-upd [*state w]
  (ssb/bind *state
            (ssb/some
             (w/mk-debounced-transform
              #(when (= :custom (prof/get-in-prof % [:bc-type]))
                 (count (prof/get-in-prof % [:coef-custom])))))
            (ssb/b-do* (fn [_] (regen-func-coefs *state (sc/to-root w)))))
  w)

;; NOTE:  current implementation doesn't update input widgets when
;;        individual values change in the state atom.
(defn make-func-panel [*state]
  (bind-coef-upd
   *state (sc/border-panel
           :id :func-pan-cont
           :vgap 10
           :center (make-func-coefs *state))))


(defn make-zeroing-panel [*pa]
  (w/forms-with-bg
   :zeroing-panel
   "pref,4dlu,pref,20dlu,pref,4dlu,pref"
   :items [(sc/label :text ::root-tab-zeroing :class :fat) (sf/next-line)
           (sc/label ::general-section-coordinates-zero-x)
           (w/input-0125-mult *pa [:zero-x] ::prof/zero-x :columns 4)
           (sc/label ::general-section-coordinates-zero-y)
           (w/input-0125-mult *pa [:zero-y] ::prof/zero-y :columns 4)
           (sc/label :text ::general-section-direction-distance
                     :icon (conf/key->icon ::zeroing-dist-icon))
           (w/input-set-distance *pa [:c-zero-distance-idx])
           (sc/label ::general-section-direction-pitch)
           (w/input-num *pa [:c-zero-w-pitch] ::prof/c-zero-w-pitch :columns 4)
           (sc/label ::general-section-temperature-air)
           (w/input-num *pa [:c-zero-air-temperature]
                        ::prof/c-zero-air-temperature :columns 4)
           (sc/label ::general-section-temperature-powder)
           (w/input-num *pa [:c-zero-p-temperature]
                        ::prof/c-zero-p-temperature :columns 4)
           (sc/label ::general-section-environment-pressure)
           (w/input-num, *pa [:c-zero-air-pressure]
                         ::prof/c-zero-air-pressure :columns 4)
           (sc/label ::general-section-environment-humidity)
           (w/input-num *pa [:c-zero-air-humidity]
                        ::prof/c-zero-air-humidity :columns 4)]))
