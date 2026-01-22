(ns lispinho.application.services.youtube-download-service
  "Application service for orchestrating YouTube video downloads.
   This service coordinates between the YouTube gateway and domain entities."
  (:require [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.domain.media.aggregates :as media-aggregates]
            [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.ports.repositories :as ports]))

;; =============================================================================
;; YouTube URL Validation Service
;; =============================================================================

(defn validate-and-create-youtube-url
  "Validates a URL string and creates a YouTubeUrl value object if valid.

   Parameters:
   - youtube-downloader-gateway: Implementation of YouTubeDownloaderGateway
   - url-string: The URL string to validate

   Returns:
   {:success true :youtube-url <youtube-url-value-object>}
   or {:success false :error <domain-error>}"
  [youtube-downloader-gateway url-string]
  (if (nil? url-string)
    {:success false
     :error (error-types/create-missing-youtube-url-error)}
    (let [validation-result (ports/validate-youtube-url
                             youtube-downloader-gateway
                             url-string)]
      (if (:valid validation-result)
        {:success true
         :youtube-url (:youtube-url validation-result)}
        {:success false
         :error (error-types/create-invalid-youtube-url-error url-string)}))))

;; =============================================================================
;; Video Metadata Fetching Service
;; =============================================================================

(defn fetch-video-metadata-for-url
  "Fetches metadata for a YouTube video.

   Parameters:
   - youtube-downloader-gateway: Implementation of YouTubeDownloaderGateway
   - youtube-url-value-object: The YouTubeUrl to fetch metadata for

   Returns:
   {:success true :metadata <video-metadata-entity>}
   or {:success false :error <domain-error>}"
  [youtube-downloader-gateway youtube-url-value-object]
  {:pre [(media-value-objects/youtube-url? youtube-url-value-object)]}
  (try
    (let [fetch-result (ports/fetch-video-metadata
                        youtube-downloader-gateway
                        youtube-url-value-object)]
      (if (:success fetch-result)
        {:success true
         :metadata (:metadata fetch-result)}
        {:success false
         :error (:error fetch-result)}))
    (catch Exception exception
      {:success false
       :error (error-types/create-unexpected-error
               exception
               "fetching video metadata")})))

;; =============================================================================
;; Video Validation Service
;; =============================================================================

(defn validate-video-for-download
  "Validates that a video can be downloaded based on metadata.

   Parameters:
   - video-metadata-entity: The VideoMetadata to validate
   - maximum-duration-minutes: Maximum allowed video duration

   Returns:
   {:valid true} or {:valid false :error <domain-error>}"
  [video-metadata-entity maximum-duration-minutes]
  {:pre [(media-entities/video-metadata? video-metadata-entity)
         (integer? maximum-duration-minutes)
         (pos? maximum-duration-minutes)]}
  (cond
    (not (:video-metadata-is-available video-metadata-entity))
    {:valid false
     :error (error-types/create-video-unavailable-error
             (media-entities/video-metadata-extract-video-id video-metadata-entity)
             "Video is not available for download")}

    (not (media-value-objects/video-duration-within-limit?
          (:video-metadata-duration video-metadata-entity)
          maximum-duration-minutes))
    {:valid false
     :error (error-types/create-video-too-long-error
             (media-entities/video-metadata-extract-duration-seconds video-metadata-entity)
             maximum-duration-minutes)}

    :else
    {:valid true}))

;; =============================================================================
;; Video Download Service
;; =============================================================================

(defn download-video-to-local-file
  "Downloads a video to a local file.

   Parameters:
   - youtube-downloader-gateway: Implementation of YouTubeDownloaderGateway
   - file-system-repository: Implementation of FileSystemRepository
   - youtube-url-value-object: The YouTubeUrl to download
   - download-directory-path: Path to the download directory

   Returns:
   {:success true :file-path <video-file-path-value-object>}
   or {:success false :error <domain-error>}"
  [youtube-downloader-gateway
   file-system-repository
   youtube-url-value-object
   download-directory-path]
  {:pre [(media-value-objects/youtube-url? youtube-url-value-object)
         (string? download-directory-path)]}
  (try
    ;; Ensure download directory exists
    (let [ensure-result (ports/ensure-directory-exists
                         file-system-repository
                         download-directory-path)]
      (if-not (:success ensure-result)
        {:success false :error (:error ensure-result)}
        ;; Download the video
        (let [download-result (ports/download-video-to-file
                               youtube-downloader-gateway
                               youtube-url-value-object
                               download-directory-path
                               media-value-objects/telegram-maximum-video-upload-size-bytes)]
          (if (:success download-result)
            {:success true
             :file-path (:file-path download-result)}
            {:success false
             :error (:error download-result)}))))
    (catch Exception exception
      {:success false
       :error (error-types/create-unexpected-error
               exception
               "downloading video")})))

;; =============================================================================
;; Complete Download Workflow Service
;; =============================================================================

(defn execute-complete-download-workflow
  "Executes the complete video download workflow:
   1. Validates the YouTube URL
   2. Fetches video metadata
   3. Validates the video meets requirements
   4. Downloads the video
   5. Creates a DownloadableVideo entity

   Parameters:
   - youtube-downloader-gateway: Implementation of YouTubeDownloaderGateway
   - file-system-repository: Implementation of FileSystemRepository
   - url-string: The YouTube URL string
   - download-directory-path: Path to the download directory
   - maximum-duration-minutes: Maximum allowed video duration

   Returns:
   {:success true :downloadable-video <downloadable-video-entity>}
   or {:success false :error <domain-error>}"
  [youtube-downloader-gateway
   file-system-repository
   url-string
   download-directory-path
   maximum-duration-minutes]
  (println "DEBUG [YouTube Service]: Starting download workflow for URL:" url-string)

  ;; Step 1: Validate URL
  (let [url-validation-result (validate-and-create-youtube-url
                               youtube-downloader-gateway
                               url-string)]
    (if-not (:success url-validation-result)
      (do
        (println "DEBUG [YouTube Service]: URL validation failed")
        url-validation-result)

      (let [youtube-url (:youtube-url url-validation-result)
            _ (println "DEBUG [YouTube Service]: URL valid, video ID:"
                       (media-value-objects/extract-youtube-url-video-id youtube-url))

            ;; Step 2: Fetch metadata
            metadata-result (fetch-video-metadata-for-url
                             youtube-downloader-gateway
                             youtube-url)]
        (if-not (:success metadata-result)
          (do
            (println "DEBUG [YouTube Service]: Metadata fetch failed")
            metadata-result)

          (let [video-metadata (:metadata metadata-result)
                _ (println "DEBUG [YouTube Service]: Metadata fetched, title:"
                           (media-entities/video-metadata-extract-title-text video-metadata))

                ;; Step 3: Validate video meets requirements
                validation-result (validate-video-for-download
                                   video-metadata
                                   maximum-duration-minutes)]
            (if-not (:valid validation-result)
              (do
                (println "DEBUG [YouTube Service]: Video validation failed")
                {:success false :error (:error validation-result)})

              (let [_ (println "DEBUG [YouTube Service]: Video validated, starting download")

                    ;; Step 4: Download video
                    download-result (download-video-to-local-file
                                     youtube-downloader-gateway
                                     file-system-repository
                                     youtube-url
                                     download-directory-path)]
                (if-not (:success download-result)
                  (do
                    (println "DEBUG [YouTube Service]: Download failed")
                    download-result)

                  (let [video-file-path (:file-path download-result)
                        _ (println "DEBUG [YouTube Service]: Download complete, file:"
                                   (media-value-objects/extract-video-file-path-string video-file-path))

                        ;; Step 5: Create DownloadableVideo entity
                        downloadable-video (media-entities/create-downloadable-video
                                            {:video-metadata-entity video-metadata
                                             :video-file-path-value-object video-file-path})]

                    ;; Verify file is within Telegram's size limit
                    (if-not (media-entities/downloadable-video-can-be-sent-to-telegram? downloadable-video)
                      (do
                        (println "DEBUG [YouTube Service]: Video file too large for Telegram")
                        {:success false
                         :error (error-types/create-video-file-too-large-error
                                 (media-entities/downloadable-video-extract-file-size-bytes downloadable-video)
                                 media-value-objects/telegram-maximum-video-upload-size-bytes)})

                      (do
                        (println "DEBUG [YouTube Service]: Workflow complete, video ready to send")
                        {:success true
                         :downloadable-video downloadable-video}))))))))))))

;; =============================================================================
;; Cleanup Service
;; =============================================================================

(defn cleanup-downloaded-video-file
  "Cleans up a downloaded video file after it has been sent.

   Parameters:
   - file-system-repository: Implementation of FileSystemRepository
   - downloadable-video-entity: The DownloadableVideo to clean up

   Returns:
   {:success true :deleted <boolean>} or {:success false :error <domain-error>}"
  [file-system-repository downloadable-video-entity]
  {:pre [(media-entities/downloadable-video? downloadable-video-entity)]}
  (let [file-path-string (media-entities/downloadable-video-extract-file-path-string
                          downloadable-video-entity)]
    (println "DEBUG [YouTube Service]: Cleaning up file:" file-path-string)
    (ports/delete-file-if-exists file-system-repository file-path-string)))
