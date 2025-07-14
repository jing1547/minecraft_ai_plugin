import { BaseMessage, CommandMessage, ResponseMessage, EventMessage, StateMessage, MessageType } from '../protocol/types';
import { MessageHandler, HandlerContext, CommandMessageHandler, ResponseMessageHandler, EventMessageHandler, StateMessageHandler } from './MessageHandler';
import { MinecraftAIBody } from '../bot/MinecraftAIBody';

/**
 * 대기 중인 요청 정보
 */
interface PendingRequest {
  resolve: (value: ResponseMessage) => void;
  reject: (reason: Error) => void;
  timeout: NodeJS.Timeout;
  timestamp: number;
  messageId: string;
}

/**
 * 이벤트 구독 정보
 */
interface EventSubscription {
  eventType: string;
  callback: (event: EventMessage) => void;
  subscriptionId: string;
}

/**
 * 라우터 설정 옵션
 */
export interface MessageRouterConfig {
  defaultTimeout: number;
  maxPendingRequests: number;
  enableLogging: boolean;
  enableMetrics: boolean;
  cleanupInterval: number;
}

/**
 * 메시지 라우터 통계
 */
export interface MessageRouterStats {
  totalMessages: number;
  messagesPerType: { [type: string]: number };
  pendingRequests: number;
  timeoutRequests: number;
  averageResponseTime: number;
  activeSubscriptions: number;
}

/**
 * 메시지 라우터 클래스
 * 메시지 핸들링, 라우팅, 요청-응답 관리를 담당
 */
export class MessageRouter implements HandlerContext {
  private config: MessageRouterConfig;
  private handlers: MessageHandler[] = [];
  private pendingRequests: Map<string, PendingRequest> = new Map();
  private eventSubscriptions: Map<string, EventSubscription[]> = new Map();
  private stats: MessageRouterStats;
  private cleanupTimer: NodeJS.Timeout | null = null;
  private responseTimeHistory: number[] = [];
  private sendResponseCallback: ((response: ResponseMessage) => Promise<void>) | null = null;
  private sendEventCallback: ((event: EventMessage) => Promise<void>) | null = null;
  private logCallback: ((message: string) => void) | null = null;
  private errorCallback: ((error: Error) => void) | null = null;

  constructor(config: Partial<MessageRouterConfig> = {}) {
    this.config = {
      defaultTimeout: 30000, // 30초
      maxPendingRequests: 100,
      enableLogging: true,
      enableMetrics: true,
      cleanupInterval: 60000, // 1분마다 정리
      ...config
    };

    this.stats = {
      totalMessages: 0,
      messagesPerType: {},
      pendingRequests: 0,
      timeoutRequests: 0,
      averageResponseTime: 0,
      activeSubscriptions: 0
    };

    this.initializeHandlers();
    this.startCleanupTimer();
  }

  /**
   * 기본 핸들러 초기화
   */
  private initializeHandlers(): void {
    this.handlers = [
      new CommandMessageHandler(this),
      new ResponseMessageHandler(this),
      new EventMessageHandler(this),
      new StateMessageHandler(this)
    ];
  }

  /**
   * 정리 타이머 시작
   */
  private startCleanupTimer(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
    }

    this.cleanupTimer = setInterval(() => {
      this.cleanupExpiredRequests();
    }, this.config.cleanupInterval);
  }

  /**
   * 만료된 요청 정리
   */
  private cleanupExpiredRequests(): void {
    const now = Date.now();
    let cleanedCount = 0;

    for (const [messageId, request] of this.pendingRequests.entries()) {
      if (now - request.timestamp > this.config.defaultTimeout) {
        clearTimeout(request.timeout);
        request.reject(new Error(`Request timeout: ${messageId}`));
        this.pendingRequests.delete(messageId);
        cleanedCount++;
        this.stats.timeoutRequests++;
      }
    }

    if (cleanedCount > 0) {
      this.log(`Cleaned up ${cleanedCount} expired requests`);
    }
  }

  /**
   * 콜백 함수 설정
   */
  public setCallbacks(callbacks: {
    sendResponse?: (response: ResponseMessage) => Promise<void>;
    sendEvent?: (event: EventMessage) => Promise<void>;
    log?: (message: string) => void;
    error?: (error: Error) => void;
  }): void {
    this.sendResponseCallback = callbacks.sendResponse || null;
    this.sendEventCallback = callbacks.sendEvent || null;
    this.logCallback = callbacks.log || null;
    this.errorCallback = callbacks.error || null;
  }

  /**
   * 커스텀 핸들러 추가
   */
  public addHandler(handler: MessageHandler): void {
    this.handlers.push(handler);
    this.log(`Added handler: ${handler.getName()}`);
  }

  /**
   * 핸들러 제거
   */
  public removeHandler(handlerName: string): void {
    const index = this.handlers.findIndex(h => h.getName() === handlerName);
    if (index >= 0) {
      this.handlers.splice(index, 1);
      this.log(`Removed handler: ${handlerName}`);
    }
  }

  /**
   * 메시지 처리
   */
  public async handleMessage(message: BaseMessage): Promise<void> {
    try {
      this.updateStats(message);
      this.log(`Routing message: ${message.type} (${message.id})`);

      // 응답 메시지의 경우 대기 중인 요청 처리
      if (message.type === MessageType.RESPONSE) {
        await this.handleResponseMessage(message as ResponseMessage);
        return;
      }

      // 적절한 핸들러 찾기
      const handler = this.handlers.find(h => h.canHandle(message));
      if (handler) {
        await handler.handleMessage(message);
        this.log(`Message handled by: ${handler.getName()}`);
      } else {
        this.log(`No handler found for message type: ${message.type}`);
      }

      // 이벤트 메시지의 경우 구독자에게 전달
      if (message.type === MessageType.EVENT) {
        await this.notifyEventSubscribers(message as EventMessage);
      }

    } catch (error) {
      this.logError(error as Error);
      throw error;
    }
  }

  /**
   * 응답 메시지 처리
   */
  private async handleResponseMessage(response: ResponseMessage): Promise<void> {
    const correlationId = response.correlationId;
    const pendingRequest = this.pendingRequests.get(correlationId);

    if (pendingRequest) {
      clearTimeout(pendingRequest.timeout);
      
      // 응답 시간 계산
      const responseTime = Date.now() - pendingRequest.timestamp;
      this.updateResponseTimeStats(responseTime);
      
      this.pendingRequests.delete(correlationId);
      pendingRequest.resolve(response);
      
      this.log(`Response received for request: ${correlationId} (${responseTime}ms)`);
    } else {
      this.log(`Received response for unknown request: ${correlationId}`);
    }
  }

  /**
   * 이벤트 구독자에게 알림
   */
  private async notifyEventSubscribers(event: EventMessage): Promise<void> {
    const eventType = event.payload.eventType;
    const subscriptions = this.eventSubscriptions.get(eventType) || [];

    for (const subscription of subscriptions) {
      try {
        subscription.callback(event);
      } catch (error) {
        this.logError(error as Error);
      }
    }

    this.log(`Event '${eventType}' notified to ${subscriptions.length} subscribers`);
  }

  /**
   * 명령 전송 (Promise 기반)
   */
  public async sendCommand(command: CommandMessage, timeoutMs?: number): Promise<ResponseMessage> {
    const timeout = timeoutMs || this.config.defaultTimeout;
    
    if (this.pendingRequests.size >= this.config.maxPendingRequests) {
      throw new Error('Maximum pending requests exceeded');
    }

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingRequests.delete(command.id);
        this.stats.timeoutRequests++;
        reject(new Error(`Command timeout: ${command.id}`));
      }, timeout);

      const pendingRequest: PendingRequest = {
        resolve,
        reject,
        timeout: timer,
        timestamp: Date.now(),
        messageId: command.id
      };

      this.pendingRequests.set(command.id, pendingRequest);
      this.log(`Command queued: ${command.id} (timeout: ${timeout}ms)`);
    });
  }

  /**
   * 이벤트 구독
   */
  public subscribeToEvent(eventType: string, callback: (event: EventMessage) => void): string {
    const subscriptionId = this.generateSubscriptionId();
    
    if (!this.eventSubscriptions.has(eventType)) {
      this.eventSubscriptions.set(eventType, []);
    }

    const subscription: EventSubscription = {
      eventType,
      callback,
      subscriptionId
    };

    this.eventSubscriptions.get(eventType)!.push(subscription);
    this.stats.activeSubscriptions++;
    
    this.log(`Subscribed to event: ${eventType} (ID: ${subscriptionId})`);
    return subscriptionId;
  }

  /**
   * 이벤트 구독 해제
   */
  public unsubscribeFromEvent(subscriptionId: string): boolean {
    for (const [eventType, subscriptions] of this.eventSubscriptions.entries()) {
      const index = subscriptions.findIndex(s => s.subscriptionId === subscriptionId);
      if (index >= 0) {
        subscriptions.splice(index, 1);
        this.stats.activeSubscriptions--;
        
        if (subscriptions.length === 0) {
          this.eventSubscriptions.delete(eventType);
        }
        
        this.log(`Unsubscribed from event: ${eventType} (ID: ${subscriptionId})`);
        return true;
      }
    }
    
    return false;
  }

  /**
   * 모든 이벤트 구독 해제
   */
  public unsubscribeFromAllEvents(): void {
    const totalSubscriptions = this.stats.activeSubscriptions;
    this.eventSubscriptions.clear();
    this.stats.activeSubscriptions = 0;
    
    this.log(`Unsubscribed from all events (${totalSubscriptions} subscriptions)`);
  }

  /**
   * 통계 업데이트
   */
  private updateStats(message: BaseMessage): void {
    if (!this.config.enableMetrics) return;

    this.stats.totalMessages++;
    this.stats.messagesPerType[message.type] = (this.stats.messagesPerType[message.type] || 0) + 1;
    this.stats.pendingRequests = this.pendingRequests.size;
  }

  /**
   * 응답 시간 통계 업데이트
   */
  private updateResponseTimeStats(responseTime: number): void {
    if (!this.config.enableMetrics) return;

    this.responseTimeHistory.push(responseTime);
    
    // 최근 100개 응답 시간만 유지
    if (this.responseTimeHistory.length > 100) {
      this.responseTimeHistory.shift();
    }
    
    // 평균 응답 시간 계산
    const sum = this.responseTimeHistory.reduce((acc, time) => acc + time, 0);
    this.stats.averageResponseTime = sum / this.responseTimeHistory.length;
  }

  /**
   * 구독 ID 생성
   */
  private generateSubscriptionId(): string {
    return `sub_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * HandlerContext 구현 - 응답 전송
   */
  public async sendResponse(response: ResponseMessage): Promise<void> {
    if (this.sendResponseCallback) {
      await this.sendResponseCallback(response);
    } else {
      throw new Error('Response callback not set');
    }
  }

  /**
   * HandlerContext 구현 - 이벤트 발생
   */
  public async emitEvent(event: EventMessage): Promise<void> {
    if (this.sendEventCallback) {
      await this.sendEventCallback(event);
    } else {
      throw new Error('Event callback not set');
    }
  }

  /**
   * HandlerContext 구현 - 로그 출력
   */
  public log(message: string): void {
    if (this.config.enableLogging) {
      const timestamp = new Date().toISOString();
      const logMessage = `[${timestamp}] [MessageRouter] ${message}`;
      
      if (this.logCallback) {
        this.logCallback(logMessage);
      } else {
        console.log(logMessage);
      }
    }
  }

  /**
   * HandlerContext 구현 - 오류 로그 출력
   */
  public logError(error: Error): void {
    const timestamp = new Date().toISOString();
    const errorMessage = `[${timestamp}] [MessageRouter] ERROR: ${error.message}`;
    
    if (this.errorCallback) {
      this.errorCallback(error);
    } else {
      console.error(errorMessage, error.stack);
    }
  }

  /**
   * 통계 정보 반환
   */
  public getStats(): MessageRouterStats {
    return { ...this.stats };
  }

  /**
   * 대기 중인 요청 정보 반환
   */
  public getPendingRequests(): { [messageId: string]: { timestamp: number; timeout: number } } {
    const result: { [messageId: string]: { timestamp: number; timeout: number } } = {};
    
    for (const [messageId, request] of this.pendingRequests.entries()) {
      result[messageId] = {
        timestamp: request.timestamp,
        timeout: this.config.defaultTimeout
      };
    }
    
    return result;
  }

  /**
   * 이벤트 구독 정보 반환
   */
  public getEventSubscriptions(): { [eventType: string]: number } {
    const result: { [eventType: string]: number } = {};
    
    for (const [eventType, subscriptions] of this.eventSubscriptions.entries()) {
      result[eventType] = subscriptions.length;
    }
    
    return result;
  }

  /**
   * 설정 업데이트
   */
  public updateConfig(config: Partial<MessageRouterConfig>): void {
    this.config = { ...this.config, ...config };
    this.log('Configuration updated');
  }

  /**
   * 라우터 종료
   */
  public shutdown(): void {
    this.log('Shutting down message router');
    
    // 정리 타이머 중지
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
    
    // 대기 중인 요청 모두 거부
    for (const [messageId, request] of this.pendingRequests.entries()) {
      clearTimeout(request.timeout);
      request.reject(new Error('Router shutdown'));
    }
    this.pendingRequests.clear();
    
    // 이벤트 구독 정리
    this.eventSubscriptions.clear();
    
    this.log('Message router shutdown complete');
  }

  /**
   * 라우터 상태 확인
   */
  public isHealthy(): boolean {
    return this.pendingRequests.size < this.config.maxPendingRequests;
  }

  /**
   * 디버그 정보 반환
   */
  public getDebugInfo(): {
    config: MessageRouterConfig;
    stats: MessageRouterStats;
    pendingRequests: number;
    eventSubscriptions: number;
    handlers: string[];
  } {
    return {
      config: this.config,
      stats: this.getStats(),
      pendingRequests: this.pendingRequests.size,
      eventSubscriptions: this.eventSubscriptions.size,
      handlers: this.handlers.map(h => h.getName())
    };
  }

  /**
   * Get bot instance (HandlerContext interface requirement)
   * @returns Bot instance or null
   */
  public getBot(): MinecraftAIBody | null {
    // MessageRouter doesn't directly manage bot instance
    // This would typically be injected or managed by a higher-level service
    return null;
  }
} 