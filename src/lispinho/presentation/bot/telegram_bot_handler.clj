(ns lispinho.presentation.bot.telegram-bot-handler
  "Main entry point for the Telegram bot.
   Handles the bot lifecycle and long-polling loop."
  (:require [lispinho.presentation.bot.update-processor :as update-processor]
            [lispinho.application.ports.repositories :as ports]
            [lispinho.infrastructure.telegram.telegram-api-client :as telegram-api-client]
            [lispinho.infrastructure.youtube.yt-dlp-adapter :as yt-dlp-adapter]
            [lispinho.infrastructure.persistence.file-system-repository :as file-system-repository]
            [lispinho.infrastructure.config.environment-config :as environment-config]))

;; =============================================================================
;; Bot State
;; =============================================================================

;; Atom holding the current offset for long-polling
(defonce current-polling-offset (atom 0))

;; Atom indicating whether the bot should continue running
(defonce bot-running-flag (atom false))

;; =============================================================================
;; Gateway Registry Initialization
;; =============================================================================

(defn create-gateway-registry-from-config
  "Creates a GatewayRegistry with all adapters initialized from configuration.

   Parameters:
   - config-repository: An implementation of ConfigurationRepository

   Returns:
   A GatewayRegistry record or nil if configuration is invalid."
  [config-repository]
  (let [bot-token (ports/get-telegram-bot-token config-repository)]
    (when bot-token
      (let [telegram-gateway (telegram-api-client/create-telegram-api-client bot-token)
            yt-dlp-path (ports/get-yt-dlp-executable-path config-repository)
            yt-dlp-cookies-path (ports/get-yt-dlp-cookies-path config-repository)
            yt-dlp-extra-arguments (ports/get-yt-dlp-extra-arguments config-repository)
            youtube-gateway (yt-dlp-adapter/create-yt-dlp-adapter
                              yt-dlp-path
                              yt-dlp-cookies-path
                              yt-dlp-extra-arguments)
            file-system-repo (file-system-repository/create-file-system-repository)]
        (ports/create-gateway-registry
         {:telegram-gateway telegram-gateway
          :youtube-downloader-gateway youtube-gateway
          :file-system-repository file-system-repo
          :configuration-repository config-repository
          :openai-gateway nil})))))

;; =============================================================================
;; Polling Loop
;; =============================================================================

(defn process-single-polling-iteration
  "Processes a single iteration of the polling loop.
   Fetches updates, processes them, and updates the offset.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - gateway-registry: The GatewayRegistry for processing
   - timeout-seconds: Timeout for long-polling

   Returns:
   {:success true :updates-processed <count>} or {:success false :error <message>}"
  [telegram-gateway gateway-registry timeout-seconds]
  (let [current-offset @current-polling-offset
        _ (println "\nDEBUG [Bot]: Polling with offset:" current-offset)
        updates-result (ports/get-updates-with-offset
                        telegram-gateway
                        current-offset
                        timeout-seconds)]
    (if-not (:success updates-result)
      (do
        (println "ERROR [Bot]: Failed to get updates:" (get-in updates-result [:error :error-message]))
        {:success false :error (get-in updates-result [:error :error-message])})

      (let [updates (:updates updates-result)
            updates-count (count updates)]
        (println "DEBUG [Bot]: Received" updates-count "update(s)")

        (when (seq updates)
          ;; Process each update
          (doseq [raw-update updates]
            (let [update-id (:update_id raw-update)]
              (println "DEBUG [Bot]: Processing update_id:" update-id)

              ;; Update offset to be after this update
              (swap! current-polling-offset max (inc update-id))

              ;; Process the update
              (try
                (update-processor/process-telegram-update raw-update gateway-registry)
                (catch Exception exception
                  (println "ERROR [Bot]: Failed to process update:" (.getMessage exception))
                  (.printStackTrace exception))))))

        {:success true :updates-processed updates-count}))))

(defn run-polling-loop
  "Runs the main polling loop until stopped.

   Parameters:
   - gateway-registry: The GatewayRegistry for processing
   - polling-interval-ms: Milliseconds to wait between empty polls
   - long-polling-timeout-seconds: Timeout for long-polling requests"
  [gateway-registry polling-interval-ms long-polling-timeout-seconds]
  (let [telegram-gateway (:telegram-gateway gateway-registry)]
    (reset! bot-running-flag true)
    (println "\n=== Bot Polling Loop Started ===")

    (while @bot-running-flag
      (let [result (process-single-polling-iteration
                    telegram-gateway
                    gateway-registry
                    long-polling-timeout-seconds)]
        ;; Small delay between polls (especially if empty)
        (when (and (:success result)
                   (zero? (:updates-processed result)))
          (Thread/sleep polling-interval-ms))))

    (println "=== Bot Polling Loop Stopped ===")))

(defn stop-bot
  "Signals the bot to stop running."
  []
  (reset! bot-running-flag false)
  (println "Bot stop signal sent."))

;; =============================================================================
;; Bot Initialization
;; =============================================================================

(defn initialize-and-start-bot
  "Initializes the bot and starts the polling loop.
   This is the main entry point for starting the bot.

   Returns:
   {:success true} or {:success false :error <message>}"
  []
  (println "\n=== Initializing Lispinho Telegram Bot ===")

  ;; Load configuration
  (let [config-repository (environment-config/create-environment-config-repository)]

    ;; Print configuration summary
    (environment-config/print-configuration-summary config-repository)

    ;; Validate configuration
    (let [validation-result (environment-config/validate-required-configuration config-repository)]
      (if-not (:valid validation-result)
        (do
          (println "ERROR [Bot]:" (:message validation-result))
          {:success false :error (:message validation-result)})

        ;; Create gateway registry
        (if-let [gateway-registry (create-gateway-registry-from-config config-repository)]
          (do
            (println "Bot initialized successfully. Starting polling loop...")

            ;; Ensure download directory exists
            (let [download-dir (ports/get-temp-download-directory config-repository)
                  file-system-repo (:file-system-repository gateway-registry)]
              (ports/ensure-directory-exists file-system-repo download-dir))

            ;; Start the polling loop (blocking)
            (run-polling-loop gateway-registry
                              1000  ; 1 second between empty polls
                              30)   ; 30 second long-polling timeout

            {:success true})

          (do
            (println "ERROR [Bot]: Failed to create gateway registry")
            {:success false :error "Failed to create gateway registry"}))))))

;; =============================================================================
;; Development Helpers
;; =============================================================================

(defn start-bot-async
  "Starts the bot in a separate thread for development.
   Returns the thread object."
  []
  (let [bot-thread (Thread. ^Runnable initialize-and-start-bot)]
    (.start bot-thread)
    bot-thread))

(defn get-bot-status
  "Returns the current status of the bot."
  []
  {:running @bot-running-flag
   :current-offset @current-polling-offset})
