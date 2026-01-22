(ns lispinho.application.use-cases.download-and-send-youtube-video-test
  (:require [clojure.test :refer :all]
            [lispinho.application.use-cases.download-and-send-youtube-video :as sut]
            [lispinho.domain.commands.entities :as commands-entities]
            [lispinho.domain.commands.value-objects :as commands-value-objects]
            [lispinho.domain.messaging.value-objects :as messaging-value-objects]
            [lispinho.domain.messaging.entities :as messaging-entities]
            [lispinho.domain.messaging.aggregates :as messaging-aggregates]
            [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.application.ports.repositories :as ports]))

;; =============================================================================
;; Mock Implementations
;; =============================================================================

(defrecord MockTelegramGateway [sent-messages-atom]
  ports/TelegramMessageGateway
  (send-text-message [_ _chat-id _text]
    (swap! sent-messages-atom conj {:type :text})
    {:success true :message-id 123})
  (send-text-message [_ _chat-id _text _reply-to]
    (swap! sent-messages-atom conj {:type :text-reply})
    {:success true :message-id 124})
  (send-video-file [_ _chat-id _video _caption]
    (swap! sent-messages-atom conj {:type :video})
    {:success true :message-id 125})
  (send-video-file [_ _chat-id _video _caption _reply-to]
    (swap! sent-messages-atom conj {:type :video-reply})
    {:success true :message-id 126})
  (send-chat-action [_ _chat-id _action]
    {:success true})
  (get-updates-with-offset [_ _offset _timeout]
    {:success true :updates []}))

(defrecord MockYouTubeGateway [should-succeed]
  ports/YouTubeDownloaderGateway
  (validate-youtube-url [_ url-string]
    (if (media-value-objects/youtube-url-string-valid? url-string)
      {:valid true :youtube-url (media-value-objects/create-youtube-url url-string)}
      {:valid false :reason "Invalid URL"}))
  (fetch-video-metadata [_ youtube-url]
    (if should-succeed
      {:success true
       :metadata (media-entities/create-video-metadata
                  {:youtube-url-value-object youtube-url
                   :video-title-value-object (media-value-objects/create-video-title "Test Video")
                   :video-duration-value-object (media-value-objects/create-video-duration 120)
                   :video-uploader-name "Test Channel"
                   :video-thumbnail-url nil
                   :video-is-available true})}
      {:success false
       :error {:error-type :domain-error
               :error-category :youtube-error
               :error-code :metadata-fetch-failed
               :error-message "Failed to fetch metadata"}}))
  (download-video-to-file [_ _url _dir _max-size]
    (if should-succeed
      {:success true
       :file-path (media-value-objects/create-video-file-path "/tmp/test.mp4" 5000000)}
      {:success false
       :error {:error-type :domain-error
               :error-category :download-error
               :error-code :download-failed
               :error-message "Download failed"}})))

(defrecord MockFileSystemRepository []
  ports/FileSystemRepository
  (ensure-directory-exists [_ path] {:success true :path path})
  (delete-file-if-exists [_ _path] {:success true :deleted true})
  (get-file-size [_ _path] {:success true :size-bytes 5000000})
  (file-exists? [_ _path] true))

(defrecord MockConfigRepository []
  ports/ConfigurationRepository
  (get-telegram-bot-token [_] "test-token")
  (get-temp-download-directory [_] "/tmp/test-downloads")
  (get-yt-dlp-executable-path [_] "yt-dlp")
  (get-yt-dlp-cookies-path [_] nil)
  (get-yt-dlp-extra-arguments [_] [])
  (get-maximum-video-duration-minutes [_] 15))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(defn create-test-parsed-command
  "Creates a test ParsedCommand for /yt command."
  [url-argument]
  (let [message-text (if url-argument
                       (str "/yt " url-argument)
                       "/yt")
        chat-id (messaging-value-objects/create-chat-id 123456789)
        message-id (messaging-value-objects/create-message-id 42)
        update-id (messaging-value-objects/create-update-id 999)
        user (messaging-entities/create-telegram-user
              {:user-id-number 111
               :user-first-name "Test"
               :user-last-name nil
               :user-username "testuser"
               :is-bot-flag false})
        chat (messaging-entities/create-telegram-chat
              {:chat-id-value-object chat-id
               :chat-type-keyword :private
               :chat-title-string nil})
        msg-text (messaging-value-objects/create-message-text message-text)
        message (messaging-entities/create-telegram-message
                 {:message-id-value-object message-id
                  :message-chat-entity chat
                  :message-sender-entity user
                  :message-text-value-object msg-text
                  :message-date-unix-timestamp 1699999999})
        context (messaging-aggregates/create-message-context
                 {:update-id-value-object update-id
                  :incoming-telegram-message message})
        command-name (commands-value-objects/create-command-name :yt)
        arguments (commands-value-objects/create-command-arguments url-argument)]
    (commands-entities/create-parsed-command
     {:message-context-aggregate context
      :command-name-value-object command-name
      :command-arguments-value-object arguments})))

(defn create-test-gateway-registry
  "Creates a test gateway registry with mocks."
  [youtube-should-succeed]
  (let [sent-messages (atom [])]
    (ports/create-gateway-registry
     {:telegram-gateway (->MockTelegramGateway sent-messages)
      :youtube-downloader-gateway (->MockYouTubeGateway youtube-should-succeed)
      :file-system-repository (->MockFileSystemRepository)
      :configuration-repository (->MockConfigRepository)
      :openai-gateway nil})))

;; =============================================================================
;; Use Case Tests
;; =============================================================================

(deftest create-download-and-send-video-input-test
  (testing "Creating use case input"
    (let [parsed-command (create-test-parsed-command "https://youtu.be/dQw4w9WgXcQ")
          gateway-registry (create-test-gateway-registry true)
          input (sut/create-download-and-send-video-input parsed-command gateway-registry)]
      (is (= :download-and-send-video (:use-case-input-type input)))
      (is (= parsed-command (:input-parsed-command input)))
      (is (= gateway-registry (:input-gateway-registry input))))))

(deftest output-creation-test
  (testing "Success output creation"
    (let [output (sut/create-success-output 123)]
      (is (:output-success output))
      (is (= 123 (:output-message-id output)))))

  (testing "Failure output creation"
    (let [error {:error-type :domain-error
                 :error-category :validation-error
                 :error-code :test-error
                 :error-message "Test error"
                 :error-context {}
                 :error-timestamp 123}
          output (sut/create-failure-output error)]
      (is (not (:output-success output)))
      (is (= error (:output-error output))))))

(deftest handle-youtube-command-test
  (testing "Command without URL argument"
    (let [parsed-command (create-test-parsed-command nil)
          gateway-registry (create-test-gateway-registry true)
          result (sut/handle-youtube-command parsed-command gateway-registry)]
      (is (not (:output-success result)))
      (is (= :missing-youtube-url (get-in result [:output-error :error-code])))))

  (testing "Command with invalid URL"
    (let [parsed-command (create-test-parsed-command "not-a-youtube-url")
          gateway-registry (create-test-gateway-registry true)
          result (sut/handle-youtube-command parsed-command gateway-registry)]
      (is (not (:output-success result)))
      (is (= :invalid-youtube-url (get-in result [:output-error :error-code])))))

  (testing "Successful download and send"
    (let [parsed-command (create-test-parsed-command "https://youtu.be/dQw4w9WgXcQ")
          gateway-registry (create-test-gateway-registry true)
          result (sut/handle-youtube-command parsed-command gateway-registry)]
      (is (:output-success result)))))
