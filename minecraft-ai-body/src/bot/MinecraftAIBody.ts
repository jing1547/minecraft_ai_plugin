import { Bot, createBot, BotOptions } from 'mineflayer';
import { pathfinder, Movements, goals } from 'mineflayer-pathfinder';
import { plugin as pvpPlugin } from 'mineflayer-pvp';
import { EventEmitter } from 'events';
import { Vec3 } from 'vec3';
import { Entity } from 'prismarine-entity';
import { Item } from 'prismarine-item';
import { Block } from 'prismarine-block';
import { Recipe } from 'prismarine-recipe';
// WebSocket functionality moved to index.ts - removed imports
// import WebSocket from 'ws';
// import { BaseMessage, MessageType, Priority, CommandMessage, ResponseMessage, EventMessage } from '../protocol/types';
// import { MessageSerializer } from '../protocol/MessageSerializer';

// WebSocket functionality moved to index.ts - interfaces commented out
/*
export interface WebSocketCommand extends BaseMessage {
    type: MessageType;
    payload: {
        action: string;
        parameters: { [key: string]: any };
        timeout?: number;
    };
}

export interface WebSocketResponse extends BaseMessage {
    type: MessageType.RESPONSE;
    correlationId: string;
    payload: {
    success: boolean;
    result?: any;
        error?: {
            code: string;
            message: string;
            details?: any;
        };
        executionTime?: number;
    };
}

function createWebSocketResponse(
    correlationId: string,
    success: boolean,
    result?: any,
    error?: string,
    executionTime?: number
): WebSocketResponse {
    // Implementation moved to index.ts
}

export interface CommandHandler {
    (command: WebSocketCommand): Promise<WebSocketResponse>;
}

export interface CommandRegistry {
    [commandType: string]: CommandHandler;
}
*/

// Event Reporting and Logging interfaces
export interface LogEntry {
    timestamp: number;
    level: LogLevel;
    category: string;
    message: string;
    data?: any;
    source?: string;
}

export interface EventReport {
    id: string;
    timestamp: number;
    type: EventType;
    data: any;
    severity: LogLevel;
}

export interface LoggingConfig {
    level: LogLevel;
    categories: string[];
    enableConsole: boolean;
    enableFile: boolean;
    enableWebSocket: boolean;
    maxLogEntries: number;
    reportingInterval: number;
}

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export type EventType = 
    | 'bot_status_change'
    | 'inventory_change'
    | 'health_change'
    | 'entity_sighted'
    | 'entity_interaction'
    | 'block_interaction'
    | 'movement_update'
    | 'chat_message'
    | 'combat_event'
    | 'error_occurred'
    | 'connection_event'
    | 'world_observation';

export interface InventoryChangeEvent {
    action: 'added' | 'removed' | 'moved';
    item: {
        type: string;
        name: string;
        count: number;
        slot: number;
    };
    previousCount?: number;
    newCount?: number;
}

export interface EntitySightingEvent {
    entityId: string;
    entityType: string;
    entityName?: string;
    position: {x: number; y: number; z: number};
    distance: number;
    isHostile: boolean;
    isPlayer: boolean;
    action: 'entered_range' | 'left_range';
}

export interface HealthChangeEvent {
    previousHealth: number;
    newHealth: number;
    previousFood: number;
    newFood: number;
    damageSource?: string;
    isHealing: boolean;
}

export interface CombatEvent {
    type: 'attacked' | 'killed' | 'damaged' | 'missed';
    target?: string;
    attacker?: string;
    damage?: number;
    weapon?: string;
}

// Movement-related interfaces
export interface MovementCommand {
    id: string;
    type: 'moveTo' | 'moveToEntity' | 'followEntity' | 'stop';
    target?: Vec3;
    entityId?: string;
    options?: MovementOptions;
    timestamp: number;
    status: 'queued' | 'executing' | 'completed' | 'failed' | 'cancelled';
}

export interface MovementOptions {
    timeout?: number;
    range?: number;
    speed?: number;
    allowSprint?: boolean;
    allowParkour?: boolean;
    allowDigging?: boolean;
    avoidWater?: boolean;
    avoidLava?: boolean;
}

export interface PathfindingResult {
    success: boolean;
    path?: Vec3[];
    distance?: number;
    duration?: number;
    error?: string;
}

// World interaction interfaces
export interface BlockPosition {
    x: number;
    y: number;
    z: number;
}

export interface BlockInteractionResult {
    success: boolean;
    blockType?: string;
    position?: Vec3;
    error?: string;
    duration?: number;
}

export interface ItemInteractionResult {
    success: boolean;
    item?: Item;
    quantity?: number;
    error?: string;
    duration?: number;
}

export interface EntityInteractionResult {
    success: boolean;
    entity?: Entity;
    result?: any;
    error?: string;
    duration?: number;
}

export interface InventoryItem {
    type: string;
    name: string;
    count: number;
    slot: number;
    metadata?: any;
}

export interface CraftingOptions {
    table?: boolean;
    count?: number;
    requireAll?: boolean;
}

// Entity detection and filtering
export interface EntityFilter {
    type?: string;
    name?: string;
    maxDistance?: number;
    hostile?: boolean;
    player?: boolean;
    mob?: boolean;
    item?: boolean;
}

export interface MinecraftAIBodyOptions {
    // Connection options
    host?: string;
    port?: number;
    username?: string;
    password?: string;
    auth?: 'microsoft' | 'mojang' | 'offline';
    version?: string;
    
    // Bot behavior options
    viewDistance?: 'tiny' | 'short' | 'normal' | 'far';
    difficulty?: number;
    maxRetries?: number;
    retryDelay?: number;
    
    // WebSocket options
    webSocketUrl?: string;
    webSocketReconnectInterval?: number;
    
    // Movement options
    movementTimeout?: number;
    maxMovementQueue?: number;
    pathfindingRange?: number;
    
    // Interaction options
    interactionTimeout?: number;
    autoCollectItems?: boolean;
    autoEquipTools?: boolean;
}

export interface BotStatus {
    connected: boolean;
    health: number;
    food: number;
    experience: number;
    level: number;
    position: {
        x: number;
        y: number;
        z: number;
    };
    dimension: string;
    gameMode: string;
    weather: string;
    timeOfDay: number;
    inventoryUsed: number;
    inventoryTotal: number;
    
    // Movement status
    isMoving: boolean;
    currentTarget?: Vec3;
    queuedMovements: number;
    followingEntity?: string;
    
    // World interaction status
    isInteracting: boolean;
    heldItem?: string;
    targetingEntity?: string;
    nearbyEntities: number;
    nearbyItems: number;
}

export class MinecraftAIBody extends EventEmitter {
    private bot: Bot | null = null;
    private movements: Movements | null = null;
    private options: MinecraftAIBodyOptions;
    private isConnected: boolean = false;
    private isConnecting: boolean = false;
    private retryCount: number = 0;
    private connectionTimeout: NodeJS.Timeout | null = null;
    private statusInterval: NodeJS.Timeout | null = null;
    
    // Movement management
    private movementQueue: MovementCommand[] = [];
    private currentMovement: MovementCommand | null = null;
    private isMoving: boolean = false;
    private followingEntity: string | null = null;
    private movementTimeout: NodeJS.Timeout | null = null;
    private pathfindingInProgress: boolean = false;
    
    // WebSocket functionality moved to index.ts - properties commented out
    /*
    private webSocket: WebSocket | null = null;
    private webSocketConnected: boolean = false;
    private webSocketReconnectTimeout: NodeJS.Timeout | null = null;
    private commandRegistry: CommandRegistry = {};
    private pendingCommands: Map<string, (response: WebSocketResponse) => void> = new Map();
    */

    // Event Reporting and Logging System
    private loggingConfig: LoggingConfig;
    private logEntries: LogEntry[] = [];
    private eventReports: EventReport[] = [];
    private reportingInterval: NodeJS.Timeout | null = null;
    private lastKnownInventory: Map<string, number> = new Map();
    private lastKnownHealth: number = 20;
    private lastKnownFood: number = 20;
    private nearbyEntitiesCache: Map<string, EntitySightingEvent> = new Map();
    private eventIdCounter: number = 0;

    constructor(options: MinecraftAIBodyOptions = {}) {
        super();
        
        // Create options object properly handling undefined values
        this.options = {
            host: options.host || 'localhost',
            port: options.port || 25565,
            username: options.username || 'AIBot',
            auth: options.auth || 'offline',
            version: options.version || '1.21.4',
            viewDistance: options.viewDistance || 'tiny',
            difficulty: options.difficulty || 2,
            maxRetries: options.maxRetries || 5,
            retryDelay: options.retryDelay || 5000,
            webSocketReconnectInterval: options.webSocketReconnectInterval || 5000,
            movementTimeout: options.movementTimeout || 30000,
            maxMovementQueue: options.maxMovementQueue || 10,
            pathfindingRange: options.pathfindingRange || 64
        };

        // Only add password if provided
        if (options.password !== undefined) {
            this.options.password = options.password;
        }

        // Only add webSocketUrl if provided
        if (options.webSocketUrl !== undefined) {
            this.options.webSocketUrl = options.webSocketUrl;
        }

        // Bind event handlers
        this.handleBotSpawn = this.handleBotSpawn.bind(this);
        this.handleBotEnd = this.handleBotEnd.bind(this);
        this.handleBotError = this.handleBotError.bind(this);
        this.handleBotDeath = this.handleBotDeath.bind(this);
        this.handleBotKicked = this.handleBotKicked.bind(this);
        this.handleBotHealth = this.handleBotHealth.bind(this);
        this.handleBotExperience = this.handleBotExperience.bind(this);
        this.handleBotChat = this.handleBotChat.bind(this);
        this.handleBotWeather = this.handleBotWeather.bind(this);
        this.handleBotTime = this.handleBotTime.bind(this);
        this.handleBotWindowOpen = this.handleBotWindowOpen.bind(this);
        this.handleBotWindowClose = this.handleBotWindowClose.bind(this);
        this.handleBotInventoryUpdate = this.handleBotInventoryUpdate.bind(this);
        
        // Bind movement event handlers
        this.handlePathfindingGoal = this.handlePathfindingGoal.bind(this);
        this.handlePathfindingInterrupted = this.handlePathfindingInterrupted.bind(this);
        this.handlePathfindingTimeout = this.handlePathfindingTimeout.bind(this);
        this.handlePathfindingError = this.handlePathfindingError.bind(this);
        
        // Initialize logging configuration
        this.loggingConfig = {
            level: 'info',
            categories: ['general', 'movement', 'interaction', 'combat', 'inventory'],
            enableConsole: true,
            enableFile: false,
            enableWebSocket: true,
            maxLogEntries: 1000,
            reportingInterval: 5000 // 5 seconds
        };
        
        // WebSocket functionality moved to index.ts - initialization commented out
        /*
        this.initializeCommandRegistry();
        
        if (this.options.webSocketUrl) {
            this.initializeWebSocket();
        }
        */
        
        // Start event reporting
        this.startEventReporting();
    }

    /**
     * Initialize and connect the bot to the Minecraft server
     */
    public async connect(): Promise<void> {
        if (this.isConnected || this.isConnecting) {
            throw new Error('Bot is already connected or connecting');
        }

        this.isConnecting = true;
        this.emit('connecting');

        try {
            // Create bot options
            const botOptions: BotOptions = {
                host: this.options.host!,
                port: this.options.port!,
                username: this.options.username!,
                auth: this.options.auth!,
                version: this.options.version!,
                viewDistance: this.options.viewDistance!
            };

            // Add password if provided
            if (this.options.password) {
                botOptions.password = this.options.password;
            }

            // Create bot instance
            this.bot = createBot(botOptions);

            // Load plugins
            this.bot.loadPlugin(pathfinder);
            this.bot.loadPlugin(pvpPlugin);

            // Setup event listeners
            this.setupEventListeners();

            // Setup connection timeout
            this.connectionTimeout = setTimeout(() => {
                if (this.isConnecting) {
                    this.handleConnectionTimeout();
                }
            }, 30000); // 30 seconds timeout

            // Wait for connection
            await this.waitForConnection();

            this.isConnecting = false;
            this.isConnected = true;
            this.retryCount = 0;

            if (this.connectionTimeout) {
                clearTimeout(this.connectionTimeout);
                this.connectionTimeout = null;
            }

            this.emit('connected');
            this.startStatusReporting();

        } catch (error) {
            this.isConnecting = false;
            this.handleConnectionError(error);
            throw error;
        }
    }

    /**
     * Disconnect the bot from the server
     */
    public async disconnect(): Promise<void> {
        if (!this.isConnected || !this.bot) {
            return;
        }

        this.isConnected = false;
        this.stopStatusReporting();
        this.clearMovementQueue();
        
        if (this.connectionTimeout) {
            clearTimeout(this.connectionTimeout);
            this.connectionTimeout = null;
        }

        if (this.movementTimeout) {
            clearTimeout(this.movementTimeout);
            this.movementTimeout = null;
        }

        try {
            this.bot.quit('Disconnecting gracefully');
            this.bot = null;
            this.movements = null;
            this.emit('disconnected');
        } catch (error) {
            this.emit('error', error);
        }
    }

    /**
     * Get current bot instance
     */
    public getBot(): Bot | null {
        return this.bot;
    }

    /**
     * Get current bot status
     */
    public getStatus(): BotStatus | null {
        if (!this.bot || !this.isConnected) {
            return null;
        }

        const status: BotStatus = {
            connected: this.isConnected,
            health: this.bot.health,
            food: this.bot.food,
            experience: this.bot.experience.points,
            level: this.bot.experience.level,
            position: {
                x: this.bot.entity.position.x,
                y: this.bot.entity.position.y,
                z: this.bot.entity.position.z
            },
            dimension: this.bot.game.dimension,
            gameMode: this.bot.game.gameMode,
            weather: this.bot.isRaining ? 'rain' : 'clear',
            timeOfDay: this.bot.time.timeOfDay,
            inventoryUsed: this.bot.inventory.slots.filter(slot => slot !== null).length,
            inventoryTotal: this.bot.inventory.slots.length,
            isMoving: this.isMoving,
            queuedMovements: this.movementQueue.length,
            isInteracting: false, // Placeholder, needs actual implementation
            nearbyEntities: 0, // Placeholder, needs actual implementation
            nearbyItems: 0 // Placeholder, needs actual implementation
        };

        // Add optional fields only if they exist
        if (this.currentMovement?.target) {
            status.currentTarget = this.currentMovement.target;
        }
        
        if (this.followingEntity) {
            status.followingEntity = this.followingEntity;
        }

        // Add heldItem only if bot is holding something
        if (this.bot.heldItem) {
            status.heldItem = this.bot.heldItem.name;
        }

        return status;
    }

    /**
     * Check if bot is connected and ready
     */
    public isReady(): boolean {
        return this.isConnected && this.bot !== null && this.movements !== null;
    }

    /**
     * Get pathfinder movements instance
     */
    public getMovements(): Movements | null {
        return this.movements;
    }

    // ===================
    // MOVEMENT METHODS
    // ===================

    /**
     * Move to specific coordinates
     */
    public async moveTo(x: number, y: number, z: number, options: MovementOptions = {}): Promise<PathfindingResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for movement');
        }

        const target = new Vec3(x, y, z);
        const movement: MovementCommand = {
            id: this.generateMovementId(),
            type: 'moveTo',
            target,
            options,
            timestamp: Date.now(),
            status: 'queued'
        };

        return this.executeMovement(movement);
    }

    /**
     * Move to a specific entity
     */
    public async moveToEntity(entityId: string, options: MovementOptions = {}): Promise<PathfindingResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for movement');
        }

        const entity = this.bot!.entities[entityId];
        if (!entity) {
            throw new Error(`Entity ${entityId} not found`);
        }

        const movement: MovementCommand = {
            id: this.generateMovementId(),
            type: 'moveToEntity',
            entityId,
            target: entity.position,
            options,
            timestamp: Date.now(),
            status: 'queued'
        };

        return this.executeMovement(movement);
    }

    /**
     * Follow a specific entity
     */
    public async followEntity(entityId: string, options: MovementOptions = {}): Promise<void> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for movement');
        }

        const entity = this.bot!.entities[entityId];
        if (!entity) {
            throw new Error(`Entity ${entityId} not found`);
        }

        // Stop current following
        if (this.followingEntity) {
            this.stopFollowing();
        }

        this.followingEntity = entityId;
        
        const movement: MovementCommand = {
            id: this.generateMovementId(),
            type: 'followEntity',
            entityId,
            target: entity.position,
            options: { ...options, range: options.range || 3 },
            timestamp: Date.now(),
            status: 'queued'
        };

        this.executeMovement(movement);
        this.emit('followingEntity', { entityId, entity: entity.username || entity.displayName });
    }

    /**
     * Stop all movement
     */
    public stopMoving(): void {
        if (!this.bot) return;

        this.bot.pathfinder.stop();
        this.clearMovementQueue();
        this.isMoving = false;
        this.currentMovement = null;
        this.pathfindingInProgress = false;

        if (this.movementTimeout) {
            clearTimeout(this.movementTimeout);
            this.movementTimeout = null;
        }

        this.emit('movementStopped');
    }

    /**
     * Stop following current entity
     */
    public stopFollowing(): void {
        if (this.followingEntity) {
            const previousEntity = this.followingEntity;
            this.followingEntity = null;
            this.stopMoving();
            this.emit('stoppedFollowing', { entityId: previousEntity });
        }
    }

    /**
     * Check if bot is currently moving
     */
    public isCurrentlyMoving(): boolean {
        return this.isMoving;
    }

    /**
     * Get current movement target
     */
    public getCurrentTarget(): Vec3 | null {
        return this.currentMovement?.target || null;
    }

    /**
     * Get movement queue length
     */
    public getQueueLength(): number {
        return this.movementQueue.length;
    }

    /**
     * Calculate distance to target
     */
    public distanceTo(target: Vec3): number {
        if (!this.bot) return Infinity;
        return this.bot.entity.position.distanceTo(target);
    }

    /**
     * Calculate distance to entity
     */
    public distanceToEntity(entityId: string): number {
        if (!this.bot) return Infinity;
        
        const entity = this.bot.entities[entityId];
        if (!entity) return Infinity;
        
        return this.bot.entity.position.distanceTo(entity.position);
    }

    /**
     * Check if path is safe for movement
     */
    public isPathSafe(start: Vec3, end: Vec3): boolean {
        if (!this.bot) return false;

        const distance = start.distanceTo(end);
        if (distance > this.options.pathfindingRange!) return false;

        try {
            // Check for dangerous blocks along the path
            const dangerousBlocks = ['lava', 'magma_block', 'fire', 'soul_fire', 'cactus'];
            
            // Simple line-of-sight check
            const steps = Math.ceil(distance);
            for (let i = 0; i <= steps; i++) {
                const t = i / steps;
                // Manual interpolation instead of lerp
                const checkPos = new Vec3(
                    start.x + (end.x - start.x) * t,
                    start.y + (end.y - start.y) * t,
                    start.z + (end.z - start.z) * t
                );
                const block = this.bot.blockAt(checkPos);
                
                if (block && dangerousBlocks.includes(block.name)) {
                    return false;
                }
                
                // Check if below bedrock
                if (checkPos.y < 0) return false;
            }
            
            return true;
        } catch (error) {
            return false;
        }
    }

    // ===================
    // WORLD INTERACTION METHODS
    // ===================

    /**
     * Place a block at the specified position
     */
    public async placeBlock(position: BlockPosition, blockType: string): Promise<BlockInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();
        const targetPos = new Vec3(position.x, position.y, position.z);

        try {
            // Find the item in inventory
            const blockItem = this.bot!.inventory.items().find(item => 
                item.name === blockType || item.displayName === blockType
            );

            if (!blockItem) {
                return {
                    success: false,
                    error: `Block type ${blockType} not found in inventory`,
                    duration: Date.now() - startTime
                };
            }

            // Equip the item
            await this.bot!.equip(blockItem, 'hand');

            // Find reference block to place against
            const referenceBlock = this.bot!.blockAt(targetPos.offset(0, -1, 0));
            if (!referenceBlock) {
                return {
                    success: false,
                    error: 'No reference block found to place against',
                    duration: Date.now() - startTime
                };
            }

            // Place the block
            await this.bot!.placeBlock(referenceBlock, new Vec3(0, 1, 0));

            this.emit('blockPlaced', { position: targetPos, blockType });
            return {
                success: true,
                blockType,
                position: targetPos,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    /**
     * Break a block at the specified position
     */
    public async breakBlock(position: BlockPosition): Promise<BlockInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();
        const targetPos = new Vec3(position.x, position.y, position.z);

        try {
            const block = this.bot!.blockAt(targetPos);
            if (!block) {
                return {
                    success: false,
                    error: 'No block found at the specified position',
                    duration: Date.now() - startTime
                };
            }

            const blockType = block.name;

            // Check if block is breakable
            if (block.hardness === -1) {
                return {
                    success: false,
                    error: 'Block is unbreakable',
                    duration: Date.now() - startTime
                };
            }

            // Auto-equip best tool if available
            if (this.options.autoEquipTools) {
                await this.autoEquipBestTool(block);
            }

            // Break the block
            await this.bot!.dig(block);

            this.emit('blockBroken', { position: targetPos, blockType });
            return {
                success: true,
                blockType,
                position: targetPos,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    /**
     * Collect nearby items
     */
    public async collectItem(itemType?: string): Promise<ItemInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();

        try {
            // Find nearby items
            const nearbyItems = Object.values(this.bot!.entities)
                .filter(entity => entity.name === 'item')
                .filter(entity => entity.position.distanceTo(this.bot!.entity.position) < 10);

            if (nearbyItems.length === 0) {
                return {
                    success: false,
                    error: 'No items found nearby',
                    duration: Date.now() - startTime
                };
            }

            // Filter by item type if specified
            let targetItem = nearbyItems[0];
            if (itemType) {
                targetItem = nearbyItems.find(item => 
                    item.metadata && typeof item.metadata === 'object' && 
                    'displayName' in item.metadata && item.metadata.displayName === itemType
                ) || nearbyItems[0];
            }

            if (!targetItem) {
                return {
                    success: false,
                    error: 'No suitable item found',
                    duration: Date.now() - startTime
                };
            }

            // Move to the item
            await this.moveTo(targetItem.position.x, targetItem.position.y, targetItem.position.z);

            // Wait for item to be collected
            await new Promise(resolve => setTimeout(resolve, 500));

            this.emit('itemCollected', { item: targetItem });
            return {
                success: true,
                quantity: 1,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    /**
     * Use an item from inventory
     */
    public async useItem(itemType: string): Promise<ItemInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();

        try {
            const item = this.findItemInInventory(itemType);
            if (!item) {
                return {
                    success: false,
                    error: `Item ${itemType} not found in inventory`,
                    duration: Date.now() - startTime
                };
            }

            // Equip the item
            await this.equipItem(itemType);

            // Use the item
            this.bot!.activateItem();

            this.emit('itemUsed', { item: item });
            return {
                success: true,
                item: item as any,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    /**
     * Attack an entity
     */
    public async attackEntity(entityId: string): Promise<EntityInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();

        try {
            const entity = this.bot!.entities[entityId];
            if (!entity) {
                return {
                    success: false,
                    error: `Entity ${entityId} not found`,
                    duration: Date.now() - startTime
                };
            }

            // Move close to the entity
            const distance = this.bot!.entity.position.distanceTo(entity.position);
            if (distance > 4) {
                await this.moveToEntity(entityId, { range: 3 });
            }

            // Attack the entity
            await this.bot!.attack(entity);

            this.emit('entityAttacked', { entityId, entity });
            return {
                success: true,
                entity,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    /**
     * Interact with an entity (right-click)
     */
    public async interactWithEntity(entityId: string): Promise<EntityInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();

        try {
            const entity = this.bot!.entities[entityId];
            if (!entity) {
                return {
                    success: false,
                    error: `Entity ${entityId} not found`,
                    duration: Date.now() - startTime
                };
            }

            // Move close to the entity
            const distance = this.bot!.entity.position.distanceTo(entity.position);
            if (distance > 4) {
                await this.moveToEntity(entityId, { range: 3 });
            }

            // Interact with the entity
            await this.bot!.useOn(entity);

            this.emit('entityInteracted', { entityId, entity });
            return {
                success: true,
                entity,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    // ===================
    // INVENTORY MANAGEMENT METHODS
    // ===================

    /**
     * Find an item in the bot's inventory
     */
    public findItemInInventory(itemType: string): InventoryItem | null {
        if (!this.bot) return null;

        const item = this.bot.inventory.items().find(item => 
            item.name === itemType || 
            item.displayName === itemType ||
            item.type === parseInt(itemType)
        );

        if (!item) return null;

        return {
            type: item.name,
            name: item.displayName,
            count: item.count,
            slot: item.slot,
            metadata: item.metadata
        };
    }

    /**
     * Equip an item in the bot's hand
     */
    public async equipItem(itemType: string): Promise<ItemInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();

        try {
            const item = this.bot!.inventory.items().find(item => 
                item.name === itemType || 
                item.displayName === itemType ||
                item.type === parseInt(itemType)
            );

            if (!item) {
                return {
                    success: false,
                    error: `Item ${itemType} not found in inventory`,
                    duration: Date.now() - startTime
                };
            }

            await this.bot!.equip(item, 'hand');

            this.emit('itemEquipped', { item });
            return {
                success: true,
                item: item as any,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    /**
     * Craft an item using available materials
     */
    public async craftItem(itemType: string, options: CraftingOptions = {}): Promise<ItemInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();

        try {
            // Get item ID
            const itemId = parseInt(itemType) || this.bot!.registry.itemsByName[itemType]?.id || 0;
            const recipes = this.bot!.recipesFor(itemId, null, 1, null);
            
            if (recipes.length === 0) {
                return {
                    success: false,
                    error: `No recipes found for ${itemType}`,
                    duration: Date.now() - startTime
                };
            }

            const recipe = recipes[0];
            if (!recipe) {
                return {
                    success: false,
                    error: 'Invalid recipe',
                    duration: Date.now() - startTime
                };
            }

            const count = options.count || 1;

            // Check if we have enough materials - simplified check
            if (options.requireAll && recipe.delta) {
                // Basic check for required materials
                const hasEnoughMaterials = Object.entries(recipe.delta).every(([slotId, quantity]) => {
                    const requiredQuantity = Math.abs(quantity as unknown as number);
                    const availableQuantity = this.bot!.inventory.count(parseInt(slotId), null);
                    return availableQuantity >= requiredQuantity;
                });

                if (!hasEnoughMaterials) {
                    return {
                        success: false,
                        error: 'Not enough materials for crafting',
                        duration: Date.now() - startTime
                    };
                }
            }

            // Craft the item
            const craftingTable = options.table ? this.findCraftingTable() : undefined;
            await this.bot!.craft(recipe, count, craftingTable || undefined);

            this.emit('itemCrafted', { itemType, count, recipe });
            return {
                success: true,
                quantity: count,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    /**
     * Deposit items in a chest
     */
    public async depositItemsInChest(chestPosition: BlockPosition, itemType?: string): Promise<ItemInteractionResult> {
        if (!this.isReady()) {
            throw new Error('Bot is not ready for interaction');
        }

        const startTime = Date.now();

        try {
            const chestPos = new Vec3(chestPosition.x, chestPosition.y, chestPosition.z);
            const chestBlock = this.bot!.blockAt(chestPos);

            if (!chestBlock || !chestBlock.name.includes('chest')) {
                return {
                    success: false,
                    error: 'No chest found at the specified position',
                    duration: Date.now() - startTime
                };
            }

            // Open the chest
            const chest = await this.bot!.openChest(chestBlock);

            // Find items to deposit
            const itemsToDeposit = itemType 
                ? this.bot!.inventory.items().filter(item => 
                    item.name === itemType || item.displayName === itemType
                )
                : this.bot!.inventory.items();

            if (itemsToDeposit.length === 0) {
                chest.close();
                return {
                    success: false,
                    error: 'No items to deposit',
                    duration: Date.now() - startTime
                };
            }

            // Deposit items
            let totalDeposited = 0;
            for (const item of itemsToDeposit) {
                await chest.deposit(item.type, item.metadata, item.count);
                totalDeposited += item.count;
            }

            chest.close();

            this.emit('itemsDeposited', { chestPosition, itemType, count: totalDeposited });
            return {
                success: true,
                quantity: totalDeposited,
                duration: Date.now() - startTime
            };

        } catch (error) {
            return {
                success: false,
                error: error instanceof Error ? error.message : 'Unknown error',
                duration: Date.now() - startTime
            };
        }
    }

    // ===================
    // UTILITY METHODS
    // ===================

    /**
     * Get nearby entities based on filter criteria
     */
    public getNearbyEntities(filter: EntityFilter = {}): Entity[] {
        if (!this.bot) return [];

        const entities = Object.values(this.bot.entities);
        const maxDistance = filter.maxDistance || 16;

        return entities.filter(entity => {
            // Distance check
            const distance = entity.position.distanceTo(this.bot!.entity.position);
            if (distance > maxDistance) return false;

            // Type filter
            if (filter.type && entity.name !== filter.type) return false;

            // Name filter
            if (filter.name && entity.displayName !== filter.name) return false;

            // Category filters
            if (filter.hostile && !this.isHostileEntity(entity)) return false;
            if (filter.player && entity.type !== 'player') return false;
            if (filter.mob && entity.type === 'player') return false;
            if (filter.item && entity.name !== 'item') return false;

            return true;
        });
    }

    /**
     * Get nearby blocks of a specific type
     */
    public getNearbyBlocks(blockType: string, maxDistance: number = 16): Vec3[] {
        if (!this.bot) return [];

        const blocks: Vec3[] = [];
        const botPos = this.bot.entity.position;

        for (let x = -maxDistance; x <= maxDistance; x++) {
            for (let y = -maxDistance; y <= maxDistance; y++) {
                for (let z = -maxDistance; z <= maxDistance; z++) {
                    const pos = botPos.offset(x, y, z);
                    const block = this.bot.blockAt(pos);
                    
                    if (block && block.name === blockType) {
                        blocks.push(pos);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Auto-equip the best tool for breaking a block
     */
    private async autoEquipBestTool(block: Block): Promise<void> {
        if (!this.bot) return;

        const tools = this.bot.inventory.items().filter(item => 
            item.name.includes('pickaxe') || 
            item.name.includes('shovel') || 
            item.name.includes('axe') || 
            item.name.includes('hoe')
        );

        if (tools.length === 0) return;

        // Simple tool selection logic
        let bestTool = tools[0];
        
        // Select appropriate tool based on block type
        if (block.name.includes('stone') || block.name.includes('ore')) {
            bestTool = tools.find(tool => tool.name.includes('pickaxe')) || bestTool;
        } else if (block.name.includes('dirt') || block.name.includes('sand')) {
            bestTool = tools.find(tool => tool.name.includes('shovel')) || bestTool;
        } else if (block.name.includes('wood') || block.name.includes('log')) {
            bestTool = tools.find(tool => tool.name.includes('axe')) || bestTool;
        }

        if (bestTool) {
            try {
                await this.bot.equip(bestTool, 'hand');
            } catch (error) {
                // Ignore equip errors
            }
        }
    }

    /**
     * Find a crafting table block nearby
     */
    private findCraftingTable(): Block | null {
        if (!this.bot) return null;

        const tables = this.getNearbyBlocks('crafting_table', 5);
        if (tables.length === 0) return null;

        const tablePos = tables[0];
        if (tablePos) {
            return this.bot.blockAt(tablePos);
        }

        return null;
    }

    /**
     * Check if an entity is hostile
     */
    private isHostileEntity(entity: Entity): boolean {
        const hostileTypes = [
            'zombie', 'skeleton', 'spider', 'creeper', 'enderman', 
            'witch', 'blaze', 'ghast', 'slime', 'magma_cube',
            'phantom', 'husk', 'stray', 'wither_skeleton'
        ];

        return hostileTypes.includes(entity.name || '');
    }

    // ===================
    // PRIVATE MOVEMENT METHODS
    // ===================

    /**
     * Execute a movement command
     */
    private async executeMovement(movement: MovementCommand): Promise<PathfindingResult> {
        return new Promise((resolve, reject) => {
            if (!this.bot || !this.movements) {
                reject(new Error('Bot not ready'));
                return;
            }

            // Add to queue if bot is currently moving
            if (this.isMoving && this.movementQueue.length < this.options.maxMovementQueue!) {
                this.movementQueue.push(movement);
                resolve({ success: true, distance: 0 });
                return;
            }

            // Clear any existing movement
            this.stopMoving();

            this.currentMovement = movement;
            this.isMoving = true;
            movement.status = 'executing';
            
            const startTime = Date.now();
            const startPos = this.bot.entity.position.clone();

            // Setup timeout
            const timeout = movement.options?.timeout || this.options.movementTimeout!;
            this.movementTimeout = setTimeout(() => {
                this.handleMovementTimeout(movement);
                reject(new Error('Movement timeout'));
            }, timeout);

            // Configure pathfinder based on options
            this.configurePathfinder(movement.options);

            // Execute movement based on type
            let goal: any;
            
            try {
                switch (movement.type) {
                    case 'moveTo':
                        goal = new goals.GoalNear(movement.target!.x, movement.target!.y, movement.target!.z, 1);
                        break;
                    case 'moveToEntity':
                        const entity = this.bot.entities[movement.entityId!];
                        if (!entity) {
                            throw new Error('Entity not found');
                        }
                        const range = movement.options?.range || 2;
                        goal = new goals.GoalFollow(entity, range);
                        break;
                    case 'followEntity':
                        const followEntity = this.bot.entities[movement.entityId!];
                        if (!followEntity) {
                            throw new Error('Entity not found');
                        }
                        const followRange = movement.options?.range || 3;
                        goal = new goals.GoalFollow(followEntity, followRange);
                        break;
                    default:
                        throw new Error('Unknown movement type');
                }

                // Set up pathfinding event handlers
                const onGoalReached = () => {
                    if (this.cleanup) {
                        this.cleanup();
                    }
                    const endTime = Date.now();
                    const distance = startPos.distanceTo(this.bot!.entity.position);
                    
                    movement.status = 'completed';
                    this.completeMovement();
                    
                    resolve({
                        success: true,
                        distance,
                        duration: endTime - startTime
                    });
                };

                const onGoalFailed = (error: any) => {
                    if (this.cleanup) {
                        this.cleanup();
                    }
                    movement.status = 'failed';
                    this.completeMovement();
                    
                    reject(new Error(`Pathfinding failed: ${error?.message || 'Unknown error'}`));
                };

                const cleanup = () => {
                    this.bot!.removeListener('goal_reached', onGoalReached);
                    this.bot!.removeListener('path_update', onGoalFailed);
                    if (this.movementTimeout) {
                        clearTimeout(this.movementTimeout);
                        this.movementTimeout = null;
                    }
                };

                this.cleanup = cleanup;

                this.bot.on('goal_reached', onGoalReached);
                this.bot.on('path_update', onGoalFailed);

                // Start pathfinding
                this.pathfindingInProgress = true;
                this.bot.pathfinder.setGoal(goal);
                
                this.emit('movementStarted', {
                    movementId: movement.id,
                    type: movement.type,
                    target: movement.target,
                    entityId: movement.entityId
                });

            } catch (error) {
                if (this.cleanup) {
                    this.cleanup();
                }
                movement.status = 'failed';
                this.completeMovement();
                reject(error);
            }
        });
    }

    private cleanup: (() => void) | null = null;

    /**
     * Configure pathfinder based on movement options
     */
    private configurePathfinder(options: MovementOptions = {}): void {
        if (!this.movements) return;

        // Configure movement settings
        this.movements.allowSprinting = options.allowSprint !== false;
        this.movements.allowParkour = options.allowParkour !== false;
        this.movements.canDig = options.allowDigging !== false;
        
        // Configure block restrictions
        if (options.avoidWater && this.bot?.registry.blocksByName.water) {
            this.movements.blocksToAvoid.add(this.bot.registry.blocksByName.water.id);
        }
        
        if (options.avoidLava && this.bot?.registry.blocksByName.lava) {
            this.movements.blocksToAvoid.add(this.bot.registry.blocksByName.lava.id);
        }
    }

    /**
     * Complete current movement and process queue
     */
    private completeMovement(): void {
        this.isMoving = false;
        this.currentMovement = null;
        this.pathfindingInProgress = false;

        // Process next movement in queue
        if (this.movementQueue.length > 0) {
            const nextMovement = this.movementQueue.shift()!;
            this.executeMovement(nextMovement);
        }
    }

    /**
     * Handle movement timeout
     */
    private handleMovementTimeout(movement: MovementCommand): void {
        this.stopMoving();
        movement.status = 'failed';
        this.emit('movementTimeout', { movementId: movement.id });
    }

    /**
     * Generate unique movement ID
     */
    private generateMovementId(): string {
        return `move_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    /**
     * Clear movement queue
     */
    private clearMovementQueue(): void {
        this.movementQueue.forEach(movement => {
            movement.status = 'cancelled';
        });
        this.movementQueue = [];
    }

    /**
     * Wait for bot to spawn in the world
     */
    private waitForConnection(): Promise<void> {
        return new Promise((resolve, reject) => {
            if (!this.bot) {
                reject(new Error('Bot instance not available'));
                return;
            }

            const onSpawn = () => {
                this.bot!.removeListener('spawn', onSpawn);
                this.bot!.removeListener('error', onError);
                this.bot!.removeListener('end', onEnd);
                resolve();
            };

            const onError = (error: Error) => {
                this.bot!.removeListener('spawn', onSpawn);
                this.bot!.removeListener('error', onError);
                this.bot!.removeListener('end', onEnd);
                reject(error);
            };

            const onEnd = (reason: string) => {
                this.bot!.removeListener('spawn', onSpawn);
                this.bot!.removeListener('error', onError);
                this.bot!.removeListener('end', onEnd);
                reject(new Error(`Connection ended: ${reason}`));
            };

            this.bot.on('spawn', onSpawn);
            this.bot.on('error', onError);
            this.bot.on('end', onEnd);
        });
    }

    /**
     * Setup all event listeners for the bot
     */
    private setupEventListeners(): void {
        if (!this.bot) return;

        // Connection events
        this.bot.on('spawn', this.handleBotSpawn);
        this.bot.on('end', this.handleBotEnd);
        this.bot.on('error', this.handleBotError);
        this.bot.on('kicked', this.handleBotKicked);
        this.bot.on('death', this.handleBotDeath);

        // Status events
        this.bot.on('health', this.handleBotHealth);
        this.bot.on('experience', this.handleBotExperience);
        
        // World events
        this.bot.on('chat', this.handleBotChat);
        this.bot.on('rain', this.handleBotWeather);
        this.bot.on('time', this.handleBotTime);
        
        // Inventory events
        this.bot.on('windowOpen', this.handleBotWindowOpen);
        this.bot.on('windowClose', this.handleBotWindowClose);
        // Use playerCollect for inventory tracking
        this.bot.on('playerCollect', this.handleBotInventoryUpdate);
        
        // Pathfinding events
        this.bot.on('goal_reached', this.handlePathfindingGoal);
        this.bot.on('path_update', this.handlePathfindingInterrupted);
        this.bot.on('goal_updated', this.handlePathfindingTimeout);
        this.bot.on('path_stop', this.handlePathfindingError);
    }

    /**
     * Handle bot spawn event
     */
    private handleBotSpawn(): void {
        if (!this.bot) return;

        // Initialize pathfinder
        this.movements = new Movements(this.bot);
        this.movements.scafoldingBlocks = [];
        this.movements.canDig = true;
        this.movements.allow1by1towers = true;
        this.movements.allowSprinting = true;
        this.movements.allowParkour = false;
        
        // Add bedrock to unbreakable blocks if available
        if (this.bot.registry.blocksByName.bedrock) {
            this.movements.blocksCantBreak.add(this.bot.registry.blocksByName.bedrock.id);
        }
        
        this.bot.pathfinder.setMovements(this.movements);

        this.emit('spawn', {
            position: this.bot.entity.position,
            dimension: this.bot.game.dimension,
            gameMode: this.bot.game.gameMode
        });
    }

    /**
     * Handle bot end event
     */
    private handleBotEnd(reason: string): void {
        this.isConnected = false;
        this.movements = null;
        this.stopStatusReporting();
        this.clearMovementQueue();
        this.stopMoving();
        
        this.emit('end', reason);
        
        // Auto-retry connection if not intentional disconnect
        if (this.retryCount < this.options.maxRetries!) {
            this.retryCount++;
            this.emit('retrying', this.retryCount);
            
            setTimeout(() => {
                this.connect().catch(error => {
                    this.emit('error', error);
                });
            }, this.options.retryDelay);
        } else {
            this.emit('maxRetriesReached');
        }
    }

    /**
     * Handle bot error event
     */
    private handleBotError(error: Error): void {
        this.emit('error', error);
    }

    /**
     * Handle bot death event
     */
    private handleBotDeath(): void {
        this.stopMoving();
        this.followingEntity = null;
        
        this.emit('death', {
            position: this.bot?.entity.position,
            cause: 'Unknown'
        });
    }

    /**
     * Handle bot kicked event
     */
    private handleBotKicked(reason: string): void {
        this.emit('kicked', reason);
    }

    /**
     * Handle bot health change
     */
    private handleBotHealth(): void {
        if (!this.bot) return;
        
        this.emit('healthChange', {
            health: this.bot.health,
            food: this.bot.food
        });
    }

    /**
     * Handle bot experience change
     */
    private handleBotExperience(): void {
        if (!this.bot) return;
        
        this.emit('experienceChange', {
            points: this.bot.experience.points,
            level: this.bot.experience.level
        });
    }

    /**
     * Handle chat messages
     */
    private handleBotChat(username: string, message: string): void {
        this.emit('chat', { username, message });
    }

    /**
     * Handle weather changes
     */
    private handleBotWeather(): void {
        if (!this.bot) return;
        
        this.emit('weatherChange', {
            isRaining: this.bot.isRaining,
            thunderState: this.bot.thunderState
        });
    }

    /**
     * Handle time changes
     */
    private handleBotTime(): void {
        if (!this.bot) return;
        
        this.emit('timeChange', {
            timeOfDay: this.bot.time.timeOfDay,
            age: this.bot.time.age
        });
    }

    /**
     * Handle window open events
     */
    private handleBotWindowOpen(window: any): void {
        this.emit('windowOpen', { window });
    }

    /**
     * Handle window close events
     */
    private handleBotWindowClose(): void {
        this.emit('windowClose');
    }

    /**
     * Handle inventory update events
     */
    private handleBotInventoryUpdate(): void {
        if (!this.bot) return;
        
        this.emit('inventoryUpdate', {
            slots: this.bot.inventory.slots,
            usedSlots: this.bot.inventory.slots.filter(slot => slot !== null).length,
            totalSlots: this.bot.inventory.slots.length
        });
    }

    // ===================
    // PATHFINDING EVENT HANDLERS
    // ===================

    /**
     * Handle pathfinding goal reached
     */
    private handlePathfindingGoal(): void {
        if (this.followingEntity) {
            // Continue following if we're in follow mode
            const entity = this.bot!.entities[this.followingEntity];
            if (entity) {
                const distance = this.bot!.entity.position.distanceTo(entity.position);
                this.emit('followingUpdate', {
                    entityId: this.followingEntity,
                    distance,
                    position: entity.position
                });
            }
        } else {
            this.emit('movementCompleted', {
                movementId: this.currentMovement?.id,
                finalPosition: this.bot?.entity.position
            });
        }
    }

    /**
     * Handle pathfinding interrupted
     */
    private handlePathfindingInterrupted(): void {
        this.emit('pathfindingUpdate', {
            movementId: this.currentMovement?.id,
            status: 'recalculating'
        });
    }

    /**
     * Handle pathfinding timeout
     */
    private handlePathfindingTimeout(): void {
        this.emit('pathfindingUpdate', {
            movementId: this.currentMovement?.id,
            status: 'goal_updated'
        });
    }

    /**
     * Handle pathfinding error
     */
    private handlePathfindingError(): void {
        this.emit('pathfindingUpdate', {
            movementId: this.currentMovement?.id,
            status: 'stopped'
        });
    }

    /**
     * Handle connection timeout
     */
    private handleConnectionTimeout(): void {
        this.isConnecting = false;
        const error = new Error('Connection timeout');
        this.emit('error', error);
    }

    /**
     * Handle connection errors
     */
    private handleConnectionError(error: any): void {
        this.isConnecting = false;
        this.emit('error', error);
    }

    /**
     * Start periodic status reporting
     */
    private startStatusReporting(): void {
        if (this.statusInterval) {
            clearInterval(this.statusInterval);
        }

        this.statusInterval = setInterval(() => {
            const status = this.getStatus();
            if (status) {
                this.emit('statusUpdate', status);
            }
        }, 5000); // Every 5 seconds
    }

    /**
     * Stop periodic status reporting
     */
    private stopStatusReporting(): void {
        if (this.statusInterval) {
            clearInterval(this.statusInterval);
            this.statusInterval = null;
        }
    }

    /**
     * WebSocket functionality moved to index.ts - method commented out
     */
    /*
    private initializeCommandRegistry(): void {
        // All command registry code moved to index.ts
    }

    private initializeWebSocket(): void {
        // WebSocket initialization moved to index.ts
    }

    private reconnectWebSocket(): void {
        // WebSocket reconnection moved to index.ts
    }

    private handleWebSocketCommand(command: any): void {
        // WebSocket command handling moved to index.ts
    }

    private sendWebSocketMessage(message: any): void {
        // WebSocket message sending moved to index.ts
    }

    public connectWebSocket(url: string): void {
        // WebSocket connection moved to index.ts
    }

    public disconnectWebSocket(): void {
        // WebSocket disconnection moved to index.ts
    }

    public isWebSocketConnected(): boolean {
        return false; // WebSocket functionality moved to index.ts
    }

    public registerCommandHandler(commandType: string, handler: any): void {
        // Command handler registration moved to index.ts
    }

    public unregisterCommandHandler(commandType: string): void {
        // Command handler unregistration moved to index.ts
    }

    public getRegisteredCommands(): string[] {
        return []; // Command registry moved to index.ts
    }

    public sendCommandResponse(response: any): void {
        // Command response sending moved to index.ts
    }
    */

    /**
     * Cleanup resources
     */
    public destroy(): void {
        this.disconnect();
        this.removeAllListeners();
        
        // Stop event reporting
        this.stopEventReporting();
        
        // WebSocket cleanup moved to index.ts
        // if (this.webSocket) {
        //     this.webSocket.close();
        // }
        // if (this.webSocketReconnectTimeout) {
        //     clearTimeout(this.webSocketReconnectTimeout);
        // }
        
        // Log shutdown
        this.logInfo('system', 'MinecraftAIBody destroyed and resources cleaned up');
    }

    /**
     * Get the inventory as an array of items
     */
    public getInventory(): InventoryItem[] {
        if (!this.bot) return [];
        
        const items: InventoryItem[] = [];
        const inventory = this.bot.inventory;
        
        inventory.slots.forEach((slot, index) => {
            if (slot !== null) {
                items.push({
                    type: slot.type.toString(),
                    name: slot.name,
                    count: slot.count,
                    slot: index,
                    metadata: slot.metadata
                });
            }
        });
        
        return items;
    }

    // ===================================================================
    // EVENT REPORTING AND LOGGING SYSTEM
    // ===================================================================

    /**
     * Start periodic event reporting
     */
    private startEventReporting(): void {
        if (this.reportingInterval) {
            clearInterval(this.reportingInterval);
        }

        this.reportingInterval = setInterval(() => {
            this.generatePeriodicReport();
            this.cleanupOldLogs();
        }, this.loggingConfig.reportingInterval);
    }

    /**
     * Stop event reporting
     */
    private stopEventReporting(): void {
        if (this.reportingInterval) {
            clearInterval(this.reportingInterval);
            this.reportingInterval = null;
        }
    }

    /**
     * Log a message with specified level and category
     */
    public log(level: LogLevel, category: string, message: string, data?: any): void {
        const entry: LogEntry = {
            timestamp: Date.now(),
            level,
            category,
            message,
            data,
            source: 'MinecraftAIBody'
        };

        // Add to log entries
        this.logEntries.push(entry);

        // Trim old entries if exceeding max
        if (this.logEntries.length > this.loggingConfig.maxLogEntries) {
            this.logEntries = this.logEntries.slice(-this.loggingConfig.maxLogEntries);
        }

        // Output based on configuration
        if (this.loggingConfig.enableConsole && this.shouldLog(level)) {
            this.outputToConsole(entry);
        }

        // WebSocket logging moved to index.ts
        // if (this.loggingConfig.enableWebSocket && this.webSocketConnected) {
        //     this.sendLogToWebSocket(entry);
        // }

        // Emit log event
        this.emit('log', entry);
    }

    /**
     * Log debug message
     */
    public logDebug(category: string, message: string, data?: any): void {
        this.log('debug', category, message, data);
    }

    /**
     * Log info message
     */
    public logInfo(category: string, message: string, data?: any): void {
        this.log('info', category, message, data);
    }

    /**
     * Log warning message
     */
    public logWarn(category: string, message: string, data?: any): void {
        this.log('warn', category, message, data);
    }

    /**
     * Log error message
     */
    public logError(category: string, message: string, data?: any): void {
        this.log('error', category, message, data);
    }

    /**
     * Report an event
     */
    public reportEvent(type: EventType, data: any, severity: LogLevel = 'info'): void {
        const eventReport: EventReport = {
            id: this.generateEventId(),
            timestamp: Date.now(),
            type,
            data,
            severity
        };

        this.eventReports.push(eventReport);

        // Log the event
        this.log(severity, 'event', `Event: ${type}`, data);

        // WebSocket event reporting moved to index.ts
        // if (this.webSocketConnected) {
        //     this.sendEventToWebSocket(eventReport);
        // }

        // Emit event
        this.emit('event', eventReport);
        this.emit(type, data);
    }

    /**
     * Update logging configuration
     */
    public updateLoggingConfig(config: Partial<LoggingConfig>): void {
        this.loggingConfig = { ...this.loggingConfig, ...config };
        
        // Restart reporting with new interval if changed
        if (config.reportingInterval) {
            this.startEventReporting();
        }

        this.logInfo('system', 'Logging configuration updated', config);
    }

    /**
     * Get current logging configuration
     */
    public getLoggingConfig(): LoggingConfig {
        return { ...this.loggingConfig };
    }

    /**
     * Get recent log entries
     */
    public getLogEntries(count?: number, level?: LogLevel): LogEntry[] {
        let entries = this.logEntries;

        if (level) {
            entries = entries.filter(entry => entry.level === level);
        }

        if (count) {
            return entries.slice(-count);
        }

        return entries.slice();
    }

    /**
     * Get recent event reports
     */
    public getEventReports(count?: number, type?: EventType): EventReport[] {
        let reports = this.eventReports;

        if (type) {
            reports = reports.filter(report => report.type === type);
        }

        if (count) {
            return reports.slice(-count);
        }

        return reports.slice();
    }

    /**
     * Clear log entries
     */
    public clearLogs(): void {
        this.logEntries = [];
        this.logInfo('system', 'Log entries cleared');
    }

    /**
     * Clear event reports
     */
    public clearEvents(): void {
        this.eventReports = [];
        this.logInfo('system', 'Event reports cleared');
    }

    // Private logging helper methods

    /**
     * Check if message should be logged based on level
     */
    private shouldLog(level: LogLevel): boolean {
        const levels = ['debug', 'info', 'warn', 'error'];
        const configLevel = levels.indexOf(this.loggingConfig.level);
        const messageLevel = levels.indexOf(level);
        return messageLevel >= configLevel;
    }

    /**
     * Output log entry to console
     */
    private outputToConsole(entry: LogEntry): void {
        const timestamp = new Date(entry.timestamp).toISOString();
        const prefix = `[${timestamp}] [${entry.level.toUpperCase()}] [${entry.category}]`;
        
        switch (entry.level) {
            case 'debug':
                console.log(`${prefix} ${entry.message}`, entry.data || '');
                break;
            case 'info':
                console.info(`${prefix} ${entry.message}`, entry.data || '');
                break;
            case 'warn':
                console.warn(`${prefix} ${entry.message}`, entry.data || '');
                break;
            case 'error':
                console.error(`${prefix} ${entry.message}`, entry.data || '');
                break;
        }
    }

    /**
     * Send log entry to WebSocket
     */
    private sendLogToWebSocket(entry: LogEntry): void {
        // WebSocket functionality moved to index.ts
        // if (this.webSocket && this.webSocket.readyState === WebSocket.OPEN) {
        //     this.webSocket.send(JSON.stringify({
        //         type: 'log',
        //         data: entry
        //     }));
        // }
    }

    /**
     * Send event report to WebSocket
     */
    private sendEventToWebSocket(eventReport: EventReport): void {
        // WebSocket functionality moved to index.ts
        // if (this.webSocket && this.webSocket.readyState === WebSocket.OPEN) {
        //     this.webSocket.send(JSON.stringify({
        //         type: 'event',
        //         data: eventReport
        //     }));
        // }
    }

    /**
     * Generate unique event ID
     */
    private generateEventId(): string {
        return `event_${++this.eventIdCounter}_${Date.now()}`;
    }

    /**
     * Generate periodic status report
     */
    private generatePeriodicReport(): void {
        if (!this.bot || !this.isConnected) return;

        const status = this.getStatus();
        if (status) {
            this.reportEvent('bot_status_change', status, 'debug');
        }

        // Check for inventory changes
        this.checkInventoryChanges();

        // Check for health changes
        this.checkHealthChanges();

        // Check for nearby entities
        this.checkNearbyEntities();
    }

    /**
     * Check for inventory changes and report them
     */
    private checkInventoryChanges(): void {
        if (!this.bot) return;

        const currentInventory = new Map<string, number>();
        
        // Build current inventory map
        this.bot.inventory.items().forEach(item => {
            const key = `${item.type}:${item.name}`;
            currentInventory.set(key, (currentInventory.get(key) || 0) + item.count);
        });

        // Compare with last known inventory
        const allKeys = new Set([...this.lastKnownInventory.keys(), ...currentInventory.keys()]);
        
        for (const key of allKeys) {
            const [type, name] = key.split(':');
            const previousCount = this.lastKnownInventory.get(key) || 0;
            const newCount = currentInventory.get(key) || 0;

            if (previousCount !== newCount && type && name) {
                const change: InventoryChangeEvent = {
                    action: newCount > previousCount ? 'added' : 'removed',
                    item: {
                        type,
                        name,
                        count: Math.abs(newCount - previousCount),
                        slot: -1 // Not tracking specific slot in this context
                    },
                    previousCount,
                    newCount
                };

                this.reportEvent('inventory_change', change, 'info');
            }
        }

        this.lastKnownInventory = currentInventory;
    }

    /**
     * Check for health changes and report them
     */
    private checkHealthChanges(): void {
        if (!this.bot) return;

        const currentHealth = this.bot.health;
        const currentFood = this.bot.food;

        if (currentHealth !== this.lastKnownHealth || currentFood !== this.lastKnownFood) {
            const change: HealthChangeEvent = {
                previousHealth: this.lastKnownHealth,
                newHealth: currentHealth,
                previousFood: this.lastKnownFood,
                newFood: currentFood,
                isHealing: currentHealth > this.lastKnownHealth
            };

            this.reportEvent('health_change', change, currentHealth < this.lastKnownHealth ? 'warn' : 'info');
            
            this.lastKnownHealth = currentHealth;
            this.lastKnownFood = currentFood;
        }
    }

    /**
     * Check for nearby entities and report sightings
     */
    private checkNearbyEntities(): void {
        if (!this.bot) return;

        const currentEntities = new Map<string, EntitySightingEvent>();
        const detectionRange = 16; // blocks

        // Get all nearby entities
        Object.values(this.bot.entities).forEach(entity => {
            if (entity.id === this.bot!.entity.id) return; // Skip self

            const distance = this.bot!.entity.position.distanceTo(entity.position);
            if (distance <= detectionRange) {
                const sighting: EntitySightingEvent = {
                    entityId: entity.id.toString(),
                    entityType: entity.name || 'unknown',
                    position: {
                        x: entity.position.x,
                        y: entity.position.y,
                        z: entity.position.z
                    },
                    distance,
                    isHostile: this.isHostileEntity(entity),
                    isPlayer: entity.type === 'player',
                    action: 'entered_range'
                };

                // Add entityName only if it exists
                if (entity.username) {
                    sighting.entityName = entity.username;
                }

                currentEntities.set(entity.id.toString(), sighting);

                // Report if new entity
                if (!this.nearbyEntitiesCache.has(entity.id.toString())) {
                    this.reportEvent('entity_sighted', sighting, sighting.isHostile ? 'warn' : 'debug');
                }
            }
        });

        // Check for entities that left range
        for (const [entityId, lastSighting] of this.nearbyEntitiesCache) {
            if (!currentEntities.has(entityId)) {
                const leftSighting = { ...lastSighting, action: 'left_range' as const };
                this.reportEvent('entity_sighted', leftSighting, 'debug');
            }
        }

        this.nearbyEntitiesCache = currentEntities;
    }

    /**
     * Clean up old log entries and event reports
     */
    private cleanupOldLogs(): void {
        const maxAge = 24 * 60 * 60 * 1000; // 24 hours
        const cutoff = Date.now() - maxAge;

        this.logEntries = this.logEntries.filter(entry => entry.timestamp > cutoff);
        this.eventReports = this.eventReports.filter(report => report.timestamp > cutoff);
    }
} 