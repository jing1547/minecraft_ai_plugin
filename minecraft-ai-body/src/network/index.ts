import { WebSocketClient, WebSocketClientConfig, ConnectionState } from './WebSocketClient';
import { MessageRouter, MessageRouterConfig } from './MessageRouter';
import { BaseMessage, CommandMessage, ResponseMessage, EventMessage, MessageType } from '../protocol/types';
import { MessageSerializer } from '../protocol/MessageSerializer';

/**
 * 네트워크 관리자 설정
 */
export interface NetworkManagerConfig {
  websocket: Partial<WebSocketClientConfig>;
  router: Partial<MessageRouterConfig>;
  autoReconnect: boolean;
  enableHeartbeat: boolean;
  heartbeatInterval: number;
}

/**
 * 네트워크 관리자 이벤트
 */
export interface NetworkManagerEvents {
  'connected': () => void;
  'disconnected': (reason: string) => void;
  'reconnecting': (attempt: number) => void;
  'message': (message: BaseMessage) => void;
  'error': (error: Error) => void;
}

/**
 * 네트워크 관리자 클래스
 * WebSocket 클라이언트와 메시지 라우터를 통합 관리
 */
export class NetworkManager {
  private client: WebSocketClient;
  private router: MessageRouter;
  private config: NetworkManagerConfig;
  private eventListeners: Map<string, Function[]> = new Map();
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private isInitialized: boolean = false;

  constructor(config: Partial<NetworkManagerConfig> = {}) {
    this.config = {
      websocket: {},
      router: {},
      autoReconnect: true,
      enableHeartbeat: true,
      heartbeatInterval: 30000, // 30초
      ...config
    };

    this.client = new WebSocketClient(this.config.websocket);
    this.router = new MessageRouter(this.config.router);
    
    this.initialize();
  }

  /**
   * 초기화
   */
  private initialize(): void {
    if (this.isInitialized) return;

    // WebSocket 클라이언트 이벤트 리스너 설정
    this.client.on('connected', () => {
      this.onConnected();
    });

    this.client.on('disconnected', (reason: string) => {
      this.onDisconnected(reason);
    });

    this.client.on('reconnecting', (attempt: number) => {
      this.onReconnecting(attempt);
    });

    this.client.on('message', (message: BaseMessage) => {
      this.onMessage(message);
    });

    this.client.on('error', (error: Error) => {
      this.onError(error);
    });

    // 메시지 라우터 콜백 설정
    this.router.setCallbacks({
      sendResponse: async (response: ResponseMessage) => {
        await this.sendMessage(response);
      },
      sendEvent: async (event: EventMessage) => {
        await this.sendMessage(event);
      },
      log: (message: string) => {
        this.log(message);
      },
      error: (error: Error) => {
        this.onError(error);
      }
    });

    this.isInitialized = true;
    this.log('Network manager initialized');
  }

  /**
   * 서버에 연결
   */
  public async connect(): Promise<void> {
    try {
      await this.client.connect();
    } catch (error) {
      this.log(`Connection failed: ${error}`);
      throw error;
    }
  }

  /**
   * 연결 해제
   */
  public disconnect(): void {
    this.stopHeartbeat();
    this.client.disconnect();
  }

  /**
   * 메시지 전송
   */
  public async sendMessage(message: BaseMessage): Promise<boolean> {
    return this.client.sendMessage(message);
  }

  /**
   * 명령 전송 (Promise 기반)
   */
  public async sendCommand(command: CommandMessage, timeoutMs?: number): Promise<ResponseMessage> {
    // 명령 전송 후 응답 대기
    const sendPromise = this.router.sendCommand(command, timeoutMs);
    
    // WebSocket으로 실제 전송
    const sent = await this.sendMessage(command);
    if (!sent) {
      throw new Error('Failed to send command');
    }

    return sendPromise;
  }

  /**
   * 이벤트 구독
   */
  public subscribeToEvent(eventType: string, callback: (event: EventMessage) => void): string {
    return this.router.subscribeToEvent(eventType, callback);
  }

  /**
   * 이벤트 구독 해제
   */
  public unsubscribeFromEvent(subscriptionId: string): boolean {
    return this.router.unsubscribeFromEvent(subscriptionId);
  }

  /**
   * 네트워크 이벤트 리스너 등록
   */
  public on<K extends keyof NetworkManagerEvents>(
    event: K,
    listener: NetworkManagerEvents[K]
  ): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(listener);
  }

  /**
   * 네트워크 이벤트 리스너 제거
   */
  public off<K extends keyof NetworkManagerEvents>(
    event: K,
    listener: NetworkManagerEvents[K]
  ): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(listener);
      if (index >= 0) {
        listeners.splice(index, 1);
      }
    }
  }

  /**
   * 연결 상태 확인
   */
  public isConnected(): boolean {
    return this.client.isConnected();
  }

  /**
   * 연결 상태 반환
   */
  public getConnectionState(): ConnectionState {
    return this.client.getConnectionState();
  }

  /**
   * 연결 ID 반환
   */
  public getConnectionId(): string | null {
    return this.client.getConnectionId();
  }

  /**
   * 통계 정보 반환
   */
  public getStats(): {
    client: any;
    router: any;
  } {
    return {
      client: this.client.getConnectionStats(),
      router: this.router.getStats()
    };
  }

  /**
   * 설정 업데이트
   */
  public updateConfig(config: Partial<NetworkManagerConfig>): void {
    this.config = { ...this.config, ...config };
    
    if (config.websocket) {
      this.client.updateConfig(config.websocket);
    }
    
    if (config.router) {
      this.router.updateConfig(config.router);
    }
    
    this.log('Network manager configuration updated');
  }

  /**
   * 연결 성공 처리
   */
  private onConnected(): void {
    this.log('Connected to server');
    this.startHeartbeat();
    this.emit('connected');
  }

  /**
   * 연결 해제 처리
   */
  private onDisconnected(reason: string): void {
    this.log(`Disconnected from server: ${reason}`);
    this.stopHeartbeat();
    this.emit('disconnected', reason);
  }

  /**
   * 재연결 시도 처리
   */
  private onReconnecting(attempt: number): void {
    this.log(`Reconnecting attempt: ${attempt}`);
    this.emit('reconnecting', attempt);
  }

  /**
   * 메시지 수신 처리
   */
  private async onMessage(message: BaseMessage): Promise<void> {
    try {
      this.emit('message', message);
      await this.router.handleMessage(message);
    } catch (error) {
      this.log(`Error handling message: ${error}`);
      this.onError(error as Error);
    }
  }

  /**
   * 오류 처리
   */
  private onError(error: Error): void {
    this.log(`Network error: ${error.message}`);
    this.emit('error', error);
  }

  /**
   * 하트비트 시작
   */
  private startHeartbeat(): void {
    if (!this.config.enableHeartbeat) return;

    this.stopHeartbeat();
    
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat();
    }, this.config.heartbeatInterval);
    
    this.log('Heartbeat started');
  }

  /**
   * 하트비트 중지
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
      this.log('Heartbeat stopped');
    }
  }

  /**
   * 하트비트 전송
   */
  private async sendHeartbeat(): Promise<void> {
    if (!this.isConnected()) return;

    try {
      const heartbeat = MessageSerializer.createStateMessage('heartbeat', { timestamp: Date.now() });
      await this.sendMessage(heartbeat);
    } catch (error) {
      this.log(`Heartbeat failed: ${error}`);
    }
  }

  /**
   * 이벤트 발생
   */
  private emit<K extends keyof NetworkManagerEvents>(
    event: K,
    ...args: Parameters<NetworkManagerEvents[K]>
  ): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach(listener => {
        try {
          (listener as any)(...args);
        } catch (error) {
          this.log(`Error in event listener for ${event}: ${error}`);
        }
      });
    }
  }

  /**
   * 로그 출력
   */
  private log(message: string): void {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] [NetworkManager] ${message}`);
  }

  /**
   * 네트워크 관리자 종료
   */
  public shutdown(): void {
    this.log('Shutting down network manager');
    
    this.stopHeartbeat();
    this.client.disconnect();
    this.router.shutdown();
    this.eventListeners.clear();
    
    this.log('Network manager shutdown complete');
  }

  /**
   * 헬스 체크
   */
  public isHealthy(): boolean {
    return this.client.isConnected() && this.router.isHealthy();
  }

  /**
   * 디버그 정보 반환
   */
  public getDebugInfo(): {
    config: NetworkManagerConfig;
    client: any;
    router: any;
    isHealthy: boolean;
  } {
    return {
      config: this.config,
      client: this.client.getConnectionStats(),
      router: this.router.getDebugInfo(),
      isHealthy: this.isHealthy()
    };
  }
}

// 모든 export
export * from './WebSocketClient';
export * from './MessageRouter';
export * from './MessageHandler'; 