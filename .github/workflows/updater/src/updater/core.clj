(ns updater.core
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [seesaw.core :as sc])
  (:gen-class))


(defn create-window []
  (let [message-label (sc/text :multi-line? true
                               :font "Arial-BOLD-24"
                               :wrap-lines? true
                               :editable? false
                               :rows 20)]
    (sc/show!
     (sc/frame :title "Update Status"
               :size [600 :by 800]
               :on-close :exit
               :content (sc/border-panel
                         :border 10
                         :center (sc/scrollable message-label))))
    message-label))

(def fail-string (str "Update failed! Please manually download"
                      "the new version from the following URL:\n"
                      "https://github.com/JAremko/profedit/releases"))


(def download-url (str "https://github.com/"
                       "JAremko/profedit/releases/"
                       "latest/download/profedit.jar"))

(defn msg [message-label ^String message]
  (sc/invoke-later (sc/config! message-label :text message)))


(defn download-file [^String url ^String filename]
  (let [response (client/get url {:as :stream :debug false})]
    (when (= 200 (:status response))
      (with-open [out (io/output-stream filename)]
        (io/copy (:body response) out)))))


(defn rename-file [^String old-name ^String new-name]
  (.renameTo (io/file old-name) (io/file new-name)))


(defn rename-loop [message-label ^String old-name ^String new-name]
  (when-not (rename-file old-name new-name)
    (msg message-label "App is still running. Waiting...")
    (Thread/sleep 2000)
    (recur message-label old-name new-name)))


(defn delete-file [^String filename]
  (when (.exists (io/file filename))
    (.delete (io/file filename))))


(defn move-file [^String old-name ^String new-name]
  (io/copy (io/file old-name) (io/file new-name) :replace true)
  (delete-file old-name))


(defn start-app [^String filename]
  (let [runtime (Runtime/getRuntime)]
    (.exec runtime filename)
    (System/exit 0)))


(defn updater [message-label]
  (let [say (partial msg message-label)
        jar-name "profedit.jar"
        new-jar-name "profedit-new.jar"
        backup-jar-name "profedit.jar.bak"]
    (say "Updating...")
    (when (.exists (io/file backup-jar-name))
      (say "Deleting old backup file...")
      (delete-file backup-jar-name))
    (download-file download-url new-jar-name)
    (if (.exists (io/file new-jar-name))
      (do
        (say "Update downloaded successfully.")
        (when (.exists (io/file jar-name))
          (say "Waiting for the application to close...")
          (rename-loop message-label jar-name backup-jar-name)
          (say "Application closed, proceeding with the update.")
          (delete-file jar-name))
        (move-file new-jar-name jar-name)
        (say "Update completed!")
        (start-app "profedit.exe"))
      (do
        (say fail-string)
        (when (.exists (io/file backup-jar-name))
          (rename-loop message-label backup-jar-name jar-name))))))


(defn -main [& _]
  (sc/invoke-later
   (let [message-label (create-window)]
     (future
       (try (updater message-label)
            (catch Exception e
              (let [reason (.getMessage e)]
                (msg message-label (str reason "\n\n\n" fail-string)))))))))
