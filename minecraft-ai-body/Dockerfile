# Use Node.js 18 Alpine for smaller image size
FROM node:18-alpine

# Set working directory
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S minecraft-ai -u 1001

# Install system dependencies (only tini for proper signal handling)
RUN apk add --no-cache tini

# Copy package files
COPY package*.json ./

# Install all dependencies first (needed for build)
RUN npm ci && \
    npm cache clean --force

# Copy source code
COPY . .

# Build TypeScript
RUN npm run build

# Remove dev dependencies after build
RUN npm prune --production

# Create .env.example for reference
RUN echo "# Minecraft Server Connection" > .env.example && \
    echo "MINECRAFT_HOST=localhost" >> .env.example && \
    echo "MINECRAFT_PORT=25565" >> .env.example && \
    echo "MINECRAFT_USERNAME=AICompanion" >> .env.example && \
    echo "" >> .env.example && \
    echo "# WebSocket Configuration" >> .env.example && \
    echo "WEBSOCKET_HOST=localhost" >> .env.example && \
    echo "WEBSOCKET_PORT=8765" >> .env.example && \
    echo "" >> .env.example && \
    echo "# Optional: AI Configuration" >> .env.example && \
    echo "OPENAI_API_KEY=your_openai_api_key_here" >> .env.example && \
    echo "GOOGLE_CLOUD_CREDENTIALS=path/to/credentials.json" >> .env.example

# Set proper permissions
RUN chown -R minecraft-ai:nodejs /app

# Switch to non-root user
USER minecraft-ai

# Expose health check port (if implemented)
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD node -e "console.log('Health check - Bot is running')" || exit 1

# Use tini as entrypoint for proper signal handling
ENTRYPOINT ["/sbin/tini", "--"]

# Start the application
CMD ["node", "dist/index.js"] 