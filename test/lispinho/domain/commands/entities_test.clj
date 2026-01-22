(ns lispinho.domain.commands.entities-test
  (:require [clojure.test :refer :all]
            [lispinho.domain.commands.entities :as sut]
            [lispinho.domain.commands.value-objects :as commands-value-objects]
            [lispinho.domain.messaging.value-objects :as messaging-value-objects]
            [lispinho.domain.messaging.entities :as messaging-entities]
            [lispinho.domain.messaging.aggregates :as messaging-aggregates]))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(defn create-sample-message-context
  "Creates a sample MessageContext for testing."
  [message-text-string]
  (let [chat-id (messaging-value-objects/create-chat-id 123456789)
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
               :chat-type-keyword :private
               :chat-title-string nil})
        message-text (when message-text-string
                       (messaging-value-objects/create-message-text message-text-string))
        message (messaging-entities/create-telegram-message
                 {:message-id-value-object message-id
                  :message-chat-entity chat
                  :message-sender-entity user
                  :message-text-value-object message-text
                  :message-date-unix-timestamp 1699999999})]
    (messaging-aggregates/create-message-context
     {:update-id-value-object update-id
      :incoming-telegram-message message})))

;; =============================================================================
;; Command Value Objects Tests
;; =============================================================================

(deftest extract-command-string-test
  (testing "Extract command from text"
    (is (= "/start" (commands-value-objects/extract-command-string-from-text "/start")))
    (is (= "/yt" (commands-value-objects/extract-command-string-from-text "/yt https://youtu.be/abc")))
    (is (= "/echo" (commands-value-objects/extract-command-string-from-text "/echo hello world"))))

  (testing "Command with @botname suffix"
    (is (= "/start" (commands-value-objects/extract-command-string-from-text "/start@mybot")))
    (is (= "/yt" (commands-value-objects/extract-command-string-from-text "/yt@mybot https://youtu.be/abc"))))

  (testing "Non-command text returns nil"
    (is (nil? (commands-value-objects/extract-command-string-from-text "hello")))
    (is (nil? (commands-value-objects/extract-command-string-from-text nil)))))

(deftest extract-arguments-test
  (testing "Extract arguments from command text"
    (is (= "https://youtu.be/abc"
           (commands-value-objects/extract-arguments-string-from-text "/yt https://youtu.be/abc")))
    (is (= "hello world"
           (commands-value-objects/extract-arguments-string-from-text "/echo hello world"))))

  (testing "No arguments returns nil"
    (is (nil? (commands-value-objects/extract-arguments-string-from-text "/start")))
    (is (nil? (commands-value-objects/extract-arguments-string-from-text "/help")))))

(deftest create-command-name-test
  (testing "Creating valid command names"
    (let [yt-command (commands-value-objects/create-command-name :yt)]
      (is (commands-value-objects/command-name? yt-command))
      (is (= :yt (commands-value-objects/extract-command-name-keyword yt-command)))
      (is (= "/yt" (commands-value-objects/extract-command-name-string yt-command)))))

  (testing "Creating command name from string"
    (let [start-command (commands-value-objects/create-command-name-from-string "/start")]
      (is (commands-value-objects/command-name? start-command))
      (is (= :start (commands-value-objects/extract-command-name-keyword start-command)))))

  (testing "Unknown command string returns nil"
    (is (nil? (commands-value-objects/create-command-name-from-string "/unknown")))))

(deftest create-command-arguments-test
  (testing "Creating command arguments"
    (let [args (commands-value-objects/create-command-arguments "arg1 arg2 arg3")]
      (is (commands-value-objects/command-arguments? args))
      (is (commands-value-objects/command-arguments-has-arguments? args))
      (is (= "arg1" (commands-value-objects/extract-command-arguments-first-token args)))
      (is (= ["arg1" "arg2" "arg3"] (commands-value-objects/extract-command-arguments-tokens args)))))

  (testing "Empty arguments"
    (let [args (commands-value-objects/create-command-arguments nil)]
      (is (commands-value-objects/command-arguments? args))
      (is (not (commands-value-objects/command-arguments-has-arguments? args)))
      (is (nil? (commands-value-objects/extract-command-arguments-first-token args))))))

;; =============================================================================
;; Parsed Command Entity Tests
;; =============================================================================

(deftest parse-command-from-message-context-test
  (testing "Parsing a valid /yt command"
    (let [context (create-sample-message-context "/yt https://youtu.be/dQw4w9WgXcQ")
          parsed-command (sut/parse-command-from-message-context context)]
      (is (sut/parsed-command? parsed-command))
      (is (sut/parsed-command-is-youtube-command? parsed-command))
      (is (= :yt (sut/parsed-command-extract-command-keyword parsed-command)))
      (is (= "https://youtu.be/dQw4w9WgXcQ"
             (sut/parsed-command-extract-first-argument parsed-command)))))

  (testing "Parsing a /start command"
    (let [context (create-sample-message-context "/start")
          parsed-command (sut/parse-command-from-message-context context)]
      (is (sut/parsed-command? parsed-command))
      (is (= :start (sut/parsed-command-extract-command-keyword parsed-command)))
      (is (not (sut/parsed-command-has-arguments? parsed-command)))))

  (testing "Parsing unknown command returns nil"
    (let [context (create-sample-message-context "/unknown command")
          parsed-command (sut/parse-command-from-message-context context)]
      (is (nil? parsed-command))))

  (testing "Parsing non-command message returns nil"
    (let [context (create-sample-message-context "hello bot")
          parsed-command (sut/parse-command-from-message-context context)]
      (is (nil? parsed-command)))))

(deftest try-parse-command-test
  (testing "Successful parse"
    (let [context (create-sample-message-context "/help")
          result (sut/try-parse-command-from-message-context context)]
      (is (:success result))
      (is (sut/parsed-command? (:command result)))))

  (testing "Failed parse for unknown command"
    (let [context (create-sample-message-context "/unknown")
          result (sut/try-parse-command-from-message-context context)]
      (is (not (:success result)))
      (is (string? (:reason result))))))
