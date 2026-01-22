(ns lispinho.domain.messaging.value-objects-test
  (:require [clojure.test :refer :all]
            [lispinho.domain.messaging.value-objects :as sut]))

;; =============================================================================
;; Chat ID Value Object Tests
;; =============================================================================

(deftest create-chat-id-test
  (testing "Creating a valid positive chat ID (user/group)"
    (let [chat-id (sut/create-chat-id 123456789)]
      (is (sut/chat-id? chat-id))
      (is (= 123456789 (sut/extract-chat-id-value chat-id)))))

  (testing "Creating a valid negative chat ID (supergroup/channel)"
    (let [chat-id (sut/create-chat-id -100123456789)]
      (is (sut/chat-id? chat-id))
      (is (= -100123456789 (sut/extract-chat-id-value chat-id)))))

  (testing "Invalid chat ID throws assertion error"
    (is (thrown? AssertionError (sut/create-chat-id "not-a-number")))
    (is (thrown? AssertionError (sut/create-chat-id nil)))))

(deftest chat-id-type-detection-test
  (testing "Private chat detection (positive ID)"
    (let [private-chat-id (sut/create-chat-id 123456789)]
      (is (sut/chat-id-is-private? private-chat-id))
      (is (not (sut/chat-id-is-group? private-chat-id)))))

  (testing "Group chat detection (negative ID)"
    (let [group-chat-id (sut/create-chat-id -100123456789)]
      (is (sut/chat-id-is-group? group-chat-id))
      (is (not (sut/chat-id-is-private? group-chat-id))))))

;; =============================================================================
;; Message ID Value Object Tests
;; =============================================================================

(deftest create-message-id-test
  (testing "Creating a valid message ID"
    (let [message-id (sut/create-message-id 42)]
      (is (sut/message-id? message-id))
      (is (= 42 (sut/extract-message-id-value message-id)))))

  (testing "Invalid message ID (zero or negative) throws assertion error"
    (is (thrown? AssertionError (sut/create-message-id 0)))
    (is (thrown? AssertionError (sut/create-message-id -1)))
    (is (thrown? AssertionError (sut/create-message-id nil)))))

;; =============================================================================
;; Message Text Value Object Tests
;; =============================================================================

(deftest create-message-text-test
  (testing "Creating a valid message text"
    (let [message-text (sut/create-message-text "Hello, world!")]
      (is (sut/message-text? message-text))
      (is (= "Hello, world!" (sut/extract-message-text-content message-text)))))

  (testing "Empty text throws assertion error"
    (is (thrown? AssertionError (sut/create-message-text "")))
    (is (thrown? AssertionError (sut/create-message-text nil))))

  (testing "Text exceeding maximum length throws assertion error"
    (let [too-long-text (apply str (repeat 5000 "a"))]
      (is (thrown? AssertionError (sut/create-message-text too-long-text))))))

(deftest message-text-command-detection-test
  (testing "Detects command messages (starts with /)"
    (let [command-text (sut/create-message-text "/start")]
      (is (sut/message-text-starts-with-command? command-text))))

  (testing "Non-command messages"
    (let [normal-text (sut/create-message-text "Hello, bot!")]
      (is (not (sut/message-text-starts-with-command? normal-text))))))

;; =============================================================================
;; Update ID Value Object Tests
;; =============================================================================

(deftest create-update-id-test
  (testing "Creating a valid update ID"
    (let [update-id (sut/create-update-id 987654321)]
      (is (sut/update-id? update-id))
      (is (= 987654321 (sut/extract-update-id-value update-id)))))

  (testing "Calculate next offset"
    (let [update-id (sut/create-update-id 100)]
      (is (= 101 (sut/calculate-next-offset-from-update-id update-id))))))
