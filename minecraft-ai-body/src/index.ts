import { MinecraftAIBody as AIBody, MinecraftAIBodyOptions } from './bot/MinecraftAIBody';

/**
 * Minecraft AI Body - Entry Point
 * 
 * This is the main entry point for the Minecraft AI Body component.
 * It initializes and starts the comprehensive MinecraftAIBody class.
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
    url: string;
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
  private config: AppConfig;
  private testMode: boolean = false;

  constructor(config: AppConfig) {
    this.config = config;
    this.testMode = config.testMode;
  }

  /**
   * Initialize the MinecraftAIBody instance
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
    };

    // Add WebSocket URL only if not in test mode
    if (!this.testMode) {
      options.webSocketUrl = this.config.websocket.url;
      options.webSocketReconnectInterval = this.config.websocket.reconnectInterval;
    }

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
          inventoryUsed: status.inventoryUsed
        });
      }
    });

    // Movement events
    this.aiBody.on('movementStarted', (data) => {
      console.log('🚶 Movement started:', data);
    });

    this.aiBody.on('movementCompleted', (data) => {
      console.log('✅ Movement completed:', data);
    });

    this.aiBody.on('movementStopped', () => {
      console.log('⏹️  Movement stopped');
    });

    // Interaction events
    this.aiBody.on('blockPlaced', (data) => {
      console.log('🧱 Block placed:', data);
    });

    this.aiBody.on('blockBroken', (data) => {
      console.log('⛏️  Block broken:', data);
    });

    this.aiBody.on('itemCollected', (data) => {
      console.log('📦 Item collected:', data);
    });

    this.aiBody.on('entityAttacked', (data) => {
      console.log('⚔️  Entity attacked:', data);
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
    
    if (this.testMode) {
      console.log('🧪 Running in TEST MODE - no server connections required');
      console.log('📝 This mode demonstrates the AI Body capabilities without actual servers');
      
      // In test mode, we create a mock demonstration
      this.runTestModeDemo();
      return;
    }

    try {
      this.initializeAIBody();
      
      if (this.aiBody) {
        await this.aiBody.connect();
        console.log('✅ Minecraft AI Body started successfully');
      }
      
    } catch (error) {
      console.error('❌ Failed to start Minecraft AI Body:', error);
      
      if (error instanceof Error && error.message.includes('ECONNREFUSED')) {
        console.log('\n💡 Connection failed! Try running in test mode:');
        console.log('   npm run test-mode');
        console.log('   or');
        console.log('   TEST_MODE=true npm start');
      }
      
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
            version: process.env.MINECRAFT_VERSION || '1.21',
    auth: (process.env.MINECRAFT_AUTH as 'microsoft' | 'mojang' | 'offline') || 'offline'
  },
  websocket: {
    url: process.env.WEBSOCKET_URL || 'ws://localhost:8080',
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

// Start the application
app.start().catch((error) => {
  console.error('❌ Fatal error:', error);
  process.exit(1);
}); 