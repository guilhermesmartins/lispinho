(ns lispinho.application.use-cases.download-and-send-youtube-video
  "Use case for downloading a YouTube video and sending it to a Telegram chat.
   This is the main entry point for the /yt command processing."
  (:require [lispinho.domain.messaging.aggregates :as messaging-aggregates]
            [lispinho.domain.commands.entities :as commands-entities]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.services.youtube-download-service :as youtube-download-service]
            [lispinho.application.services.message-reply-service :as message-reply-service]
            [lispinho.application.ports.repositories :as ports]))

;; =============================================================================
;; Use Case Input
;; =============================================================================

(defn create-download-and-send-video-input
  "Creates the input structure for the download-and-send use case.

   Parameters:
   - parsed-command-entity: The ParsedCommand containing the YouTube URL
   - gateway-registry: The GatewayRegistry with all dependencies"
  [parsed-command-entity gateway-registry]
  {:pre [(commands-entities/parsed-command? parsed-command-entity)
         (map? gateway-registry)
         (contains? gateway-registry :telegram-gateway)
         (contains? gateway-registry :youtube-downloader-gateway)
         (contains? gateway-registry :file-system-repository)
         (contains? gateway-registry :configuration-repository)]}
  {:use-case-input-type :download-and-send-video
   :input-parsed-command parsed-command-entity
   :input-gateway-registry gateway-registry})

;; =============================================================================
;; Use Case Output
;; =============================================================================

(defn create-success-output
  "Creates a successful use case output."
  [message-id]
  {:use-case-output-type :download-and-send-video
   :output-success true
   :output-message-id message-id})

(defn create-failure-output
  "Creates a failed use case output."
  [domain-error]
  {:pre [(error-types/domain-error? domain-error)]}
  {:use-case-output-type :download-and-send-video
   :output-success false
   :output-error domain-error})

;; =============================================================================
;; Use Case Execution
;; =============================================================================

(defn execute-download-and-send-youtube-video
  "Executes the complete download-and-send YouTube video use case.

   This use case:
   1. Extracts the YouTube URL from the command arguments
   2. Validates the URL
   3. Fetches video metadata
   4. Validates video meets requirements (duration, availability)
   5. Downloads the video
   6. Sends the video to the chat (with reply-to for groups)
   7. Cleans up the downloaded file

   Parameters:
   - use-case-input: The input structure from create-download-and-send-video-input

   Returns:
   A use case output with :output-success true/false"
  [use-case-input]
  (let [parsed-command (:input-parsed-command use-case-input)
        gateway-registry (:input-gateway-registry use-case-input)
        message-context (commands-entities/parsed-command-extract-message-context parsed-command)

        ;; Extract gateways from registry
        telegram-gateway (:telegram-gateway gateway-registry)
        youtube-gateway (:youtube-downloader-gateway gateway-registry)
        file-system-repo (:file-system-repository gateway-registry)
        config-repo (:configuration-repository gateway-registry)

        ;; Get configuration values
        download-directory (ports/get-temp-download-directory config-repo)
        max-duration-minutes (ports/get-maximum-video-duration-minutes config-repo)

        ;; Extract URL from command arguments
        url-string (commands-entities/parsed-command-extract-first-argument parsed-command)]

    (println "DEBUG [Use Case]: Starting download-and-send for URL:" url-string)

    ;; Check if URL was provided
    (if (nil? url-string)
      (let [error (error-types/create-missing-youtube-url-error)]
        (println "DEBUG [Use Case]: No URL provided")
        (message-reply-service/send-error-reply-to-context
         telegram-gateway
         message-context
         error)
        (create-failure-output error))

      ;; Execute the download workflow
      (let [_ (println "DEBUG [Use Case]: Sending initial progress message")
            chat-id (messaging-aggregates/message-context-extract-chat-id-for-response message-context)

            ;; Send initial progress indicator
            _ (message-reply-service/send-typing-action telegram-gateway chat-id)

            ;; Execute download workflow
            download-result (youtube-download-service/execute-complete-download-workflow
                             youtube-gateway
                             file-system-repo
                             url-string
                             download-directory
                             max-duration-minutes)]

        (if-not (:success download-result)
          ;; Download failed - send error message
          (let [error (:error download-result)]
            (println "DEBUG [Use Case]: Download workflow failed:"
                     (error-types/format-error-for-logging error))
            (message-reply-service/send-error-reply-to-context
             telegram-gateway
             message-context
             error)
            (create-failure-output error))

          ;; Download succeeded - send video
          (let [downloadable-video (:downloadable-video download-result)
                _ (println "DEBUG [Use Case]: Download complete, sending video")

                ;; Send the video
                send-result (message-reply-service/send-video-reply-to-context
                             telegram-gateway
                             message-context
                             downloadable-video)]

            ;; Cleanup the downloaded file (regardless of send success)
            (youtube-download-service/cleanup-downloaded-video-file
             file-system-repo
             downloadable-video)

            (if (:success send-result)
              (do
                (println "DEBUG [Use Case]: Video sent successfully")
                (create-success-output (:message-id send-result)))

              (let [error (:error send-result)]
                (println "DEBUG [Use Case]: Video send failed:"
                         (error-types/format-error-for-logging error))
                (message-reply-service/send-error-reply-to-context
                 telegram-gateway
                 message-context
                 error)
                (create-failure-output error)))))))))

;; =============================================================================
;; Convenience Function
;; =============================================================================

(defn handle-youtube-command
  "Convenience function to handle a /yt command directly.

   Parameters:
   - parsed-command-entity: The ParsedCommand from the /yt command
   - gateway-registry: The GatewayRegistry with all dependencies

   Returns:
   A use case output with :output-success true/false"
  [parsed-command-entity gateway-registry]
  {:pre [(commands-entities/parsed-command? parsed-command-entity)
         (commands-entities/parsed-command-is-youtube-command? parsed-command-entity)]}
  (let [use-case-input (create-download-and-send-video-input
                        parsed-command-entity
                        gateway-registry)]
    (execute-download-and-send-youtube-video use-case-input)))
