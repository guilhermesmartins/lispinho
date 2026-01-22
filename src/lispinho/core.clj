(ns lispinho.core
  "Main entry point for the Lispinho Telegram Bot.
   This namespace bootstraps the DDD architecture and starts the bot."
  (:gen-class)
  (:require [lispinho.presentation.bot.telegram-bot-handler :as bot-handler]))

;; =============================================================================
;; Application Entry Point
;; =============================================================================

(defn -main
  "Main entry point for the application.
   Initializes the DDD infrastructure and starts the Telegram bot polling loop."
  [& _args]
  (println "\n")
  (println "╔══════════════════════════════════════════════════════════════╗")
  (println "║              LISPINHO TELEGRAM BOT                           ║")
  (println "║              Domain-Driven Design Architecture               ║")
  (println "╚══════════════════════════════════════════════════════════════╝")
  (println "\n")

  ;; Initialize and start the bot
  (let [result (bot-handler/initialize-and-start-bot)]
    (when-not (:success result)
      (println "\nBot failed to start:" (:error result))
      (System/exit 1))))

;; =============================================================================
;; REPL Development Helpers
;; =============================================================================

(defn start-bot
  "Starts the bot (blocking call). For REPL use."
  []
  (bot-handler/initialize-and-start-bot))

(defn start-bot-async
  "Starts the bot in a background thread. For REPL use.
   Returns the thread object."
  []
  (bot-handler/start-bot-async))

(defn stop-bot
  "Stops the running bot. For REPL use."
  []
  (bot-handler/stop-bot))

(defn bot-status
  "Returns the current bot status. For REPL use."
  []
  (bot-handler/get-bot-status))
