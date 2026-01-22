(ns lispinho.domain.commands.entities
  "Entity definitions for the commands domain.
   ParsedCommand represents a fully parsed bot command ready for execution."
  (:require [lispinho.domain.commands.value-objects :as commands-value-objects]
            [lispinho.domain.messaging.aggregates :as messaging-aggregates]
            [clojure.string :as string]))

;; =============================================================================
;; Parsed Command Entity
;; =============================================================================
;; Represents a bot command that has been parsed from a message and is ready
;; for execution. Contains the command name, arguments, and context needed
;; for executing the command.

(defn create-parsed-command
  "Creates a ParsedCommand entity from a message context.

   Parameters:
   - message-context-aggregate: The MessageContext containing the command message
   - command-name-value-object: The parsed CommandName
   - command-arguments-value-object: The parsed CommandArguments"
  [{:keys [message-context-aggregate
           command-name-value-object
           command-arguments-value-object]}]
  {:pre [(messaging-aggregates/message-context? message-context-aggregate)
         (commands-value-objects/command-name? command-name-value-object)
         (commands-value-objects/command-arguments? command-arguments-value-object)]}
  {:entity-type :parsed-command
   :parsed-command-name command-name-value-object
   :parsed-command-arguments command-arguments-value-object
   :parsed-command-context message-context-aggregate
   :parsed-command-created-at (System/currentTimeMillis)})

(defn parsed-command?
  "Predicate to check if the given value is a valid ParsedCommand entity."
  [potential-parsed-command]
  (and (map? potential-parsed-command)
       (= :parsed-command (:entity-type potential-parsed-command))
       (commands-value-objects/command-name? (:parsed-command-name potential-parsed-command))
       (commands-value-objects/command-arguments? (:parsed-command-arguments potential-parsed-command))
       (messaging-aggregates/message-context? (:parsed-command-context potential-parsed-command))))

(defn parsed-command-extract-command-keyword
  "Extracts the command keyword from a ParsedCommand entity."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (commands-value-objects/extract-command-name-keyword
   (:parsed-command-name parsed-command-entity)))

(defn parsed-command-extract-command-string
  "Extracts the command string (e.g., '/yt') from a ParsedCommand entity."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (commands-value-objects/extract-command-name-string
   (:parsed-command-name parsed-command-entity)))

(defn parsed-command-has-arguments?
  "Checks if the parsed command has any arguments."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (commands-value-objects/command-arguments-has-arguments?
   (:parsed-command-arguments parsed-command-entity)))

(defn parsed-command-extract-first-argument
  "Extracts the first argument from the parsed command, or nil if none."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (commands-value-objects/extract-command-arguments-first-token
   (:parsed-command-arguments parsed-command-entity)))

(defn parsed-command-extract-raw-arguments
  "Extracts the raw arguments string from the parsed command."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (commands-value-objects/extract-command-arguments-raw-string
   (:parsed-command-arguments parsed-command-entity)))

(defn parsed-command-extract-message-context
  "Extracts the MessageContext from the parsed command."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (:parsed-command-context parsed-command-entity))

(defn parsed-command-is-youtube-command?
  "Checks if this parsed command is the YouTube download command."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (commands-value-objects/command-name-is-youtube-command?
   (:parsed-command-name parsed-command-entity)))

(defn parsed-command-extract-sender-display-name
  "Extracts the display name of the user who sent this command."
  [parsed-command-entity]
  {:pre [(parsed-command? parsed-command-entity)]}
  (messaging-aggregates/message-context-extract-sender-display-name
   (:parsed-command-context parsed-command-entity)))

;; =============================================================================
;; Command Parsing Functions
;; =============================================================================

(defn parse-command-from-message-context
  "Parses a command from a MessageContext aggregate.
   Returns a ParsedCommand entity or nil if the message is not a valid command."
  [message-context-aggregate]
  {:pre [(messaging-aggregates/message-context? message-context-aggregate)]}
  (when (messaging-aggregates/message-context-is-command? message-context-aggregate)
    (let [message-text (messaging-aggregates/message-context-extract-message-text
                        message-context-aggregate)
          command-string (commands-value-objects/extract-command-string-from-text message-text)
          command-name (commands-value-objects/create-command-name-from-string command-string)]
      (when command-name
        (let [arguments-string (commands-value-objects/extract-arguments-string-from-text message-text)
              arguments (commands-value-objects/create-command-arguments arguments-string)]
          (create-parsed-command
           {:message-context-aggregate message-context-aggregate
            :command-name-value-object command-name
            :command-arguments-value-object arguments}))))))

(defn try-parse-command-from-message-context
  "Safely attempts to parse a command from a MessageContext.
   Returns {:success true :command <parsed-command>} or {:success false :reason <string>}."
  [message-context-aggregate]
  (try
    (if-let [parsed-command (parse-command-from-message-context message-context-aggregate)]
      {:success true
       :command parsed-command}
      {:success false
       :reason "Message is not a recognized command"})
    (catch Exception exception
      {:success false
       :reason (str "Error parsing command: " (.getMessage exception))})))
