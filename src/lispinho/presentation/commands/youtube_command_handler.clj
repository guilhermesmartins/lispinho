(ns lispinho.presentation.commands.youtube-command-handler
  "Handler for the /yt YouTube download command."
  (:require [lispinho.domain.commands.entities :as commands-entities]
            [lispinho.application.use-cases.download-and-send-youtube-video :as download-use-case]))

;; =============================================================================
;; YouTube Command Handler
;; =============================================================================

(defn handle-youtube-download-command
  "Handles the /yt command for downloading and sending YouTube videos.

   This handler:
   1. Validates the command has a YouTube URL argument
   2. Delegates to the download-and-send use case
   3. Returns the use case result

   Parameters:
   - parsed-command-entity: The ParsedCommand containing the /yt command
   - gateway-registry: The GatewayRegistry with all dependencies

   Returns:
   Use case output with :output-success true/false"
  [parsed-command-entity gateway-registry]
  {:pre [(commands-entities/parsed-command? parsed-command-entity)
         (commands-entities/parsed-command-is-youtube-command? parsed-command-entity)]}
  (let [sender-name (commands-entities/parsed-command-extract-sender-display-name parsed-command-entity)
        url-argument (commands-entities/parsed-command-extract-first-argument parsed-command-entity)]
    (println "DEBUG [YouTube Handler]: Processing /yt command from" sender-name)
    (println "DEBUG [YouTube Handler]: URL argument:" url-argument)

    ;; Delegate to use case
    (download-use-case/handle-youtube-command
     parsed-command-entity
     gateway-registry)))
