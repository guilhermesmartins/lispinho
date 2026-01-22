(ns lispinho.domain.messaging.entities
  "Entity definitions for the messaging domain.
   Entities have identity and can change over time while maintaining that identity."
  (:require [lispinho.domain.messaging.value-objects :as messaging-value-objects]))

;; =============================================================================
;; Telegram User Entity
;; =============================================================================

(defn create-telegram-user
  "Creates a TelegramUser entity representing a user who sent a message.

   Parameters:
   - user-id-number: The unique Telegram user ID
   - user-first-name: The user's first name (required by Telegram)
   - user-last-name: The user's last name (optional)
   - user-username: The user's @username (optional)
   - is-bot-flag: Whether this user is a bot"
  [{:keys [user-id-number
           user-first-name
           user-last-name
           user-username
           is-bot-flag]}]
  {:pre [(integer? user-id-number)
         (pos? user-id-number)
         (string? user-first-name)
         (seq user-first-name)]}
  {:entity-type :telegram-user
   :telegram-user-id user-id-number
   :telegram-user-first-name user-first-name
   :telegram-user-last-name user-last-name
   :telegram-user-username user-username
   :telegram-user-is-bot (boolean is-bot-flag)})

(defn telegram-user?
  "Predicate to check if the given value is a valid TelegramUser entity."
  [potential-telegram-user]
  (and (map? potential-telegram-user)
       (= :telegram-user (:entity-type potential-telegram-user))
       (integer? (:telegram-user-id potential-telegram-user))))

(defn telegram-user-display-name
  "Returns a human-readable display name for the user.
   Prefers username, then full name, then first name."
  [telegram-user-entity]
  {:pre [(telegram-user? telegram-user-entity)]}
  (or (when-let [username (:telegram-user-username telegram-user-entity)]
        (str "@" username))
      (when-let [last-name (:telegram-user-last-name telegram-user-entity)]
        (str (:telegram-user-first-name telegram-user-entity) " " last-name))
      (:telegram-user-first-name telegram-user-entity)))

;; =============================================================================
;; Telegram Chat Entity
;; =============================================================================

(def valid-chat-type-values
  "Valid chat types in Telegram."
  #{:private :group :supergroup :channel})

(defn create-telegram-chat
  "Creates a TelegramChat entity representing a chat context.

   Parameters:
   - chat-id-value-object: A ChatId value object
   - chat-type-keyword: One of :private, :group, :supergroup, :channel
   - chat-title-string: The title of the chat (for groups/channels, nil for private)"
  [{:keys [chat-id-value-object
           chat-type-keyword
           chat-title-string]}]
  {:pre [(messaging-value-objects/chat-id? chat-id-value-object)
         (contains? valid-chat-type-values chat-type-keyword)]}
  {:entity-type :telegram-chat
   :telegram-chat-id chat-id-value-object
   :telegram-chat-type chat-type-keyword
   :telegram-chat-title chat-title-string})

(defn telegram-chat?
  "Predicate to check if the given value is a valid TelegramChat entity."
  [potential-telegram-chat]
  (and (map? potential-telegram-chat)
       (= :telegram-chat (:entity-type potential-telegram-chat))
       (messaging-value-objects/chat-id? (:telegram-chat-id potential-telegram-chat))
       (contains? valid-chat-type-values (:telegram-chat-type potential-telegram-chat))))

(defn telegram-chat-is-private?
  "Checks if the chat is a private/DM conversation."
  [telegram-chat-entity]
  {:pre [(telegram-chat? telegram-chat-entity)]}
  (= :private (:telegram-chat-type telegram-chat-entity)))

(defn telegram-chat-is-group-or-supergroup?
  "Checks if the chat is a group or supergroup (where replies should quote the original message)."
  [telegram-chat-entity]
  {:pre [(telegram-chat? telegram-chat-entity)]}
  (contains? #{:group :supergroup} (:telegram-chat-type telegram-chat-entity)))

;; =============================================================================
;; Telegram Message Entity
;; =============================================================================

(defn create-telegram-message
  "Creates a TelegramMessage entity representing a received message.

   Parameters:
   - message-id-value-object: A MessageId value object
   - message-chat-entity: The TelegramChat where the message was sent
   - message-sender-entity: The TelegramUser who sent the message
   - message-text-value-object: The MessageText content (optional for non-text messages)
   - message-date-unix-timestamp: Unix timestamp when the message was sent"
  [{:keys [message-id-value-object
           message-chat-entity
           message-sender-entity
           message-text-value-object
           message-date-unix-timestamp]}]
  {:pre [(messaging-value-objects/message-id? message-id-value-object)
         (telegram-chat? message-chat-entity)
         (telegram-user? message-sender-entity)
         (integer? message-date-unix-timestamp)]}
  {:entity-type :telegram-message
   :telegram-message-id message-id-value-object
   :telegram-message-chat message-chat-entity
   :telegram-message-sender message-sender-entity
   :telegram-message-text message-text-value-object
   :telegram-message-date message-date-unix-timestamp})

(defn telegram-message?
  "Predicate to check if the given value is a valid TelegramMessage entity."
  [potential-telegram-message]
  (and (map? potential-telegram-message)
       (= :telegram-message (:entity-type potential-telegram-message))
       (messaging-value-objects/message-id? (:telegram-message-id potential-telegram-message))
       (telegram-chat? (:telegram-message-chat potential-telegram-message))))

(defn telegram-message-has-text?
  "Checks if the message contains text content."
  [telegram-message-entity]
  {:pre [(telegram-message? telegram-message-entity)]}
  (some? (:telegram-message-text telegram-message-entity)))

(defn telegram-message-is-command?
  "Checks if the message is a bot command (starts with '/')."
  [telegram-message-entity]
  {:pre [(telegram-message? telegram-message-entity)]}
  (and (telegram-message-has-text? telegram-message-entity)
       (messaging-value-objects/message-text-starts-with-command?
        (:telegram-message-text telegram-message-entity))))

(defn telegram-message-requires-reply-to-original?
  "Determines if the response to this message should be a reply (in groups)
   or a direct message (in DMs)."
  [telegram-message-entity]
  {:pre [(telegram-message? telegram-message-entity)]}
  (telegram-chat-is-group-or-supergroup?
   (:telegram-message-chat telegram-message-entity)))
