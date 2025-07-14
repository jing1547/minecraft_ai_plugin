import WebSocket from 'ws';
import { BaseMessage, CommandMessage, MessageType, Priority } from '../protocol/types';
import { MessageSerializer } from '../protocol/MessageSerializer';

export interface WebSocketClientConfig {
  host: string;
  port: number;
  reconnectInterval: number;
  maxReconnectInterval: number;
  maxRetries: number;
  timeoutMs: number;
  enableLogging: boolean;
  enableMessageQueue: boolean;
  maxQueueSize: number;
}

export enum ConnectionState {
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  RECONNECTING = 'reconnecting',
  FAILED = 'failed'
}

export interface WebSocketClientEvents {
  'connected': () => void;
  'disconnected': (reason: string) => void;
  'message': (message: BaseMessage) => void;
  'error': (error: Error) => void;
  'reconnecting': (attempt: number) => void;
  'reconnectFailed': (error: Error) => void;
  'stateChange': (state: ConnectionState) => void;
}

/**
 * WebSocket 클라이언트 클래스
 * Java 서버와의 WebSocket 연결을 관리하고 메시지 교환을 처리
 */
export class WebSocketClient {
  private config: WebSocketClientConfig;
  private ws: WebSocket | null = null;
  private connectionState: ConnectionState = ConnectionState.DISCONNECTED;
  private reconnectAttempts: number = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private messageQueue: BaseMessage[] = [];
  private eventListeners: Map<string, Function[]> = new Map();
  private lastError: Error | null = null;
  private connectionId: string | null = null;

  constructor(config: Partial<WebSocketClientConfig> = {}) {
    this.config = {
      host: 'localhost',
      port: 8080,
      reconnectInterval: 1000,
      maxReconnectInterval: 60000,
      maxRetries: 10,
      timeoutMs: 5000,
      enableLogging: true,
      enableMessageQueue: true,
      maxQueueSize: 100,
      ...config
    };

    this.initializeEventListeners();
  }

  /**
   * 이벤트 리스너 초기화
   */
  private initializeEventListeners(): void {
    Object.keys(this.eventListeners).forEach(event => {
      this.eventListeners.set(event, []);
    });
  }

  /**
   * 서버에 연결
   */
  public async connect(): Promise<void> {
    if (this.connectionState === ConnectionState.CONNECTED || 
        this.connectionState === ConnectionState.CONNECTING) {
      this.log('Already connected or connecting');
      return;
    }

    this.setState(ConnectionState.CONNECTING);
    this.log(`Connecting to ws://${this.config.host}:${this.config.port}`);

    try {
      await this.establishConnection();
    } catch (error) {
      this.onConnectionError(error);
      throw error;
    }
  }

  /**
   * 실제 WebSocket 연결 수립
   */
  private establishConnection(): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = `ws://${this.config.host}:${this.config.port}`;
      this.ws = new WebSocket(url);

      const connectTimeout = setTimeout(() => {
        if (this.ws) {
          this.ws.terminate();
          reject(new Error('Connection timeout'));
        }
      }, this.config.timeoutMs);

      this.ws.onopen = () => {
        clearTimeout(connectTimeout);
        this.onConnectionOpen();
        resolve();
      };

      this.ws.onmessage = (event) => {
        this.onMessage(event.data);
      };

      this.ws.onclose = (event) => {
        clearTimeout(connectTimeout);
        this.onConnectionClose(event.code, event.reason);
      };

      this.ws.onerror = (error) => {
        clearTimeout(connectTimeout);
        this.onConnectionError(error);
        reject(error);
      };
    });
  }

  /**
   * 연결 열림 처리
   */
  private onConnectionOpen(): void {
    this.log('WebSocket connection established');
    this.setState(ConnectionState.CONNECTED);
    this.reconnectAttempts = 0;
    this.lastError = null;
    
    // 대기 중인 메시지 전송
    this.sendQueuedMessages();
    
    this.emit('connected');
  }

  /**
   * 메시지 수신 처리
   */
  private onMessage(data: any): void {
    let messageData: string = '';
    
    try {
      if (typeof data === 'string') {
        messageData = data;
      } else if (Buffer.isBuffer(data)) {
        messageData = data.toString();
      } else if (data instanceof ArrayBuffer) {
        messageData = Buffer.from(data).toString();
      } else {
        messageData = String(data);
      }
      
      // 디버깅을 위해 원본 메시지 로깅
      this.log(`Raw message received: ${messageData}`);
      
      // null 또는 빈 메시지 처리
      if (!messageData || messageData.trim() === '' || messageData.trim() === 'null') {
        this.log('Received empty or null message, ignoring');
        return;
      }
      
      // ping/pong 메시지는 프로토콜 외부의 연결 유지 메시지
      const parsed = JSON.parse(messageData);
      
      // JSON.parse 결과가 null인 경우도 처리
      if (parsed === null || typeof parsed !== 'object') {
        this.log('Received invalid JSON message, ignoring');
        return;
      }
      if (parsed.type === 'ping') {
        this.log('Received ping, sending pong');
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          // Java 서버가 기대하는 BaseMessage 형식으로 pong 응답
          const pongMessage = {
            type: 'pong',
            id: `pong-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            timestamp: new Date().toISOString(),
            version: '1.0.0',
            payload: { acknowledged: true }
          };
          this.ws.send(JSON.stringify(pongMessage));
        }
        return;
      }
      
      const message = MessageSerializer.deserialize(messageData);
      this.log(`Parsed message: ${message.type}`);
      
      // 연결 확인 메시지 처리
      if (message.type === MessageType.STATE && message.payload?.stateType === 'connection') {
        this.connectionId = message.payload.data?.connectionId;
        this.log(`Connection ID assigned: ${this.connectionId}`);
      }
      
      this.emit('message', message);
    } catch (error) {
      this.log(`Failed to parse message: ${error}`);
      this.log(`Raw message that failed: ${messageData}`);
      this.emit('error', error as Error);
    }
  }

  /**
   * 연결 닫힘 처리
   */
  private onConnectionClose(code: number, reason: string): void {
    this.log(`WebSocket connection closed: ${code} - ${reason}`);
    this.setState(ConnectionState.DISCONNECTED);
    this.ws = null;
    this.connectionId = null;
    
    this.emit('disconnected', reason || 'Connection closed');
    
    // 재연결 시도
    if (this.reconnectAttempts < this.config.maxRetries) {
      this.scheduleReconnect();
    } else {
      this.log('Max reconnect attempts reached');
      this.setState(ConnectionState.FAILED);
    }
  }

  /**
   * 연결 오류 처리
   */
  private onConnectionError(error: any): void {
    this.lastError = error;
    this.log(`WebSocket error: ${error.message}`);
    this.emit('error', error);
  }

  /**
   * 재연결 스케줄링
   */
  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    this.reconnectAttempts++;
    this.setState(ConnectionState.RECONNECTING);
    
    // 지수 백오프 계산
    const delay = Math.min(
      this.config.reconnectInterval * Math.pow(2, this.reconnectAttempts - 1),
      this.config.maxReconnectInterval
    );

    this.log(`Scheduling reconnect attempt ${this.reconnectAttempts} in ${delay}ms`);
    this.emit('reconnecting', this.reconnectAttempts);

    this.reconnectTimer = setTimeout(() => {
      this.log(`Reconnect attempt ${this.reconnectAttempts}`);
      this.connect().catch(error => {
        this.log(`Reconnect failed: ${error.message}`);
        this.emit('reconnectFailed', error);
      });
    }, delay);
  }

  /**
   * 연결 해제
   */
  public disconnect(): void {
    this.log('Disconnecting WebSocket');
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }

    this.setState(ConnectionState.DISCONNECTED);
    this.connectionId = null;
    this.reconnectAttempts = 0;
  }

  /**
   * 메시지 전송
   */
  public sendMessage(message: BaseMessage): boolean {
    if (this.connectionState !== ConnectionState.CONNECTED) {
      if (this.config.enableMessageQueue) {
        this.queueMessage(message);
        this.log(`Message queued: ${message.type}`);
        return true;
      } else {
        this.log('Cannot send message: not connected');
        return false;
      }
    }

    try {
      const serialized = MessageSerializer.serialize(message);
      this.ws!.send(serialized);
      this.log(`Message sent: ${message.type}`);
      return true;
    } catch (error) {
      this.log(`Failed to send message: ${error}`);
      this.emit('error', error as Error);
      return false;
    }
  }

  /**
   * 메시지 큐에 추가
   */
  private queueMessage(message: BaseMessage): void {
    if (this.messageQueue.length >= this.config.maxQueueSize) {
      this.messageQueue.shift(); // 오래된 메시지 제거
      this.log('Message queue full, removed oldest message');
    }
    
    this.messageQueue.push(message);
  }

  /**
   * 큐에 저장된 메시지 전송
   */
  private sendQueuedMessages(): void {
    if (this.messageQueue.length === 0) return;

    this.log(`Sending ${this.messageQueue.length} queued messages`);
    
    const messages = [...this.messageQueue];
    this.messageQueue = [];
    
    messages.forEach(message => {
      this.sendMessage(message);
    });
  }

  /**
   * 연결 상태 변경
   */
  private setState(state: ConnectionState): void {
    if (this.connectionState !== state) {
      this.connectionState = state;
      this.emit('stateChange', state);
    }
  }

  /**
   * 이벤트 리스너 등록
   */
  public on<K extends keyof WebSocketClientEvents>(
    event: K,
    listener: WebSocketClientEvents[K]
  ): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(listener);
  }

  /**
   * 이벤트 리스너 제거
   */
  public off<K extends keyof WebSocketClientEvents>(
    event: K,
    listener: WebSocketClientEvents[K]
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
   * 이벤트 발생
   */
  private emit<K extends keyof WebSocketClientEvents>(
    event: K,
    ...args: Parameters<WebSocketClientEvents[K]>
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
    if (this.config.enableLogging) {
      const timestamp = new Date().toISOString();
      console.log(`[${timestamp}] [WebSocketClient] ${message}`);
    }
  }

  /**
   * 현재 연결 상태 반환
   */
  public getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * 연결 ID 반환
   */
  public getConnectionId(): string | null {
    return this.connectionId;
  }

  /**
   * 연결 상태 확인
   */
  public isConnected(): boolean {
    return this.connectionState === ConnectionState.CONNECTED;
  }

  /**
   * 재연결 시도 횟수 반환
   */
  public getReconnectAttempts(): number {
    return this.reconnectAttempts;
  }

  /**
   * 마지막 오류 반환
   */
  public getLastError(): Error | null {
    return this.lastError;
  }

  /**
   * 큐 크기 반환
   */
  public getQueueSize(): number {
    return this.messageQueue.length;
  }

  /**
   * 설정 업데이트
   */
  public updateConfig(config: Partial<WebSocketClientConfig>): void {
    this.config = { ...this.config, ...config };
    this.log('Configuration updated');
  }

  /**
   * 연결 통계 반환
   */
  public getConnectionStats(): {
    state: ConnectionState;
    reconnectAttempts: number;
    queueSize: number;
    connectionId: string | null;
    lastError: string | null;
  } {
    return {
      state: this.connectionState,
      reconnectAttempts: this.reconnectAttempts,
      queueSize: this.messageQueue.length,
      connectionId: this.connectionId,
      lastError: this.lastError ? this.lastError.message : null
    };
  }
} 