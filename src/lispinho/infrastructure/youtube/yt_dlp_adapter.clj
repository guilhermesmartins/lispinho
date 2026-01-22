(ns lispinho.infrastructure.youtube.yt-dlp-adapter
  "Infrastructure adapter for yt-dlp CLI tool.
   Implements the YouTubeDownloaderGateway protocol."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]
            [cheshire.core :as json]
            [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.ports.repositories :as ports])
  (:import [java.io File]))

;; =============================================================================
;; yt-dlp Configuration
;; =============================================================================

(def default-yt-dlp-format-selection
  "Format selection for yt-dlp to get reasonable quality videos.
   Prioritizes H.264/AAC in MP4 containers for Telegram compatibility.
   Uses height limits instead of filesize filters since m3u8 streams
   don't have exact filesize metadata. File size is checked after download."
  (str "bestvideo[height<=720][ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a][acodec^=mp4a]/"
       "bestvideo[height<=720][vcodec^=avc1]+bestaudio[acodec^=mp4a]/"
       "best[height<=720][ext=mp4]/"
       "best"))

(def fallback-yt-dlp-format-selection
  "Fallback format selection if the primary format isn't available.
   Uses lower resolution to ensure smaller file sizes and keeps MP4/H.264."
  (str "best[height<=480][ext=mp4][vcodec^=avc1][acodec^=mp4a]/"
       "best[height<=480][ext=mp4]/"
       "best"))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn yt-dlp-installed?
  "Checks if yt-dlp is installed and accessible."
  [yt-dlp-path]
  (try
    (let [result (shell/sh yt-dlp-path "--version")]
      (zero? (:exit result)))
    (catch Exception _
      false)))

(defn parse-yt-dlp-json-output
  "Parses JSON output from yt-dlp."
  [json-string]
  (try
    (json/parse-string json-string true)
    (catch Exception _
      nil)))

(defn extract-video-metadata-from-info
  "Extracts video metadata from yt-dlp info dictionary."
  [youtube-url-value-object info-dict]
  (when info-dict
    (let [title (or (:title info-dict) "Unknown Title")
          duration-seconds (or (:duration info-dict) 0)
          uploader (or (:uploader info-dict)
                       (:channel info-dict)
                       "Unknown Uploader")
          thumbnail-url (:thumbnail info-dict)
          is-available (not (or (:is_live info-dict)
                                (string/includes? (str (:availability info-dict)) "private")
                                (string/includes? (str (:availability info-dict)) "premium")))]
      (media-entities/create-video-metadata
       {:youtube-url-value-object youtube-url-value-object
        :video-title-value-object (media-value-objects/create-video-title title)
        :video-duration-value-object (media-value-objects/create-video-duration (int duration-seconds))
        :video-uploader-name uploader
        :video-thumbnail-url thumbnail-url
        :video-is-available is-available}))))

(defn find-downloaded-video-file
  "Finds the downloaded video file in the target directory.
   yt-dlp may add extensions, so we search for the file."
  [target-directory video-id]
  (let [dir-file (File. target-directory)
        files (.listFiles dir-file)]
    (when files
      (->> files
           (filter #(.isFile %))
           (filter #(string/includes? (.getName %) video-id))
           (filter #(some (fn [ext] (string/ends-with? (.getName %) ext))
                          [".mp4" ".webm" ".mkv" ".mov"]))
           first))))

(defn build-yt-dlp-command-arguments
  "Builds yt-dlp CLI arguments, optionally adding cookies and extra arguments.

   Parameters:
   - yt-dlp-path: Path to yt-dlp executable
   - cookies-path: Optional cookies file path
   - extra-arguments: Optional vector of extra CLI args
   - additional-args: Remaining yt-dlp arguments"
  [yt-dlp-path cookies-path extra-arguments & additional-args]
  (let [base-args [yt-dlp-path]
        cookies-args (when (and cookies-path (seq cookies-path))
                       ["--cookies" cookies-path])
        extra-args (when (seq extra-arguments)
                     (vec extra-arguments))
        full-args (cond-> base-args
                     cookies-args (into cookies-args)
                     extra-args (into extra-args)
                     (seq additional-args) (into additional-args))]
    full-args))

(defn yt-dlp-output-indicates-authentication-required?
  "Detects the common YouTube 'not a bot' / sign-in challenge in yt-dlp output." 
  [yt-dlp-output-string]
  (let [output (string/lower-case (str yt-dlp-output-string))]
    (or (string/includes? output "sign in to confirm you're not a bot")
        (string/includes? output "confirm you're not a bot")
        (string/includes? output "use --cookies-from-browser")
        (string/includes? output "use --cookies"))))

;; =============================================================================
;; yt-dlp Adapter Record
;; =============================================================================

(defrecord YtDlpAdapter [yt-dlp-path yt-dlp-cookies-path yt-dlp-extra-arguments]
  ports/YouTubeDownloaderGateway

  (validate-youtube-url
    [_gateway url-string]
    (if (media-value-objects/youtube-url-string-valid? url-string)
      {:valid true
       :youtube-url (media-value-objects/create-youtube-url url-string)}
      {:valid false
       :reason "URL is not a valid YouTube video URL"}))

  (fetch-video-metadata
    [_gateway youtube-url-value-object]
    (let [video-url (media-value-objects/extract-youtube-url-normalized youtube-url-value-object)
          video-id (media-value-objects/extract-youtube-url-video-id youtube-url-value-object)]
      (println "DEBUG [yt-dlp]: Fetching metadata for video:" video-id)
      (if-not (yt-dlp-installed? yt-dlp-path)
        {:success false
         :error (error-types/create-yt-dlp-not-found-error yt-dlp-path)}

        (try
           (let [result (apply shell/sh
                                (build-yt-dlp-command-arguments
                                 yt-dlp-path
                                 yt-dlp-cookies-path
                                 yt-dlp-extra-arguments
                                 "--dump-json"
                                 "--no-download"
                                 "--no-warnings"
                                 video-url))]
             (if (zero? (:exit result))
              (if-let [info-dict (parse-yt-dlp-json-output (:out result))]
                (if-let [metadata (extract-video-metadata-from-info
                                   youtube-url-value-object
                                   info-dict)]
                  {:success true
                   :metadata metadata}
                  {:success false
                   :error (error-types/create-video-metadata-fetch-error
                           video-id
                           "Failed to parse video information")})
                {:success false
                 :error (error-types/create-video-metadata-fetch-error
                         video-id
                         "Invalid JSON response from yt-dlp")})
              (let [combined-output (str (:err result) "\n" (:out result))]
                (if (yt-dlp-output-indicates-authentication-required? combined-output)
                  {:success false
                   :error (error-types/create-youtube-authentication-required-error
                           video-id
                           "Sign in to confirm you're not a bot")}
                  {:success false
                   :error (error-types/create-video-metadata-fetch-error
                           video-id
                           (or (:err result) "Unknown yt-dlp error"))}))))
           (catch Exception exception
             {:success false
              :error (error-types/create-video-metadata-fetch-error
                      video-id
                     (.getMessage exception))})))))

  (download-video-to-file
    [_gateway youtube-url-value-object target-directory-path maximum-file-size-bytes]
    (let [video-url (media-value-objects/extract-youtube-url-normalized youtube-url-value-object)
          video-id (media-value-objects/extract-youtube-url-video-id youtube-url-value-object)
          output-template (str target-directory-path "/" video-id ".%(ext)s")]
      (println "DEBUG [yt-dlp]: Downloading video:" video-id "to:" target-directory-path)
      (if-not (yt-dlp-installed? yt-dlp-path)
        {:success false
         :error (error-types/create-yt-dlp-not-found-error yt-dlp-path)}

        (try
          ;; Try with preferred format first
           (let [result (apply shell/sh
                                (build-yt-dlp-command-arguments
                                 yt-dlp-path
                                 yt-dlp-cookies-path
                                 yt-dlp-extra-arguments
                                 "-f" default-yt-dlp-format-selection
                                 "-o" output-template
                                 "--no-playlist"
                                 "--no-warnings"
                                 "--merge-output-format" "mp4"
                                "--recode-video" "mp4"
                                video-url))]
            (if (zero? (:exit result))
              ;; Find the downloaded file
              (if-let [downloaded-file (find-downloaded-video-file target-directory-path video-id)]
                (let [file-size (.length downloaded-file)
                      file-path (.getAbsolutePath downloaded-file)]
                  (println "DEBUG [yt-dlp]: Download complete, file:" file-path "size:" file-size)
                  (if (> file-size maximum-file-size-bytes)
                    {:success false
                     :error (error-types/create-video-file-too-large-error
                             file-size
                             maximum-file-size-bytes)}
                    {:success true
                     :file-path (media-value-objects/create-video-file-path file-path file-size)}))
                {:success false
                 :error (error-types/create-download-failed-error
                         video-id
                         "Downloaded file not found")})

               ;; Try with fallback format
                (let [combined-output (str (:err result) "\n" (:out result))
                      fallback-result (apply shell/sh
                                             (build-yt-dlp-command-arguments
                                              yt-dlp-path
                                              yt-dlp-cookies-path
                                              yt-dlp-extra-arguments
                                              "-f" fallback-yt-dlp-format-selection
                                              "-o" output-template
                                              "--no-playlist"
                                              "--no-warnings"
                                              "--merge-output-format" "mp4"
                                              "--recode-video" "mp4"
                                              video-url))]
                 (if (zero? (:exit fallback-result))
                   (if-let [downloaded-file (find-downloaded-video-file target-directory-path video-id)]
                     (let [file-size (.length downloaded-file)
                           file-path (.getAbsolutePath downloaded-file)]
                       (if (> file-size maximum-file-size-bytes)
                         {:success false
                          :error (error-types/create-video-file-too-large-error
                                  file-size
                                  maximum-file-size-bytes)}
                         {:success true
                          :file-path (media-value-objects/create-video-file-path file-path file-size)}))
                     {:success false
                      :error (error-types/create-download-failed-error
                              video-id
                              "Downloaded file not found")})
                   (let [fallback-output (str (:err fallback-result) "\n" (:out fallback-result) "\n" combined-output)]
                     (if (yt-dlp-output-indicates-authentication-required? fallback-output)
                       {:success false
                        :error (error-types/create-youtube-authentication-required-error
                                video-id
                                "Sign in to confirm you're not a bot")}
                       {:success false
                        :error (error-types/create-download-failed-error
                                video-id
                                (or (:err fallback-result) (:err result) "Unknown yt-dlp error"))}))))))
           (catch Exception exception
             {:success false
              :error (error-types/create-download-failed-error
                      video-id
                      (.getMessage exception))}))))))

;; =============================================================================
;; Factory Function
;; =============================================================================

(defn create-yt-dlp-adapter
  "Creates a new YtDlpAdapter instance.

   Parameters:
   - yt-dlp-path: Path to the yt-dlp executable (defaults to 'yt-dlp')
   - yt-dlp-cookies-path: Optional path to a cookies.txt file"
   ([]
    (create-yt-dlp-adapter "yt-dlp" nil []))
   ([yt-dlp-path]
    (create-yt-dlp-adapter yt-dlp-path nil []))
   ([yt-dlp-path yt-dlp-cookies-path]
    (create-yt-dlp-adapter yt-dlp-path yt-dlp-cookies-path []))
   ([yt-dlp-path yt-dlp-cookies-path yt-dlp-extra-arguments]
   {:pre [(string? yt-dlp-path)
           (seq yt-dlp-path)
           (or (nil? yt-dlp-cookies-path)
               (and (string? yt-dlp-cookies-path)
                    (seq yt-dlp-cookies-path)))]}
    (->YtDlpAdapter yt-dlp-path yt-dlp-cookies-path (or yt-dlp-extra-arguments []))))
