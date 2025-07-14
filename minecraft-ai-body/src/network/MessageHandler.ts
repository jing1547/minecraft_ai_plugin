import { BaseMessage, CommandMessage, ResponseMessage, EventMessage, StateMessage, MessageType, CommandAction, Priority } from '../protocol/types';
import { MinecraftAIBody } from '../bot/MinecraftAIBody';
import { Vec3 } from 'vec3';

/**
 * 메시지 핸들러 인터페이스
 */
export interface MessageHandler {
  /**
   * 메시지 처리
   * @param message 처리할 메시지
   * @returns 처리 결과 (Promise)
   */
  handleMessage(message: BaseMessage): Promise<void>;

  /**
   * 핸들러가 처리할 수 있는 메시지 타입인지 확인
   * @param message 확인할 메시지
   * @returns 처리 가능 여부
   */
  canHandle(message: BaseMessage): boolean;

  /**
   * 핸들러 이름 반환
   */
  getName(): string;
}

/**
 * 핸들러 컨텍스트 인터페이스
 */
export interface HandlerContext {
  /**
   * 응답 메시지 전송
   * @param response 응답 메시지
   */
  sendResponse(response: ResponseMessage): Promise<void>;

  /**
   * 이벤트 발생
   * @param event 이벤트 메시지
   */
  emitEvent(event: EventMessage): Promise<void>;

  /**
   * 로그 출력
   * @param message 로그 메시지
   */
  log(message: string): void;

  /**
   * 오류 로그 출력
   * @param error 오류
   */
  logError(error: Error): void;

  /**
   * 봇 인스턴스 가져오기
   */
  getBot(): MinecraftAIBody | null;
}

/**
 * 추상 메시지 핸들러
 */
export abstract class AbstractMessageHandler implements MessageHandler {
  protected context: HandlerContext;
  protected name: string;

  constructor(context: HandlerContext, name: string) {
    this.context = context;
    this.name = name;
  }

  abstract handleMessage(message: BaseMessage): Promise<void>;
  abstract canHandle(message: BaseMessage): boolean;

  getName(): string {
    return this.name;
  }

  protected log(message: string): void {
    this.context.log(message);
  }

  protected logError(error: Error): void {
    this.context.logError(error);
  }

  protected getBot(): MinecraftAIBody | null {
    return this.context.getBot();
  }
}

/**
 * 명령 메시지 핸들러
 */
export class CommandMessageHandler extends AbstractMessageHandler {
  private commandHandlers: Map<string, (command: CommandMessage) => Promise<void>> = new Map();

  constructor(context: HandlerContext) {
    super(context, 'CommandMessageHandler');
    this.initializeCommandHandlers();
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.COMMAND;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    try {
      const command = message as CommandMessage;
      this.log(`Processing command: ${command.payload.action}`);

      const handler = this.commandHandlers.get(command.payload.action);
      if (handler) {
        await handler(command);
      } else {
        await this.sendErrorResponse(command, new Error(`Unknown command: ${command.payload.action}`));
      }
    } catch (error) {
      this.logError(error as Error);
      if ('id' in message) {
        await this.sendErrorResponse(message as CommandMessage, error as Error);
      }
    }
  }

  private initializeCommandHandlers(): void {
    this.commandHandlers.set('moveTo', this.handleMoveToCommand.bind(this));
    this.commandHandlers.set('follow', this.handleFollowCommand.bind(this));
    this.commandHandlers.set('stop', this.handleStopCommand.bind(this));
    this.commandHandlers.set('attack', this.handleAttackCommand.bind(this));
    this.commandHandlers.set('use', this.handleUseCommand.bind(this));
    this.commandHandlers.set('break', this.handleBreakCommand.bind(this));
    this.commandHandlers.set('place', this.handlePlaceCommand.bind(this));
    this.commandHandlers.set('equipItem', this.handleEquipItemCommand.bind(this));
    this.commandHandlers.set('craftItem', this.handleCraftItemCommand.bind(this));
    this.commandHandlers.set('dropItem', this.handleDropItemCommand.bind(this));
    this.commandHandlers.set('chat', this.handleChatCommand.bind(this));
    this.commandHandlers.set('whisper', this.handleWhisperCommand.bind(this));
    this.commandHandlers.set('disconnect', this.handleDisconnectCommand.bind(this));
    this.commandHandlers.set('status', this.handleStatusCommand.bind(this));
    this.commandHandlers.set('debug', this.handleDebugCommand.bind(this));
  }

  private async handleMoveToCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { x, y, z } = command.payload.parameters;
      if (typeof x !== 'number' || typeof y !== 'number' || typeof z !== 'number') {
        throw new Error('Invalid coordinates provided');
      }

      this.log(`Moving bot to position: (${x}, ${y}, ${z})`);
      const result = await bot.moveTo(x, y, z, command.payload.parameters.options || {});
      
      await this.sendSuccessResponse(command, {
        success: result.success,
        position: { x, y, z },
        distance: result.distance,
        duration: result.duration,
        path: result.path
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleFollowCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { entityId } = command.payload.parameters;
      if (!entityId) {
        throw new Error('Entity ID is required for follow command');
      }

      this.log(`Bot following entity: ${entityId}`);
      await bot.followEntity(entityId, command.payload.parameters.options || {});
      
      await this.sendSuccessResponse(command, {
        success: true,
        followingEntity: entityId,
        message: `Now following entity: ${entityId}`
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleStopCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      this.log('Stopping bot movement');
      bot.stopMoving();
      bot.stopFollowing();
      
      await this.sendSuccessResponse(command, {
        success: true,
        message: 'Bot movement stopped',
        queueCleared: true
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleAttackCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { entityId } = command.payload.parameters;
      if (!entityId) {
        throw new Error('Entity ID is required for attack command');
      }

      this.log(`Bot attacking entity: ${entityId}`);
      const result = await bot.attackEntity(entityId);
      
      await this.sendSuccessResponse(command, {
        success: result.success,
        entityId: entityId,
        damage: result.result,
        duration: result.duration
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleUseCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { itemType } = command.payload.parameters;
      if (!itemType) {
        throw new Error('Item type is required for use command');
      }

      this.log(`Bot using item: ${itemType}`);
      const result = await bot.useItem(itemType);
      
      await this.sendSuccessResponse(command, {
        success: result.success,
        itemType: itemType,
        quantity: result.quantity,
        duration: result.duration
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleBreakCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { x, y, z } = command.payload.parameters;
      if (typeof x !== 'number' || typeof y !== 'number' || typeof z !== 'number') {
        throw new Error('Invalid block coordinates provided');
      }

      this.log(`Bot breaking block at: (${x}, ${y}, ${z})`);
      const result = await bot.breakBlock({ x, y, z });
      
      await this.sendSuccessResponse(command, {
        success: result.success,
        position: { x, y, z },
        blockType: result.blockType,
        duration: result.duration
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handlePlaceCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { x, y, z, blockType } = command.payload.parameters;
      if (typeof x !== 'number' || typeof y !== 'number' || typeof z !== 'number' || !blockType) {
        throw new Error('Invalid parameters for place command');
      }

      this.log(`Bot placing block ${blockType} at: (${x}, ${y}, ${z})`);
      const result = await bot.placeBlock({ x, y, z }, blockType);
      
      await this.sendSuccessResponse(command, {
        success: result.success,
        position: { x, y, z },
        blockType: blockType,
        duration: result.duration
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleEquipItemCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { itemType } = command.payload.parameters;
      if (!itemType) {
        throw new Error('Item type is required for equip command');
      }

      this.log(`Bot equipping item: ${itemType}`);
      const result = await bot.equipItem(itemType);
      
      await this.sendSuccessResponse(command, {
        success: result.success,
        itemType: itemType,
        duration: result.duration
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleCraftItemCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      const { itemType, count = 1 } = command.payload.parameters;
      if (!itemType) {
        throw new Error('Item type is required for craft command');
      }

      this.log(`Bot crafting item: ${itemType} (count: ${count})`);
      const result = await bot.craftItem(itemType, { count });
      
      await this.sendSuccessResponse(command, {
        success: result.success,
        itemType: itemType,
        quantity: result.quantity,
        duration: result.duration
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleDropItemCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      const botInstance = bot?.getBot();
      if (!bot || !botInstance) {
        throw new Error('Bot not available');
      }

      const { itemType, count = 1 } = command.payload.parameters;
      if (!itemType) {
        throw new Error('Item type is required for drop command');
      }

      this.log(`Bot dropping item: ${itemType} (count: ${count})`);
      
      // Find item in inventory
      const item = bot.findItemInInventory(itemType);
      if (!item) {
        throw new Error(`Item ${itemType} not found in inventory`);
      }

      // Drop the item
      const inventorySlot = botInstance.inventory.slots[item.slot];
      if (inventorySlot) {
        await botInstance.tossStack(inventorySlot);
      } else {
        throw new Error(`No item found in slot ${item.slot}`);
      }
      
      await this.sendSuccessResponse(command, {
        success: true,
        itemType: itemType,
        quantity: count,
        message: `Dropped ${count} ${itemType}`
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleChatCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      const botInstance = bot?.getBot();
      if (!bot || !botInstance) {
        throw new Error('Bot not available');
      }

      const { message } = command.payload.parameters;
      if (!message) {
        throw new Error('Message is required for chat command');
      }

      this.log(`Bot sending chat message: ${message}`);
      botInstance.chat(message);
      
      await this.sendSuccessResponse(command, {
        success: true,
        message: `Chat message sent: ${message}`
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleWhisperCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      const botInstance = bot?.getBot();
      if (!bot || !botInstance) {
        throw new Error('Bot not available');
      }

      const { player, message } = command.payload.parameters;
      if (!player || !message) {
        throw new Error('Player and message are required for whisper command');
      }

      this.log(`Bot sending whisper to ${player}: ${message}`);
      botInstance.whisper(player, message);
      
      await this.sendSuccessResponse(command, {
        success: true,
        player: player,
        message: `Whisper sent to ${player}: ${message}`
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleDisconnectCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      this.log('Bot disconnecting from server');
      await bot.disconnect();
      
      await this.sendSuccessResponse(command, {
        success: true,
        message: 'Bot disconnected from server'
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleStatusCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      this.log('Getting bot status information');
      const status = bot.getStatus();
      const inventory = bot.getInventory();
      
      await this.sendSuccessResponse(command, {
        status: status,
        inventory: inventory,
        nearbyEntities: bot.getNearbyEntities().length,
        isMoving: bot.isCurrentlyMoving(),
        queueLength: bot.getQueueLength()
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async handleDebugCommand(command: CommandMessage): Promise<void> {
    try {
      const bot = this.getBot();
      if (!bot) {
        throw new Error('Bot not available');
      }

      this.log('Collecting debug information');
      const status = bot.getStatus();
      const inventory = bot.getInventory();
      const nearbyEntities = bot.getNearbyEntities();
      const logEntries = bot.getLogEntries(50);
      
      await this.sendSuccessResponse(command, {
        botStatus: status,
        inventory: inventory,
        nearbyEntities: nearbyEntities.map(entity => ({
          id: entity.id,
          name: entity.name || entity.type,
          type: entity.type,
          position: entity.position
        })),
        recentLogs: logEntries,
        memory: {
          used: process.memoryUsage().heapUsed,
          total: process.memoryUsage().heapTotal
        },
        uptime: process.uptime()
      });
    } catch (error) {
      await this.sendErrorResponse(command, error as Error);
    }
  }

  private async sendSuccessResponse(command: CommandMessage, result: any): Promise<void> {
    const now = Date.now();
    const commandTime = parseInt(command.timestamp) || now;
    const response: ResponseMessage = {
      id: this.generateId(),
      correlationId: command.id,
      type: MessageType.RESPONSE,
      priority: command.priority || Priority.NORMAL,
      timestamp: now.toString(),
      version: '1.0.0',
      payload: {
        success: true,
        result: result,
        executionTime: now - commandTime
      }
    };

    await this.context.sendResponse(response);
  }

  private async sendErrorResponse(command: CommandMessage, error: Error): Promise<void> {
    const now = Date.now();
    const commandTime = parseInt(command.timestamp) || now;
    const response: ResponseMessage = {
      id: this.generateId(),
      correlationId: command.id,
      type: MessageType.RESPONSE,
      priority: command.priority || Priority.NORMAL,
      timestamp: now.toString(),
      version: '1.0.0',
      payload: {
        success: false,
        error: {
          code: 'COMMAND_EXECUTION_ERROR',
          message: error.message,
          details: error.stack
        },
        executionTime: now - commandTime
      }
    };

    await this.context.sendResponse(response);
  }

  private generateId(): string {
    return `cmd_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
}

/**
 * 응답 메시지 핸들러
 */
export class ResponseMessageHandler extends AbstractMessageHandler {
  constructor(context: HandlerContext) {
    super(context, 'ResponseMessageHandler');
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.RESPONSE;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    try {
      const response = message as ResponseMessage;
      this.log(`Processing response: ${response.correlationId}`);

      // 응답 메시지를 적절한 시스템으로 라우팅
      if (response.payload.success) {
        this.log(`Command ${response.correlationId} completed successfully`);
        
        // 성공한 명령어 결과를 WebSocket을 통해 클라이언트에 전송
        await this.context.sendResponse(response);
      } else {
        this.logError(new Error(`Command ${response.correlationId} failed: ${response.payload.error?.message}`));
        
        // 실패한 명령어 정보를 WebSocket을 통해 클라이언트에 전송
        await this.context.sendResponse(response);
      }

    } catch (error) {
      this.logError(error as Error);
    }
  }
}

/**
 * 이벤트 메시지 핸들러
 */
export class EventMessageHandler extends AbstractMessageHandler {
  constructor(context: HandlerContext) {
    super(context, 'EventMessageHandler');
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.EVENT;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    try {
      const event = message as EventMessage;
      this.log(`Processing event: ${event.payload.eventType}`);

      // 이벤트 타입에 따라 적절한 핸들러 호출
      switch (event.payload.eventType) {
        case 'player_joined':
          this.handlePlayerJoinedEvent(event);
          break;
        case 'player_left':
          this.handlePlayerLeftEvent(event);
          break;
        case 'chat_message':
          this.handleChatMessageEvent(event);
          break;
        case 'bot_spawned':
          this.handleBotSpawnedEvent(event);
          break;
        case 'bot_died':
          this.handleBotDiedEvent(event);
          break;
        default:
          this.log(`Unknown event type: ${event.payload.eventType}`);
      }

      // 이벤트를 WebSocket을 통해 클라이언트에 전송
      await this.context.emitEvent(event);

    } catch (error) {
      this.logError(error as Error);
    }
  }

  private handlePlayerJoinedEvent(event: EventMessage): void {
    const playerName = event.payload.data.playerName;
    this.log(`Player joined: ${playerName}`);
    
    // 플레이어 참여 환영 메시지 전송
    const bot = this.getBot();
    const botInstance = bot?.getBot();
    if (botInstance) {
      botInstance.chat(`Welcome ${playerName}!`);
    }
  }

  private handlePlayerLeftEvent(event: EventMessage): void {
    const playerName = event.payload.data.playerName;
    this.log(`Player left: ${playerName}`);
    
    // 플레이어 떠남 메시지 기록
    const bot = this.getBot();
    if (bot) {
      bot.logInfo('player_events', `Player ${playerName} has left the server`);
    }
  }

  private handleChatMessageEvent(event: EventMessage): void {
    const { username, message } = event.payload.data;
    this.log(`Chat message from ${username}: ${message}`);
    
    // 채팅 메시지를 AI 대화 시스템에 전달하거나 명령어 처리
    const bot = this.getBot();
    if (bot && message.startsWith('!')) {
      // 명령어 처리 로직
      const command = message.substring(1).trim();
      this.log(`Processing chat command: ${command}`);
      // 실제 명령어 처리 로직을 여기에 구현
    }
  }

  private handleBotSpawnedEvent(event: EventMessage): void {
    this.log('Bot has spawned in the world');
    
    // 봇 스폰 후 초기화 작업
    const bot = this.getBot();
    if (bot) {
      bot.logInfo('bot_lifecycle', 'Bot successfully spawned and ready for commands');
    }
  }

  private handleBotDiedEvent(event: EventMessage): void {
    const deathMessage = event.payload.data.deathMessage;
    this.log(`Bot died: ${deathMessage}`);
    
    // 봇 사망 처리 - 리스폰 또는 알림
    const bot = this.getBot();
    if (bot) {
      bot.logWarn('bot_lifecycle', `Bot death: ${deathMessage}`);
    }
  }
}

/**
 * 상태 메시지 핸들러
 */
export class StateMessageHandler extends AbstractMessageHandler {
  constructor(context: HandlerContext) {
    super(context, 'StateMessageHandler');
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.STATE;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    try {
      const state = message as StateMessage;
      this.log(`Processing state update: ${state.payload.stateType}`);

      // 상태 타입에 따라 적절한 핸들러 호출
      switch (state.payload.stateType) {
        case 'connection':
          this.handleConnectionState(state);
          break;
        case 'bot_status':
          this.handleBotStatusState(state);
          break;
        case 'world_info':
          this.handleWorldInfoState(state);
          break;
        default:
          this.log(`Unknown state type: ${state.payload.stateType}`);
      }

    } catch (error) {
      this.logError(error as Error);
    }
  }

  private handleConnectionState(state: StateMessage): void {
    const connectionData = state.payload.data;
    this.log(`Connection state update: ${JSON.stringify(connectionData)}`);
    
    // 연결 상태 변화에 따른 처리
    const bot = this.getBot();
    if (bot && connectionData.connected === false) {
      bot.logWarn('connection', 'Connection lost, attempting to reconnect...');
    }
  }

  private handleBotStatusState(state: StateMessage): void {
    const statusData = state.payload.data;
    this.log(`Bot status update: Health=${statusData.health}, Food=${statusData.food}`);
    
    // 봇 상태 모니터링 및 자동 대응
    const bot = this.getBot();
    if (bot) {
      if (statusData.health < 5) {
        bot.logWarn('bot_health', 'Bot health is critically low!');
      }
      if (statusData.food < 5) {
        bot.logWarn('bot_health', 'Bot food level is critically low!');
      }
    }
  }

  private handleWorldInfoState(state: StateMessage): void {
    const worldData = state.payload.data;
    this.log(`World info update: Time=${worldData.timeOfDay}, Weather=${worldData.weather}`);
    
    // 월드 정보 변화에 따른 봇 행동 조정
    const bot = this.getBot();
    if (bot) {
      bot.logInfo('world_info', `World state: ${worldData.weather} at time ${worldData.timeOfDay}`);
    }
  }
} 