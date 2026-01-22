(defproject lispinho "0.1.0-SNAPSHOT"
  :description "Lispinho Telegram Bot - A DDD-architected Telegram bot with YouTube video download support"
  :url "https://github.com/yourusername/lispinho"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [cheshire/cheshire "6.0.0"]
                 [clj-http "3.13.0"]
                 [io.github.cdimascio/java-dotenv "5.2.2"]]

  :main lispinho.core
  :aot [lispinho.core]

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["resources"]

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "1.4.4"]]
                   :source-paths ["dev"]}

             :test {:dependencies []}

             ;; Integration tests profile (requires yt-dlp installed)
             :integration {:test-selectors {:integration :integration
                                            :default (complement :integration)}}}

  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :all (constantly true)}

  :repl-options {:init-ns lispinho.core
                 :welcome (println "Welcome to Lispinho Bot REPL!\nUse (start-bot) to start the bot or (start-bot-async) for background.")}

  :jvm-opts ["-Xmx512m"]

  :aliases {"run-bot" ["run"]
            "test-unit" ["test"]
            "test-integration" ["with-profile" "+integration" "test" ":integration"]
            "test-all" ["with-profile" "+integration" "test" ":all"]})
