import { EventEmitter } from 'events';
import { BaseMessage, CommandMessage } from '../protocol/types';

/**
 * 연결 상태 열거형
 */
export enum ConnectionHealth {
  HEALTHY = 'healthy',
  DEGRADED = 'degraded',
  UNHEALTHY = 'unhealthy',
  CRITICAL = 'critical'
}

/**
 * Circuit Breaker 상태
 */
export enum CircuitBreakerState {
  CLOSED = 'closed',      // 정상 동작
  OPEN = 'open',          // 연결 차단
  HALF_OPEN = 'half_open' // 테스트 연결
}

/**
 * 연결 메트릭
 */
export interface ConnectionMetrics {
  totalConnections: number;
  successfulConnections: number;
  failedConnections: number;
  reconnectionAttempts: number;
  lastSuccessfulConnection: number;
  lastFailedConnection: number;
  averageConnectionTime: number;
  heartbeatFailures: number;
  messagesSent: number;
  messagesReceived: number;
  messagesLost: number;
  uptime: number;
}

/**
 * Circuit Breaker 설정
 */
export interface CircuitBreakerConfig {
  failureThreshold: number;    // 실패 임계값
  recoveryTimeout: number;     // 복구 시간 (ms)
  halfOpenMaxCalls: number;    // half-open 상태에서 최대 호출 수
  monitoringPeriod: number;    // 모니터링 주기 (ms)
}

/**
 * 중요 메시지 정보
 */
export interface CriticalMessage {
  message: BaseMessage;
  timestamp: number;
  retryCount: number;
  maxRetries: number;
  priority: number;
}

/**
 * 연결 모니터 설정
 */
export interface ConnectionMonitorConfig {
  heartbeatInterval: number;
  heartbeatTimeout: number;
  maxReconnectAttempts: number;
  reconnectDelay: number;
  maxReconnectDelay: number;
  circuitBreaker: CircuitBreakerConfig;
  enableMessageReplay: boolean;
  maxCriticalMessages: number;
  healthCheckInterval: number;
  metricsRetentionPeriod: number;
}

/**
 * 연결 상태 모니터링 및 복구 관리 클래스
 */
export class ConnectionMonitor extends EventEmitter {
  private config: ConnectionMonitorConfig;
  private metrics: ConnectionMetrics;
  private circuitBreakerState: CircuitBreakerState = CircuitBreakerState.CLOSED;
  private criticalMessages: Map<string, CriticalMessage> = new Map();
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private healthCheckTimer: NodeJS.Timeout | null = null;
  private lastHeartbeatResponse: number = 0;
  private connectionStartTime: number = 0;
  private circuitBreakerFailures: number = 0;
  private circuitBreakerLastFailure: number = 0;
  private halfOpenCallCount: number = 0;
  private isShuttingDown: boolean = false;

  constructor(config: Partial<ConnectionMonitorConfig> = {}) {
    super();
    
    this.config = {
      heartbeatInterval: 30000,      // 30초
      heartbeatTimeout: 10000,       // 10초
      maxReconnectAttempts: 10,
      reconnectDelay: 1000,          // 1초
      maxReconnectDelay: 60000,      // 60초
      circuitBreaker: {
        failureThreshold: 5,
        recoveryTimeout: 30000,      // 30초
        halfOpenMaxCalls: 3,
        monitoringPeriod: 60000      // 1분
      },
      enableMessageReplay: true,
      maxCriticalMessages: 100,
      healthCheckInterval: 5000,     // 5초
      metricsRetentionPeriod: 3600000, // 1시간
      ...config
    };

    this.metrics = {
      totalConnections: 0,
      successfulConnections: 0,
      failedConnections: 0,
      reconnectionAttempts: 0,
      lastSuccessfulConnection: 0,
      lastFailedConnection: 0,
      averageConnectionTime: 0,
      heartbeatFailures: 0,
      messagesSent: 0,
      messagesReceived: 0,
      messagesLost: 0,
      uptime: Date.now()
    };

    this.startHealthCheck();
    this.startHeartbeat();
  }

  /**
   * 연결 시작 기록
   */
  public onConnectionStart(): void {
    this.connectionStartTime = Date.now();
    this.metrics.totalConnections++;
    this.log('Connection attempt started');
  }

  /**
   * 연결 성공 기록
   */
  public onConnectionSuccess(): void {
    const connectionTime = Date.now() - this.connectionStartTime;
    this.metrics.successfulConnections++;
    this.metrics.lastSuccessfulConnection = Date.now();
    
    // 평균 연결 시간 계산
    this.updateAverageConnectionTime(connectionTime);
    
    // Circuit Breaker 상태 업데이트
    this.onCircuitBreakerSuccess();
    
    // 중요 메시지 재생
    this.replayCriticalMessages();
    
    this.log(`Connection successful (${connectionTime}ms)`);
    this.emit('connectionSuccess', connectionTime);
  }

  /**
   * 연결 실패 기록
   */
  public onConnectionFailure(error: Error): void {
    this.metrics.failedConnections++;
    this.metrics.lastFailedConnection = Date.now();
    
    // Circuit Breaker 상태 업데이트
    this.onCircuitBreakerFailure();
    
    this.log(`Connection failed: ${error.message}`);
    this.emit('connectionFailure', error);
  }

  /**
   * 재연결 시도 기록
   */
  public onReconnectionAttempt(): void {
    this.metrics.reconnectionAttempts++;
    this.log(`Reconnection attempt #${this.metrics.reconnectionAttempts}`);
    this.emit('reconnectionAttempt', this.metrics.reconnectionAttempts);
  }

  /**
   * 하트비트 응답 기록
   */
  public onHeartbeatResponse(): void {
    this.lastHeartbeatResponse = Date.now();
    this.log('Heartbeat response received');
  }

  /**
   * 하트비트 실패 기록
   */
  public onHeartbeatFailure(): void {
    this.metrics.heartbeatFailures++;
    this.log('Heartbeat failed');
    this.emit('heartbeatFailure');
  }

  /**
   * 메시지 전송 기록
   */
  public onMessageSent(message: BaseMessage): void {
    this.metrics.messagesSent++;
    
    // 중요 메시지인 경우 저장
    if (this.isCriticalMessage(message)) {
      this.storeCriticalMessage(message);
    }
  }

  /**
   * 메시지 수신 기록
   */
  public onMessageReceived(message: BaseMessage): void {
    this.metrics.messagesReceived++;
    
    // 응답 메시지인 경우 중요 메시지에서 제거
    if (message.correlationId) {
      this.removeCriticalMessage(message.correlationId);
    }
  }

  /**
   * 메시지 손실 기록
   */
  public onMessageLost(): void {
    this.metrics.messagesLost++;
    this.log('Message lost');
    this.emit('messageLost');
  }

  /**
   * Circuit Breaker 상태 확인
   */
  public canAttemptConnection(): boolean {
    switch (this.circuitBreakerState) {
      case CircuitBreakerState.CLOSED:
        return true;
      
      case CircuitBreakerState.OPEN:
        // 복구 시간이 지났는지 확인
        const timeSinceLastFailure = Date.now() - this.circuitBreakerLastFailure;
        if (timeSinceLastFailure > this.config.circuitBreaker.recoveryTimeout) {
          this.circuitBreakerState = CircuitBreakerState.HALF_OPEN;
          this.halfOpenCallCount = 0;
          this.log('Circuit breaker moved to HALF_OPEN state');
          return true;
        }
        return false;
      
      case CircuitBreakerState.HALF_OPEN:
        return this.halfOpenCallCount < this.config.circuitBreaker.halfOpenMaxCalls;
      
      default:
        return false;
    }
  }

  /**
   * 연결 상태 확인
   */
  public getConnectionHealth(): ConnectionHealth {
    const now = Date.now();
    const timeSinceLastSuccess = now - this.metrics.lastSuccessfulConnection;
    const timeSinceLastHeartbeat = now - this.lastHeartbeatResponse;
    
    // Critical: 매우 오랫동안 연결되지 않음
    if (timeSinceLastSuccess > 300000 || this.circuitBreakerState === CircuitBreakerState.OPEN) {
      return ConnectionHealth.CRITICAL;
    }
    
    // Unhealthy: 하트비트 실패가 많거나 최근 연결 실패가 많음
    if (timeSinceLastHeartbeat > this.config.heartbeatTimeout * 2 || 
        this.metrics.heartbeatFailures > 5) {
      return ConnectionHealth.UNHEALTHY;
    }
    
    // Degraded: 일부 문제가 있지만 동작 가능
    if (timeSinceLastHeartbeat > this.config.heartbeatTimeout || 
        this.metrics.failedConnections > this.metrics.successfulConnections) {
      return ConnectionHealth.DEGRADED;
    }
    
    return ConnectionHealth.HEALTHY;
  }

  /**
   * 중요 메시지 저장
   */
  private storeCriticalMessage(message: BaseMessage): void {
    if (!this.config.enableMessageReplay) return;
    
    const criticalMessage: CriticalMessage = {
      message,
      timestamp: Date.now(),
      retryCount: 0,
      maxRetries: 3,
      priority: this.getMessagePriority(message)
    };
    
    this.criticalMessages.set(message.id, criticalMessage);
    
    // 최대 개수 초과 시 오래된 메시지 제거
    if (this.criticalMessages.size > this.config.maxCriticalMessages) {
      const oldestMessageId = this.getOldestCriticalMessageId();
      if (oldestMessageId) {
        this.criticalMessages.delete(oldestMessageId);
      }
    }
    
    this.log(`Critical message stored: ${message.id}`);
  }

  /**
   * 중요 메시지 제거
   */
  private removeCriticalMessage(messageId: string): void {
    if (this.criticalMessages.delete(messageId)) {
      this.log(`Critical message removed: ${messageId}`);
    }
  }

  /**
   * 중요 메시지 재생
   */
  private async replayCriticalMessages(): Promise<void> {
    if (!this.config.enableMessageReplay || this.criticalMessages.size === 0) {
      return;
    }
    
    this.log(`Replaying ${this.criticalMessages.size} critical messages`);
    
    // 우선순위 순으로 정렬
    const sortedMessages = Array.from(this.criticalMessages.values())
      .sort((a, b) => b.priority - a.priority || a.timestamp - b.timestamp);
    
    for (const criticalMessage of sortedMessages) {
      try {
        criticalMessage.retryCount++;
        this.emit('replayMessage', criticalMessage.message);
        this.log(`Replayed message: ${criticalMessage.message.id} (attempt ${criticalMessage.retryCount})`);
        
        // 최대 재시도 횟수 초과 시 제거
        if (criticalMessage.retryCount >= criticalMessage.maxRetries) {
          this.criticalMessages.delete(criticalMessage.message.id);
          this.log(`Critical message expired: ${criticalMessage.message.id}`);
        }
        
        // 재생 간격
        await this.delay(100);
        
      } catch (error) {
        this.log(`Failed to replay message ${criticalMessage.message.id}: ${error}`);
      }
    }
  }

  /**
   * Circuit Breaker 성공 처리
   */
  private onCircuitBreakerSuccess(): void {
    if (this.circuitBreakerState === CircuitBreakerState.HALF_OPEN) {
      this.circuitBreakerState = CircuitBreakerState.CLOSED;
      this.circuitBreakerFailures = 0;
      this.log('Circuit breaker moved to CLOSED state');
    }
  }

  /**
   * Circuit Breaker 실패 처리
   */
  private onCircuitBreakerFailure(): void {
    this.circuitBreakerFailures++;
    this.circuitBreakerLastFailure = Date.now();
    
    if (this.circuitBreakerState === CircuitBreakerState.HALF_OPEN) {
      this.halfOpenCallCount++;
    }
    
    if (this.circuitBreakerFailures >= this.config.circuitBreaker.failureThreshold) {
      this.circuitBreakerState = CircuitBreakerState.OPEN;
      this.log(`Circuit breaker OPENED after ${this.circuitBreakerFailures} failures`);
      this.emit('circuitBreakerOpened');
    }
  }

  /**
   * 하트비트 시작
   */
  private startHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
    }
    
    this.heartbeatTimer = setInterval(() => {
      if (!this.isShuttingDown) {
        this.emit('sendHeartbeat');
      }
    }, this.config.heartbeatInterval);
  }

  /**
   * 헬스 체크 시작
   */
  private startHealthCheck(): void {
    if (this.healthCheckTimer) {
      clearInterval(this.healthCheckTimer);
    }
    
    this.healthCheckTimer = setInterval(() => {
      if (!this.isShuttingDown) {
        const health = this.getConnectionHealth();
        this.emit('healthCheck', health);
        
        // 하트비트 타임아웃 체크
        const timeSinceLastHeartbeat = Date.now() - this.lastHeartbeatResponse;
        if (timeSinceLastHeartbeat > this.config.heartbeatTimeout) {
          this.onHeartbeatFailure();
          this.emit('heartbeatTimeout');
        }
      }
    }, this.config.healthCheckInterval);
  }

  /**
   * 메시지가 중요한지 확인
   */
  private isCriticalMessage(message: BaseMessage): boolean {
    // 명령 메시지는 중요한 것으로 간주
    return message.type === 'command' || 
           (!!message.priority && ['high', 'urgent'].includes(message.priority));
  }

  /**
   * 메시지 우선순위 반환
   */
  private getMessagePriority(message: BaseMessage): number {
    switch (message.priority) {
      case 'urgent': return 4;
      case 'high': return 3;
      case 'normal': return 2;
      case 'low': return 1;
      default: return 2;
    }
  }

  /**
   * 가장 오래된 중요 메시지 ID 반환
   */
  private getOldestCriticalMessageId(): string | null {
    let oldestId: string | null = null;
    let oldestTimestamp = Date.now();
    
    for (const [id, message] of this.criticalMessages.entries()) {
      if (message.timestamp < oldestTimestamp) {
        oldestTimestamp = message.timestamp;
        oldestId = id;
      }
    }
    
    return oldestId;
  }

  /**
   * 평균 연결 시간 업데이트
   */
  private updateAverageConnectionTime(connectionTime: number): void {
    const totalConnections = this.metrics.successfulConnections;
    const currentAverage = this.metrics.averageConnectionTime;
    
    this.metrics.averageConnectionTime = 
      (currentAverage * (totalConnections - 1) + connectionTime) / totalConnections;
  }

  /**
   * 지연 함수
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * 로그 출력
   */
  private log(message: string): void {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] [ConnectionMonitor] ${message}`);
  }

  /**
   * 메트릭 반환
   */
  public getMetrics(): ConnectionMetrics {
    return { ...this.metrics };
  }

  /**
   * Circuit Breaker 상태 반환
   */
  public getCircuitBreakerState(): CircuitBreakerState {
    return this.circuitBreakerState;
  }

  /**
   * 중요 메시지 개수 반환
   */
  public getCriticalMessageCount(): number {
    return this.criticalMessages.size;
  }

  /**
   * 진단 정보 반환
   */
  public getDiagnostics(): {
    health: ConnectionHealth;
    metrics: ConnectionMetrics;
    circuitBreakerState: CircuitBreakerState;
    criticalMessageCount: number;
    timeSinceLastHeartbeat: number;
    isShuttingDown: boolean;
  } {
    return {
      health: this.getConnectionHealth(),
      metrics: this.getMetrics(),
      circuitBreakerState: this.circuitBreakerState,
      criticalMessageCount: this.criticalMessages.size,
      timeSinceLastHeartbeat: Date.now() - this.lastHeartbeatResponse,
      isShuttingDown: this.isShuttingDown
    };
  }

  /**
   * 설정 업데이트
   */
  public updateConfig(config: Partial<ConnectionMonitorConfig>): void {
    this.config = { ...this.config, ...config };
    
    // 타이머 재시작
    this.startHeartbeat();
    this.startHealthCheck();
    
    this.log('Connection monitor configuration updated');
  }

  /**
   * 종료
   */
  public shutdown(): void {
    this.isShuttingDown = true;
    this.log('Connection monitor shutting down');
    
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    
    if (this.healthCheckTimer) {
      clearInterval(this.healthCheckTimer);
      this.healthCheckTimer = null;
    }
    
    // 중요 메시지 정리
    this.criticalMessages.clear();
    
    this.removeAllListeners();
    this.log('Connection monitor shutdown complete');
  }
} 