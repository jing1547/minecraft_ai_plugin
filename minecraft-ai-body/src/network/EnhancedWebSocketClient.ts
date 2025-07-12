import WebSocket from 'ws';
import { BaseMessage, MessageType } from '../protocol/types';
import { MessageSerializer } from '../protocol/MessageSerializer';
import { ConnectionMonitor, ConnectionHealth, CircuitBreakerState, ConnectionMonitorConfig } from './ConnectionMonitor';

export interface EnhancedWebSocketClientConfig {
  host: string;
  port: number;
  reconnectInterval: number;
  maxReconnectInterval: number;
  maxRetries: number;
  timeoutMs: number;
  enableLogging: boolean;
  enableMessageQueue: boolean;
  maxQueueSize: number;
  monitor: Partial<ConnectionMonitorConfig>;
}

export enum EnhancedConnectionState {
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  RECONNECTING = 'reconnecting',
  FAILED = 'failed',
  CIRCUIT_BREAKER_OPEN = 'circuit_breaker_open'
}

export interface EnhancedWebSocketClientEvents {
  'connected': () => void;
  'disconnected': (reason: string) => void;
  'message': (message: BaseMessage) => void;
  'error': (error: Error) => void;
  'reconnecting': (attempt: number) => void;
  'reconnectFailed': (error: Error) => void;
  'stateChange': (state: EnhancedConnectionState) => void;
  'healthChange': (health: ConnectionHealth) => void;
  'circuitBreakerOpened': () => void;
  'messageReplayed': (message: BaseMessage) => void;
}

/**
 * 향상된 WebSocket 클라이언트 클래스
 * ConnectionMonitor를 통합하여 더 강력한 오류 처리 및 복구 메커니즘 제공
 */
export class EnhancedWebSocketClient {
  private config: EnhancedWebSocketClientConfig;
  private ws: WebSocket | null = null;
  private connectionState: EnhancedConnectionState = EnhancedConnectionState.DISCONNECTED;
  private reconnectAttempts: number = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private messageQueue: BaseMessage[] = [];
  private eventListeners: Map<string, Function[]> = new Map();
  private lastError: Error | null = null;
  private connectionId: string | null = null;
  private monitor: ConnectionMonitor;
  private gracefulShutdown: boolean = false;

  constructor(config: Partial<EnhancedWebSocketClientConfig> = {}) {
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
      monitor: {},
      ...config
    };

    this.monitor = new ConnectionMonitor(this.config.monitor);
    this.initializeEventListeners();
    this.setupMonitorListeners();
  }

  /**
   * 이벤트 리스너 초기화
   */
  private initializeEventListeners(): void {
    const events: (keyof EnhancedWebSocketClientEvents)[] = [
      'connected', 'disconnected', 'message', 'error', 
      'reconnecting', 'reconnectFailed', 'stateChange',
      'healthChange', 'circuitBreakerOpened', 'messageReplayed'
    ];
    
    events.forEach(event => {
      this.eventListeners.set(event, []);
    });
  }

  /**
   * 모니터 이벤트 리스너 설정
   */
  private setupMonitorListeners(): void {
    this.monitor.on('sendHeartbeat', () => {
      this.sendHeartbeat();
    });

    this.monitor.on('healthCheck', (health: ConnectionHealth) => {
      this.emit('healthChange', health);
      this.handleHealthChange(health);
    });

    this.monitor.on('circuitBreakerOpened', () => {
      this.setState(EnhancedConnectionState.CIRCUIT_BREAKER_OPEN);
      this.emit('circuitBreakerOpened');
    });

    this.monitor.on('replayMessage', (message: BaseMessage) => {
      this.sendMessage(message);
      this.emit('messageReplayed', message);
    });

    this.monitor.on('heartbeatTimeout', () => {
      this.log('Heartbeat timeout detected, initiating reconnection');
      this.handleConnectionLoss();
    });
  }

  /**
   * 서버에 연결
   */
  public async connect(): Promise<void> {
    if (this.connectionState === EnhancedConnectionState.CONNECTED || 
        this.connectionState === EnhancedConnectionState.CONNECTING) {
      this.log('Already connected or connecting');
      return;
    }

    // Circuit Breaker 상태 확인
    if (!this.monitor.canAttemptConnection()) {
      const error = new Error('Connection blocked by circuit breaker');
      this.handleConnectionError(error);
      throw error;
    }

    this.setState(EnhancedConnectionState.CONNECTING);
    this.monitor.onConnectionStart();
    this.log(`Connecting to ws://${this.config.host}:${this.config.port}`);

    try {
      await this.establishConnection();
    } catch (error) {
      this.handleConnectionError(error as Error);
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
    this.setState(EnhancedConnectionState.CONNECTED);
    this.reconnectAttempts = 0;
    this.lastError = null;
    
    // 모니터에 성공 기록
    this.monitor.onConnectionSuccess();
    
    // 대기 중인 메시지 전송
    this.sendQueuedMessages();
    
    this.emit('connected');
  }

  /**
   * 메시지 수신 처리
   */
  private onMessage(data: any): void {
    try {
      let messageData: string;
      
      if (typeof data === 'string') {
        messageData = data;
      } else if (Buffer.isBuffer(data)) {
        messageData = data.toString();
      } else if (data instanceof ArrayBuffer) {
        messageData = Buffer.from(data).toString();
      } else {
        messageData = String(data);
      }
      
      const message = MessageSerializer.deserialize(messageData);
      this.log(`Received message: ${message.type}`);
      
      // 모니터에 메시지 수신 기록
      this.monitor.onMessageReceived(message);
      
      // 연결 확인 메시지 처리
      if (message.type === MessageType.STATE && message.payload?.stateType === 'connection') {
        this.connectionId = message.payload.data?.connectionId;
        this.log(`Connection ID assigned: ${this.connectionId}`);
      }
      
      // 하트비트 응답 처리
      if (this.isHeartbeatResponse(message)) {
        this.monitor.onHeartbeatResponse();
      }
      
      this.emit('message', message);
    } catch (error) {
      this.log(`Failed to parse message: ${error}`);
      this.emit('error', error as Error);
    }
  }

  /**
   * 연결 닫힘 처리
   */
  private onConnectionClose(code: number, reason: string): void {
    this.log(`WebSocket connection closed: ${code} - ${reason}`);
    this.setState(EnhancedConnectionState.DISCONNECTED);
    this.ws = null;
    this.connectionId = null;
    
    this.emit('disconnected', reason || 'Connection closed');
    
    // 정상적인 종료가 아닌 경우에만 재연결 시도
    if (!this.gracefulShutdown && this.reconnectAttempts < this.config.maxRetries) {
      this.scheduleReconnect();
    } else if (this.reconnectAttempts >= this.config.maxRetries) {
      this.log('Max reconnect attempts reached');
      this.setState(EnhancedConnectionState.FAILED);
    }
  }

  /**
   * 연결 오류 처리
   */
  private onConnectionError(error: any): void {
    this.lastError = error;
    this.log(`WebSocket error: ${error.message}`);
    
    // 모니터에 연결 실패 기록
    this.monitor.onConnectionFailure(error);
    
    this.emit('error', error);
  }

  /**
   * 재연결 스케줄링
   */
  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }

    // Circuit Breaker 상태 확인
    if (!this.monitor.canAttemptConnection()) {
      this.log('Reconnection blocked by circuit breaker');
      this.setState(EnhancedConnectionState.CIRCUIT_BREAKER_OPEN);
      return;
    }

    this.reconnectAttempts++;
    this.setState(EnhancedConnectionState.RECONNECTING);
    
    // 모니터에 재연결 시도 기록
    this.monitor.onReconnectionAttempt();
    
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
  public disconnect(graceful: boolean = true): void {
    this.gracefulShutdown = graceful;
    this.log('Disconnecting WebSocket');
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }

    this.setState(EnhancedConnectionState.DISCONNECTED);
    this.connectionId = null;
    this.reconnectAttempts = 0;
  }

  /**
   * 메시지 전송
   */
  public sendMessage(message: BaseMessage): boolean {
    if (this.connectionState !== EnhancedConnectionState.CONNECTED) {
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
      
      // 모니터에 메시지 전송 기록
      this.monitor.onMessageSent(message);
      
      return true;
    } catch (error) {
      this.log(`Failed to send message: ${error}`);
      this.monitor.onMessageLost();
      this.emit('error', error as Error);
      return false;
    }
  }

  /**
   * 하트비트 전송
   */
  private sendHeartbeat(): void {
    if (this.connectionState === EnhancedConnectionState.CONNECTED) {
      try {
        const heartbeat = MessageSerializer.createStateMessage('heartbeat', { 
          timestamp: Date.now(),
          connectionId: this.connectionId
        });
        this.sendMessage(heartbeat);
      } catch (error) {
        this.log(`Failed to send heartbeat: ${error}`);
        this.monitor.onHeartbeatFailure();
      }
    }
  }

  /**
   * 하트비트 응답인지 확인
   */
  private isHeartbeatResponse(message: BaseMessage): boolean {
    return message.type === MessageType.STATE && 
           message.payload?.stateType === 'heartbeat_response';
  }

  /**
   * 헬스 상태 변화 처리
   */
  private handleHealthChange(health: ConnectionHealth): void {
    switch (health) {
      case ConnectionHealth.CRITICAL:
        this.log('Connection health is CRITICAL, attempting reconnection');
        this.handleConnectionLoss();
        break;
      
      case ConnectionHealth.UNHEALTHY:
        this.log('Connection health is UNHEALTHY, monitoring closely');
        break;
      
      case ConnectionHealth.DEGRADED:
        this.log('Connection health is DEGRADED');
        break;
      
      case ConnectionHealth.HEALTHY:
        // 정상 상태, 특별한 조치 불필요
        break;
    }
  }

  /**
   * 연결 손실 처리
   */
  private handleConnectionLoss(): void {
    if (this.connectionState === EnhancedConnectionState.CONNECTED) {
      this.log('Connection loss detected, initiating reconnection');
      this.disconnect(false);
      this.scheduleReconnect();
    }
  }

  /**
   * 연결 오류 처리
   */
  private handleConnectionError(error: Error): void {
    this.lastError = error;
    this.log(`Connection error: ${error.message}`);
    this.monitor.onConnectionFailure(error);
  }

  /**
   * 메시지 큐에 추가
   */
  private queueMessage(message: BaseMessage): void {
    if (this.messageQueue.length >= this.config.maxQueueSize) {
      this.messageQueue.shift(); // 오래된 메시지 제거
      this.log('Message queue full, removed oldest message');
      this.monitor.onMessageLost();
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
  private setState(state: EnhancedConnectionState): void {
    if (this.connectionState !== state) {
      this.connectionState = state;
      this.emit('stateChange', state);
    }
  }

  /**
   * 이벤트 리스너 등록
   */
  public on<K extends keyof EnhancedWebSocketClientEvents>(
    event: K,
    listener: EnhancedWebSocketClientEvents[K]
  ): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(listener);
  }

  /**
   * 이벤트 리스너 제거
   */
  public off<K extends keyof EnhancedWebSocketClientEvents>(
    event: K,
    listener: EnhancedWebSocketClientEvents[K]
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
  private emit<K extends keyof EnhancedWebSocketClientEvents>(
    event: K,
    ...args: Parameters<EnhancedWebSocketClientEvents[K]>
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
      console.log(`[${timestamp}] [EnhancedWebSocketClient] ${message}`);
    }
  }

  /**
   * 현재 연결 상태 반환
   */
  public getConnectionState(): EnhancedConnectionState {
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
    return this.connectionState === EnhancedConnectionState.CONNECTED;
  }

  /**
   * 연결 헬스 상태 반환
   */
  public getConnectionHealth(): ConnectionHealth {
    return this.monitor.getConnectionHealth();
  }

  /**
   * Circuit Breaker 상태 반환
   */
  public getCircuitBreakerState(): CircuitBreakerState {
    return this.monitor.getCircuitBreakerState();
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
   * 연결 통계 반환
   */
  public getConnectionStats(): {
    state: EnhancedConnectionState;
    health: ConnectionHealth;
    circuitBreakerState: CircuitBreakerState;
    reconnectAttempts: number;
    queueSize: number;
    connectionId: string | null;
    lastError: string | null;
    metrics: any;
  } {
    return {
      state: this.connectionState,
      health: this.getConnectionHealth(),
      circuitBreakerState: this.getCircuitBreakerState(),
      reconnectAttempts: this.reconnectAttempts,
      queueSize: this.messageQueue.length,
      connectionId: this.connectionId,
      lastError: this.lastError ? this.lastError.message : null,
      metrics: this.monitor.getMetrics()
    };
  }

  /**
   * 진단 정보 반환
   */
  public getDiagnostics(): any {
    return {
      client: this.getConnectionStats(),
      monitor: this.monitor.getDiagnostics()
    };
  }

  /**
   * 설정 업데이트
   */
  public updateConfig(config: Partial<EnhancedWebSocketClientConfig>): void {
    this.config = { ...this.config, ...config };
    
    if (config.monitor) {
      this.monitor.updateConfig(config.monitor);
    }
    
    this.log('Enhanced WebSocket client configuration updated');
  }

  /**
   * 종료
   */
  public shutdown(): void {
    this.log('Shutting down enhanced WebSocket client');
    
    this.gracefulShutdown = true;
    this.disconnect(true);
    this.monitor.shutdown();
    this.eventListeners.clear();
    
    this.log('Enhanced WebSocket client shutdown complete');
  }
} 