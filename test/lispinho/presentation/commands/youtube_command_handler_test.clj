(ns lispinho.presentation.commands.youtube-command-handler-test
  (:require [clojure.test :refer :all]
            [lispinho.presentation.commands.youtube-command-handler :as sut]
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

(defrecord MockTelegramGateway [interactions-atom]
  ports/TelegramMessageGateway
  (send-text-message [_ chat-id text]
    (swap! interactions-atom conj {:action :send-text :chat-id chat-id :text text})
    {:success true :message-id 100})
  (send-text-message [_ chat-id text reply-to]
    (swap! interactions-atom conj {:action :send-text-reply :chat-id chat-id :text text :reply-to reply-to})
    {:success true :message-id 101})
  (send-video-file [_ chat-id video caption]
    (swap! interactions-atom conj {:action :send-video :chat-id chat-id :video video :caption caption})
    {:success true :message-id 102})
  (send-video-file [_ chat-id video caption reply-to]
    (swap! interactions-atom conj {:action :send-video-reply :chat-id chat-id :video video :caption caption :reply-to reply-to})
    {:success true :message-id 103})
  (send-chat-action [_ chat-id action]
    (swap! interactions-atom conj {:action :chat-action :chat-id chat-id :type action})
    {:success true})
  (get-updates-with-offset [_ _ _]
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
                   :video-duration-value-object (media-value-objects/create-video-duration 60)
                   :video-uploader-name "Test Uploader"
                   :video-thumbnail-url nil
                   :video-is-available true})}
      {:success false
       :error {:error-type :domain-error
               :error-category :youtube-error
               :error-code :metadata-fetch-failed
               :error-message "Fetch failed"
               :error-context {}
               :error-timestamp 0}}))
  (download-video-to-file [_ _url _dir _max-size]
    (if should-succeed
      {:success true
       :file-path (media-value-objects/create-video-file-path "/tmp/test.mp4" 1000000)}
      {:success false
       :error {:error-type :domain-error
               :error-category :download-error
               :error-code :download-failed
               :error-message "Download failed"
               :error-context {}
               :error-timestamp 0}})))

(defrecord MockFileSystemRepository []
  ports/FileSystemRepository
  (ensure-directory-exists [_ path] {:success true :path path})
  (delete-file-if-exists [_ _] {:success true :deleted true})
  (get-file-size [_ _] {:success true :size-bytes 1000000})
  (file-exists? [_ _] true))

(defrecord MockConfigRepository []
  ports/ConfigurationRepository
  (get-telegram-bot-token [_] "test-token")
  (get-temp-download-directory [_] "/tmp/test")
  (get-yt-dlp-executable-path [_] "yt-dlp")
  (get-yt-dlp-cookies-path [_] nil)
  (get-yt-dlp-extra-arguments [_] [])
  (get-maximum-video-duration-minutes [_] 15))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(defn create-yt-command
  "Creates a /yt ParsedCommand with the given URL argument."
  [url-arg]
  (let [text (if url-arg (str "/yt " url-arg) "/yt")
        chat-id (messaging-value-objects/create-chat-id 12345)
        msg-id (messaging-value-objects/create-message-id 1)
        update-id (messaging-value-objects/create-update-id 1)
        user (messaging-entities/create-telegram-user
              {:user-id-number 999
               :user-first-name "Tester"
               :user-last-name nil
               :user-username nil
               :is-bot-flag false})
        chat (messaging-entities/create-telegram-chat
              {:chat-id-value-object chat-id
               :chat-type-keyword :private
               :chat-title-string nil})
        msg-text (messaging-value-objects/create-message-text text)
        message (messaging-entities/create-telegram-message
                 {:message-id-value-object msg-id
                  :message-chat-entity chat
                  :message-sender-entity user
                  :message-text-value-object msg-text
                  :message-date-unix-timestamp 0})
        context (messaging-aggregates/create-message-context
                 {:update-id-value-object update-id
                  :incoming-telegram-message message})
        cmd-name (commands-value-objects/create-command-name :yt)
        cmd-args (commands-value-objects/create-command-arguments url-arg)]
    (commands-entities/create-parsed-command
     {:message-context-aggregate context
      :command-name-value-object cmd-name
      :command-arguments-value-object cmd-args})))

(defn create-gateway-registry
  "Creates a gateway registry with mocks."
  [youtube-success]
  (let [interactions (atom [])]
    {:registry (ports/create-gateway-registry
                {:telegram-gateway (->MockTelegramGateway interactions)
                 :youtube-downloader-gateway (->MockYouTubeGateway youtube-success)
                 :file-system-repository (->MockFileSystemRepository)
                 :configuration-repository (->MockConfigRepository)
                 :openai-gateway nil})
     :interactions interactions}))

;; =============================================================================
;; Handler Tests
;; =============================================================================

(deftest handle-youtube-download-command-test
  (testing "Handler processes valid /yt command"
    (let [parsed-command (create-yt-command "https://youtu.be/dQw4w9WgXcQ")
          {:keys [registry interactions]} (create-gateway-registry true)
          result (sut/handle-youtube-download-command parsed-command registry)]
      (is (:output-success result))
      (is (some #(= :send-video (:action %)) @interactions))))

  (testing "Handler handles missing URL"
    (let [parsed-command (create-yt-command nil)
          {:keys [registry interactions]} (create-gateway-registry true)
          result (sut/handle-youtube-download-command parsed-command registry)]
      (is (not (:output-success result)))
      (is (= :missing-youtube-url (get-in result [:output-error :error-code])))
      (is (some #(= :send-text (:action %)) @interactions))))

  (testing "Handler handles download failure"
    (let [parsed-command (create-yt-command "https://youtu.be/dQw4w9WgXcQ")
          {:keys [registry interactions]} (create-gateway-registry false)
          result (sut/handle-youtube-download-command parsed-command registry)]
      (is (not (:output-success result)))
      ;; Should send error message
      (is (some #(contains? #{:send-text :send-text-reply} (:action %)) @interactions)))))
