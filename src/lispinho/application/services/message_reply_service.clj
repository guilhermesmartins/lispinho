(ns lispinho.application.services.message-reply-service
  "Application service for handling message replies.
   Orchestrates sending text messages and videos with proper reply behavior."
  (:require [lispinho.domain.messaging.value-objects :as messaging-value-objects]
            [lispinho.domain.messaging.aggregates :as messaging-aggregates]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.ports.repositories :as ports]))

;; =============================================================================
;; Text Message Reply Service
;; =============================================================================

(defn send-text-reply-to-context
  "Sends a text reply based on a message context.
   Handles the reply-to-original behavior for groups.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - message-context-aggregate: The MessageContext to reply to
   - text-content: The text message to send

   Returns:
   {:success true :message-id <id>} or {:success false :error <domain-error>}"
  [telegram-gateway message-context-aggregate text-content]
  {:pre [(messaging-aggregates/message-context? message-context-aggregate)
         (string? text-content)
         (seq text-content)]}
  (let [chat-id (messaging-aggregates/message-context-extract-chat-id-for-response
                 message-context-aggregate)
        reply-to-message-id (messaging-aggregates/message-context-extract-reply-to-message-id
                             message-context-aggregate)]
    (println "DEBUG [Reply Service]: Sending text reply to chat:"
             (messaging-value-objects/extract-chat-id-value chat-id)
             "reply-to:" (when reply-to-message-id
                           (messaging-value-objects/extract-message-id-value reply-to-message-id)))
    (try
      (if reply-to-message-id
        (ports/send-text-message
         telegram-gateway
         chat-id
         text-content
         reply-to-message-id)
        (ports/send-text-message
         telegram-gateway
         chat-id
         text-content))
      (catch Exception exception
        {:success false
         :error (error-types/create-telegram-send-message-error
                 (messaging-value-objects/extract-chat-id-value chat-id)
                 (.getMessage exception))}))))

(defn send-error-reply-to-context
  "Sends a domain error as a formatted reply.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - message-context-aggregate: The MessageContext to reply to
   - domain-error: The error to send

   Returns:
   {:success true :message-id <id>} or {:success false :error <domain-error>}"
  [telegram-gateway message-context-aggregate domain-error]
  {:pre [(messaging-aggregates/message-context? message-context-aggregate)
         (error-types/domain-error? domain-error)]}
  (let [error-message (error-types/format-error-for-user-message domain-error)]
    (println "DEBUG [Reply Service]: Sending error reply:" error-message)
    (send-text-reply-to-context
     telegram-gateway
     message-context-aggregate
     error-message)))

;; =============================================================================
;; Chat Action Service
;; =============================================================================

(defn send-upload-video-action
  "Sends the 'upload_video' chat action to indicate video upload is in progress.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - chat-id-value-object: The chat to send the action to

   Returns:
   {:success true} or {:success false :error <domain-error>}"
  [telegram-gateway chat-id-value-object]
  {:pre [(messaging-value-objects/chat-id? chat-id-value-object)]}
  (println "DEBUG [Reply Service]: Sending upload_video action to chat:"
           (messaging-value-objects/extract-chat-id-value chat-id-value-object))
  (try
    (ports/send-chat-action telegram-gateway chat-id-value-object :upload_video)
    (catch Exception exception
      {:success false
       :error (error-types/create-unexpected-error
               exception
               "sending chat action")})))

(defn send-typing-action
  "Sends the 'typing' chat action to indicate the bot is processing.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - chat-id-value-object: The chat to send the action to

   Returns:
   {:success true} or {:success false :error <domain-error>}"
  [telegram-gateway chat-id-value-object]
  {:pre [(messaging-value-objects/chat-id? chat-id-value-object)]}
  (println "DEBUG [Reply Service]: Sending typing action to chat:"
           (messaging-value-objects/extract-chat-id-value chat-id-value-object))
  (try
    (ports/send-chat-action telegram-gateway chat-id-value-object :typing)
    (catch Exception exception
      {:success false
       :error (error-types/create-unexpected-error
               exception
               "sending typing action")})))

;; =============================================================================
;; Video Message Reply Service
;; =============================================================================

(defn send-video-reply-to-context
  "Sends a video file as a reply based on a message context.
   Handles the reply-to-original behavior for groups.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - message-context-aggregate: The MessageContext to reply to
   - downloadable-video-entity: The video to send

   Returns:
   {:success true :message-id <id>} or {:success false :error <domain-error>}"
  [telegram-gateway message-context-aggregate downloadable-video-entity]
  {:pre [(messaging-aggregates/message-context? message-context-aggregate)
         (media-entities/downloadable-video? downloadable-video-entity)]}
  (let [chat-id (messaging-aggregates/message-context-extract-chat-id-for-response
                 message-context-aggregate)
        reply-to-message-id (messaging-aggregates/message-context-extract-reply-to-message-id
                             message-context-aggregate)
        video-file-path (:downloadable-video-file-path downloadable-video-entity)
        caption (media-entities/downloadable-video-extract-caption downloadable-video-entity)]
    (println "DEBUG [Reply Service]: Sending video reply to chat:"
             (messaging-value-objects/extract-chat-id-value chat-id))
    (try
      ;; Send upload_video action before starting upload
      (send-upload-video-action telegram-gateway chat-id)

      ;; Send the video
      (if reply-to-message-id
        (ports/send-video-file
         telegram-gateway
         chat-id
         video-file-path
         caption
         reply-to-message-id)
        (ports/send-video-file
         telegram-gateway
         chat-id
         video-file-path
         caption))
      (catch Exception exception
        {:success false
         :error (error-types/create-telegram-upload-error
                 (.getMessage exception))}))))

;; =============================================================================
;; Response Specification Service
;; =============================================================================

(defn send-text-from-response-specification
  "Sends a text message based on a ResponseSpecification value object.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - response-specification: The ResponseSpecification to send

   Returns:
   {:success true :message-id <id>} or {:success false :error <domain-error>}"
  [telegram-gateway response-specification]
  {:pre [(messaging-aggregates/response-specification? response-specification)]}
  (let [chat-id (:response-spec-chat-id response-specification)
        text-content (:response-spec-text-content response-specification)
        reply-to-id (:response-spec-reply-to-message-id response-specification)]
    (println "DEBUG [Reply Service]: Sending from response spec to chat:"
             (messaging-value-objects/extract-chat-id-value chat-id))
    (try
      (if reply-to-id
        (ports/send-text-message telegram-gateway chat-id text-content reply-to-id)
        (ports/send-text-message telegram-gateway chat-id text-content))
      (catch Exception exception
        {:success false
         :error (error-types/create-telegram-send-message-error
                 (messaging-value-objects/extract-chat-id-value chat-id)
                 (.getMessage exception))}))))

;; =============================================================================
;; Progress Update Service
;; =============================================================================

(defn send-progress-message
  "Sends a progress update message during long-running operations.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - message-context-aggregate: The MessageContext to reply to
   - progress-message: The progress message text

   Returns:
   {:success true :message-id <id>} or {:success false :error <domain-error>}"
  [telegram-gateway message-context-aggregate progress-message]
  {:pre [(messaging-aggregates/message-context? message-context-aggregate)
         (string? progress-message)]}
  (let [chat-id (messaging-aggregates/message-context-extract-chat-id-for-response
                 message-context-aggregate)]
    ;; Send typing indicator first
    (send-typing-action telegram-gateway chat-id)

    ;; Send the progress message (without replying to avoid clutter)
    (println "DEBUG [Reply Service]: Sending progress message:" progress-message)
    (try
      (ports/send-text-message telegram-gateway chat-id progress-message)
      (catch Exception exception
        {:success false
         :error (error-types/create-telegram-send-message-error
                 (messaging-value-objects/extract-chat-id-value chat-id)
                 (.getMessage exception))}))))
