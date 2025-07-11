import { Bot, createBot } from 'mineflayer';
import { pathfinder, Movements } from 'mineflayer-pathfinder';
import { plugin as pvp } from 'mineflayer-pvp';
// import builder from 'mineflayer-builder'; // TODO: Fix import issues
import WebSocket from 'ws';

/**
 * Minecraft AI Body - Node.js Mineflayer Bot Component
 * 
 * This is the "body" component of the hybrid architecture that handles:
 * - Game world interactions and movement
 * - Block manipulation and building
 * - Combat and PvP
 * - WebSocket communication with Java Plugin (brain)
 */

interface BotConfig {
  host: string;
  port: number;
  username: string;
  version?: string;
  auth?: 'microsoft' | 'mojang' | 'offline';
}

interface WebSocketConfig {
  host: string;
  port: number;
  reconnectInterval: number;
}

class MinecraftAIBody {
  private bot: Bot | null = null;
  private websocket: WebSocket | null = null;
  private config: BotConfig;
  private wsConfig: WebSocketConfig;
  private isConnected: boolean = false;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 5;

  constructor(botConfig: BotConfig, wsConfig: WebSocketConfig) {
    this.config = botConfig;
    this.wsConfig = wsConfig;
  }

  /**
   * Initialize the Mineflayer bot with all required plugins
   */
  private async initializeBot(): Promise<void> {
    console.log('🤖 Initializing Mineflayer bot...');
    
    try {
      this.bot = createBot({
        host: this.config.host,
        port: this.config.port,
        username: this.config.username,
        version: this.config.version || '1.20.4',
        auth: this.config.auth || 'offline'
      });

      // Load essential plugins
      this.bot.loadPlugin(pathfinder);
      this.bot.loadPlugin(pvp);
      // this.bot.loadPlugin(builder); // TODO: Enable when builder plugin works

      this.setupBotEventHandlers();
      
    } catch (error) {
      console.error('❌ Failed to initialize bot:', error);
      throw error;
    }
  }

  /**
   * Setup event handlers for the Mineflayer bot
   */
  private setupBotEventHandlers(): void {
    if (!this.bot) return;

    this.bot.on('login', () => {
      console.log('✅ Bot logged into Minecraft server');
      this.isConnected = true;
      this.reconnectAttempts = 0;
      
      // Initialize pathfinding
      if (this.bot?.entity.position) {
        const movements = new Movements(this.bot);
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
      
      // Send chat messages to Java Plugin for AI processing
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

  /**
   * Initialize WebSocket connection to Java Plugin
   */
  private async initializeWebSocket(): Promise<void> {
    console.log('🔌 Connecting to WebSocket server...');
    
    try {
      const wsUrl = `ws://${this.wsConfig.host}:${this.wsConfig.port}`;
      this.websocket = new WebSocket(wsUrl);

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

    } catch (error) {
      console.error('❌ Failed to initialize WebSocket:', error);
      this.scheduleWebSocketReconnection();
    }
  }

  /**
   * Handle incoming WebSocket messages from Java Plugin
   */
  private handleWebSocketMessage(data: string): void {
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
    } catch (error) {
      console.error('❌ Failed to parse WebSocket message:', error);
    }
  }

  /**
   * Send message to Java Plugin via WebSocket
   */
  private sendWebSocketMessage(message: any): void {
    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
      this.websocket.send(JSON.stringify(message));
    } else {
      console.warn('⚠️  WebSocket not connected, message not sent:', message);
    }
  }

  /**
   * Handle movement commands from Java Plugin
   */
  private async handleMoveCommand(message: any): Promise<void> {
    if (!this.bot) return;

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
    } catch (error) {
      console.error('❌ Move failed:', error);
      this.sendWebSocketMessage({
        type: 'move_result',
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  }

  /**
   * Handle chat commands from Java Plugin
   */
  private handleChatCommand(message: any): void {
    if (!this.bot) return;

    const { text } = message;
    console.log(`💬 Sending chat: ${text}`);
    this.bot.chat(text);
  }

  /**
   * Handle action commands from Java Plugin
   */
  private async handleActionCommand(message: any): Promise<void> {
    // TODO: Implement various bot actions (dig, place, attack, etc.)
    console.log('🎯 Action command received:', message);
  }

  /**
   * Handle bot disconnection and attempt reconnection
   */
  private handleBotDisconnection(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`🔄 Attempting to reconnect bot (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);
      
      setTimeout(() => {
        this.initializeBot();
      }, 5000);
    } else {
      console.error('❌ Max reconnection attempts reached for bot');
    }
  }

  /**
   * Schedule WebSocket reconnection
   */
  private scheduleWebSocketReconnection(): void {
    setTimeout(() => {
      console.log('🔄 Attempting to reconnect WebSocket...');
      this.initializeWebSocket();
    }, this.wsConfig.reconnectInterval);
  }

  /**
   * Start the Minecraft AI Body
   */
  public async start(): Promise<void> {
    console.log('🚀 Starting Minecraft AI Body...');
    
    try {
      // Initialize WebSocket connection first
      await this.initializeWebSocket();
      
      // Then initialize the bot
      await this.initializeBot();
      
      console.log('✅ Minecraft AI Body started successfully');
      
    } catch (error) {
      console.error('❌ Failed to start Minecraft AI Body:', error);
      process.exit(1);
    }
  }

  /**
   * Stop the Minecraft AI Body
   */
  public async stop(): Promise<void> {
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

// Configuration
const botConfig: BotConfig = {
  host: process.env.MINECRAFT_HOST || 'localhost',
  port: parseInt(process.env.MINECRAFT_PORT || '25565'),
  username: process.env.BOT_USERNAME || 'AICompanion',
  version: process.env.MINECRAFT_VERSION || '1.20.4',
  auth: 'offline'
};

const wsConfig: WebSocketConfig = {
  host: process.env.WEBSOCKET_HOST || 'localhost',
  port: parseInt(process.env.WEBSOCKET_PORT || '8080'),
  reconnectInterval: parseInt(process.env.RECONNECT_INTERVAL || '5000')
};

// Initialize and start the bot
const aiBody = new MinecraftAIBody(botConfig, wsConfig);

// Graceful shutdown
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

// Start the application
aiBody.start().catch((error) => {
  console.error('❌ Fatal error:', error);
  process.exit(1);
}); 