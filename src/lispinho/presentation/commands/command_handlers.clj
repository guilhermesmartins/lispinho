(ns lispinho.presentation.commands.command-handlers
  "Command dispatcher and handlers for bot commands."
  (:require [lispinho.domain.commands.entities :as commands-entities]
            [lispinho.domain.messaging.aggregates :as messaging-aggregates]
            [lispinho.application.services.message-reply-service :as message-reply-service]
            [lispinho.presentation.commands.youtube-command-handler :as youtube-command-handler]))

;; =============================================================================
;; Command Handler Responses
;; =============================================================================

(def help-command-response-text
  "Response text for the /help command."
  "Available commands:

/start - Start the bot and see welcome message
/help - Show this help message
/echo <text> - Echo back your message
/lisper <prompt> - Send a prompt to the AI assistant
/yt <youtube-url> - Download and send a YouTube video

For /yt, supported URL formats:
• youtube.com/watch?v=VIDEO_ID
• youtu.be/VIDEO_ID
• youtube.com/shorts/VIDEO_ID")

(def start-command-response-text
  "Response text for the /start command."
  "Welcome to Lispinho Bot!

I can help you with:
• Echoing messages (/echo)
• AI assistance (/lisper)
• Downloading YouTube videos (/yt)

Type /help to see all available commands.")

;; =============================================================================
;; Simple Command Handlers
;; =============================================================================

(defn handle-start-command
  "Handles the /start command.

   Parameters:
   - parsed-command-entity: The ParsedCommand
   - gateway-registry: The GatewayRegistry

   Returns:
   {:success true/false :response <string>}"
  [parsed-command-entity gateway-registry]
  (let [message-context (commands-entities/parsed-command-extract-message-context parsed-command-entity)
        telegram-gateway (:telegram-gateway gateway-registry)]
    (println "DEBUG [Commands]: Handling /start command")
    (message-reply-service/send-text-reply-to-context
     telegram-gateway
     message-context
     start-command-response-text)
    {:success true :response start-command-response-text}))

(defn handle-help-command
  "Handles the /help command.

   Parameters:
   - parsed-command-entity: The ParsedCommand
   - gateway-registry: The GatewayRegistry

   Returns:
   {:success true/false :response <string>}"
  [parsed-command-entity gateway-registry]
  (let [message-context (commands-entities/parsed-command-extract-message-context parsed-command-entity)
        telegram-gateway (:telegram-gateway gateway-registry)]
    (println "DEBUG [Commands]: Handling /help command")
    (message-reply-service/send-text-reply-to-context
     telegram-gateway
     message-context
     help-command-response-text)
    {:success true :response help-command-response-text}))

(defn handle-echo-command
  "Handles the /echo command.

   Parameters:
   - parsed-command-entity: The ParsedCommand
   - gateway-registry: The GatewayRegistry

   Returns:
   {:success true/false :response <string>}"
  [parsed-command-entity gateway-registry]
  (let [message-context (commands-entities/parsed-command-extract-message-context parsed-command-entity)
        telegram-gateway (:telegram-gateway gateway-registry)
        arguments (commands-entities/parsed-command-extract-raw-arguments parsed-command-entity)]
    (println "DEBUG [Commands]: Handling /echo command with args:" arguments)
    (if (seq arguments)
      (do
        (message-reply-service/send-text-reply-to-context
         telegram-gateway
         message-context
         arguments)
        {:success true :response arguments})
      (let [usage-message "Please provide a message to echo. Usage: /echo <message>"]
        (message-reply-service/send-text-reply-to-context
         telegram-gateway
         message-context
         usage-message)
        {:success false :response usage-message}))))

(defn handle-lisper-command
  "Handles the /lisper command (OpenAI integration).
   Note: This is a placeholder - OpenAI integration needs to be implemented.

   Parameters:
   - parsed-command-entity: The ParsedCommand
   - gateway-registry: The GatewayRegistry

   Returns:
   {:success true/false :response <string>}"
  [parsed-command-entity gateway-registry]
  (let [message-context (commands-entities/parsed-command-extract-message-context parsed-command-entity)
        telegram-gateway (:telegram-gateway gateway-registry)
        arguments (commands-entities/parsed-command-extract-raw-arguments parsed-command-entity)]
    (println "DEBUG [Commands]: Handling /lisper command with args:" arguments)
    (if (seq arguments)
      ;; TODO: Implement OpenAI integration via OpenAIGateway
      (let [response-message "The /lisper command is currently being updated. Please try again later."]
        (message-reply-service/send-text-reply-to-context
         telegram-gateway
         message-context
         response-message)
        {:success false :response response-message})
      (let [usage-message "Please provide a prompt. Usage: /lisper <your prompt>"]
        (message-reply-service/send-text-reply-to-context
         telegram-gateway
         message-context
         usage-message)
        {:success false :response usage-message}))))

;; =============================================================================
;; Command Dispatcher
;; =============================================================================

(defn dispatch-command
  "Dispatches a parsed command to its appropriate handler.

   Parameters:
   - parsed-command-entity: The ParsedCommand to dispatch
   - gateway-registry: The GatewayRegistry for processing

   Returns:
   Handler-specific result map"
  [parsed-command-entity gateway-registry]
  {:pre [(commands-entities/parsed-command? parsed-command-entity)]}
  (let [command-keyword (commands-entities/parsed-command-extract-command-keyword parsed-command-entity)]
    (println "DEBUG [Commands]: Dispatching command:" command-keyword)

    (case command-keyword
      :start (handle-start-command parsed-command-entity gateway-registry)
      :help (handle-help-command parsed-command-entity gateway-registry)
      :echo (handle-echo-command parsed-command-entity gateway-registry)
      :lisper (handle-lisper-command parsed-command-entity gateway-registry)
      :yt (youtube-command-handler/handle-youtube-download-command
           parsed-command-entity
           gateway-registry)

      ;; Unknown command (shouldn't happen with proper parsing)
      (let [message-context (commands-entities/parsed-command-extract-message-context parsed-command-entity)
            telegram-gateway (:telegram-gateway gateway-registry)
            unknown-message "Unknown command. Type /help to see available commands."]
        (message-reply-service/send-text-reply-to-context
         telegram-gateway
         message-context
         unknown-message)
        {:success false :response unknown-message}))))
