# Lispinho Telegram Bot

A Telegram bot written in Clojure using Domain-Driven Design (DDD) architecture. Supports YouTube video downloading and sending to chat.

## Project Overview

Lispinho is a Telegram bot that can:
- Respond to basic commands (`/start`, `/help`, `/echo`)
- Download YouTube videos and send them directly to Telegram chats (`/yt`)
- Integrate with AI services (`/lisper` - placeholder for OpenAI)

## Architecture

The project follows Domain-Driven Design (DDD) principles with a layered architecture:

```
src/lispinho/
├── domain/           # Pure business logic, no external dependencies
│   ├── messaging/    # Chat, Message, User value objects and entities
│   ├── media/        # YouTube URL, Video metadata, Download request
│   ├── commands/     # Command parsing and representation
│   └── errors/       # Domain error definitions
├── application/      # Use cases and orchestration
│   ├── ports/        # Protocol definitions (interfaces)
│   ├── services/     # Application services
│   └── use_cases/    # Use case implementations
├── infrastructure/   # External system adapters
│   ├── telegram/     # Telegram Bot API client
│   ├── youtube/      # yt-dlp wrapper
│   ├── persistence/  # File system operations
│   └── config/       # Environment configuration
└── presentation/     # Entry points and handlers
    ├── bot/          # Bot lifecycle and update processing
    └── commands/     # Command dispatching and handling
```

### Layer Responsibilities

1. **Domain Layer**: Pure Clojure functions defining business rules. No side effects, no external dependencies. Contains value objects (immutable data), entities (identity-based), and aggregates (consistency boundaries).

2. **Application Layer**: Orchestrates domain objects to implement use cases. Defines ports (protocols) that infrastructure must implement.

3. **Infrastructure Layer**: Implements the ports defined in the application layer. Handles HTTP calls to Telegram API, shell calls to yt-dlp, file system operations.

4. **Presentation Layer**: Entry points for the application. Handles incoming Telegram updates and routes them to appropriate command handlers.

## Running the Bot

### Prerequisites

1. **Clojure & Leiningen**: Install via Homebrew: `brew install clojure leiningen`
2. **yt-dlp**: Required for YouTube downloads: `brew install yt-dlp`

### Environment Setup

Create a `.env` file in the project root:

```env
TELEGRAM_BOT_TOKEN=your_telegram_bot_token_here
TEMP_DOWNLOAD_DIR=/tmp/lispinho-downloads
YT_DLP_PATH=yt-dlp
YT_DLP_EXTRA_ARGS=
MAX_VIDEO_DURATION_MINUTES=15

# Optional - authentication for YouTube bot checks / age gates
YT_DLP_COOKIES_PATH=/app/cookies.txt
```

### YouTube Authentication / "Not a Bot" Challenges

YouTube will sometimes return a verification page ("Sign in to confirm you’re not a bot").
When that happens, the most reliable mitigations are:

- Use a fresh `cookies.txt` exported from a browser with an active, logged-in YouTube session.
- Prefer `yt-dlp --cookies-from-browser <browser>` on the same machine where you are logged in,
  then export to `cookies.txt` if you need to run inside Docker.
- If you're on a datacenter/VPS IP, you may need a residential proxy/VPN exit.

You can pass deployment-specific workarounds without code changes via `YT_DLP_EXTRA_ARGS`:

```bash
# Example: use Android client
YT_DLP_EXTRA_ARGS='--extractor-args youtube:player_client=android'

# Example: route through a proxy
YT_DLP_EXTRA_ARGS='--proxy socks5://127.0.0.1:1080'
```

### Starting the Bot

```bash
# Run the bot
lein run

# Or use the alias
lein run-bot
```

### REPL Development

```bash
lein repl

;; In the REPL:
(start-bot)           ; Start bot (blocking)
(start-bot-async)     ; Start bot in background thread
(stop-bot)            ; Stop the bot
(bot-status)          ; Check current status
```

## Running Tests

```bash
# Run unit tests only (default)
lein test

# Run integration tests (requires yt-dlp)
lein test-integration

# Run all tests
lein test-all
```

## Bot Commands

| Command | Description |
|---------|-------------|
| `/start` | Welcome message |
| `/help` | List available commands |
| `/echo <text>` | Echo back the provided text |
| `/yt <url>` | Download and send a YouTube video |
| `/lisper <prompt>` | Send prompt to AI (placeholder) |

### YouTube URL Formats Supported

- `youtube.com/watch?v=VIDEO_ID`
- `youtu.be/VIDEO_ID`
- `youtube.com/shorts/VIDEO_ID`
- `m.youtube.com/watch?v=VIDEO_ID`

### Video Constraints

- Maximum duration: 15 minutes (configurable)
- Maximum file size: 50MB (Telegram Bot API limit)
- Preferred format: MP4 with H.264 codec

## Code Conventions

### Naming Conventions

- **Verbose naming**: All variables and functions use descriptive, verbose names
  - `message-context-aggregate` instead of `ctx`
  - `youtube-url-value-object` instead of `url`
  - `extract-video-id-from-url-string` instead of `get-id`

- **Suffix conventions**:
  - `*-value-object`: Immutable value objects
  - `*-entity`: Entities with identity
  - `*-aggregate`: Aggregate roots
  - `*-gateway`: External service adapters
  - `*-repository`: Data access implementations

### Function Patterns

- **Predicates**: End with `?` (e.g., `youtube-url?`, `message-context?`)
- **Extractors**: Start with `extract-` (e.g., `extract-chat-id-value`)
- **Creators**: Start with `create-` (e.g., `create-youtube-url`)
- **Validators**: Return `{:valid true/false ...}`
- **Operations**: Return `{:success true/false ...}`

### Error Handling

Domain errors are structured maps with:
```clojure
{:error-type :domain-error
 :error-category :validation-error|:youtube-error|:download-error|...
 :error-code :specific-error-code
 :error-message "Human readable message"
 :error-context {...additional data...}}
```

## Project Dependencies

| Dependency | Purpose |
|------------|---------|
| `clojure` | Core language |
| `cheshire` | JSON parsing |
| `clj-http` | HTTP client for Telegram API |
| `java-dotenv` | Environment variable management |

## External Dependencies

| Tool | Purpose | Installation |
|------|---------|--------------|
| `yt-dlp` | YouTube video downloading | `brew install yt-dlp` |

## File Structure

```
lispinho/
├── project.clj              # Leiningen project configuration
├── CLAUDE.md                # This file
├── .env                     # Environment variables (create this)
├── src/
│   └── lispinho/
│       ├── core.clj         # Application entry point
│       ├── domain/          # Domain layer
│       ├── application/     # Application layer
│       ├── infrastructure/  # Infrastructure layer
│       └── presentation/    # Presentation layer
└── test/
    └── lispinho/            # Tests mirroring src structure
```

## Common Development Tasks

### Adding a New Command

1. Add command to `known-command-names` in `domain/commands/value_objects.clj`
2. Update mappings in the same file
3. Create handler function in `presentation/commands/command_handlers.clj`
4. Add case in `dispatch-command` function

### Adding a New External Service

1. Define protocol in `application/ports/repositories.clj`
2. Create adapter in `infrastructure/`
3. Add to `GatewayRegistry`
4. Wire in `presentation/bot/telegram_bot_handler.clj`

### Debugging

The codebase includes extensive debug println statements prefixed with `DEBUG [Component]:`. These can be filtered or removed in production.
