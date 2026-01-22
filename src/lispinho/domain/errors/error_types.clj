(ns lispinho.domain.errors.error-types
  "Domain error definitions for the Lispinho bot.
   These represent all the ways operations can fail in the domain.")

;; =============================================================================
;; Error Categories
;; =============================================================================

(def error-categories
  "Categories of errors in the domain."
  #{:validation-error      ; Input validation failures
    :youtube-error         ; YouTube-related errors
    :download-error        ; Download operation failures
    :telegram-error        ; Telegram API errors
    :configuration-error   ; Missing or invalid configuration
    :system-error})        ; Unexpected system failures

;; =============================================================================
;; Domain Error Structure
;; =============================================================================

(defn create-domain-error
  "Creates a domain error with structured information.

   Parameters:
   - error-category-keyword: One of the error categories
   - error-code-keyword: A specific error code within the category
   - error-message-string: Human-readable error message
   - error-context-map: Optional additional context data"
  ([error-category-keyword error-code-keyword error-message-string]
   (create-domain-error error-category-keyword error-code-keyword error-message-string {}))
  ([error-category-keyword error-code-keyword error-message-string error-context-map]
   {:pre [(contains? error-categories error-category-keyword)
          (keyword? error-code-keyword)
          (string? error-message-string)
          (map? error-context-map)]}
   {:error-type :domain-error
    :error-category error-category-keyword
    :error-code error-code-keyword
    :error-message error-message-string
    :error-context error-context-map
    :error-timestamp (System/currentTimeMillis)}))

(defn domain-error?
  "Predicate to check if the given value is a domain error."
  [potential-error]
  (and (map? potential-error)
       (= :domain-error (:error-type potential-error))
       (contains? error-categories (:error-category potential-error))))

;; =============================================================================
;; Validation Errors
;; =============================================================================

(defn create-invalid-youtube-url-error
  "Creates an error for an invalid YouTube URL."
  [provided-url-string]
  (create-domain-error
   :validation-error
   :invalid-youtube-url
   "The provided URL is not a valid YouTube video URL."
   {:provided-url provided-url-string
    :supported-formats ["youtube.com/watch?v=VIDEO_ID"
                        "youtu.be/VIDEO_ID"
                        "youtube.com/shorts/VIDEO_ID"
                        "m.youtube.com/watch?v=VIDEO_ID"]}))

(defn create-missing-youtube-url-error
  "Creates an error when no YouTube URL was provided with the /yt command."
  []
  (create-domain-error
   :validation-error
   :missing-youtube-url
   "Please provide a YouTube URL. Usage: /yt <youtube-url>"
   {:usage-example "/yt https://www.youtube.com/watch?v=dQw4w9WgXcQ"}))

(defn create-video-too-long-error
  "Creates an error when a video exceeds the maximum duration limit."
  [video-duration-seconds maximum-duration-minutes]
  (create-domain-error
   :validation-error
   :video-too-long
   (format "Video duration (%d minutes) exceeds the maximum allowed (%d minutes)."
           (quot video-duration-seconds 60)
           maximum-duration-minutes)
   {:video-duration-seconds video-duration-seconds
    :maximum-duration-minutes maximum-duration-minutes}))

(defn create-video-file-too-large-error
  "Creates an error when a video file exceeds Telegram's upload limit."
  [file-size-bytes maximum-size-bytes]
  (create-domain-error
   :validation-error
   :video-file-too-large
   (format "Video file size (%.1f MB) exceeds Telegram's limit (%.1f MB)."
           (/ file-size-bytes 1024.0 1024.0)
           (/ maximum-size-bytes 1024.0 1024.0))
   {:file-size-bytes file-size-bytes
    :maximum-size-bytes maximum-size-bytes}))

;; =============================================================================
;; YouTube Errors
;; =============================================================================

(defn create-video-unavailable-error
  "Creates an error when a YouTube video is not available."
  [video-id reason-string]
  (create-domain-error
   :youtube-error
   :video-unavailable
   (str "Video is not available: " reason-string)
   {:video-id video-id
    :reason reason-string}))

(defn create-video-private-error
  "Creates an error when trying to download a private video."
  [video-id]
  (create-domain-error
   :youtube-error
   :video-private
   "This video is private and cannot be downloaded."
   {:video-id video-id}))

(defn create-video-age-restricted-error
  "Creates an error when trying to download an age-restricted video."
  [video-id]
  (create-domain-error
   :youtube-error
   :video-age-restricted
   "This video is age-restricted and cannot be downloaded without authentication."
   {:video-id video-id}))

(defn create-video-metadata-fetch-error
  "Creates an error when unable to fetch video metadata."
  [video-id reason-string]
  (create-domain-error
   :youtube-error
   :metadata-fetch-failed
   (str "Failed to fetch video information: " reason-string)
   {:video-id video-id
    :reason reason-string}))

(defn create-youtube-authentication-required-error
  "Creates an error when YouTube requires sign-in / bot verification.

   This commonly happens when YouTube challenges the current IP or when the
   provided cookies are missing/expired."
  [video-id reason-string]
  (create-domain-error
   :youtube-error
   :authentication-required
   (str "YouTube is requesting verification/sign-in: " reason-string)
   {:video-id video-id
    :reason reason-string
    :suggested-fixes ["Refresh cookies and ensure you're logged in"
                      "Try yt-dlp --cookies-from-browser on a machine with a valid session"
                      "Use a different network / proxy (residential IPs work better)"
                      "Update yt-dlp to the latest version"]}))

;; =============================================================================
;; Download Errors
;; =============================================================================

(defn create-download-failed-error
  "Creates an error when a video download fails."
  [video-id reason-string]
  (create-domain-error
   :download-error
   :download-failed
   (str "Failed to download video: " reason-string)
   {:video-id video-id
    :reason reason-string}))

(defn create-yt-dlp-not-found-error
  "Creates an error when yt-dlp is not installed or not found."
  [yt-dlp-path]
  (create-domain-error
   :download-error
   :yt-dlp-not-found
   "yt-dlp is not installed or not found. Please install it with: brew install yt-dlp"
   {:expected-path yt-dlp-path}))

(defn create-download-timeout-error
  "Creates an error when a download times out."
  [video-id timeout-seconds]
  (create-domain-error
   :download-error
   :download-timeout
   (format "Download timed out after %d seconds." timeout-seconds)
   {:video-id video-id
    :timeout-seconds timeout-seconds}))

;; =============================================================================
;; Telegram Errors
;; =============================================================================

(defn create-telegram-api-error
  "Creates an error for Telegram API failures."
  [api-method-name error-code error-description]
  (create-domain-error
   :telegram-error
   :api-error
   (format "Telegram API error: %s" error-description)
   {:api-method api-method-name
    :error-code error-code
    :error-description error-description}))

(defn create-telegram-upload-error
  "Creates an error when video upload to Telegram fails."
  [reason-string]
  (create-domain-error
   :telegram-error
   :upload-failed
   (str "Failed to upload video to Telegram: " reason-string)
   {:reason reason-string}))

(defn create-telegram-send-message-error
  "Creates an error when sending a message to Telegram fails."
  [chat-id reason-string]
  (create-domain-error
   :telegram-error
   :send-message-failed
   (str "Failed to send message: " reason-string)
   {:chat-id chat-id
    :reason reason-string}))

;; =============================================================================
;; Configuration Errors
;; =============================================================================

(defn create-missing-bot-token-error
  "Creates an error when the Telegram bot token is not configured."
  []
  (create-domain-error
   :configuration-error
   :missing-bot-token
   "TELEGRAM_BOT_TOKEN environment variable is not set."
   {:required-env-var "TELEGRAM_BOT_TOKEN"}))

(defn create-missing-download-directory-error
  "Creates an error when the download directory is not configured."
  []
  (create-domain-error
   :configuration-error
   :missing-download-directory
   "TEMP_DOWNLOAD_DIR environment variable is not set."
   {:required-env-var "TEMP_DOWNLOAD_DIR"
    :default-value "/tmp/lispinho-downloads"}))

;; =============================================================================
;; System Errors
;; =============================================================================

(defn create-unexpected-error
  "Creates an error for unexpected system failures."
  [exception operation-description]
  (create-domain-error
   :system-error
   :unexpected-error
   (format "Unexpected error during %s: %s" operation-description (.getMessage exception))
   {:operation operation-description
    :exception-type (str (type exception))
    :exception-message (.getMessage exception)}))

;; =============================================================================
;; Error Formatting
;; =============================================================================

(defn format-error-for-user-message
  "Formats a domain error into a user-friendly message for Telegram."
  [domain-error]
  {:pre [(domain-error? domain-error)]}
  (let [base-message (str "❌ " (:error-message domain-error))]
    (if (= :authentication-required (:error-code domain-error))
      (let [suggested-fixes (get-in domain-error [:error-context :suggested-fixes])
            formatted-fixes (when (seq suggested-fixes)
                              (str "\n\nTry:\n- "
                                   (clojure.string/join "\n- " suggested-fixes)))]
        (str base-message formatted-fixes))
      base-message)))

(defn format-error-for-logging
  "Formats a domain error into a string suitable for logging."
  [domain-error]
  {:pre [(domain-error? domain-error)]}
  (format "[%s/%s] %s | Context: %s"
          (name (:error-category domain-error))
          (name (:error-code domain-error))
          (:error-message domain-error)
          (pr-str (:error-context domain-error))))
