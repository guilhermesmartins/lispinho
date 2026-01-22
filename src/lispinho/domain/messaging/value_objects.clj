(ns lispinho.domain.messaging.value-objects
  "Value objects for the messaging domain.
   These are immutable, identity-less objects that represent concepts in the messaging context."
  (:require [clojure.spec.alpha :as spec]))

;; =============================================================================
;; Chat ID Value Object
;; =============================================================================

(defn create-chat-id
  "Creates a ChatId value object from a numeric identifier.
   Chat IDs in Telegram can be positive (users, groups) or negative (supergroups, channels)."
  [numeric-chat-identifier]
  {:pre [(integer? numeric-chat-identifier)]}
  {:value-object-type :chat-id
   :chat-id-value numeric-chat-identifier})

(defn chat-id?
  "Predicate to check if the given value is a valid ChatId value object."
  [potential-chat-id]
  (and (map? potential-chat-id)
       (= :chat-id (:value-object-type potential-chat-id))
       (integer? (:chat-id-value potential-chat-id))))

(defn extract-chat-id-value
  "Extracts the raw numeric value from a ChatId value object."
  [chat-id-value-object]
  {:pre [(chat-id? chat-id-value-object)]}
  (:chat-id-value chat-id-value-object))

(defn chat-id-is-group?
  "Determines if the ChatId represents a group chat (negative ID in Telegram)."
  [chat-id-value-object]
  {:pre [(chat-id? chat-id-value-object)]}
  (neg? (:chat-id-value chat-id-value-object)))

(defn chat-id-is-private?
  "Determines if the ChatId represents a private/DM chat (positive ID in Telegram)."
  [chat-id-value-object]
  {:pre [(chat-id? chat-id-value-object)]}
  (pos? (:chat-id-value chat-id-value-object)))

;; =============================================================================
;; Message ID Value Object
;; =============================================================================

(defn create-message-id
  "Creates a MessageId value object from a numeric identifier.
   Message IDs are always positive integers in Telegram."
  [numeric-message-identifier]
  {:pre [(and (integer? numeric-message-identifier)
              (pos? numeric-message-identifier))]}
  {:value-object-type :message-id
   :message-id-value numeric-message-identifier})

(defn message-id?
  "Predicate to check if the given value is a valid MessageId value object."
  [potential-message-id]
  (and (map? potential-message-id)
       (= :message-id (:value-object-type potential-message-id))
       (integer? (:message-id-value potential-message-id))
       (pos? (:message-id-value potential-message-id))))

(defn extract-message-id-value
  "Extracts the raw numeric value from a MessageId value object."
  [message-id-value-object]
  {:pre [(message-id? message-id-value-object)]}
  (:message-id-value message-id-value-object))

;; =============================================================================
;; Message Text Value Object
;; =============================================================================

(def maximum-telegram-message-text-length
  "Maximum length for a Telegram text message (4096 characters)."
  4096)

(defn create-message-text
  "Creates a MessageText value object from a string.
   The text must not be nil or empty, and must not exceed Telegram's limit."
  [text-content]
  {:pre [(string? text-content)
         (seq text-content)
         (<= (count text-content) maximum-telegram-message-text-length)]}
  {:value-object-type :message-text
   :message-text-content text-content
   :message-text-length (count text-content)})

(defn message-text?
  "Predicate to check if the given value is a valid MessageText value object."
  [potential-message-text]
  (and (map? potential-message-text)
       (= :message-text (:value-object-type potential-message-text))
       (string? (:message-text-content potential-message-text))
       (seq (:message-text-content potential-message-text))))

(defn extract-message-text-content
  "Extracts the raw string content from a MessageText value object."
  [message-text-value-object]
  {:pre [(message-text? message-text-value-object)]}
  (:message-text-content message-text-value-object))

(defn message-text-starts-with-command?
  "Checks if the message text starts with a command (i.e., starts with '/')."
  [message-text-value-object]
  {:pre [(message-text? message-text-value-object)]}
  (clojure.string/starts-with?
   (:message-text-content message-text-value-object)
   "/"))

;; =============================================================================
;; Update ID Value Object
;; =============================================================================

(defn create-update-id
  "Creates an UpdateId value object from a numeric identifier.
   Update IDs are always positive integers and monotonically increasing."
  [numeric-update-identifier]
  {:pre [(and (integer? numeric-update-identifier)
              (pos? numeric-update-identifier))]}
  {:value-object-type :update-id
   :update-id-value numeric-update-identifier})

(defn update-id?
  "Predicate to check if the given value is a valid UpdateId value object."
  [potential-update-id]
  (and (map? potential-update-id)
       (= :update-id (:value-object-type potential-update-id))
       (integer? (:update-id-value potential-update-id))
       (pos? (:update-id-value potential-update-id))))

(defn extract-update-id-value
  "Extracts the raw numeric value from an UpdateId value object."
  [update-id-value-object]
  {:pre [(update-id? update-id-value-object)]}
  (:update-id-value update-id-value-object))

(defn calculate-next-offset-from-update-id
  "Calculates the next offset to use for long-polling based on the current update ID."
  [update-id-value-object]
  {:pre [(update-id? update-id-value-object)]}
  (inc (:update-id-value update-id-value-object)))
