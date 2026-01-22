(ns lispinho.presentation.bot.update-processor
  "Processes incoming Telegram updates and routes them to appropriate handlers."
  (:require [lispinho.domain.messaging.value-objects :as messaging-value-objects]
            [lispinho.domain.messaging.entities :as messaging-entities]
            [lispinho.domain.messaging.aggregates :as messaging-aggregates]
            [lispinho.domain.commands.entities :as commands-entities]
            [lispinho.presentation.commands.command-handlers :as command-handlers]))

;; =============================================================================
;; Update Parsing
;; =============================================================================

(defn parse-telegram-user-from-raw-data
  "Parses a TelegramUser entity from raw Telegram API data."
  [raw-user-data]
  (when raw-user-data
    (messaging-entities/create-telegram-user
     {:user-id-number (:id raw-user-data)
      :user-first-name (:first_name raw-user-data)
      :user-last-name (:last_name raw-user-data)
      :user-username (:username raw-user-data)
      :is-bot-flag (:is_bot raw-user-data)})))

(defn parse-telegram-chat-from-raw-data
  "Parses a TelegramChat entity from raw Telegram API data."
  [raw-chat-data]
  (when raw-chat-data
    (let [chat-id (messaging-value-objects/create-chat-id (:id raw-chat-data))
          chat-type-string (:type raw-chat-data)
          chat-type-keyword (keyword chat-type-string)]
      (messaging-entities/create-telegram-chat
       {:chat-id-value-object chat-id
        :chat-type-keyword chat-type-keyword
        :chat-title-string (:title raw-chat-data)}))))

(defn parse-telegram-message-from-raw-data
  "Parses a TelegramMessage entity from raw Telegram API data."
  [raw-message-data]
  (when raw-message-data
    (let [message-id (messaging-value-objects/create-message-id (:message_id raw-message-data))
          chat-entity (parse-telegram-chat-from-raw-data (:chat raw-message-data))
          sender-entity (parse-telegram-user-from-raw-data (:from raw-message-data))
          message-text (when-let [text (:text raw-message-data)]
                         (when (seq text)
                           (messaging-value-objects/create-message-text text)))
          message-date (:date raw-message-data)]
      (when (and chat-entity sender-entity)
        (messaging-entities/create-telegram-message
         {:message-id-value-object message-id
          :message-chat-entity chat-entity
          :message-sender-entity sender-entity
          :message-text-value-object message-text
          :message-date-unix-timestamp message-date})))))

(defn parse-message-context-from-raw-update
  "Parses a MessageContext aggregate from raw Telegram update data."
  [raw-update-data]
  (when-let [raw-message (:message raw-update-data)]
    (when-let [telegram-message (parse-telegram-message-from-raw-data raw-message)]
      (let [update-id (messaging-value-objects/create-update-id (:update_id raw-update-data))]
        (messaging-aggregates/create-message-context
         {:update-id-value-object update-id
          :incoming-telegram-message telegram-message})))))

;; =============================================================================
;; Update Processing
;; =============================================================================

(defn process-command-message
  "Processes a message that contains a command.

   Parameters:
   - message-context-aggregate: The MessageContext to process
   - gateway-registry: The GatewayRegistry for executing commands

   Returns:
   {:processed true :result <command-result>} or {:processed false :reason <string>}"
  [message-context-aggregate gateway-registry]
  (println "DEBUG [Update Processor]: Processing command message")

  ;; Try to parse as a command
  (let [parse-result (commands-entities/try-parse-command-from-message-context
                      message-context-aggregate)]
    (if (:success parse-result)
      (let [parsed-command (:command parse-result)
            command-keyword (commands-entities/parsed-command-extract-command-keyword parsed-command)]
        (println "DEBUG [Update Processor]: Parsed command:" command-keyword)

        ;; Dispatch to command handler
        (let [handler-result (command-handlers/dispatch-command parsed-command gateway-registry)]
          {:processed true
           :result handler-result}))

      ;; Command parsing failed
      (do
        (println "DEBUG [Update Processor]: Command parsing failed:" (:reason parse-result))
        {:processed false
         :reason (:reason parse-result)}))))

(defn process-text-message
  "Processes a text message that is not a command.

   Parameters:
   - message-context-aggregate: The MessageContext to process
   - gateway-registry: The GatewayRegistry for processing

   Returns:
   {:processed true} or {:processed false :reason <string>}"
  [message-context-aggregate _gateway-registry]
  (let [message-text (messaging-aggregates/message-context-extract-message-text
                      message-context-aggregate)
        sender-name (messaging-aggregates/message-context-extract-sender-display-name
                     message-context-aggregate)]
    (println "DEBUG [Update Processor]: Ignoring non-command message from" sender-name ":" message-text)
    {:processed false
     :reason "Message is not a command"}))

(defn process-non-text-message
  "Processes a message that has no text content.

   Parameters:
   - message-context-aggregate: The MessageContext to process

   Returns:
   {:processed false :reason <string>}"
  [_message-context-aggregate]
  (println "DEBUG [Update Processor]: Ignoring non-text message")
  {:processed false
   :reason "Message has no text content"})

;; =============================================================================
;; Main Processing Entry Point
;; =============================================================================

(defn process-telegram-update
  "Main entry point for processing a Telegram update.
   Parses the update and routes it to the appropriate handler.

   Parameters:
   - raw-update-data: The raw update data from Telegram API
   - gateway-registry: The GatewayRegistry for processing

   Returns:
   {:processed true/false :reason <string>}"
  [raw-update-data gateway-registry]
  (println "DEBUG [Update Processor]: Processing update_id:" (:update_id raw-update-data))

  ;; Parse the update into a MessageContext
  (if-let [message-context (parse-message-context-from-raw-update raw-update-data)]
    ;; Check if this is a text message
    (let [incoming-message (:message-context-incoming-message message-context)]
      (if (messaging-entities/telegram-message-has-text? incoming-message)
        ;; Check if it's a command
        (if (messaging-entities/telegram-message-is-command? incoming-message)
          (process-command-message message-context gateway-registry)
          (process-text-message message-context gateway-registry))
        (process-non-text-message message-context)))

    ;; Failed to parse update
    (do
      (println "DEBUG [Update Processor]: Could not parse update (possibly not a message)")
      {:processed false
       :reason "Update does not contain a parseable message"})))
