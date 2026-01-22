(ns lispinho.application.services.message-reply-service-test
  (:require [clojure.test :refer :all]
            [lispinho.application.services.message-reply-service :as sut]
            [lispinho.domain.messaging.value-objects :as messaging-value-objects]
            [lispinho.domain.messaging.entities :as messaging-entities]
            [lispinho.domain.messaging.aggregates :as messaging-aggregates]
            [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.ports.repositories :as ports]))

;; =============================================================================
;; Mock Gateway Implementation
;; =============================================================================

(defrecord MockTelegramGateway [sent-messages-atom]
  ports/TelegramMessageGateway

  (send-text-message [_ chat-id text]
    (swap! sent-messages-atom conj {:type :text
                                    :chat-id chat-id
                                    :text text
                                    :reply-to nil})
    {:success true :message-id 123})

  (send-text-message [_ chat-id text reply-to-message-id]
    (swap! sent-messages-atom conj {:type :text
                                    :chat-id chat-id
                                    :text text
                                    :reply-to reply-to-message-id})
    {:success true :message-id 124})

  (send-video-file [_ chat-id video-file-path caption]
    (swap! sent-messages-atom conj {:type :video
                                    :chat-id chat-id
                                    :file-path video-file-path
                                    :caption caption
                                    :reply-to nil})
    {:success true :message-id 125})

  (send-video-file [_ chat-id video-file-path caption reply-to-message-id]
    (swap! sent-messages-atom conj {:type :video
                                    :chat-id chat-id
                                    :file-path video-file-path
                                    :caption caption
                                    :reply-to reply-to-message-id})
    {:success true :message-id 126})

  (send-chat-action [_ chat-id action]
    (swap! sent-messages-atom conj {:type :action
                                    :chat-id chat-id
                                    :action action})
    {:success true})

  (get-updates-with-offset [_ _offset _timeout]
    {:success true :updates []}))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(defn create-test-message-context
  "Creates a test MessageContext."
  [chat-type message-text]
  (let [chat-id-num (if (= chat-type :private) 123456789 -100123456789)
        chat-id (messaging-value-objects/create-chat-id chat-id-num)
        message-id (messaging-value-objects/create-message-id 42)
        update-id (messaging-value-objects/create-update-id 999)
        user (messaging-entities/create-telegram-user
              {:user-id-number 111
               :user-first-name "Test"
               :user-last-name "User"
               :user-username "testuser"
               :is-bot-flag false})
        chat (messaging-entities/create-telegram-chat
              {:chat-id-value-object chat-id
               :chat-type-keyword chat-type
               :chat-title-string (when (not= chat-type :private) "Test Group")})
        msg-text (when message-text
                   (messaging-value-objects/create-message-text message-text))
        message (messaging-entities/create-telegram-message
                 {:message-id-value-object message-id
                  :message-chat-entity chat
                  :message-sender-entity user
                  :message-text-value-object msg-text
                  :message-date-unix-timestamp 1699999999})]
    (messaging-aggregates/create-message-context
     {:update-id-value-object update-id
      :incoming-telegram-message message})))

;; =============================================================================
;; Text Reply Tests
;; =============================================================================

(deftest send-text-reply-to-context-test
  (testing "Reply to private chat (no reply-to)"
    (let [sent-messages (atom [])
          mock-gateway (->MockTelegramGateway sent-messages)
          context (create-test-message-context :private "/start")
          result (sut/send-text-reply-to-context mock-gateway context "Hello!")]
      (is (:success result))
      (is (= 1 (count @sent-messages)))
      (is (= "Hello!" (:text (first @sent-messages))))
      (is (nil? (:reply-to (first @sent-messages))))))

  (testing "Reply to group chat (with reply-to)"
    (let [sent-messages (atom [])
          mock-gateway (->MockTelegramGateway sent-messages)
          context (create-test-message-context :group "/yt url")
          result (sut/send-text-reply-to-context mock-gateway context "Processing...")]
      (is (:success result))
      (is (= 1 (count @sent-messages)))
      (is (= "Processing..." (:text (first @sent-messages))))
      (is (some? (:reply-to (first @sent-messages)))))))

;; =============================================================================
;; Error Reply Tests
;; =============================================================================

(deftest send-error-reply-to-context-test
  (testing "Send error reply"
    (let [sent-messages (atom [])
          mock-gateway (->MockTelegramGateway sent-messages)
          context (create-test-message-context :private "/yt")
          domain-error (error-types/create-missing-youtube-url-error)
          result (sut/send-error-reply-to-context mock-gateway context domain-error)]
      (is (:success result))
      (is (= 1 (count @sent-messages)))
      (is (clojure.string/starts-with? (:text (first @sent-messages)) "❌")))))

;; =============================================================================
;; Chat Action Tests
;; =============================================================================

(deftest send-upload-video-action-test
  (testing "Send upload video action"
    (let [sent-messages (atom [])
          mock-gateway (->MockTelegramGateway sent-messages)
          chat-id (messaging-value-objects/create-chat-id 123456789)
          result (sut/send-upload-video-action mock-gateway chat-id)]
      (is (:success result))
      (is (= 1 (count @sent-messages)))
      (is (= :action (:type (first @sent-messages))))
      (is (= :upload_video (:action (first @sent-messages)))))))

(deftest send-typing-action-test
  (testing "Send typing action"
    (let [sent-messages (atom [])
          mock-gateway (->MockTelegramGateway sent-messages)
          chat-id (messaging-value-objects/create-chat-id 123456789)
          result (sut/send-typing-action mock-gateway chat-id)]
      (is (:success result))
      (is (= 1 (count @sent-messages)))
      (is (= :action (:type (first @sent-messages))))
      (is (= :typing (:action (first @sent-messages)))))))
