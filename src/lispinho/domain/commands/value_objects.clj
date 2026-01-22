(ns lispinho.domain.commands.value-objects
  "Value objects for the commands domain.
   These represent bot commands and their arguments."
  (:require [clojure.string :as string]))

;; =============================================================================
;; Command Name Value Object
;; =============================================================================

(def known-command-names
  "Set of recognized bot commands."
  #{:start :help :echo :lisper :yt})

(def command-name-to-string-mapping
  "Mapping from command keyword to slash-command string."
  {:start "/start"
   :help "/help"
   :echo "/echo"
   :lisper "/lisper"
   :yt "/yt"})

(def string-to-command-name-mapping
  "Mapping from slash-command string to command keyword."
  {"/start" :start
   "/help" :help
   "/echo" :echo
   "/lisper" :lisper
   "/yt" :yt})

(defn extract-command-string-from-text
  "Extracts the command portion from a message text (e.g., '/yt' from '/yt https://...').
   Also handles @botname suffix (e.g., '/yt@mybot')."
  [message-text-string]
  (when (and (string? message-text-string)
             (string/starts-with? message-text-string "/"))
    (let [first-space-index (string/index-of message-text-string " ")
          command-with-possible-mention (if first-space-index
                                          (subs message-text-string 0 first-space-index)
                                          message-text-string)
          at-sign-index (string/index-of command-with-possible-mention "@")]
      (if at-sign-index
        (subs command-with-possible-mention 0 at-sign-index)
        command-with-possible-mention))))

(defn command-string-to-keyword
  "Converts a command string like '/yt' to a keyword like :yt."
  [command-string]
  (get string-to-command-name-mapping (string/lower-case command-string)))

(defn create-command-name
  "Creates a CommandName value object from a command keyword.

   Parameters:
   - command-keyword: One of the known command keywords (e.g., :yt, :start)"
  [command-keyword]
  {:pre [(keyword? command-keyword)
         (contains? known-command-names command-keyword)]}
  {:value-object-type :command-name
   :command-name-keyword command-keyword
   :command-name-string (get command-name-to-string-mapping command-keyword)})

(defn create-command-name-from-string
  "Creates a CommandName value object from a slash-command string.
   Returns nil if the string is not a recognized command."
  [command-string]
  (when-let [command-keyword (command-string-to-keyword command-string)]
    (create-command-name command-keyword)))

(defn command-name?
  "Predicate to check if the given value is a valid CommandName value object."
  [potential-command-name]
  (and (map? potential-command-name)
       (= :command-name (:value-object-type potential-command-name))
       (keyword? (:command-name-keyword potential-command-name))
       (contains? known-command-names (:command-name-keyword potential-command-name))))

(defn extract-command-name-keyword
  "Extracts the command keyword from a CommandName value object."
  [command-name-value-object]
  {:pre [(command-name? command-name-value-object)]}
  (:command-name-keyword command-name-value-object))

(defn extract-command-name-string
  "Extracts the slash-command string from a CommandName value object."
  [command-name-value-object]
  {:pre [(command-name? command-name-value-object)]}
  (:command-name-string command-name-value-object))

(defn command-name-is-youtube-command?
  "Checks if the command name is the YouTube download command."
  [command-name-value-object]
  {:pre [(command-name? command-name-value-object)]}
  (= :yt (:command-name-keyword command-name-value-object)))

;; =============================================================================
;; Command Arguments Value Object
;; =============================================================================

(defn extract-arguments-string-from-text
  "Extracts the arguments portion from a message text (everything after the command).
   Returns nil if there are no arguments."
  [message-text-string]
  (when (and (string? message-text-string)
             (string/starts-with? message-text-string "/"))
    (let [first-space-index (string/index-of message-text-string " ")]
      (when first-space-index
        (let [args-portion (string/trim (subs message-text-string (inc first-space-index)))]
          (when (seq args-portion)
            args-portion))))))

(defn create-command-arguments
  "Creates a CommandArguments value object from an arguments string.

   Parameters:
   - arguments-string: The raw arguments string (may be nil or empty)
   - split-arguments: Whether to split the arguments into tokens"
  ([arguments-string]
   (create-command-arguments arguments-string true))
  ([arguments-string split-arguments]
   (let [trimmed-args (when (string? arguments-string)
                        (string/trim arguments-string))
         has-arguments (and trimmed-args (seq trimmed-args))
         argument-tokens (when (and has-arguments split-arguments)
                           (string/split trimmed-args #"\s+"))]
     {:value-object-type :command-arguments
      :command-arguments-raw-string (or trimmed-args "")
      :command-arguments-tokens (or argument-tokens [])
      :command-arguments-count (count (or argument-tokens []))
      :command-arguments-has-arguments has-arguments})))

(defn command-arguments?
  "Predicate to check if the given value is a valid CommandArguments value object."
  [potential-command-arguments]
  (and (map? potential-command-arguments)
       (= :command-arguments (:value-object-type potential-command-arguments))
       (string? (:command-arguments-raw-string potential-command-arguments))
       (vector? (:command-arguments-tokens potential-command-arguments))))

(defn command-arguments-has-arguments?
  "Checks if the command arguments contain any arguments."
  [command-arguments-value-object]
  {:pre [(command-arguments? command-arguments-value-object)]}
  (:command-arguments-has-arguments command-arguments-value-object))

(defn extract-command-arguments-raw-string
  "Extracts the raw arguments string from a CommandArguments value object."
  [command-arguments-value-object]
  {:pre [(command-arguments? command-arguments-value-object)]}
  (:command-arguments-raw-string command-arguments-value-object))

(defn extract-command-arguments-first-token
  "Extracts the first argument token, or nil if no arguments."
  [command-arguments-value-object]
  {:pre [(command-arguments? command-arguments-value-object)]}
  (first (:command-arguments-tokens command-arguments-value-object)))

(defn extract-command-arguments-tokens
  "Extracts all argument tokens as a vector."
  [command-arguments-value-object]
  {:pre [(command-arguments? command-arguments-value-object)]}
  (:command-arguments-tokens command-arguments-value-object))

(defn command-arguments-get-argument-at-index
  "Gets the argument at the specified index (0-based), or nil if not present."
  [command-arguments-value-object index]
  {:pre [(command-arguments? command-arguments-value-object)
         (integer? index)
         (>= index 0)]}
  (get (:command-arguments-tokens command-arguments-value-object) index))
