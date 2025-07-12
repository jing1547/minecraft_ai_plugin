import { Bot, createBot, BotOptions } from 'mineflayer';
import { pathfinder, Movements, goals } from 'mineflayer-pathfinder';
import { plugin as pvpPlugin } from 'mineflayer-pvp';
import { EventEmitter } from 'events';
import { Vec3 } from 'vec3';

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

    constructor(options: MinecraftAIBodyOptions = {}) {
        super();
        
        // Create options object properly handling undefined values
        this.options = {
            host: options.host || 'localhost',
            port: options.port || 25565,
            username: options.username || 'AIBot',
            auth: options.auth || 'offline',
            version: options.version || '1.20.1',
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
            queuedMovements: this.movementQueue.length
        };

        // Add optional fields only if they exist
        if (this.currentMovement?.target) {
            status.currentTarget = this.currentMovement.target;
        }
        
        if (this.followingEntity) {
            status.followingEntity = this.followingEntity;
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
     * Check if path is safe (no lava, deep water, etc.)
     */
    public isPathSafe(start: Vec3, end: Vec3): boolean {
        if (!this.bot) return false;
        
        // Basic safety check - can be enhanced with more sophisticated logic
        const startBlock = this.bot.blockAt(start);
        const endBlock = this.bot.blockAt(end);
        
        if (!startBlock || !endBlock) return false;
        
        // Check for lava
        if (startBlock.name.includes('lava') || endBlock.name.includes('lava')) {
            return false;
        }
        
        // Check for void
        if (start.y < 0 || end.y < 0) {
            return false;
        }
        
        return true;
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
     * Cleanup resources
     */
    public destroy(): void {
        this.disconnect();
        this.removeAllListeners();
    }
} 