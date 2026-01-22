(ns lispinho.infrastructure.telegram.telegram-api-client
  "Infrastructure adapter for the Telegram Bot API.
   Implements the TelegramMessageGateway protocol."
  (:require [clj-http.client :as http-client]
            [cheshire.core :as json]
            [lispinho.domain.messaging.value-objects :as messaging-value-objects]
            [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.ports.repositories :as ports])
  (:import [java.io File]))

;; =============================================================================
;; Telegram API Configuration
;; =============================================================================

(def telegram-api-base-url
  "Base URL for Telegram Bot API."
  "https://api.telegram.org/bot")

(def default-long-polling-timeout-seconds
  "Default timeout for long polling requests."
  30)

(def chat-action-types
  "Mapping from action keywords to Telegram action strings."
  {:typing "typing"
   :upload_video "upload_video"
   :upload_photo "upload_photo"
   :upload_document "upload_document"
   :record_video "record_video"})

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn build-telegram-api-url
  "Builds a Telegram API URL for a given method."
  [bot-token method-name]
  (str telegram-api-base-url bot-token "/" method-name))

(defn handle-telegram-api-response
  "Handles a Telegram API response, extracting success/error information."
  [api-method response]
  (let [body (if (string? (:body response))
               (json/parse-string (:body response) true)
               (:body response))]
    (if (get body :ok)
      {:success true
       :result (get body :result)}
      {:success false
       :error (error-types/create-telegram-api-error
               api-method
               (get body :error_code)
               (get body :description "Unknown error"))})))

(defn handle-telegram-api-exception
  "Handles exceptions from Telegram API calls."
  [api-method exception]
  {:success false
   :error (error-types/create-telegram-api-error
           api-method
           0
           (.getMessage exception))})

;; =============================================================================
;; Telegram API Client Record
;; =============================================================================

(defrecord TelegramApiClient [bot-token]
  ports/TelegramMessageGateway

  (send-text-message
    [_gateway chat-id-value-object text-content]
    (ports/send-text-message _gateway chat-id-value-object text-content nil))

  (send-text-message
    [_gateway chat-id-value-object text-content reply-to-message-id-value-object]
    (let [chat-id (messaging-value-objects/extract-chat-id-value chat-id-value-object)
          api-url (build-telegram-api-url bot-token "sendMessage")
          form-params (cond-> {"chat_id" chat-id
                               "text" text-content}
                        reply-to-message-id-value-object
                        (assoc "reply_to_message_id"
                               (messaging-value-objects/extract-message-id-value
                                reply-to-message-id-value-object)))]
      (println "DEBUG [Telegram API]: sendMessage to chat:" chat-id)
      (try
        (let [response (http-client/post api-url
                                         {:form-params form-params
                                          :as :json
                                          :throw-exceptions false})
              result (handle-telegram-api-response "sendMessage" response)]
          (if (:success result)
            {:success true
             :message-id (get-in result [:result :message_id])}
            result))
        (catch Exception exception
          (handle-telegram-api-exception "sendMessage" exception)))))

  (send-video-file
    [_gateway chat-id-value-object video-file-path-value-object caption-text]
    (ports/send-video-file _gateway chat-id-value-object video-file-path-value-object caption-text nil))

  (send-video-file
    [_gateway chat-id-value-object video-file-path-value-object caption-text reply-to-message-id-value-object]
    (let [chat-id (messaging-value-objects/extract-chat-id-value chat-id-value-object)
          file-path (media-value-objects/extract-video-file-path-string video-file-path-value-object)
          api-url (build-telegram-api-url bot-token "sendVideo")
          video-file (File. file-path)
          multipart-params (cond-> [{:name "chat_id" :content (str chat-id)}
                                    {:name "video" :content video-file}
                                    {:name "supports_streaming" :content "true"}]
                             caption-text
                             (conj {:name "caption" :content caption-text})
                             reply-to-message-id-value-object
                             (conj {:name "reply_to_message_id"
                                    :content (str (messaging-value-objects/extract-message-id-value
                                                   reply-to-message-id-value-object))}))]
      (println "DEBUG [Telegram API]: sendVideo to chat:" chat-id "file:" file-path)
      (try
        (let [response (http-client/post api-url
                                         {:multipart multipart-params
                                          :as :json
                                          :throw-exceptions false
                                          :socket-timeout 300000  ; 5 minutes for large uploads
                                          :connection-timeout 30000})
              result (handle-telegram-api-response "sendVideo" response)]
          (if (:success result)
            {:success true
             :message-id (get-in result [:result :message_id])}
            result))
        (catch Exception exception
          (handle-telegram-api-exception "sendVideo" exception)))))

  (send-chat-action
    [_gateway chat-id-value-object action-keyword]
    (let [chat-id (messaging-value-objects/extract-chat-id-value chat-id-value-object)
          action-string (get chat-action-types action-keyword "typing")
          api-url (build-telegram-api-url bot-token "sendChatAction")]
      (println "DEBUG [Telegram API]: sendChatAction" action-string "to chat:" chat-id)
      (try
        (let [response (http-client/post api-url
                                         {:form-params {"chat_id" chat-id
                                                        "action" action-string}
                                          :as :json
                                          :throw-exceptions false})
              result (handle-telegram-api-response "sendChatAction" response)]
          (if (:success result)
            {:success true}
            result))
        (catch Exception exception
          (handle-telegram-api-exception "sendChatAction" exception)))))

  (get-updates-with-offset
    [_gateway current-offset-value timeout-seconds]
    (let [api-url (build-telegram-api-url bot-token "getUpdates")
          timeout (or timeout-seconds default-long-polling-timeout-seconds)]
      (println "DEBUG [Telegram API]: getUpdates with offset:" current-offset-value)
      (try
        (let [response (http-client/get api-url
                                        {:query-params {"offset" current-offset-value
                                                        "timeout" timeout}
                                         :as :json
                                         :throw-exceptions false
                                         :socket-timeout (* (+ timeout 10) 1000)})
              result (handle-telegram-api-response "getUpdates" response)]
          (if (:success result)
            {:success true
             :updates (vec (:result result))}
            result))
        (catch Exception exception
          (handle-telegram-api-exception "getUpdates" exception))))))

;; =============================================================================
;; Factory Function
;; =============================================================================

(defn create-telegram-api-client
  "Creates a new TelegramApiClient instance.

   Parameters:
   - bot-token: The Telegram Bot API token"
  [bot-token]
  {:pre [(string? bot-token)
         (seq bot-token)]}
  (->TelegramApiClient bot-token))
