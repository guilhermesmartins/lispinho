(ns lispinho.infrastructure.youtube.yt-dlp-adapter-test
  (:require [clojure.test :refer :all]
            [lispinho.infrastructure.youtube.yt-dlp-adapter :as sut]
            [lispinho.domain.media.value-objects :as media-value-objects]
            [lispinho.application.ports.repositories :as ports]))

;; =============================================================================
;; Unit Tests for yt-dlp Adapter
;; =============================================================================
;; Note: Integration tests require yt-dlp to be installed.
;; Unit tests cover the validation and helper functions.

(deftest create-yt-dlp-adapter-test
  (testing "Creating adapter with default path"
    (let [adapter (sut/create-yt-dlp-adapter)]
      (is (some? adapter))
      (is (= "yt-dlp" (:yt-dlp-path adapter)))
      (is (nil? (:yt-dlp-cookies-path adapter)))))

  (testing "Creating adapter with custom path"
    (let [adapter (sut/create-yt-dlp-adapter "/usr/local/bin/yt-dlp")]
      (is (some? adapter))
      (is (= "/usr/local/bin/yt-dlp" (:yt-dlp-path adapter)))
      (is (nil? (:yt-dlp-cookies-path adapter)))))

  (testing "Creating adapter with cookies path"
    (let [adapter (sut/create-yt-dlp-adapter "yt-dlp" "/tmp/cookies.txt")]
      (is (some? adapter))
      (is (= "yt-dlp" (:yt-dlp-path adapter)))
      (is (= "/tmp/cookies.txt" (:yt-dlp-cookies-path adapter)))))

  (testing "Creating adapter with empty path throws"
    (is (thrown? AssertionError (sut/create-yt-dlp-adapter "")))
    (is (thrown? AssertionError (sut/create-yt-dlp-adapter nil)))
    (is (thrown? AssertionError (sut/create-yt-dlp-adapter "yt-dlp" "")))))

(deftest validate-youtube-url-test
  (let [adapter (sut/create-yt-dlp-adapter)]

    (testing "Valid YouTube URLs"
      (let [result (ports/validate-youtube-url adapter "https://youtu.be/dQw4w9WgXcQ")]
        (is (:valid result))
        (is (media-value-objects/youtube-url? (:youtube-url result)))))

    (testing "Invalid URLs"
      (let [result (ports/validate-youtube-url adapter "https://vimeo.com/123")]
        (is (not (:valid result)))
        (is (string? (:reason result)))))))

(deftest format-selection-test
  (testing "Default format selection is defined"
    (is (string? sut/default-yt-dlp-format-selection))
    (is (> (count sut/default-yt-dlp-format-selection) 0)))

  (testing "Fallback format selection is defined"
    (is (string? sut/fallback-yt-dlp-format-selection))
    (is (> (count sut/fallback-yt-dlp-format-selection) 0))))

;; =============================================================================
;; Integration Tests (require yt-dlp to be installed)
;; =============================================================================
;; These tests are tagged with :integration and can be run separately.

(deftest ^:integration yt-dlp-installed-test
  (testing "yt-dlp is installed and accessible"
    (is (sut/yt-dlp-installed? "yt-dlp")
        "yt-dlp should be installed for integration tests. Run: brew install yt-dlp")))

(deftest ^:integration fetch-video-metadata-integration-test
  (testing "Fetch metadata for a real YouTube video"
    (let [adapter (sut/create-yt-dlp-adapter)
          youtube-url (media-value-objects/create-youtube-url "https://youtu.be/dQw4w9WgXcQ")
          result (ports/fetch-video-metadata adapter youtube-url)]
      (is (:success result) "Should successfully fetch metadata")
      (when (:success result)
        (is (some? (:metadata result)))
        (is (= "dQw4w9WgXcQ"
               (media-value-objects/extract-youtube-url-video-id
                (:video-metadata-youtube-url (:metadata result)))))))))
