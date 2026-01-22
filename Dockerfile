# =============================================================================
# Stage 1: Build
# =============================================================================
FROM clojure:temurin-21-lein AS builder

WORKDIR /app

# Copy project file first to cache dependencies
COPY project.clj .

# Download dependencies (cached unless project.clj changes)
RUN lein deps

# Copy source code
COPY src/ src/
COPY resources/ resources/

# Build uberjar
RUN lein uberjar

# =============================================================================
# Stage 2: Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# Install dependencies and keep yt-dlp current.
# Alpine's packaged yt-dlp can lag behind YouTube changes, so we install via pip.
RUN apk add --no-cache \
    python3 \
    py3-pip \
    ffmpeg \
    ca-certificates && \
    pip3 install --no-cache-dir --upgrade --break-system-packages yt-dlp && \
    yt-dlp --version

# Create non-root user for security
RUN addgroup -g 1000 lispinho && \
    adduser -u 1000 -G lispinho -s /bin/sh -D lispinho

WORKDIR /app

# Create temp download directory
RUN mkdir -p /tmp/lispinho-downloads && \
    chown -R lispinho:lispinho /tmp/lispinho-downloads

# Copy the uberjar from builder stage
COPY --from=builder /app/target/lispinho-*-standalone.jar app.jar

# Change ownership of app directory
RUN chown -R lispinho:lispinho /app

# Switch to non-root user
USER lispinho

# Environment variables with defaults
ENV TEMP_DOWNLOAD_DIR=/tmp/lispinho-downloads
ENV YT_DLP_PATH=yt-dlp
ENV MAX_VIDEO_DURATION_MINUTES=15

# Health check - verify Java can run
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD java -version || exit 1

# Run the application
CMD ["java", "-Xmx512m", "-jar", "app.jar"]
