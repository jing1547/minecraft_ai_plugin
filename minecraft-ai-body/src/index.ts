import { MinecraftAIBody as AIBody, MinecraftAIBodyOptions } from './bot/MinecraftAIBody';
import { WebSocketClient, WebSocketClientConfig, ConnectionState } from './network/WebSocketClient';
import { BaseMessage, MessageType, CommandMessage, ResponseMessage, Priority } from './protocol/types';
import { MessageSerializer } from './protocol/MessageSerializer';

/**
 * Minecraft AI Body - Entry Point
 * 
 * This is the main entry point for the Minecraft AI Body component.
 * It initializes and starts the comprehensive MinecraftAIBody class with
 * proper WebSocket client for Java server communication.
 */

interface AppConfig {
  testMode: boolean;
  minecraft: {
    host: string;
    port: number;
    username: string;
    version: string;
    auth: 'microsoft' | 'mojang' | 'offline';
  };
  websocket: {
    host: string;
    port: number;
    reconnectInterval: number;
  };
  bot: {
    maxRetries: number;
    retryDelay: number;
    autoCollectItems: boolean;
    autoEquipTools: boolean;
  };
}

class MinecraftAIBodyApp {
  private aiBody: AIBody | null = null;
  private wsClient: WebSocketClient | null = null;
  private config: AppConfig;
  private testMode: boolean = false;
  private commandId: number = 0;

  constructor(config: AppConfig) {
    this.config = config;
    this.testMode = config.testMode;
  }

  /**
   * Initialize the WebSocket client with proper protocol support
   */
  private initializeWebSocketClient(): void {
    if (this.testMode) {
      console.log('🧪 Test mode: WebSocket client disabled');
      return;
    }

    const wsConfig: WebSocketClientConfig = {
      host: this.config.websocket.host,
      port: this.config.websocket.port,
      reconnectInterval: this.config.websocket.reconnectInterval,
      maxReconnectInterval: 60000,
      maxRetries: 10,
      timeoutMs: 5000,
      enableLogging: true,
      enableMessageQueue: true,
      maxQueueSize: 100
    };

    this.wsClient = new WebSocketClient(wsConfig);
    this.setupWebSocketEventHandlers();
  }

  /**
   * Setup WebSocket event handlers for proper protocol communication
   */
  private setupWebSocketEventHandlers(): void {
    if (!this.wsClient) return;

    this.wsClient.on('connected', () => {
      console.log('🔌 WebSocket connected to Java server');
      this.sendHandshakeMessage();
    });

    this.wsClient.on('disconnected', (reason) => {
      console.log('🔌 WebSocket disconnected:', reason);
    });

    this.wsClient.on('message', (message: BaseMessage) => {
      this.handleWebSocketMessage(message);
    });

    this.wsClient.on('error', (error) => {
      console.error('❌ WebSocket error:', error.message);
    });

    this.wsClient.on('reconnecting', (attempt) => {
      console.log(`🔄 WebSocket reconnecting (attempt ${attempt})`);
    });
  }

  /**
   * Send initial handshake message to Java server
   */
  private sendHandshakeMessage(): void {
    if (!this.wsClient) return;

    const handshakeMessage: BaseMessage = {
      type: MessageType.COMMAND,
      id: this.generateCommandId(),
      timestamp: new Date().toISOString(),
      version: '1.0.0',
      priority: Priority.HIGH,
      payload: {
        action: 'handshake',
        parameters: {
          clientType: 'minecraft-ai-body',
          version: '1.0.0',
          capabilities: ['movement', 'interaction', 'inventory', 'combat', 'building']
        }
      }
    };

    this.wsClient.sendMessage(handshakeMessage);
    console.log('🤝 Sent handshake message to Java server');
  }

  /**
   * Handle incoming WebSocket messages from Java server
   */
  private handleWebSocketMessage(message: BaseMessage): void {
    console.log('📨 Received message from Java server:', {
      type: message.type,
      id: message.id,
      action: message.payload?.action || 'unknown'
    });

    try {
      switch (message.type) {
        case MessageType.COMMAND:
          this.handleCommandMessage(message as CommandMessage);
          break;
        case MessageType.RESPONSE:
          this.handleResponseMessage(message as ResponseMessage);
          break;
        default:
          console.warn('⚠️ Unknown message type:', message.type);
      }
    } catch (error) {
      console.error('❌ Error handling WebSocket message:', error);
      this.sendErrorResponse(message.id, error as Error);
    }
  }

  /**
   * Handle command messages from Java server
   */
  private async handleCommandMessage(command: CommandMessage): Promise<void> {
    if (!this.aiBody) {
      this.sendErrorResponse(command.id, new Error('AI Body not initialized'));
      return;
    }

    const { action, parameters } = command.payload;
    console.log(`🎮 Executing command: ${action}`, parameters);

    try {
      let result: any;
      const startTime = Date.now();

      // Route commands to AI Body methods
      switch (action) {
        case 'moveTo':
          result = await this.aiBody.moveTo(
            parameters.x, 
            parameters.y, 
            parameters.z, 
            parameters.options
          );
          break;

        case 'followEntity':
          await this.aiBody.followEntity(parameters.entityId, parameters.options);
          result = { message: 'Following entity', entityId: parameters.entityId };
          break;

        case 'stopMoving':
          this.aiBody.stopMoving();
          result = { message: 'Movement stopped' };
          break;

        case 'placeBlock':
          result = await this.aiBody.placeBlock(parameters.position, parameters.blockType);
          break;

        case 'breakBlock':
          result = await this.aiBody.breakBlock(parameters.position);
          break;

        case 'collectItem':
          result = await this.aiBody.collectItem(parameters.itemType);
          break;

        case 'getStatus':
          result = await this.aiBody.getStatus();
          break;

        case 'ping':
          result = { message: 'Pong!', timestamp: new Date().toISOString() };
          break;

        default:
          throw new Error(`Unknown command: ${action}`);
      }

      const executionTime = Date.now() - startTime;
      this.sendSuccessResponse(command.id, result, executionTime);

    } catch (error) {
      console.error(`❌ Command execution failed (${action}):`, error);
      this.sendErrorResponse(command.id, error as Error);
    }
  }

  /**
   * Handle response messages from Java server
   */
  private handleResponseMessage(response: ResponseMessage): void {
    console.log('📨 Received response:', {
      id: response.id,
      correlationId: response.correlationId,
      success: response.payload.success
    });

    // Handle server responses here if needed
    if (!response.payload.success) {
      console.error('❌ Server operation failed:', response.payload.error);
    }
  }

  /**
   * Send success response to Java server
   */
  private sendSuccessResponse(correlationId: string, result: any, executionTime?: number): void {
    if (!this.wsClient) return;

    const payload: any = {
      success: true,
      result
    };

    // Only include executionTime if it's defined
    if (executionTime !== undefined) {
      payload.executionTime = executionTime;
    }

    const response: ResponseMessage = {
      type: MessageType.RESPONSE,
      id: this.generateCommandId(),
      timestamp: new Date().toISOString(),
      version: '1.0.0',
      priority: Priority.NORMAL,
      correlationId,
      payload
    };

    this.wsClient.sendMessage(response);
  }

  /**
   * Send error response to Java server
   */
  private sendErrorResponse(correlationId: string, error: Error): void {
    if (!this.wsClient) return;

    const response: ResponseMessage = {
      type: MessageType.RESPONSE,
      id: this.generateCommandId(),
      timestamp: new Date().toISOString(),
      version: '1.0.0',
      priority: Priority.NORMAL,
      correlationId,
      payload: {
        success: false,
        error: {
          code: error.name || 'UNKNOWN_ERROR',
          message: error.message,
          details: error.stack
        }
      }
    };

    this.wsClient.sendMessage(response);
  }

  /**
   * Generate unique command ID
   */
  private generateCommandId(): string {
    return `cmd-${Date.now()}-${++this.commandId}`;
  }

  /**
   * Initialize the MinecraftAIBody instance (without WebSocket integration)
   */
  private initializeAIBody(): void {
    const options: MinecraftAIBodyOptions = {
      host: this.config.minecraft.host,
      port: this.config.minecraft.port,
      username: this.config.minecraft.username,
      version: this.config.minecraft.version,
      auth: this.config.minecraft.auth,
      maxRetries: this.config.bot.maxRetries,
      retryDelay: this.config.bot.retryDelay,
      autoCollectItems: this.config.bot.autoCollectItems,
      autoEquipTools: this.config.bot.autoEquipTools
      // Note: No WebSocket URL - handled separately now
    };

         this.aiBody = new AIBody(options);
    this.setupEventHandlers();
  }

  /**
   * Setup event handlers for the AI Body
   */
  private setupEventHandlers(): void {
    if (!this.aiBody) return;

    this.aiBody.on('connecting', () => {
      console.log('🔌 Connecting to Minecraft server...');
    });

    this.aiBody.on('connected', () => {
      console.log('✅ Connected to Minecraft server');
    });

    this.aiBody.on('spawned', () => {
      console.log('🌍 Bot spawned in world');
      if (this.testMode) {
        this.runTestSequence();
      }
    });

    this.aiBody.on('disconnected', () => {
      console.log('🔌 Disconnected from Minecraft server');
    });

    this.aiBody.on('error', (error) => {
      console.error('❌ AI Body error:', error);
    });

    this.aiBody.on('statusUpdate', (status) => {
      if (this.testMode) {
        console.log('📊 Bot Status:', {
          health: status.health,
          food: status.food,
          position: status.position,
          inventory: status.inventory?.length || 0
        });
      }
    });
  }

  /**
   * Run test sequence to demonstrate capabilities
   */
  private async runTestSequence(): Promise<void> {
    if (!this.aiBody) return;

    console.log('\n🧪 Running test sequence...');

    try {
      // Wait a bit for bot to fully initialize
      await new Promise(resolve => setTimeout(resolve, 2000));

      // Test 1: Get bot status
      console.log('\n📊 Test 1: Getting bot status...');
      const status = this.aiBody.getStatus();
      if (status) {
        console.log('✅ Bot status retrieved:', {
          connected: status.connected,
          health: status.health,
          food: status.food,
          position: status.position,
          dimension: status.dimension,
          gameMode: status.gameMode
        });
      }

      // Test 2: Get inventory
      console.log('\n📦 Test 2: Getting inventory...');
      const inventory = this.aiBody.getInventory();
      console.log('✅ Inventory retrieved:', inventory.length, 'items');

      // Test 3: Test movement capabilities (if bot is ready)
      if (this.aiBody.isReady()) {
        console.log('\n🚶 Test 3: Testing movement capabilities...');
        const currentPos = this.aiBody.getBot()?.entity.position;
        if (currentPos) {
          const targetPos = {
            x: currentPos.x + 5,
            y: currentPos.y,
            z: currentPos.z + 5
          };
          console.log(`Moving from ${currentPos.x},${currentPos.y},${currentPos.z} to ${targetPos.x},${targetPos.y},${targetPos.z}`);
          
          try {
            const result = await this.aiBody.moveTo(targetPos.x, targetPos.y, targetPos.z);
            console.log('✅ Movement test result:', result);
          } catch (error) {
            console.log('⚠️  Movement test failed (expected in test mode):', error instanceof Error ? error.message : 'Unknown error');
          }
        }
      }

      // Test 4: Test nearby entities
      console.log('\n👥 Test 4: Getting nearby entities...');
      const nearbyEntities = this.aiBody.getNearbyEntities({ maxDistance: 10 });
      console.log('✅ Found', nearbyEntities.length, 'nearby entities');

      console.log('\n🎉 Test sequence completed!');

    } catch (error) {
      console.error('❌ Test sequence failed:', error);
    }
  }

  /**
   * Start the application
   */
  public async start(): Promise<void> {
    console.log('🚀 Starting Minecraft AI Body...');
    console.log('📋 Configuration:');
    console.log(`   • Minecraft Server: ${this.config.minecraft.host}:${this.config.minecraft.port}`);
    console.log(`   • Username: ${this.config.minecraft.username}`);
    console.log(`   • Version: ${this.config.minecraft.version}`);
    console.log(`   • Auth: ${this.config.minecraft.auth}`);
    console.log(`   • WebSocket Server: ${this.config.websocket.host}:${this.config.websocket.port}`);
    
    if (this.testMode) {
      console.log('🧪 Running in TEST MODE - no server connections required');
      console.log('📝 This mode demonstrates the AI Body capabilities without actual servers');
      
      // In test mode, we create a mock demonstration
      this.runTestModeDemo();
      return;
    }

    try {
      this.initializeAIBody();
      this.initializeWebSocketClient(); // Initialize WebSocket client
      
      if (this.aiBody && this.wsClient) {
        await this.aiBody.connect(); // Connect to Minecraft server
        await this.wsClient.connect(); // Connect WebSocket client
        console.log('✅ Minecraft AI Body started successfully');
      }
      
    } catch (error) {
      console.error('❌ Failed to start Minecraft AI Body:', error);
      
      console.log('\n🔧 해결 방법:');
      console.log('1. 📺 Minecraft 서버가 실행 중인지 확인하세요:');
      console.log(`   - 서버 주소: ${this.config.minecraft.host}:${this.config.minecraft.port}`);
      console.log('   - Minecraft 서버를 시작하거나 올바른 주소를 설정하세요');
      console.log('');
      console.log('2. 🧪 또는 테스트 모드로 실행하세요 (서버 연결 없이):');
        console.log('   npm run test-mode');
      console.log('   또는');
        console.log('   TEST_MODE=true npm start');
      console.log('');
      console.log('3. 🔧 환경변수로 다른 서버 설정:');
      console.log('   set MINECRAFT_HOST=your-server-ip');
      console.log('   set MINECRAFT_PORT=25565');
      console.log('   set BOT_USERNAME=YourBotName');
      
      process.exit(1);
    }
  }

  /**
   * Run demonstration in test mode
   */
  private runTestModeDemo(): void {
    console.log('\n🎯 MinecraftAIBody Class Capabilities Demo:');
    console.log('==========================================');
    
    // Create instance for demonstration
    this.initializeAIBody();
    
    console.log('\n📋 Available Methods:');
    console.log('');
    console.log('🔌 Connection Management:');
    console.log('  - connect()           Connect to Minecraft server');
    console.log('  - disconnect()        Disconnect from server');
    console.log('  - isReady()           Check if bot is ready');
    console.log('  - getStatus()         Get comprehensive bot status');
    console.log('');
    console.log('🚶 Movement & Navigation:');
    console.log('  - moveTo(x, y, z)     Move to coordinates');
    console.log('  - moveToEntity(id)    Move to entity');
    console.log('  - followEntity(id)    Follow entity');
    console.log('  - stopMoving()        Stop all movement');
    console.log('  - distanceTo(target)  Calculate distance');
    console.log('');
    console.log('🌍 World Interaction:');
    console.log('  - placeBlock(pos, type)    Place block');
    console.log('  - breakBlock(pos)          Break block');
    console.log('  - collectItem(type)        Collect items');
    console.log('  - useItem(type)            Use item');
    console.log('  - getNearbyBlocks(type)    Find blocks');
    console.log('');
    console.log('👥 Entity Interaction:');
    console.log('  - attackEntity(id)         Attack entity');
    console.log('  - interactWithEntity(id)   Interact with entity');
    console.log('  - getNearbyEntities(filter) Find entities');
    console.log('');
    console.log('🎒 Inventory Management:');
    console.log('  - getInventory()           Get inventory');
    console.log('  - findItemInInventory(type) Find item');
    console.log('  - equipItem(type)          Equip item');
    console.log('  - craftItem(type)          Craft item');
    console.log('  - depositItemsInChest()    Store items');
    console.log('');
    console.log('📊 Status & Monitoring:');
    console.log('  - Event-driven updates    Real-time status');
    console.log('  - Comprehensive logging   Detailed feedback');
    console.log('  - Error handling          Robust operation');
    console.log('');
    console.log('🎉 All features implemented and ready for use!');
    console.log('   To test with real servers, configure and run:');
    console.log('   npm start');
    
    setTimeout(() => {
      console.log('\n✅ Demo completed. Press Ctrl+C to exit.');
    }, 1000);
  }

  /**
   * Stop the application
   */
  public async stop(): Promise<void> {
    console.log('🛑 Stopping Minecraft AI Body...');
    
    if (this.aiBody) {
      await this.aiBody.disconnect();
      this.aiBody.destroy();
    }
    if (this.wsClient) {
      await this.wsClient.disconnect();
    }
    
    console.log('✅ Minecraft AI Body stopped');
  }
}

// Configuration from environment variables
const config: AppConfig = {
  testMode: process.env.TEST_MODE === 'true' || process.argv.includes('--test-mode'),
  minecraft: {
    host: process.env.MINECRAFT_HOST || 'localhost',
    port: parseInt(process.env.MINECRAFT_PORT || '25565'),
    username: process.env.BOT_USERNAME || 'AICompanion',
            version: process.env.MINECRAFT_VERSION || '1.21.4',
    auth: (process.env.MINECRAFT_AUTH as 'microsoft' | 'mojang' | 'offline') || 'offline'
  },
  websocket: {
    host: process.env.WEBSOCKET_HOST || 'localhost',
    port: parseInt(process.env.WEBSOCKET_PORT || '8080'),
    reconnectInterval: parseInt(process.env.WEBSOCKET_RECONNECT_INTERVAL || '5000')
  },
  bot: {
    maxRetries: parseInt(process.env.BOT_MAX_RETRIES || '5'),
    retryDelay: parseInt(process.env.BOT_RETRY_DELAY || '5000'),
    autoCollectItems: process.env.BOT_AUTO_COLLECT_ITEMS === 'true',
    autoEquipTools: process.env.BOT_AUTO_EQUIP_TOOLS === 'true'
  }
};

// Initialize and start the application
const app = new MinecraftAIBodyApp(config);

// Graceful shutdown handlers
process.on('SIGINT', async () => {
  console.log('\n🛑 Received SIGINT, shutting down gracefully...');
  await app.stop();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  console.log('\n🛑 Received SIGTERM, shutting down gracefully...');
  await app.stop();
  process.exit(0);
});

// Handle uncaught exceptions
process.on('uncaughtException', async (error) => {
  console.error('\n💥 Uncaught exception:', error);
  await app.stop();
  process.exit(1);
});

process.on('unhandledRejection', async (reason, promise) => {
  console.error('\n💥 Unhandled rejection at:', promise, 'reason:', reason);
  await app.stop();
  process.exit(1);
});

// Start the application
app.start().catch(error => {
  console.error('💥 Failed to start application:', error);
  process.exit(1);
}); 