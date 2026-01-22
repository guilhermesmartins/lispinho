(ns lispinho.infrastructure.persistence.file-system-repository
  "Infrastructure adapter for file system operations.
   Implements the FileSystemRepository protocol."
  (:require [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.ports.repositories :as ports])
  (:import [java.io File]
           [java.nio.file Files Paths]))

;; =============================================================================
;; File System Repository Record
;; =============================================================================

(defrecord FileSystemRepositoryImpl []
  ports/FileSystemRepository

  (ensure-directory-exists
    [_repository directory-path-string]
    (println "DEBUG [File System]: Ensuring directory exists:" directory-path-string)
    (try
      (let [directory-file (File. directory-path-string)]
        (if (.exists directory-file)
          (if (.isDirectory directory-file)
            {:success true :path directory-path-string}
            {:success false
             :error (error-types/create-domain-error
                     :system-error
                     :path-not-directory
                     (str "Path exists but is not a directory: " directory-path-string)
                     {:path directory-path-string})})
          (if (.mkdirs directory-file)
            {:success true :path directory-path-string}
            {:success false
             :error (error-types/create-domain-error
                     :system-error
                     :directory-creation-failed
                     (str "Failed to create directory: " directory-path-string)
                     {:path directory-path-string})})))
      (catch Exception exception
        {:success false
         :error (error-types/create-unexpected-error
                 exception
                 "ensuring directory exists")})))

  (delete-file-if-exists
    [_repository file-path-string]
    (println "DEBUG [File System]: Deleting file if exists:" file-path-string)
    (try
      (let [file (File. file-path-string)]
        (if (.exists file)
          (if (.delete file)
            {:success true :deleted true}
            {:success false
             :error (error-types/create-domain-error
                     :system-error
                     :file-deletion-failed
                     (str "Failed to delete file: " file-path-string)
                     {:path file-path-string})})
          {:success true :deleted false}))
      (catch Exception exception
        {:success false
         :error (error-types/create-unexpected-error
                 exception
                 "deleting file")})))

  (get-file-size
    [_repository file-path-string]
    (try
      (let [file (File. file-path-string)]
        (if (.exists file)
          {:success true :size-bytes (.length file)}
          {:success false
           :error (error-types/create-domain-error
                   :system-error
                   :file-not-found
                   (str "File not found: " file-path-string)
                   {:path file-path-string})}))
      (catch Exception exception
        {:success false
         :error (error-types/create-unexpected-error
                 exception
                 "getting file size")})))

  (file-exists?
    [_repository file-path-string]
    (try
      (.exists (File. file-path-string))
      (catch Exception _
        false))))

;; =============================================================================
;; Factory Function
;; =============================================================================

(defn create-file-system-repository
  "Creates a new FileSystemRepositoryImpl instance."
  []
  (->FileSystemRepositoryImpl))
