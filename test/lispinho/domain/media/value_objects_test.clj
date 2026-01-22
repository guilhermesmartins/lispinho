(ns lispinho.domain.media.value-objects-test
  (:require [clojure.test :refer :all]
            [lispinho.domain.media.value-objects :as sut]))

;; =============================================================================
;; YouTube URL Value Object Tests
;; =============================================================================

(deftest youtube-url-validation-test
  (testing "Standard YouTube URLs"
    (is (sut/youtube-url-string-valid? "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    (is (sut/youtube-url-string-valid? "http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    (is (sut/youtube-url-string-valid? "www.youtube.com/watch?v=dQw4w9WgXcQ"))
    (is (sut/youtube-url-string-valid? "youtube.com/watch?v=dQw4w9WgXcQ")))

  (testing "Short YouTube URLs"
    (is (sut/youtube-url-string-valid? "https://youtu.be/dQw4w9WgXcQ"))
    (is (sut/youtube-url-string-valid? "youtu.be/dQw4w9WgXcQ")))

  (testing "YouTube Shorts URLs"
    (is (sut/youtube-url-string-valid? "https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    (is (sut/youtube-url-string-valid? "youtube.com/shorts/dQw4w9WgXcQ")))

  (testing "Mobile YouTube URLs"
    (is (sut/youtube-url-string-valid? "https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    (is (sut/youtube-url-string-valid? "m.youtube.com/watch?v=dQw4w9WgXcQ")))

  (testing "URLs with extra parameters"
    (is (sut/youtube-url-string-valid? "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120")))

  (testing "Invalid URLs"
    (is (not (sut/youtube-url-string-valid? "https://vimeo.com/123456")))
    (is (not (sut/youtube-url-string-valid? "https://www.google.com")))
    (is (not (sut/youtube-url-string-valid? "not-a-url")))
    (is (not (sut/youtube-url-string-valid? "")))
    (is (not (sut/youtube-url-string-valid? nil)))))

(deftest extract-video-id-test
  (testing "Extract video ID from various URL formats"
    (is (= "dQw4w9WgXcQ" (sut/extract-video-id-from-url-string
                          "https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
    (is (= "dQw4w9WgXcQ" (sut/extract-video-id-from-url-string
                          "https://youtu.be/dQw4w9WgXcQ")))
    (is (= "dQw4w9WgXcQ" (sut/extract-video-id-from-url-string
                          "https://www.youtube.com/shorts/dQw4w9WgXcQ")))
    (is (= "dQw4w9WgXcQ" (sut/extract-video-id-from-url-string
                          "https://m.youtube.com/watch?v=dQw4w9WgXcQ")))))

(deftest create-youtube-url-test
  (testing "Creating a valid YouTube URL value object"
    (let [youtube-url (sut/create-youtube-url "https://youtu.be/dQw4w9WgXcQ")]
      (is (sut/youtube-url? youtube-url))
      (is (= "dQw4w9WgXcQ" (sut/extract-youtube-url-video-id youtube-url)))
      (is (= "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
             (sut/extract-youtube-url-normalized youtube-url)))))

  (testing "Invalid URL throws assertion error"
    (is (thrown? AssertionError (sut/create-youtube-url "not-a-url")))))

;; =============================================================================
;; Video File Path Value Object Tests
;; =============================================================================

(deftest create-video-file-path-test
  (testing "Creating a valid video file path"
    (let [file-path (sut/create-video-file-path "/tmp/video.mp4" 10000000)]
      (is (sut/video-file-path? file-path))
      (is (= "/tmp/video.mp4" (sut/extract-video-file-path-string file-path)))
      (is (= 10000000 (sut/extract-video-file-size-bytes file-path)))))

  (testing "File extension extraction"
    (let [file-path (sut/create-video-file-path "/tmp/test.MP4" 1000)]
      (is (= ".mp4" (:video-file-path-extension file-path))))))

(deftest video-file-telegram-size-limit-test
  (testing "File within Telegram's limit"
    (let [small-file (sut/create-video-file-path "/tmp/small.mp4" (* 10 1024 1024))]
      (is (sut/video-file-within-telegram-size-limit? small-file))))

  (testing "File exceeding Telegram's limit"
    (let [large-file (sut/create-video-file-path "/tmp/large.mp4" (* 60 1024 1024))]
      (is (not (sut/video-file-within-telegram-size-limit? large-file))))))

;; =============================================================================
;; Video Title Value Object Tests
;; =============================================================================

(deftest create-video-title-test
  (testing "Creating a valid video title"
    (let [title (sut/create-video-title "My Cool Video")]
      (is (sut/video-title? title))
      (is (= "My Cool Video" (sut/extract-video-title-text title)))))

  (testing "Title is trimmed"
    (let [title (sut/create-video-title "  Trimmed Title  ")]
      (is (= "Trimmed Title" (sut/extract-video-title-text title)))))

  (testing "Long title is truncated"
    (let [long-title (apply str (repeat 300 "a"))
          title (sut/create-video-title long-title)]
      (is (= 256 (count (sut/extract-video-title-text title)))))))

;; =============================================================================
;; Video Duration Value Object Tests
;; =============================================================================

(deftest create-video-duration-test
  (testing "Creating a valid video duration"
    (let [duration (sut/create-video-duration 185)]
      (is (sut/video-duration? duration))
      (is (= 185 (sut/extract-video-duration-seconds duration)))
      (is (= "3:05" (:video-duration-display-string duration)))))

  (testing "Zero duration is valid"
    (let [duration (sut/create-video-duration 0)]
      (is (sut/video-duration? duration))
      (is (= "0:00" (:video-duration-display-string duration))))))

(deftest video-duration-limit-check-test
  (testing "Duration within limit"
    (let [duration (sut/create-video-duration (* 10 60))]
      (is (sut/video-duration-within-limit? duration 15))))

  (testing "Duration exceeding limit"
    (let [duration (sut/create-video-duration (* 20 60))]
      (is (not (sut/video-duration-within-limit? duration 15))))))
