(ns lispinho.domain.media.aggregates-test
  (:require [clojure.test :refer :all]
            [lispinho.domain.media.aggregates :as sut]
            [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.domain.messaging.value-objects :as messaging-value-objects]))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(def sample-youtube-url
  (media-value-objects/create-youtube-url "https://youtu.be/dQw4w9WgXcQ"))

(def sample-chat-id
  (messaging-value-objects/create-chat-id 123456789))

(def sample-message-id
  (messaging-value-objects/create-message-id 42))

;; =============================================================================
;; Video Download Request Aggregate Tests
;; =============================================================================

(deftest create-video-download-request-test
  (testing "Creating a new video download request"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id nil
                    :maximum-video-duration-minutes 15})]
      (is (sut/video-download-request? request))
      (is (sut/video-download-request-is-pending? request))
      (is (not (sut/video-download-request-is-completed? request)))
      (is (not (sut/video-download-request-is-failed? request)))))

  (testing "Creating a request with reply-to"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id sample-message-id
                    :maximum-video-duration-minutes 15})]
      (is (sut/video-download-request? request))
      (is (sut/video-download-request-should-reply-to-original? request)))))

(deftest video-download-request-state-transitions-test
  (testing "Transition from pending to fetching-metadata"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id nil
                    :maximum-video-duration-minutes 15})
          transitioned (sut/video-download-request-transition-to-fetching-metadata request)]
      (is (= :fetching-metadata (:video-download-request-status transitioned)))))

  (testing "Cannot transition from non-pending state to fetching-metadata"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id nil
                    :maximum-video-duration-minutes 15})
          fetching (sut/video-download-request-transition-to-fetching-metadata request)]
      (is (thrown? AssertionError
                   (sut/video-download-request-transition-to-fetching-metadata fetching))))))

(deftest video-download-request-mark-as-failed-test
  (testing "Mark request as failed"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id nil
                    :maximum-video-duration-minutes 15})
          failed (sut/video-download-request-mark-as-failed request "Download failed")]
      (is (sut/video-download-request-is-failed? failed))
      (is (= "Download failed" (sut/video-download-request-extract-error-message failed))))))

(deftest video-download-request-query-functions-test
  (testing "Extract YouTube URL"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id nil
                    :maximum-video-duration-minutes 15})
          extracted-url (sut/video-download-request-extract-youtube-url request)]
      (is (= sample-youtube-url extracted-url))))

  (testing "Extract chat ID"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id nil
                    :maximum-video-duration-minutes 15})
          extracted-chat-id (sut/video-download-request-extract-chat-id request)]
      (is (= sample-chat-id extracted-chat-id))))

  (testing "Extract reply-to message ID (nil for DMs)"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id nil
                    :maximum-video-duration-minutes 15})]
      (is (nil? (sut/video-download-request-extract-reply-to-message-id request)))))

  (testing "Extract reply-to message ID (present for groups)"
    (let [request (sut/create-video-download-request
                   {:youtube-url-value-object sample-youtube-url
                    :requesting-chat-id sample-chat-id
                    :reply-to-message-id sample-message-id
                    :maximum-video-duration-minutes 15})]
      (is (= sample-message-id
             (sut/video-download-request-extract-reply-to-message-id request))))))
