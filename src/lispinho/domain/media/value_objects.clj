(ns lispinho.domain.media.value-objects
  "Value objects for the media/video domain.
   These represent YouTube URLs, file paths, and video metadata."
  (:require [clojure.string :as string]))

;; =============================================================================
;; YouTube URL Value Object
;; =============================================================================

(def youtube-url-patterns
  "Regular expression patterns for valid YouTube URL formats.
   Supports:
   - youtube.com/watch?v=VIDEO_ID
   - youtu.be/VIDEO_ID
   - youtube.com/shorts/VIDEO_ID
   - m.youtube.com/watch?v=VIDEO_ID"
  {:standard-watch-pattern #"(?:https?://)?(?:www\.)?youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})(?:&.*)?$"
   :short-url-pattern #"(?:https?://)?(?:www\.)?youtu\.be/([a-zA-Z0-9_-]{11})(?:\?.*)?$"
   :shorts-pattern #"(?:https?://)?(?:www\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})(?:\?.*)?$"
   :mobile-pattern #"(?:https?://)?m\.youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})(?:&.*)?$"})

(defn extract-video-id-from-url-string
  "Attempts to extract a YouTube video ID from a URL string.
   Returns the 11-character video ID or nil if not a valid YouTube URL."
  [url-string]
  (when (string? url-string)
    (let [trimmed-url (string/trim url-string)]
      (some (fn [[_pattern-name regex-pattern]]
              (when-let [match-result (re-matches regex-pattern trimmed-url)]
                (second match-result)))
            youtube-url-patterns))))

(defn youtube-url-string-valid?
  "Predicate to check if a string is a valid YouTube URL."
  [url-string]
  (some? (extract-video-id-from-url-string url-string)))

(defn create-youtube-url
  "Creates a YouTubeUrl value object from a URL string.
   Validates and normalizes the URL, extracting the video ID.

   Throws an exception if the URL is not a valid YouTube URL."
  [url-string]
  {:pre [(string? url-string)
         (youtube-url-string-valid? url-string)]}
  (let [extracted-video-id (extract-video-id-from-url-string url-string)
        normalized-url (str "https://www.youtube.com/watch?v=" extracted-video-id)]
    {:value-object-type :youtube-url
     :youtube-url-original url-string
     :youtube-url-normalized normalized-url
     :youtube-url-video-id extracted-video-id}))

(defn youtube-url?
  "Predicate to check if the given value is a valid YouTubeUrl value object."
  [potential-youtube-url]
  (and (map? potential-youtube-url)
       (= :youtube-url (:value-object-type potential-youtube-url))
       (string? (:youtube-url-video-id potential-youtube-url))
       (= 11 (count (:youtube-url-video-id potential-youtube-url)))))

(defn extract-youtube-url-video-id
  "Extracts the video ID from a YouTubeUrl value object."
  [youtube-url-value-object]
  {:pre [(youtube-url? youtube-url-value-object)]}
  (:youtube-url-video-id youtube-url-value-object))

(defn extract-youtube-url-normalized
  "Extracts the normalized URL string from a YouTubeUrl value object."
  [youtube-url-value-object]
  {:pre [(youtube-url? youtube-url-value-object)]}
  (:youtube-url-normalized youtube-url-value-object))

;; =============================================================================
;; Video File Path Value Object
;; =============================================================================

(def valid-video-file-extensions
  "Set of valid video file extensions that can be uploaded to Telegram."
  #{".mp4" ".mov" ".avi" ".mkv" ".webm"})

(defn get-file-extension
  "Extracts the file extension from a path string (lowercase)."
  [path-string]
  (when (string? path-string)
    (let [last-dot-index (string/last-index-of path-string ".")]
      (when (and last-dot-index (pos? last-dot-index))
        (string/lower-case (subs path-string last-dot-index))))))

(defn video-file-path-string-valid?
  "Checks if a path string represents a valid video file path."
  [path-string]
  (and (string? path-string)
       (seq path-string)
       (contains? valid-video-file-extensions (get-file-extension path-string))))

(defn create-video-file-path
  "Creates a VideoFilePath value object from a file system path string.

   Parameters:
   - file-path-string: The absolute or relative path to the video file
   - file-size-in-bytes: The size of the file in bytes"
  [file-path-string file-size-in-bytes]
  {:pre [(string? file-path-string)
         (seq file-path-string)
         (integer? file-size-in-bytes)
         (pos? file-size-in-bytes)]}
  (let [file-extension (get-file-extension file-path-string)]
    {:value-object-type :video-file-path
     :video-file-path-string file-path-string
     :video-file-path-extension file-extension
     :video-file-path-size-bytes file-size-in-bytes}))

(defn video-file-path?
  "Predicate to check if the given value is a valid VideoFilePath value object."
  [potential-video-file-path]
  (and (map? potential-video-file-path)
       (= :video-file-path (:value-object-type potential-video-file-path))
       (string? (:video-file-path-string potential-video-file-path))
       (integer? (:video-file-path-size-bytes potential-video-file-path))))

(defn extract-video-file-path-string
  "Extracts the file path string from a VideoFilePath value object."
  [video-file-path-value-object]
  {:pre [(video-file-path? video-file-path-value-object)]}
  (:video-file-path-string video-file-path-value-object))

(defn extract-video-file-size-bytes
  "Extracts the file size in bytes from a VideoFilePath value object."
  [video-file-path-value-object]
  {:pre [(video-file-path? video-file-path-value-object)]}
  (:video-file-path-size-bytes video-file-path-value-object))

;; =============================================================================
;; Telegram File Size Constraints
;; =============================================================================

(def telegram-maximum-video-upload-size-bytes
  "Maximum file size for video uploads to Telegram Bot API (50MB)."
  (* 50 1024 1024))

(defn video-file-within-telegram-size-limit?
  "Checks if a VideoFilePath is within Telegram's upload size limit."
  [video-file-path-value-object]
  {:pre [(video-file-path? video-file-path-value-object)]}
  (<= (:video-file-path-size-bytes video-file-path-value-object)
      telegram-maximum-video-upload-size-bytes))

;; =============================================================================
;; Video Title Value Object
;; =============================================================================

(def maximum-video-title-length
  "Maximum length for a video title in our system."
  256)

(defn create-video-title
  "Creates a VideoTitle value object from a title string.
   Trims and validates the title."
  [title-string]
  {:pre [(string? title-string)
         (seq (string/trim title-string))]}
  (let [trimmed-title (string/trim title-string)
        truncated-title (if (> (count trimmed-title) maximum-video-title-length)
                          (subs trimmed-title 0 maximum-video-title-length)
                          trimmed-title)]
    {:value-object-type :video-title
     :video-title-text truncated-title
     :video-title-original-length (count title-string)}))

(defn video-title?
  "Predicate to check if the given value is a valid VideoTitle value object."
  [potential-video-title]
  (and (map? potential-video-title)
       (= :video-title (:value-object-type potential-video-title))
       (string? (:video-title-text potential-video-title))
       (seq (:video-title-text potential-video-title))))

(defn extract-video-title-text
  "Extracts the title text from a VideoTitle value object."
  [video-title-value-object]
  {:pre [(video-title? video-title-value-object)]}
  (:video-title-text video-title-value-object))

;; =============================================================================
;; Video Duration Value Object
;; =============================================================================

(defn create-video-duration
  "Creates a VideoDuration value object from seconds.

   Parameters:
   - duration-seconds: The duration of the video in seconds"
  [duration-seconds]
  {:pre [(integer? duration-seconds)
         (>= duration-seconds 0)]}
  (let [duration-minutes (quot duration-seconds 60)
        remaining-seconds (rem duration-seconds 60)]
    {:value-object-type :video-duration
     :video-duration-seconds duration-seconds
     :video-duration-minutes duration-minutes
     :video-duration-display-string (format "%d:%02d" duration-minutes remaining-seconds)}))

(defn video-duration?
  "Predicate to check if the given value is a valid VideoDuration value object."
  [potential-video-duration]
  (and (map? potential-video-duration)
       (= :video-duration (:value-object-type potential-video-duration))
       (integer? (:video-duration-seconds potential-video-duration))
       (>= (:video-duration-seconds potential-video-duration) 0)))

(defn extract-video-duration-seconds
  "Extracts the duration in seconds from a VideoDuration value object."
  [video-duration-value-object]
  {:pre [(video-duration? video-duration-value-object)]}
  (:video-duration-seconds video-duration-value-object))

(defn video-duration-within-limit?
  "Checks if the video duration is within a specified maximum (in minutes)."
  [video-duration-value-object maximum-duration-minutes]
  {:pre [(video-duration? video-duration-value-object)
         (integer? maximum-duration-minutes)
         (pos? maximum-duration-minutes)]}
  (<= (:video-duration-seconds video-duration-value-object)
      (* maximum-duration-minutes 60)))
