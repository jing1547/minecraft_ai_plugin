"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const mineflayer_1 = require("mineflayer");
const mineflayer_pathfinder_1 = require("mineflayer-pathfinder");
const mineflayer_pvp_1 = require("mineflayer-pvp");
const ws_1 = __importDefault(require("ws"));
class MinecraftAIBody {
    constructor(botConfig, wsConfig) {
        this.bot = null;
        this.websocket = null;
        this.isConnected = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.config = botConfig;
        this.wsConfig = wsConfig;
    }
    async initializeBot() {
        console.log('🤖 Initializing Mineflayer bot...');
        try {
            this.bot = (0, mineflayer_1.createBot)({
                host: this.config.host,
                port: this.config.port,
                username: this.config.username,
                version: this.config.version || '1.20.4',
                auth: this.config.auth || 'offline'
            });
            this.bot.loadPlugin(mineflayer_pathfinder_1.pathfinder);
            this.bot.loadPlugin(mineflayer_pvp_1.plugin);
            this.setupBotEventHandlers();
        }
        catch (error) {
            console.error('❌ Failed to initialize bot:', error);
            throw error;
        }
    }
    setupBotEventHandlers() {
        if (!this.bot)
            return;
        this.bot.on('login', () => {
            console.log('✅ Bot logged into Minecraft server');
            this.isConnected = true;
            this.reconnectAttempts = 0;
            if (this.bot?.entity.position) {
                const movements = new mineflayer_pathfinder_1.Movements(this.bot);
                this.bot.pathfinder.setMovements(movements);
            }
        });
        this.bot.on('spawn', () => {
            console.log('🌍 Bot spawned in world');
            this.sendWebSocketMessage({
                type: 'bot_status',
                status: 'spawned',
                position: this.bot?.entity.position,
                health: this.bot?.health,
                timestamp: new Date().toISOString()
            });
        });
        this.bot.on('chat', (username, message) => {
            console.log(`💬 Chat [${username}]: ${message}`);
            this.sendWebSocketMessage({
                type: 'chat_message',
                username,
                message,
                timestamp: new Date().toISOString()
            });
        });
        this.bot.on('error', (error) => {
            console.error('❌ Bot error:', error);
            this.handleBotDisconnection();
        });
        this.bot.on('end', (reason) => {
            console.log('🔌 Bot disconnected:', reason);
            this.isConnected = false;
            this.handleBotDisconnection();
        });
        this.bot.on('death', () => {
            console.log('💀 Bot died, respawning...');
            this.sendWebSocketMessage({
                type: 'bot_status',
                status: 'died',
                timestamp: new Date().toISOString()
            });
        });
    }
    async initializeWebSocket() {
        console.log('🔌 Connecting to WebSocket server...');
        try {
            const wsUrl = `ws://${this.wsConfig.host}:${this.wsConfig.port}`;
            this.websocket = new ws_1.default(wsUrl);
            this.websocket.on('open', () => {
                console.log('✅ WebSocket connected to Java Plugin');
                this.sendWebSocketMessage({
                    type: 'connection',
                    status: 'connected',
                    clientType: 'mineflayer-bot',
                    timestamp: new Date().toISOString()
                });
            });
            this.websocket.on('message', (data) => {
                this.handleWebSocketMessage(data.toString());
            });
            this.websocket.on('error', (error) => {
                console.error('❌ WebSocket error:', error);
            });
            this.websocket.on('close', () => {
                console.log('🔌 WebSocket disconnected');
                this.scheduleWebSocketReconnection();
            });
        }
        catch (error) {
            console.error('❌ Failed to initialize WebSocket:', error);
            this.scheduleWebSocketReconnection();
        }
    }
    handleWebSocketMessage(data) {
        try {
            const message = JSON.parse(data);
            console.log('📨 Received message:', message);
            switch (message.type) {
                case 'move':
                    this.handleMoveCommand(message);
                    break;
                case 'chat':
                    this.handleChatCommand(message);
                    break;
                case 'action':
                    this.handleActionCommand(message);
                    break;
                default:
                    console.log('⚠️  Unknown message type:', message.type);
            }
        }
        catch (error) {
            console.error('❌ Failed to parse WebSocket message:', error);
        }
    }
    sendWebSocketMessage(message) {
        if (this.websocket && this.websocket.readyState === ws_1.default.OPEN) {
            this.websocket.send(JSON.stringify(message));
        }
        else {
            console.warn('⚠️  WebSocket not connected, message not sent:', message);
        }
    }
    async handleMoveCommand(message) {
        if (!this.bot)
            return;
        const { x, y, z } = message;
        console.log(`🚶 Moving to position: ${x}, ${y}, ${z}`);
        try {
            const { goals } = require('mineflayer-pathfinder');
            const goal = new goals.GoalNear(x, y, z, 1);
            await this.bot.pathfinder.goto(goal);
            this.sendWebSocketMessage({
                type: 'move_result',
                success: true,
                position: this.bot.entity.position,
                timestamp: new Date().toISOString()
            });
        }
        catch (error) {
            console.error('❌ Move failed:', error);
            this.sendWebSocketMessage({
                type: 'move_result',
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                timestamp: new Date().toISOString()
            });
        }
    }
    handleChatCommand(message) {
        if (!this.bot)
            return;
        const { text } = message;
        console.log(`💬 Sending chat: ${text}`);
        this.bot.chat(text);
    }
    async handleActionCommand(message) {
        console.log('🎯 Action command received:', message);
    }
    handleBotDisconnection() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`🔄 Attempting to reconnect bot (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
            setTimeout(() => {
                this.initializeBot();
            }, 5000);
        }
        else {
            console.error('❌ Max reconnection attempts reached for bot');
        }
    }
    scheduleWebSocketReconnection() {
        setTimeout(() => {
            console.log('🔄 Attempting to reconnect WebSocket...');
            this.initializeWebSocket();
        }, this.wsConfig.reconnectInterval);
    }
    async start() {
        console.log('🚀 Starting Minecraft AI Body...');
        try {
            await this.initializeWebSocket();
            await this.initializeBot();
            console.log('✅ Minecraft AI Body started successfully');
        }
        catch (error) {
            console.error('❌ Failed to start Minecraft AI Body:', error);
            process.exit(1);
        }
    }
    async stop() {
        console.log('🛑 Stopping Minecraft AI Body...');
        if (this.bot) {
            this.bot.quit();
        }
        if (this.websocket) {
            this.websocket.close();
        }
        console.log('✅ Minecraft AI Body stopped');
    }
}
const botConfig = {
    host: process.env.MINECRAFT_HOST || 'localhost',
    port: parseInt(process.env.MINECRAFT_PORT || '25565'),
    username: process.env.BOT_USERNAME || 'AICompanion',
    version: process.env.MINECRAFT_VERSION || '1.20.4',
    auth: 'offline'
};
const wsConfig = {
    host: process.env.WEBSOCKET_HOST || 'localhost',
    port: parseInt(process.env.WEBSOCKET_PORT || '8080'),
    reconnectInterval: parseInt(process.env.RECONNECT_INTERVAL || '5000')
};
const aiBody = new MinecraftAIBody(botConfig, wsConfig);
process.on('SIGINT', async () => {
    console.log('\\n🛑 Received SIGINT, shutting down gracefully...');
    await aiBody.stop();
    process.exit(0);
});
process.on('SIGTERM', async () => {
    console.log('\\n🛑 Received SIGTERM, shutting down gracefully...');
    await aiBody.stop();
    process.exit(0);
});
aiBody.start().catch((error) => {
    console.error('❌ Fatal error:', error);
    process.exit(1);
});
//# sourceMappingURL=index.js.map