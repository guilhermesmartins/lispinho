(ns lispinho.domain.messaging.aggregates
  "Aggregate roots for the messaging domain.
   Aggregates ensure consistency boundaries and encapsulate business rules."
  (:require [lispinho.domain.messaging.value-objects :as messaging-value-objects]
            [lispinho.domain.messaging.entities :as messaging-entities]))

;; =============================================================================
;; Message Context Aggregate Root
;; =============================================================================
;; This aggregate represents the full context needed to process and respond to
;; an incoming Telegram update. It serves as the root aggregate for message
;; processing workflows.

(defn create-message-context
  "Creates a MessageContext aggregate root from an incoming Telegram update.

   This aggregate encapsulates:
   - The original message that triggered processing
   - The update ID for offset tracking
   - Derived properties for determining response behavior

   Parameters:
   - update-id-value-object: The UpdateId from the Telegram update
   - incoming-telegram-message: The TelegramMessage entity"
  [{:keys [update-id-value-object
           incoming-telegram-message]}]
  {:pre [(messaging-value-objects/update-id? update-id-value-object)
         (messaging-entities/telegram-message? incoming-telegram-message)]}
  (let [chat-entity (:telegram-message-chat incoming-telegram-message)
        is-private-chat (messaging-entities/telegram-chat-is-private? chat-entity)
        requires-reply-to-original (messaging-entities/telegram-message-requires-reply-to-original?
                                    incoming-telegram-message)]
    {:aggregate-type :message-context
     :message-context-update-id update-id-value-object
     :message-context-incoming-message incoming-telegram-message
     :message-context-is-private-chat is-private-chat
     :message-context-requires-reply-to-original requires-reply-to-original
     :message-context-response-chat-id (:telegram-chat-id chat-entity)
     :message-context-reply-to-message-id (when requires-reply-to-original
                                            (:telegram-message-id incoming-telegram-message))}))

(defn message-context?
  "Predicate to check if the given value is a valid MessageContext aggregate."
  [potential-message-context]
  (and (map? potential-message-context)
       (= :message-context (:aggregate-type potential-message-context))
       (messaging-value-objects/update-id? (:message-context-update-id potential-message-context))
       (messaging-entities/telegram-message? (:message-context-incoming-message potential-message-context))))

(defn message-context-extract-chat-id-for-response
  "Extracts the ChatId value object to use when sending a response."
  [message-context-aggregate]
  {:pre [(message-context? message-context-aggregate)]}
  (:message-context-response-chat-id message-context-aggregate))

(defn message-context-extract-reply-to-message-id
  "Extracts the MessageId to reply to, or nil if this should be a direct message.
   Returns nil for private chats, returns the original message ID for groups."
  [message-context-aggregate]
  {:pre [(message-context? message-context-aggregate)]}
  (:message-context-reply-to-message-id message-context-aggregate))

(defn message-context-extract-message-text
  "Extracts the raw text content from the incoming message, or nil if no text."
  [message-context-aggregate]
  {:pre [(message-context? message-context-aggregate)]}
  (when-let [text-value-object (get-in message-context-aggregate
                                       [:message-context-incoming-message
                                        :telegram-message-text])]
    (messaging-value-objects/extract-message-text-content text-value-object)))

(defn message-context-is-command?
  "Checks if the incoming message in this context is a bot command."
  [message-context-aggregate]
  {:pre [(message-context? message-context-aggregate)]}
  (messaging-entities/telegram-message-is-command?
   (:message-context-incoming-message message-context-aggregate)))

(defn message-context-extract-sender-display-name
  "Extracts a human-readable name for the message sender."
  [message-context-aggregate]
  {:pre [(message-context? message-context-aggregate)]}
  (messaging-entities/telegram-user-display-name
   (get-in message-context-aggregate
           [:message-context-incoming-message :telegram-message-sender])))

(defn message-context-calculate-next-polling-offset
  "Calculates the next offset value to use for long-polling based on this context."
  [message-context-aggregate]
  {:pre [(message-context? message-context-aggregate)]}
  (messaging-value-objects/calculate-next-offset-from-update-id
   (:message-context-update-id message-context-aggregate)))

;; =============================================================================
;; Response Specification Value Object
;; =============================================================================
;; This is a value object that specifies how a response should be sent,
;; derived from the MessageContext aggregate.

(defn create-response-specification-from-context
  "Creates a response specification that describes how to send a reply.

   Parameters:
   - message-context-aggregate: The MessageContext to derive response behavior from
   - response-text-content: The text content to send as a response"
  [message-context-aggregate response-text-content]
  {:pre [(message-context? message-context-aggregate)
         (string? response-text-content)
         (seq response-text-content)]}
  (let [chat-id (message-context-extract-chat-id-for-response message-context-aggregate)
        reply-to-id (message-context-extract-reply-to-message-id message-context-aggregate)]
    {:value-object-type :response-specification
     :response-spec-chat-id chat-id
     :response-spec-reply-to-message-id reply-to-id
     :response-spec-text-content response-text-content
     :response-spec-should-reply (some? reply-to-id)}))

(defn response-specification?
  "Predicate to check if the given value is a valid ResponseSpecification."
  [potential-response-spec]
  (and (map? potential-response-spec)
       (= :response-specification (:value-object-type potential-response-spec))
       (messaging-value-objects/chat-id? (:response-spec-chat-id potential-response-spec))
       (string? (:response-spec-text-content potential-response-spec))))
