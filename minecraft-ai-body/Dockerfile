# Use Node.js 18 Alpine for smaller image size
FROM node:18-alpine

# Set working directory
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S minecraft-ai -u 1001

# Install system dependencies
RUN apk add --no-cache \
    dumb-init \
    tini

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production && \
    npm cache clean --force

# Copy source code
COPY . .

# Build TypeScript
RUN npm run build

# Create .env.example for reference
RUN cat > .env.example << 'EOF'
# Minecraft Server Connection
MINECRAFT_HOST=localhost
MINECRAFT_PORT=25565
MINECRAFT_USERNAME=AICompanion

# WebSocket Configuration
WEBSOCKET_HOST=localhost
WEBSOCKET_PORT=8765

# Optional: AI Configuration
OPENAI_API_KEY=your_openai_api_key_here
GOOGLE_CLOUD_CREDENTIALS=path/to/credentials.json
EOF

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