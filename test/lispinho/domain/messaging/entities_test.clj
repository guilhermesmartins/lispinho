(ns lispinho.domain.messaging.entities-test
  (:require [clojure.test :refer :all]
            [lispinho.domain.messaging.entities :as sut]
            [lispinho.domain.messaging.value-objects :as value-objects]))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(def sample-user-data
  {:user-id-number 123456789
   :user-first-name "John"
   :user-last-name "Doe"
   :user-username "johndoe"
   :is-bot-flag false})

(def sample-chat-id
  (value-objects/create-chat-id 123456789))

(def sample-group-chat-id
  (value-objects/create-chat-id -100123456789))

;; =============================================================================
;; Telegram User Entity Tests
;; =============================================================================

(deftest create-telegram-user-test
  (testing "Creating a valid Telegram user"
    (let [user (sut/create-telegram-user sample-user-data)]
      (is (sut/telegram-user? user))
      (is (= 123456789 (:telegram-user-id user)))
      (is (= "John" (:telegram-user-first-name user)))
      (is (= "Doe" (:telegram-user-last-name user)))
      (is (= "johndoe" (:telegram-user-username user)))
      (is (not (:telegram-user-is-bot user)))))

  (testing "Creating a user without optional fields"
    (let [minimal-user (sut/create-telegram-user
                        {:user-id-number 999
                         :user-first-name "Jane"
                         :user-last-name nil
                         :user-username nil
                         :is-bot-flag false})]
      (is (sut/telegram-user? minimal-user))
      (is (nil? (:telegram-user-last-name minimal-user))))))

(deftest telegram-user-display-name-test
  (testing "Display name prefers username"
    (let [user (sut/create-telegram-user sample-user-data)]
      (is (= "@johndoe" (sut/telegram-user-display-name user)))))

  (testing "Display name falls back to full name"
    (let [user (sut/create-telegram-user
                (assoc sample-user-data :user-username nil))]
      (is (= "John Doe" (sut/telegram-user-display-name user)))))

  (testing "Display name falls back to first name only"
    (let [user (sut/create-telegram-user
                (assoc sample-user-data :user-username nil :user-last-name nil))]
      (is (= "John" (sut/telegram-user-display-name user))))))

;; =============================================================================
;; Telegram Chat Entity Tests
;; =============================================================================

(deftest create-telegram-chat-test
  (testing "Creating a private chat"
    (let [chat (sut/create-telegram-chat
                {:chat-id-value-object sample-chat-id
                 :chat-type-keyword :private
                 :chat-title-string nil})]
      (is (sut/telegram-chat? chat))
      (is (sut/telegram-chat-is-private? chat))
      (is (not (sut/telegram-chat-is-group-or-supergroup? chat)))))

  (testing "Creating a group chat"
    (let [chat (sut/create-telegram-chat
                {:chat-id-value-object sample-group-chat-id
                 :chat-type-keyword :group
                 :chat-title-string "Test Group"})]
      (is (sut/telegram-chat? chat))
      (is (not (sut/telegram-chat-is-private? chat)))
      (is (sut/telegram-chat-is-group-or-supergroup? chat))))

  (testing "Creating a supergroup chat"
    (let [chat (sut/create-telegram-chat
                {:chat-id-value-object sample-group-chat-id
                 :chat-type-keyword :supergroup
                 :chat-title-string "Super Test Group"})]
      (is (sut/telegram-chat? chat))
      (is (sut/telegram-chat-is-group-or-supergroup? chat)))))

;; =============================================================================
;; Telegram Message Entity Tests
;; =============================================================================

(deftest create-telegram-message-test
  (testing "Creating a valid message"
    (let [user (sut/create-telegram-user sample-user-data)
          chat (sut/create-telegram-chat
                {:chat-id-value-object sample-chat-id
                 :chat-type-keyword :private
                 :chat-title-string nil})
          message-id (value-objects/create-message-id 42)
          message-text (value-objects/create-message-text "/start")
          message (sut/create-telegram-message
                   {:message-id-value-object message-id
                    :message-chat-entity chat
                    :message-sender-entity user
                    :message-text-value-object message-text
                    :message-date-unix-timestamp 1699999999})]
      (is (sut/telegram-message? message))
      (is (sut/telegram-message-has-text? message))
      (is (sut/telegram-message-is-command? message))))

  (testing "Message without text"
    (let [user (sut/create-telegram-user sample-user-data)
          chat (sut/create-telegram-chat
                {:chat-id-value-object sample-chat-id
                 :chat-type-keyword :private
                 :chat-title-string nil})
          message-id (value-objects/create-message-id 43)
          message (sut/create-telegram-message
                   {:message-id-value-object message-id
                    :message-chat-entity chat
                    :message-sender-entity user
                    :message-text-value-object nil
                    :message-date-unix-timestamp 1699999999})]
      (is (sut/telegram-message? message))
      (is (not (sut/telegram-message-has-text? message)))
      (is (not (sut/telegram-message-is-command? message))))))

(deftest telegram-message-reply-behavior-test
  (testing "Private messages don't require reply-to"
    (let [user (sut/create-telegram-user sample-user-data)
          private-chat (sut/create-telegram-chat
                        {:chat-id-value-object sample-chat-id
                         :chat-type-keyword :private
                         :chat-title-string nil})
          message-id (value-objects/create-message-id 44)
          message (sut/create-telegram-message
                   {:message-id-value-object message-id
                    :message-chat-entity private-chat
                    :message-sender-entity user
                    :message-text-value-object nil
                    :message-date-unix-timestamp 1699999999})]
      (is (not (sut/telegram-message-requires-reply-to-original? message)))))

  (testing "Group messages require reply-to"
    (let [user (sut/create-telegram-user sample-user-data)
          group-chat (sut/create-telegram-chat
                      {:chat-id-value-object sample-group-chat-id
                       :chat-type-keyword :group
                       :chat-title-string "Test Group"})
          message-id (value-objects/create-message-id 45)
          message (sut/create-telegram-message
                   {:message-id-value-object message-id
                    :message-chat-entity group-chat
                    :message-sender-entity user
                    :message-text-value-object nil
                    :message-date-unix-timestamp 1699999999})]
      (is (sut/telegram-message-requires-reply-to-original? message)))))
