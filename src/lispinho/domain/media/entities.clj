(ns lispinho.domain.media.entities
  "Entity definitions for the media/video domain.
   Entities represent videos and their metadata with identity."
  (:require [lispinho.domain.media.value-objects :as media-value-objects]))

;; =============================================================================
;; Video Metadata Entity
;; =============================================================================
;; Represents metadata about a YouTube video obtained before downloading.
;; This allows us to validate the video (duration, availability) before
;; committing to download.

(defn create-video-metadata
  "Creates a VideoMetadata entity from information retrieved about a video.

   Parameters:
   - youtube-url-value-object: The YouTubeUrl identifying the video
   - video-title-value-object: The VideoTitle of the video
   - video-duration-value-object: The VideoDuration of the video
   - video-uploader-name: The name of the channel/uploader (string)
   - video-thumbnail-url: URL to the video thumbnail (optional string)
   - video-is-available: Whether the video is available for download (boolean)"
  [{:keys [youtube-url-value-object
           video-title-value-object
           video-duration-value-object
           video-uploader-name
           video-thumbnail-url
           video-is-available]}]
  {:pre [(media-value-objects/youtube-url? youtube-url-value-object)
         (media-value-objects/video-title? video-title-value-object)
         (media-value-objects/video-duration? video-duration-value-object)
         (string? video-uploader-name)
         (boolean? video-is-available)]}
  {:entity-type :video-metadata
   :video-metadata-youtube-url youtube-url-value-object
   :video-metadata-title video-title-value-object
   :video-metadata-duration video-duration-value-object
   :video-metadata-uploader-name video-uploader-name
   :video-metadata-thumbnail-url video-thumbnail-url
   :video-metadata-is-available video-is-available})

(defn video-metadata?
  "Predicate to check if the given value is a valid VideoMetadata entity."
  [potential-video-metadata]
  (and (map? potential-video-metadata)
       (= :video-metadata (:entity-type potential-video-metadata))
       (media-value-objects/youtube-url? (:video-metadata-youtube-url potential-video-metadata))
       (media-value-objects/video-title? (:video-metadata-title potential-video-metadata))
       (media-value-objects/video-duration? (:video-metadata-duration potential-video-metadata))))

(defn video-metadata-extract-video-id
  "Extracts the YouTube video ID from a VideoMetadata entity."
  [video-metadata-entity]
  {:pre [(video-metadata? video-metadata-entity)]}
  (media-value-objects/extract-youtube-url-video-id
   (:video-metadata-youtube-url video-metadata-entity)))

(defn video-metadata-extract-title-text
  "Extracts the title text from a VideoMetadata entity."
  [video-metadata-entity]
  {:pre [(video-metadata? video-metadata-entity)]}
  (media-value-objects/extract-video-title-text
   (:video-metadata-title video-metadata-entity)))

(defn video-metadata-extract-duration-seconds
  "Extracts the duration in seconds from a VideoMetadata entity."
  [video-metadata-entity]
  {:pre [(video-metadata? video-metadata-entity)]}
  (media-value-objects/extract-video-duration-seconds
   (:video-metadata-duration video-metadata-entity)))

(defn video-metadata-is-downloadable?
  "Checks if the video metadata indicates the video can be downloaded.
   Validates availability and duration constraints."
  [video-metadata-entity maximum-duration-minutes]
  {:pre [(video-metadata? video-metadata-entity)
         (integer? maximum-duration-minutes)
         (pos? maximum-duration-minutes)]}
  (and (:video-metadata-is-available video-metadata-entity)
       (media-value-objects/video-duration-within-limit?
        (:video-metadata-duration video-metadata-entity)
        maximum-duration-minutes)))

(defn video-metadata-format-for-caption
  "Formats the video metadata as a caption string for Telegram."
  [video-metadata-entity]
  {:pre [(video-metadata? video-metadata-entity)]}
  (let [title (video-metadata-extract-title-text video-metadata-entity)
        uploader (:video-metadata-uploader-name video-metadata-entity)
        duration-display (get-in video-metadata-entity
                                 [:video-metadata-duration :video-duration-display-string])]
    (str title "\n"
         "by " uploader " (" duration-display ")")))

;; =============================================================================
;; Downloadable Video Entity
;; =============================================================================
;; Represents a video that has been downloaded and is ready to be sent.
;; This entity combines the metadata with the actual file location.

(defn create-downloadable-video
  "Creates a DownloadableVideo entity from metadata and downloaded file.

   Parameters:
   - video-metadata-entity: The VideoMetadata for the video
   - video-file-path-value-object: The VideoFilePath pointing to the downloaded file"
  [{:keys [video-metadata-entity
           video-file-path-value-object]}]
  {:pre [(video-metadata? video-metadata-entity)
         (media-value-objects/video-file-path? video-file-path-value-object)]}
  {:entity-type :downloadable-video
   :downloadable-video-metadata video-metadata-entity
   :downloadable-video-file-path video-file-path-value-object
   :downloadable-video-is-within-telegram-limit
   (media-value-objects/video-file-within-telegram-size-limit? video-file-path-value-object)})

(defn downloadable-video?
  "Predicate to check if the given value is a valid DownloadableVideo entity."
  [potential-downloadable-video]
  (and (map? potential-downloadable-video)
       (= :downloadable-video (:entity-type potential-downloadable-video))
       (video-metadata? (:downloadable-video-metadata potential-downloadable-video))
       (media-value-objects/video-file-path?
        (:downloadable-video-file-path potential-downloadable-video))))

(defn downloadable-video-can-be-sent-to-telegram?
  "Checks if the downloadable video can be sent via Telegram Bot API.
   The file must be within Telegram's size limit."
  [downloadable-video-entity]
  {:pre [(downloadable-video? downloadable-video-entity)]}
  (:downloadable-video-is-within-telegram-limit downloadable-video-entity))

(defn downloadable-video-extract-file-path-string
  "Extracts the file path string from a DownloadableVideo entity."
  [downloadable-video-entity]
  {:pre [(downloadable-video? downloadable-video-entity)]}
  (media-value-objects/extract-video-file-path-string
   (:downloadable-video-file-path downloadable-video-entity)))

(defn downloadable-video-extract-file-size-bytes
  "Extracts the file size in bytes from a DownloadableVideo entity."
  [downloadable-video-entity]
  {:pre [(downloadable-video? downloadable-video-entity)]}
  (media-value-objects/extract-video-file-size-bytes
   (:downloadable-video-file-path downloadable-video-entity)))

(defn downloadable-video-extract-caption
  "Extracts a formatted caption for the video."
  [downloadable-video-entity]
  {:pre [(downloadable-video? downloadable-video-entity)]}
  (video-metadata-format-for-caption
   (:downloadable-video-metadata downloadable-video-entity)))

(defn downloadable-video-extract-duration-for-telegram
  "Extracts the duration in seconds, formatted for Telegram's sendVideo."
  [downloadable-video-entity]
  {:pre [(downloadable-video? downloadable-video-entity)]}
  (video-metadata-extract-duration-seconds
   (:downloadable-video-metadata downloadable-video-entity)))
