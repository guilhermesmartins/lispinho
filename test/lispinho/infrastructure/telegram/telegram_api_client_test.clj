(ns lispinho.infrastructure.telegram.telegram-api-client-test
  (:require [clojure.test :refer :all]
            [lispinho.infrastructure.telegram.telegram-api-client :as sut]))

;; =============================================================================
;; Unit Tests for Telegram API Client
;; =============================================================================
;; Note: These tests only test the internal helper functions.
;; Full integration tests require a real Telegram bot token.

(deftest build-telegram-api-url-test
  (testing "Building API URLs"
    (is (= "https://api.telegram.org/botTEST_TOKEN/sendMessage"
           (sut/build-telegram-api-url "TEST_TOKEN" "sendMessage")))
    (is (= "https://api.telegram.org/botABC123/getUpdates"
           (sut/build-telegram-api-url "ABC123" "getUpdates")))))

(deftest handle-telegram-api-response-test
  (testing "Successful response"
    (let [response {:body {:ok true
                           :result {:message_id 123
                                    :chat {:id 456}}}}
          result (sut/handle-telegram-api-response "sendMessage" response)]
      (is (:success result))
      (is (= {:message_id 123 :chat {:id 456}} (:result result)))))

  (testing "Error response"
    (let [response {:body {:ok false
                           :error_code 400
                           :description "Bad Request"}}
          result (sut/handle-telegram-api-response "sendMessage" response)]
      (is (not (:success result)))
      (is (= :api-error (get-in result [:error :error-code]))))))

(deftest create-telegram-api-client-test
  (testing "Creating client with valid token"
    (let [client (sut/create-telegram-api-client "valid-token")]
      (is (some? client))
      (is (= "valid-token" (:bot-token client)))))

  (testing "Creating client with empty token throws"
    (is (thrown? AssertionError (sut/create-telegram-api-client "")))
    (is (thrown? AssertionError (sut/create-telegram-api-client nil)))))

(deftest chat-action-types-test
  (testing "Chat action type mappings"
    (is (= "typing" (get sut/chat-action-types :typing)))
    (is (= "upload_video" (get sut/chat-action-types :upload_video)))
    (is (= "upload_photo" (get sut/chat-action-types :upload_photo)))))
