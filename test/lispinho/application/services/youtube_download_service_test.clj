(ns lispinho.application.services.youtube-download-service-test
  (:require [clojure.test :refer :all]
            [lispinho.application.services.youtube-download-service :as sut]
            [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.domain.media.entities :as media-entities]
            [lispinho.domain.errors.error-types :as error-types]
            [lispinho.application.ports.repositories :as ports]))

;; =============================================================================
;; Mock Gateway Implementations
;; =============================================================================

(defrecord MockYouTubeGateway [should-succeed metadata-response download-response]
  ports/YouTubeDownloaderGateway

  (validate-youtube-url [_ url-string]
    (if (media-value-objects/youtube-url-string-valid? url-string)
      {:valid true
       :youtube-url (media-value-objects/create-youtube-url url-string)}
      {:valid false
       :reason "Invalid URL"}))

  (fetch-video-metadata [_ youtube-url-value-object]
    (if should-succeed
      {:success true
       :metadata metadata-response}
      {:success false
       :error (error-types/create-video-metadata-fetch-error
               (media-value-objects/extract-youtube-url-video-id youtube-url-value-object)
               "Mock failure")}))

  (download-video-to-file [_ _youtube-url _target-dir _max-size]
    (if should-succeed
      {:success true
       :file-path download-response}
      {:success false
       :error (error-types/create-download-failed-error "test" "Mock download failure")})))

(defrecord MockFileSystemRepository []
  ports/FileSystemRepository

  (ensure-directory-exists [_ directory-path]
    {:success true :path directory-path})

  (delete-file-if-exists [_ _file-path]
    {:success true :deleted true})

  (get-file-size [_ _file-path]
    {:success true :size-bytes 1000000})

  (file-exists? [_ _file-path]
    true))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(def sample-youtube-url
  (media-value-objects/create-youtube-url "https://youtu.be/dQw4w9WgXcQ"))

(def sample-video-metadata
  (media-entities/create-video-metadata
   {:youtube-url-value-object sample-youtube-url
    :video-title-value-object (media-value-objects/create-video-title "Test Video")
    :video-duration-value-object (media-value-objects/create-video-duration 180)
    :video-uploader-name "Test Channel"
    :video-thumbnail-url "https://example.com/thumb.jpg"
    :video-is-available true}))

(def sample-video-file-path
  (media-value-objects/create-video-file-path "/tmp/test.mp4" 10000000))

;; =============================================================================
;; URL Validation Tests
;; =============================================================================

(deftest validate-and-create-youtube-url-test
  (let [mock-gateway (->MockYouTubeGateway true nil nil)]

    (testing "Valid YouTube URL"
      (let [result (sut/validate-and-create-youtube-url
                    mock-gateway
                    "https://youtu.be/dQw4w9WgXcQ")]
        (is (:success result))
        (is (media-value-objects/youtube-url? (:youtube-url result)))))

    (testing "Invalid YouTube URL"
      (let [result (sut/validate-and-create-youtube-url
                    mock-gateway
                    "https://vimeo.com/123")]
        (is (not (:success result)))
        (is (error-types/domain-error? (:error result)))))

    (testing "Nil URL"
      (let [result (sut/validate-and-create-youtube-url mock-gateway nil)]
        (is (not (:success result)))
        (is (= :missing-youtube-url (get-in result [:error :error-code])))))))

;; =============================================================================
;; Video Validation Tests
;; =============================================================================

(deftest validate-video-for-download-test
  (testing "Valid video within duration limit"
    (let [result (sut/validate-video-for-download sample-video-metadata 15)]
      (is (:valid result))))

  (testing "Video exceeding duration limit"
    (let [long-video-metadata (media-entities/create-video-metadata
                               {:youtube-url-value-object sample-youtube-url
                                :video-title-value-object (media-value-objects/create-video-title "Long Video")
                                :video-duration-value-object (media-value-objects/create-video-duration 1200) ; 20 minutes
                                :video-uploader-name "Test Channel"
                                :video-thumbnail-url nil
                                :video-is-available true})
          result (sut/validate-video-for-download long-video-metadata 15)]
      (is (not (:valid result)))
      (is (= :video-too-long (get-in result [:error :error-code])))))

  (testing "Unavailable video"
    (let [unavailable-video (media-entities/create-video-metadata
                             {:youtube-url-value-object sample-youtube-url
                              :video-title-value-object (media-value-objects/create-video-title "Private Video")
                              :video-duration-value-object (media-value-objects/create-video-duration 60)
                              :video-uploader-name "Test Channel"
                              :video-thumbnail-url nil
                              :video-is-available false})
          result (sut/validate-video-for-download unavailable-video 15)]
      (is (not (:valid result)))
      (is (= :video-unavailable (get-in result [:error :error-code]))))))

;; =============================================================================
;; Complete Workflow Tests
;; =============================================================================

(deftest execute-complete-download-workflow-test
  (testing "Successful download workflow"
    (let [mock-youtube-gateway (->MockYouTubeGateway true sample-video-metadata sample-video-file-path)
          mock-file-system (->MockFileSystemRepository)
          result (sut/execute-complete-download-workflow
                  mock-youtube-gateway
                  mock-file-system
                  "https://youtu.be/dQw4w9WgXcQ"
                  "/tmp/downloads"
                  15)]
      (is (:success result))
      (is (media-entities/downloadable-video? (:downloadable-video result)))))

  (testing "Failed workflow - invalid URL"
    (let [mock-youtube-gateway (->MockYouTubeGateway true sample-video-metadata sample-video-file-path)
          mock-file-system (->MockFileSystemRepository)
          result (sut/execute-complete-download-workflow
                  mock-youtube-gateway
                  mock-file-system
                  "not-a-url"
                  "/tmp/downloads"
                  15)]
      (is (not (:success result)))
      (is (error-types/domain-error? (:error result)))))

  (testing "Failed workflow - metadata fetch failure"
    (let [mock-youtube-gateway (->MockYouTubeGateway false nil nil)
          mock-file-system (->MockFileSystemRepository)
          result (sut/execute-complete-download-workflow
                  mock-youtube-gateway
                  mock-file-system
                  "https://youtu.be/dQw4w9WgXcQ"
                  "/tmp/downloads"
                  15)]
      (is (not (:success result)))
      (is (error-types/domain-error? (:error result))))))
