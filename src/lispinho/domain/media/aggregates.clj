(ns lispinho.domain.media.aggregates
  "Aggregate roots for the media/video domain.
   The VideoDownloadRequest aggregate orchestrates the video download workflow."
  (:require [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.domain.messaging.value-objects :as messaging-value-objects]))

;; =============================================================================
;; Video Download Request Status
;; =============================================================================

(def video-download-request-status-values
  "Possible states for a video download request."
  #{:pending           ; Request created, not yet processed
    :fetching-metadata ; Retrieving video information
    :validating        ; Checking if video meets constraints
    :downloading       ; Actually downloading the video
    :completed         ; Download successful, video ready
    :failed})          ; An error occurred

;; =============================================================================
;; Video Download Request Aggregate Root
;; =============================================================================
;; This aggregate manages the entire lifecycle of a video download request,
;; from initial URL submission to final download completion or failure.

(defn create-video-download-request
  "Creates a new VideoDownloadRequest aggregate in pending state.

   Parameters:
   - youtube-url-value-object: The YouTubeUrl to download
   - requesting-chat-id: The ChatId of the chat requesting the download
   - reply-to-message-id: The MessageId to reply to (nil for DMs)
   - maximum-video-duration-minutes: Max allowed video duration in minutes"
  [{:keys [youtube-url-value-object
           requesting-chat-id
           reply-to-message-id
           maximum-video-duration-minutes]}]
  {:pre [(media-value-objects/youtube-url? youtube-url-value-object)
         (messaging-value-objects/chat-id? requesting-chat-id)
         (or (nil? reply-to-message-id)
             (messaging-value-objects/message-id? reply-to-message-id))
         (integer? maximum-video-duration-minutes)
         (pos? maximum-video-duration-minutes)]}
  {:aggregate-type :video-download-request
   :video-download-request-id (java.util.UUID/randomUUID)
   :video-download-request-youtube-url youtube-url-value-object
   :video-download-request-chat-id requesting-chat-id
   :video-download-request-reply-to-message-id reply-to-message-id
   :video-download-request-max-duration-minutes maximum-video-duration-minutes
   :video-download-request-status :pending
   :video-download-request-metadata nil
   :video-download-request-downloadable-video nil
   :video-download-request-error-message nil
   :video-download-request-created-at (System/currentTimeMillis)
   :video-download-request-updated-at (System/currentTimeMillis)})

(defn video-download-request?
  "Predicate to check if the given value is a valid VideoDownloadRequest aggregate."
  [potential-request]
  (and (map? potential-request)
       (= :video-download-request (:aggregate-type potential-request))
       (media-value-objects/youtube-url? (:video-download-request-youtube-url potential-request))
       (messaging-value-objects/chat-id? (:video-download-request-chat-id potential-request))
       (contains? video-download-request-status-values
                  (:video-download-request-status potential-request))))

;; =============================================================================
;; State Transition Functions
;; =============================================================================

(defn video-download-request-transition-to-fetching-metadata
  "Transitions the request to the fetching-metadata state."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)
         (= :pending (:video-download-request-status video-download-request-aggregate))]}
  (assoc video-download-request-aggregate
         :video-download-request-status :fetching-metadata
         :video-download-request-updated-at (System/currentTimeMillis)))

(defn video-download-request-set-metadata
  "Sets the video metadata and transitions to validating state."
  [video-download-request-aggregate video-metadata-entity]
  {:pre [(video-download-request? video-download-request-aggregate)
         (= :fetching-metadata (:video-download-request-status video-download-request-aggregate))
         (media-entities/video-metadata? video-metadata-entity)]}
  (assoc video-download-request-aggregate
         :video-download-request-status :validating
         :video-download-request-metadata video-metadata-entity
         :video-download-request-updated-at (System/currentTimeMillis)))

(defn video-download-request-transition-to-downloading
  "Transitions the request to the downloading state after validation passes."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)
         (= :validating (:video-download-request-status video-download-request-aggregate))]}
  (let [metadata (:video-download-request-metadata video-download-request-aggregate)
        max-duration (:video-download-request-max-duration-minutes video-download-request-aggregate)]
    (if (media-entities/video-metadata-is-downloadable? metadata max-duration)
      (assoc video-download-request-aggregate
             :video-download-request-status :downloading
             :video-download-request-updated-at (System/currentTimeMillis))
      (throw (ex-info "Video does not meet download requirements"
                      {:video-download-request-id (:video-download-request-id video-download-request-aggregate)
                       :reason :validation-failed})))))

(defn video-download-request-set-downloaded-video
  "Sets the downloaded video and transitions to completed state."
  [video-download-request-aggregate downloadable-video-entity]
  {:pre [(video-download-request? video-download-request-aggregate)
         (= :downloading (:video-download-request-status video-download-request-aggregate))
         (media-entities/downloadable-video? downloadable-video-entity)]}
  (assoc video-download-request-aggregate
         :video-download-request-status :completed
         :video-download-request-downloadable-video downloadable-video-entity
         :video-download-request-updated-at (System/currentTimeMillis)))

(defn video-download-request-mark-as-failed
  "Marks the request as failed with an error message."
  [video-download-request-aggregate error-message-string]
  {:pre [(video-download-request? video-download-request-aggregate)
         (string? error-message-string)
         (seq error-message-string)]}
  (assoc video-download-request-aggregate
         :video-download-request-status :failed
         :video-download-request-error-message error-message-string
         :video-download-request-updated-at (System/currentTimeMillis)))

;; =============================================================================
;; Query Functions
;; =============================================================================

(defn video-download-request-is-pending?
  "Checks if the request is in pending state."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)]}
  (= :pending (:video-download-request-status video-download-request-aggregate)))

(defn video-download-request-is-completed?
  "Checks if the request completed successfully."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)]}
  (= :completed (:video-download-request-status video-download-request-aggregate)))

(defn video-download-request-is-failed?
  "Checks if the request failed."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)]}
  (= :failed (:video-download-request-status video-download-request-aggregate)))

(defn video-download-request-extract-youtube-url
  "Extracts the YouTubeUrl from the request."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)]}
  (:video-download-request-youtube-url video-download-request-aggregate))

(defn video-download-request-extract-chat-id
  "Extracts the requesting ChatId from the request."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)]}
  (:video-download-request-chat-id video-download-request-aggregate))

(defn video-download-request-extract-reply-to-message-id
  "Extracts the MessageId to reply to (nil for DMs)."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)]}
  (:video-download-request-reply-to-message-id video-download-request-aggregate))

(defn video-download-request-extract-downloadable-video
  "Extracts the DownloadableVideo from a completed request."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)
         (video-download-request-is-completed? video-download-request-aggregate)]}
  (:video-download-request-downloadable-video video-download-request-aggregate))

(defn video-download-request-extract-error-message
  "Extracts the error message from a failed request."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)
         (video-download-request-is-failed? video-download-request-aggregate)]}
  (:video-download-request-error-message video-download-request-aggregate))

(defn video-download-request-should-reply-to-original?
  "Checks if the response should reply to the original message."
  [video-download-request-aggregate]
  {:pre [(video-download-request? video-download-request-aggregate)]}
  (some? (:video-download-request-reply-to-message-id video-download-request-aggregate)))
