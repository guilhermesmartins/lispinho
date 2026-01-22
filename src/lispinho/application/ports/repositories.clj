(ns lispinho.application.ports.repositories
  "Port definitions (protocols) for external dependencies.
   These protocols define the interfaces that infrastructure adapters must implement.")

;; =============================================================================
;; Telegram Message Gateway Protocol
;; =============================================================================
;; Defines operations for interacting with Telegram's API.

(defprotocol TelegramMessageGateway
  "Gateway protocol for Telegram messaging operations.
   Implementations handle the actual HTTP communication with Telegram's Bot API."

  (send-text-message
    [gateway chat-id-value-object text-content]
    [gateway chat-id-value-object text-content reply-to-message-id-value-object]
    "Sends a text message to a chat.
     Returns {:success true :message-id <id>} or {:success false :error <domain-error>}")

  (send-video-file
    [gateway chat-id-value-object video-file-path-value-object caption-text]
    [gateway chat-id-value-object video-file-path-value-object caption-text reply-to-message-id-value-object]
    "Sends a video file to a chat.
     Returns {:success true :message-id <id>} or {:success false :error <domain-error>}")

  (send-chat-action
    [gateway chat-id-value-object action-keyword]
    "Sends a chat action (typing indicator, upload_video, etc.).
     action-keyword should be one of: :typing :upload_video :upload_photo :upload_document
     Returns {:success true} or {:success false :error <domain-error>}")

  (get-updates-with-offset
    [gateway current-offset-value timeout-seconds]
    "Fetches new updates from Telegram using long polling.
     Returns {:success true :updates <vector-of-updates>} or {:success false :error <domain-error>}"))

;; =============================================================================
;; YouTube Downloader Gateway Protocol
;; =============================================================================
;; Defines operations for downloading videos from YouTube.

(defprotocol YouTubeDownloaderGateway
  "Gateway protocol for YouTube video downloading operations.
   Implementations typically wrap yt-dlp or similar tools."

  (fetch-video-metadata
    [gateway youtube-url-value-object]
    "Fetches metadata about a YouTube video without downloading it.
     Returns {:success true :metadata <video-metadata-entity>}
     or {:success false :error <domain-error>}")

  (download-video-to-file
    [gateway youtube-url-value-object target-directory-path maximum-file-size-bytes]
    "Downloads a video to a specified directory.
     Returns {:success true :file-path <video-file-path-value-object>}
     or {:success false :error <domain-error>}")

  (validate-youtube-url
    [gateway url-string]
    "Validates whether a string is a valid YouTube URL.
     Returns {:valid true :youtube-url <youtube-url-value-object>}
     or {:valid false :reason <string>}"))

;; =============================================================================
;; File System Repository Protocol
;; =============================================================================
;; Defines operations for managing temporary files.

(defprotocol FileSystemRepository
  "Repository protocol for file system operations.
   Manages temporary files used during video processing."

  (ensure-directory-exists
    [repository directory-path-string]
    "Ensures a directory exists, creating it if necessary.
     Returns {:success true :path <string>} or {:success false :error <domain-error>}")

  (delete-file-if-exists
    [repository file-path-string]
    "Deletes a file if it exists. Used for cleanup after sending.
     Returns {:success true :deleted <boolean>} or {:success false :error <domain-error>}")

  (get-file-size
    [repository file-path-string]
    "Gets the size of a file in bytes.
     Returns {:success true :size-bytes <long>} or {:success false :error <domain-error>}")

  (file-exists?
    [repository file-path-string]
    "Checks if a file exists.
     Returns true or false."))

;; =============================================================================
;; Configuration Repository Protocol
;; =============================================================================
;; Defines operations for accessing configuration values.

(defprotocol ConfigurationRepository
  "Repository protocol for accessing application configuration.
   Typically reads from environment variables."

  (get-telegram-bot-token
    [repository]
    "Gets the Telegram bot token.
     Returns the token string or nil if not configured.")

  (get-temp-download-directory
    [repository]
    "Gets the temporary download directory path.
     Returns the path string or a default value.")

  (get-yt-dlp-executable-path
    [repository]
    "Gets the path to the yt-dlp executable.
     Returns the path string or 'yt-dlp' as default.")

  (get-yt-dlp-cookies-path
    [repository]
    "Gets the path to a yt-dlp cookies file.
     Returns the path string or nil if not configured.")

  (get-yt-dlp-extra-arguments
    [repository]
    "Gets additional yt-dlp CLI arguments.

     Intended for deployment-time tweaks (proxy, extractor args, user agent, etc.)
     without changing code.

     Returns a vector of argument strings (e.g. ["--proxy" "socks5://..."]).
     Defaults to an empty vector.")

  (get-maximum-video-duration-minutes
    [repository]
    "Gets the maximum allowed video duration in minutes.
     Returns an integer, defaulting to 15."))

;; =============================================================================
;; OpenAI Gateway Protocol (for /lisper command)
;; =============================================================================
;; Preserves the existing OpenAI integration.

(defprotocol OpenAIGateway
  "Gateway protocol for OpenAI API operations.
   Used by the /lisper command."

  (send-completion-request
    [gateway prompt-text]
    "Sends a completion request to OpenAI.
     Returns {:success true :response-text <string>}
     or {:success false :error <domain-error>}"))

;; =============================================================================
;; Gateway Registry
;; =============================================================================
;; A record type that holds all gateway implementations for dependency injection.

(defrecord GatewayRegistry
  [telegram-gateway
   youtube-downloader-gateway
   file-system-repository
   configuration-repository
   openai-gateway])

(defn create-gateway-registry
  "Creates a GatewayRegistry with all the provided gateway implementations.

   Parameters:
   - telegram-gateway: Implementation of TelegramMessageGateway
   - youtube-downloader-gateway: Implementation of YouTubeDownloaderGateway
   - file-system-repository: Implementation of FileSystemRepository
   - configuration-repository: Implementation of ConfigurationRepository
   - openai-gateway: Implementation of OpenAIGateway (optional)"
  [{:keys [telegram-gateway
           youtube-downloader-gateway
           file-system-repository
           configuration-repository
           openai-gateway]}]
  (->GatewayRegistry
   telegram-gateway
   youtube-downloader-gateway
   file-system-repository
   configuration-repository
   openai-gateway))
