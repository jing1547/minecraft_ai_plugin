import { BaseMessage, CommandMessage, ResponseMessage, EventMessage, StateMessage, MessageType, CommandAction } from '../protocol/types';

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
   * @param error 오류 정보
   */
  logError(error: Error): void;
}

/**
 * 추상 메시지 핸들러 기본 클래스
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
    this.context.log(`[${this.name}] ${message}`);
  }

  protected logError(error: Error): void {
    this.context.logError(error);
  }
}

/**
 * 커맨드 메시지 핸들러
 */
export class CommandMessageHandler extends AbstractMessageHandler {
  private commandHandlers: Map<string, (command: CommandMessage) => Promise<void>> = new Map();

  constructor(context: HandlerContext) {
    super(context, 'CommandHandler');
    this.initializeCommandHandlers();
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.COMMAND;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    if (!this.canHandle(message)) {
      throw new Error(`Cannot handle message type: ${message.type}`);
    }

    const commandMessage = message as CommandMessage;
    const action = commandMessage.payload.action;

    this.log(`Handling command: ${action}`);

    const handler = this.commandHandlers.get(action);
    if (handler) {
      try {
        await handler(commandMessage);
      } catch (error) {
        this.logError(error as Error);
        await this.sendErrorResponse(commandMessage, error as Error);
      }
    } else {
      const errorMessage = `Unknown command action: ${action}`;
      this.log(errorMessage);
      await this.sendErrorResponse(commandMessage, new Error(errorMessage));
    }
  }

  private initializeCommandHandlers(): void {
    this.commandHandlers.set(CommandAction.MOVE_TO, this.handleMoveToCommand.bind(this));
    this.commandHandlers.set(CommandAction.FOLLOW, this.handleFollowCommand.bind(this));
    this.commandHandlers.set(CommandAction.STOP, this.handleStopCommand.bind(this));
    this.commandHandlers.set(CommandAction.ATTACK, this.handleAttackCommand.bind(this));
    this.commandHandlers.set(CommandAction.USE, this.handleUseCommand.bind(this));
    this.commandHandlers.set(CommandAction.BREAK, this.handleBreakCommand.bind(this));
    this.commandHandlers.set(CommandAction.PLACE, this.handlePlaceCommand.bind(this));
    this.commandHandlers.set(CommandAction.EQUIP_ITEM, this.handleEquipItemCommand.bind(this));
    this.commandHandlers.set(CommandAction.CRAFT_ITEM, this.handleCraftItemCommand.bind(this));
    this.commandHandlers.set(CommandAction.DROP_ITEM, this.handleDropItemCommand.bind(this));
    this.commandHandlers.set(CommandAction.CHAT, this.handleChatCommand.bind(this));
    this.commandHandlers.set(CommandAction.WHISPER, this.handleWhisperCommand.bind(this));
    this.commandHandlers.set(CommandAction.DISCONNECT, this.handleDisconnectCommand.bind(this));
    this.commandHandlers.set(CommandAction.STATUS, this.handleStatusCommand.bind(this));
    this.commandHandlers.set(CommandAction.DEBUG, this.handleDebugCommand.bind(this));
  }

  private async handleMoveToCommand(command: CommandMessage): Promise<void> {
    const { x, y, z } = command.payload.parameters;
    this.log(`Moving to position: ${x}, ${y}, ${z}`);
    
    // TODO: 실제 Mineflayer 봇 이동 로직 구현
    // await this.bot.pathfinder.goto(new goals.GoalBlock(x, y, z));
    
    await this.sendSuccessResponse(command, { 
      message: `Successfully moved to ${x}, ${y}, ${z}` 
    });
  }

  private async handleFollowCommand(command: CommandMessage): Promise<void> {
    const { playerName, distance = 3 } = command.payload.parameters;
    this.log(`Following player: ${playerName} at distance ${distance}`);
    
    // TODO: 실제 Mineflayer 봇 따라가기 로직 구현
    // const player = this.bot.players[playerName];
    // if (player) {
    //   await this.bot.pathfinder.goto(new goals.GoalFollow(player.entity, distance));
    // }
    
    await this.sendSuccessResponse(command, { 
      message: `Following ${playerName} at distance ${distance}` 
    });
  }

  private async handleStopCommand(command: CommandMessage): Promise<void> {
    this.log('Stopping current action');
    
    // TODO: 실제 Mineflayer 봇 정지 로직 구현
    // this.bot.pathfinder.stop();
    
    await this.sendSuccessResponse(command, { 
      message: 'Stopped current action' 
    });
  }

  private async handleAttackCommand(command: CommandMessage): Promise<void> {
    const { target } = command.payload.parameters;
    this.log(`Attacking target: ${target}`);
    
    // TODO: 실제 Mineflayer 봇 공격 로직 구현
    // const targetEntity = this.bot.nearestEntity(entity => entity.name === target);
    // if (targetEntity) {
    //   await this.bot.attack(targetEntity);
    // }
    
    await this.sendSuccessResponse(command, { 
      message: `Attack initiated on ${target}` 
    });
  }

  private async handleUseCommand(command: CommandMessage): Promise<void> {
    const { itemName } = command.payload.parameters;
    this.log(`Using item: ${itemName}`);
    
    // TODO: 실제 Mineflayer 봇 아이템 사용 로직 구현
    
    await this.sendSuccessResponse(command, { 
      message: `Used item: ${itemName}` 
    });
  }

  private async handleBreakCommand(command: CommandMessage): Promise<void> {
    const { x, y, z } = command.payload.parameters;
    this.log(`Breaking block at: ${x}, ${y}, ${z}`);
    
    // TODO: 실제 Mineflayer 봇 블록 파괴 로직 구현
    // const block = this.bot.blockAt(new Vec3(x, y, z));
    // if (block) {
    //   await this.bot.dig(block);
    // }
    
    await this.sendSuccessResponse(command, { 
      message: `Block broken at ${x}, ${y}, ${z}` 
    });
  }

  private async handlePlaceCommand(command: CommandMessage): Promise<void> {
    const { x, y, z, face } = command.payload.parameters;
    this.log(`Placing block at: ${x}, ${y}, ${z}, face: ${face}`);
    
    // TODO: 실제 Mineflayer 봇 블록 설치 로직 구현
    
    await this.sendSuccessResponse(command, { 
      message: `Block placed at ${x}, ${y}, ${z}` 
    });
  }

  private async handleEquipItemCommand(command: CommandMessage): Promise<void> {
    const { itemName, slot } = command.payload.parameters;
    this.log(`Equipping item: ${itemName} to slot: ${slot}`);
    
    // TODO: 실제 Mineflayer 봇 아이템 장착 로직 구현
    
    await this.sendSuccessResponse(command, { 
      message: `Equipped ${itemName} to slot ${slot}` 
    });
  }

  private async handleCraftItemCommand(command: CommandMessage): Promise<void> {
    const { itemName, count = 1 } = command.payload.parameters;
    this.log(`Crafting item: ${itemName}, count: ${count}`);
    
    // TODO: 실제 Mineflayer 봇 아이템 제작 로직 구현
    
    await this.sendSuccessResponse(command, { 
      message: `Crafted ${count} ${itemName}` 
    });
  }

  private async handleDropItemCommand(command: CommandMessage): Promise<void> {
    const { itemName, count = 1 } = command.payload.parameters;
    this.log(`Dropping item: ${itemName}, count: ${count}`);
    
    // TODO: 실제 Mineflayer 봇 아이템 드롭 로직 구현
    
    await this.sendSuccessResponse(command, { 
      message: `Dropped ${count} ${itemName}` 
    });
  }

  private async handleChatCommand(command: CommandMessage): Promise<void> {
    const { message } = command.payload.parameters;
    this.log(`Sending chat message: ${message}`);
    
    // TODO: 실제 Mineflayer 봇 채팅 로직 구현
    // this.bot.chat(message);
    
    await this.sendSuccessResponse(command, { 
      message: `Chat sent: ${message}` 
    });
  }

  private async handleWhisperCommand(command: CommandMessage): Promise<void> {
    const { message, target } = command.payload.parameters;
    this.log(`Sending whisper to ${target}: ${message}`);
    
    // TODO: 실제 Mineflayer 봇 귓속말 로직 구현
    // this.bot.whisper(target, message);
    
    await this.sendSuccessResponse(command, { 
      message: `Whisper sent to ${target}: ${message}` 
    });
  }

  private async handleDisconnectCommand(command: CommandMessage): Promise<void> {
    this.log('Disconnecting from server');
    
    // TODO: 실제 Mineflayer 봇 연결 해제 로직 구현
    // this.bot.quit();
    
    await this.sendSuccessResponse(command, { 
      message: 'Disconnected from server' 
    });
  }

  private async handleStatusCommand(command: CommandMessage): Promise<void> {
    this.log('Getting bot status');
    
    // TODO: 실제 Mineflayer 봇 상태 정보 수집
    const status = {
      health: 20,
      food: 20,
      position: { x: 0, y: 64, z: 0 },
      gameMode: 'survival',
      experience: 0
    };
    
    await this.sendSuccessResponse(command, status);
  }

  private async handleDebugCommand(command: CommandMessage): Promise<void> {
    this.log('Getting debug information');
    
    // TODO: 실제 디버그 정보 수집
    const debug = {
      uptime: process.uptime(),
      memoryUsage: process.memoryUsage(),
      nodeVersion: process.version,
      platform: process.platform
    };
    
    await this.sendSuccessResponse(command, debug);
  }

  private async sendSuccessResponse(command: CommandMessage, result: any): Promise<void> {
    const response: ResponseMessage = {
      type: MessageType.RESPONSE,
      id: this.generateId(),
      timestamp: new Date().toISOString(),
      version: '1.0.0',
      correlationId: command.id,
      payload: {
        success: true,
        result: result,
        executionTime: Date.now() - new Date(command.timestamp).getTime()
      }
    };

    await this.context.sendResponse(response);
  }

  private async sendErrorResponse(command: CommandMessage, error: Error): Promise<void> {
    const response: ResponseMessage = {
      type: MessageType.RESPONSE,
      id: this.generateId(),
      timestamp: new Date().toISOString(),
      version: '1.0.0',
      correlationId: command.id,
      payload: {
        success: false,
        error: {
          code: 'COMMAND_ERROR',
          message: error.message,
          details: error.stack
        },
        executionTime: Date.now() - new Date(command.timestamp).getTime()
      }
    };

    await this.context.sendResponse(response);
  }

  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}

/**
 * 응답 메시지 핸들러
 */
export class ResponseMessageHandler extends AbstractMessageHandler {
  constructor(context: HandlerContext) {
    super(context, 'ResponseHandler');
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.RESPONSE;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    if (!this.canHandle(message)) {
      throw new Error(`Cannot handle message type: ${message.type}`);
    }

    const responseMessage = message as ResponseMessage;
    this.log(`Received response for request: ${responseMessage.correlationId}`);

    // 응답 메시지는 MessageRouter에서 pending requests에 대한 처리를 담당
    // 여기서는 로깅만 수행
    if (responseMessage.payload.success) {
      this.log(`Command executed successfully`);
    } else {
      this.log(`Command failed: ${responseMessage.payload.error?.message}`);
    }
  }
}

/**
 * 이벤트 메시지 핸들러
 */
export class EventMessageHandler extends AbstractMessageHandler {
  constructor(context: HandlerContext) {
    super(context, 'EventHandler');
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.EVENT;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    if (!this.canHandle(message)) {
      throw new Error(`Cannot handle message type: ${message.type}`);
    }

    const eventMessage = message as EventMessage;
    this.log(`Received event: ${eventMessage.payload.eventType}`);

    // 이벤트 메시지 처리 로직
    switch (eventMessage.payload.eventType) {
      case 'player-joined':
        this.handlePlayerJoinedEvent(eventMessage);
        break;
      case 'player-left':
        this.handlePlayerLeftEvent(eventMessage);
        break;
      case 'chat-message':
        this.handleChatMessageEvent(eventMessage);
        break;
      case 'bot-spawned':
        this.handleBotSpawnedEvent(eventMessage);
        break;
      case 'bot-died':
        this.handleBotDiedEvent(eventMessage);
        break;
      default:
        this.log(`Unknown event type: ${eventMessage.payload.eventType}`);
    }
  }

  private handlePlayerJoinedEvent(event: EventMessage): void {
    const playerName = event.payload.data.playerName;
    this.log(`Player joined: ${playerName}`);
    
    // TODO: 플레이어 참여 처리 로직
  }

  private handlePlayerLeftEvent(event: EventMessage): void {
    const playerName = event.payload.data.playerName;
    this.log(`Player left: ${playerName}`);
    
    // TODO: 플레이어 떠남 처리 로직
  }

  private handleChatMessageEvent(event: EventMessage): void {
    const { username, message } = event.payload.data;
    this.log(`Chat message from ${username}: ${message}`);
    
    // TODO: 채팅 메시지 처리 로직
  }

  private handleBotSpawnedEvent(event: EventMessage): void {
    const position = event.payload.data.position;
    this.log(`Bot spawned at: ${position.x}, ${position.y}, ${position.z}`);
    
    // TODO: 봇 스폰 처리 로직
  }

  private handleBotDiedEvent(event: EventMessage): void {
    const deathMessage = event.payload.data.deathMessage;
    this.log(`Bot died: ${deathMessage}`);
    
    // TODO: 봇 사망 처리 로직
  }
}

/**
 * 상태 메시지 핸들러
 */
export class StateMessageHandler extends AbstractMessageHandler {
  constructor(context: HandlerContext) {
    super(context, 'StateHandler');
  }

  canHandle(message: BaseMessage): boolean {
    return message.type === MessageType.STATE;
  }

  async handleMessage(message: BaseMessage): Promise<void> {
    if (!this.canHandle(message)) {
      throw new Error(`Cannot handle message type: ${message.type}`);
    }

    const stateMessage = message as StateMessage;
    this.log(`Received state update: ${stateMessage.payload.stateType}`);

    // 상태 메시지 처리 로직
    switch (stateMessage.payload.stateType) {
      case 'connection':
        this.handleConnectionState(stateMessage);
        break;
      case 'bot-status':
        this.handleBotStatusState(stateMessage);
        break;
      case 'world-info':
        this.handleWorldInfoState(stateMessage);
        break;
      default:
        this.log(`Unknown state type: ${stateMessage.payload.stateType}`);
    }
  }

  private handleConnectionState(state: StateMessage): void {
    const { connectionId, status } = state.payload.data;
    this.log(`Connection state: ${status}, ID: ${connectionId}`);
    
    // TODO: 연결 상태 처리 로직
  }

  private handleBotStatusState(state: StateMessage): void {
    const { health, food, position } = state.payload.data;
    this.log(`Bot status - Health: ${health}, Food: ${food}, Position: ${position.x},${position.y},${position.z}`);
    
    // TODO: 봇 상태 처리 로직
  }

  private handleWorldInfoState(state: StateMessage): void {
    const { time, weather, players } = state.payload.data;
    this.log(`World info - Time: ${time}, Weather: ${weather}, Players: ${players.length}`);
    
    // TODO: 월드 정보 처리 로직
  }
} 