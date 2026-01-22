(ns lispinho.infrastructure.config.environment-config
  "Infrastructure adapter for environment-based configuration.
   Implements the ConfigurationRepository protocol."
  (:require [clojure.string :as string]
            [lispinho.application.ports.repositories :as ports])
  (:import [io.github.cdimascio.dotenv Dotenv]))

;; =============================================================================
;; Default Configuration Values
;; =============================================================================

(def default-temp-download-directory
  "Default temporary directory for video downloads."
  "/tmp/lispinho-downloads")

(def default-yt-dlp-executable-path
  "Default path to yt-dlp executable."
  "yt-dlp")

(def default-yt-dlp-cookies-path
  "Default path to a yt-dlp cookies file."
  nil)

(def default-yt-dlp-extra-arguments
  "Default extra yt-dlp arguments."
  [])

(def default-maximum-video-duration-minutes
  "Default maximum video duration in minutes."
  15)

;; =============================================================================
;; Parsing Helpers
;; =============================================================================

(defn parse-yt-dlp-extra-arguments-string
  "Parses a string of yt-dlp arguments into a vector.

   Supports basic quoting with single (') or double (\") quotes.
   Example:
   --proxy \"socks5://127.0.0.1:1080\" --extractor-args youtube:player_client=android

   Returns [] when the input is nil/blank."
  [arguments-string]
  (let [trimmed (some-> arguments-string string/trim)]
    (if-not (and trimmed (seq trimmed))
      []
      (loop [characters (seq trimmed)
             current-token ""
             tokens []
             quote-character nil]
        (if-not characters
          (cond-> tokens
            (seq current-token) (conj current-token))
          (let [character (first characters)
                remaining (next characters)]
            (cond
              ;; Whitespace outside quotes splits tokens
              (and (nil? quote-character) (Character/isWhitespace ^Character character))
              (recur (drop-while #(Character/isWhitespace ^Character %) remaining)
                     ""
                     (cond-> tokens
                       (seq current-token) (conj current-token))
                     nil)

              ;; Enter/exit quotes
              (and (nil? quote-character) (or (= character \') (= character \")))
              (recur remaining current-token tokens character)

              (and quote-character (= character quote-character))
              (recur remaining current-token tokens nil)

              ;; Default: accumulate char
              :else
              (recur remaining (str current-token character) tokens quote-character))))))))

;; =============================================================================
;; Environment Configuration Record
;; =============================================================================

(defrecord EnvironmentConfigRepository [dotenv-instance]
  ports/ConfigurationRepository

  (get-telegram-bot-token
    [_repository]
    (let [token (.get dotenv-instance "TELEGRAM_BOT_TOKEN")]
      (when (and token (seq token))
        token)))

  (get-temp-download-directory
    [_repository]
    (let [configured-dir (.get dotenv-instance "TEMP_DOWNLOAD_DIR")]
      (if (and configured-dir (seq configured-dir))
        configured-dir
        default-temp-download-directory)))

  (get-yt-dlp-executable-path
    [_repository]
    (let [configured-path (.get dotenv-instance "YT_DLP_PATH")]
      (if (and configured-path (seq configured-path))
        configured-path
        default-yt-dlp-executable-path)))

  (get-yt-dlp-cookies-path
    [_repository]
    (let [configured-path (.get dotenv-instance "YT_DLP_COOKIES_PATH")]
      (if (and configured-path (seq configured-path))
        configured-path
        default-yt-dlp-cookies-path)))

  (get-yt-dlp-extra-arguments
    [_repository]
    (let [configured-arguments (.get dotenv-instance "YT_DLP_EXTRA_ARGS")
          parsed-arguments (parse-yt-dlp-extra-arguments-string configured-arguments)]
      (if (seq parsed-arguments)
        parsed-arguments
        default-yt-dlp-extra-arguments)))

  (get-maximum-video-duration-minutes
    [_repository]
    (let [configured-minutes (.get dotenv-instance "MAX_VIDEO_DURATION_MINUTES")]
      (if (and configured-minutes (seq configured-minutes))
        (try
          (Integer/parseInt configured-minutes)
          (catch NumberFormatException _
            default-maximum-video-duration-minutes))
        default-maximum-video-duration-minutes))))

;; =============================================================================
;; Factory Functions
;; =============================================================================

(defn create-environment-config-repository
  "Creates a new EnvironmentConfigRepository instance.
   Loads environment variables from .env file if present."
  []
  (let [dotenv-instance (Dotenv/load)]
    (->EnvironmentConfigRepository dotenv-instance)))

(defn create-environment-config-repository-with-dotenv
  "Creates a new EnvironmentConfigRepository with a pre-loaded Dotenv instance.
   Useful for testing or when sharing the Dotenv instance."
  [dotenv-instance]
  {:pre [(some? dotenv-instance)]}
  (->EnvironmentConfigRepository dotenv-instance))

;; =============================================================================
;; Validation Functions
;; =============================================================================

(defn validate-required-configuration
  "Validates that all required configuration values are present.
   Returns {:valid true} or {:valid false :missing <vector-of-missing-keys>}"
  [config-repository]
  (let [missing-keys (cond-> []
                       (nil? (ports/get-telegram-bot-token config-repository))
                       (conj :telegram-bot-token))]
    (if (empty? missing-keys)
      {:valid true}
      {:valid false
       :missing missing-keys
       :message (str "Missing required configuration: "
                     (clojure.string/join ", " (map name missing-keys)))})))

(defn print-configuration-summary
  "Prints a summary of the current configuration (without sensitive values)."
  [config-repository]
  (println "\n=== Configuration Summary ===")
  (println "Telegram Bot Token:" (if (ports/get-telegram-bot-token config-repository)
                                     "[CONFIGURED]"
                                     "[MISSING]"))
  (println "Temp Download Directory:" (ports/get-temp-download-directory config-repository))
  (println "yt-dlp Path:" (ports/get-yt-dlp-executable-path config-repository))
  (println "yt-dlp Cookies:" (if (ports/get-yt-dlp-cookies-path config-repository)
                                "[CONFIGURED]"
                                "[NOT SET]"))
  (println "yt-dlp Extra Args:" (let [args (ports/get-yt-dlp-extra-arguments config-repository)]
                                 (if (seq args)
                                   (str "[CONFIGURED " (count args) " args]")
                                   "[NOT SET]")))
  (println "Max Video Duration:" (ports/get-maximum-video-duration-minutes config-repository) "minutes")
  (println "=============================\n"))
