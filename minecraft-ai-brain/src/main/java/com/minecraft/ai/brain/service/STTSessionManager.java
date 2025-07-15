package com.minecraft.ai.brain.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * STT 세션 관리자
 * 오류 복구와 세션 타임아웃을 처리합니다.
 */
public class STTSessionManager {
    
    private static final Logger logger = Logger.getLogger(STTSessionManager.class.getName());
    
    // 세션 타임아웃 설정
    private static final long SESSION_TIMEOUT = 30000; // 30초
    private static final long CLEANUP_INTERVAL = 5000; // 5초마다 정리
    private static final int MAX_ERROR_COUNT = 3; // 최대 오류 횟수
    private static final long ERROR_RESET_TIME = 60000; // 1분 후 오류 카운트 리셋
    
    // 세션 관리
    private final Map<UUID, STTSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    // 오류 추적
    private final Map<UUID, ErrorTracker> errorTrackers = new ConcurrentHashMap<>();
    
    /**
     * STT 세션 정보
     */
    private static class STTSession {
        private final UUID playerId;
        private final long startTime;
        private long lastActivityTime;
        private SessionState state;
        private int processedFrames;
        private int errorCount;
        
        public STTSession(UUID playerId) {
            this.playerId = playerId;
            this.startTime = System.currentTimeMillis();
            this.lastActivityTime = startTime;
            this.state = SessionState.ACTIVE;
            this.processedFrames = 0;
            this.errorCount = 0;
        }
        
        public void updateActivity() {
            this.lastActivityTime = System.currentTimeMillis();
            this.processedFrames++;
        }
        
        public boolean isTimedOut() {
            return System.currentTimeMillis() - lastActivityTime > SESSION_TIMEOUT;
        }
        
        public void incrementErrorCount() {
            this.errorCount++;
        }
        
        // Getters
        public UUID getPlayerId() { return playerId; }
        public long getStartTime() { return startTime; }
        public long getLastActivityTime() { return lastActivityTime; }
        public SessionState getState() { return state; }
        public void setState(SessionState state) { this.state = state; }
        public int getProcessedFrames() { return processedFrames; }
        public int getErrorCount() { return errorCount; }
    }
    
    /**
     * 세션 상태
     */
    private enum SessionState {
        ACTIVE,      // 활성 상태
        SUSPENDED,   // 일시 중지 (오류 복구 대기)
        TERMINATED   // 종료됨
    }
    
    /**
     * 오류 추적기
     */
    private static class ErrorTracker {
        private int totalErrors;
        private long firstErrorTime;
        private long lastErrorTime;
        private String lastErrorMessage;
        
        public ErrorTracker() {
            this.totalErrors = 0;
            this.firstErrorTime = 0;
            this.lastErrorTime = 0;
            this.lastErrorMessage = "";
        }
        
        public void recordError(String message) {
            long currentTime = System.currentTimeMillis();
            
            // 오류 리셋 시간이 지났으면 카운트 초기화
            if (firstErrorTime > 0 && currentTime - firstErrorTime > ERROR_RESET_TIME) {
                reset();
            }
            
            if (totalErrors == 0) {
                firstErrorTime = currentTime;
            }
            
            totalErrors++;
            lastErrorTime = currentTime;
            lastErrorMessage = message;
        }
        
        public void reset() {
            totalErrors = 0;
            firstErrorTime = 0;
            lastErrorTime = 0;
            lastErrorMessage = "";
        }
        
        public boolean shouldSuspend() {
            return totalErrors >= MAX_ERROR_COUNT;
        }
        
        // Getters
        public int getTotalErrors() { return totalErrors; }
        public long getFirstErrorTime() { return firstErrorTime; }
        public long getLastErrorTime() { return lastErrorTime; }
        public String getLastErrorMessage() { return lastErrorMessage; }
    }
    
    /**
     * 생성자
     */
    public STTSessionManager() {
        // 주기적 세션 정리 시작
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupSessions, 
            CLEANUP_INTERVAL, 
            CLEANUP_INTERVAL, 
            TimeUnit.MILLISECONDS
        );
        
        logger.info("STTSessionManager initialized");
    }
    
    /**
     * 새 세션 생성
     * @param playerId 플레이어 ID
     * @return 생성된 세션
     */
    public STTSession createSession(UUID playerId) {
        // 기존 세션이 있으면 종료
        terminateSession(playerId);
        
        // 오류 추적기 초기화
        ErrorTracker tracker = errorTrackers.get(playerId);
        if (tracker != null && tracker.shouldSuspend()) {
            logger.warning("Player " + playerId + " is suspended due to excessive errors");
            return null;
        }
        
        // 새 세션 생성
        STTSession session = new STTSession(playerId);
        sessions.put(playerId, session);
        
        logger.info("Created new STT session for player: " + playerId);
        return session;
    }
    
    /**
     * 세션 가져오기
     * @param playerId 플레이어 ID
     * @return 세션 또는 null
     */
    public STTSession getSession(UUID playerId) {
        STTSession session = sessions.get(playerId);
        
        if (session != null && session.isTimedOut()) {
            logger.warning("Session timed out for player: " + playerId);
            terminateSession(playerId);
            return null;
        }
        
        return session;
    }
    
    /**
     * 세션 활동 업데이트
     * @param playerId 플레이어 ID
     */
    public void updateSessionActivity(UUID playerId) {
        STTSession session = sessions.get(playerId);
        if (session != null) {
            session.updateActivity();
        }
    }
    
    /**
     * 오류 기록
     * @param playerId 플레이어 ID
     * @param errorMessage 오류 메시지
     */
    public void recordError(UUID playerId, String errorMessage) {
        // 오류 추적기 업데이트
        ErrorTracker tracker = errorTrackers.computeIfAbsent(playerId, k -> new ErrorTracker());
        tracker.recordError(errorMessage);
        
        // 세션 오류 카운트 증가
        STTSession session = sessions.get(playerId);
        if (session != null) {
            session.incrementErrorCount();
            
            // 오류가 많으면 세션 일시 중지
            if (tracker.shouldSuspend()) {
                session.setState(SessionState.SUSPENDED);
                logger.severe("Session suspended for player " + playerId + 
                            " due to excessive errors: " + errorMessage);
            }
        }
        
        logger.warning("STT error for player " + playerId + ": " + errorMessage);
    }
    
    /**
     * 오류 추적기 리셋
     * @param playerId 플레이어 ID
     */
    public void resetErrorTracker(UUID playerId) {
        ErrorTracker tracker = errorTrackers.get(playerId);
        if (tracker != null) {
            tracker.reset();
            logger.info("Reset error tracker for player: " + playerId);
        }
    }
    
    /**
     * 세션 복구 시도
     * @param playerId 플레이어 ID
     * @return 복구 성공 여부
     */
    public boolean attemptRecovery(UUID playerId) {
        STTSession session = sessions.get(playerId);
        ErrorTracker tracker = errorTrackers.get(playerId);
        
        if (session == null || tracker == null) {
            return false;
        }
        
        // 일시 중지된 세션만 복구 가능
        if (session.getState() != SessionState.SUSPENDED) {
            return false;
        }
        
        // 일정 시간이 지나면 복구 시도
        long timeSinceLastError = System.currentTimeMillis() - tracker.getLastErrorTime();
        if (timeSinceLastError > 10000) { // 10초 후 복구 시도
            session.setState(SessionState.ACTIVE);
            tracker.reset();
            logger.info("Recovered STT session for player: " + playerId);
            return true;
        }
        
        return false;
    }
    
    /**
     * 세션 종료
     * @param playerId 플레이어 ID
     */
    public void terminateSession(UUID playerId) {
        STTSession session = sessions.remove(playerId);
        if (session != null) {
            session.setState(SessionState.TERMINATED);
            logger.info("Terminated STT session for player: " + playerId + 
                      " (processed " + session.getProcessedFrames() + " frames)");
        }
    }
    
    /**
     * 주기적 세션 정리
     */
    private void cleanupSessions() {
        try {
            long currentTime = System.currentTimeMillis();
            
            sessions.entrySet().removeIf(entry -> {
                STTSession session = entry.getValue();
                
                // 타임아웃된 세션 제거
                if (session.isTimedOut()) {
                    logger.info("Removing timed out session for player: " + entry.getKey());
                    return true;
                }
                
                // 종료된 세션 제거
                if (session.getState() == SessionState.TERMINATED) {
                    return true;
                }
                
                // 일시 중지된 세션 복구 시도
                if (session.getState() == SessionState.SUSPENDED) {
                    attemptRecovery(entry.getKey());
                }
                
                return false;
            });
            
            // 오래된 오류 추적기 정리
            errorTrackers.entrySet().removeIf(entry -> {
                ErrorTracker tracker = entry.getValue();
                return tracker.getTotalErrors() == 0 || 
                       (currentTime - tracker.getLastErrorTime() > ERROR_RESET_TIME * 2);
            });
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during session cleanup", e);
        }
    }
    
    /**
     * 세션 통계 가져오기
     * @return 통계 맵
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        stats.put("active_sessions", sessions.size());
        stats.put("error_trackers", errorTrackers.size());
        
        int activeSessions = 0;
        int suspendedSessions = 0;
        int totalFrames = 0;
        
        for (STTSession session : sessions.values()) {
            if (session.getState() == SessionState.ACTIVE) {
                activeSessions++;
            } else if (session.getState() == SessionState.SUSPENDED) {
                suspendedSessions++;
            }
            totalFrames += session.getProcessedFrames();
        }
        
        stats.put("active_count", activeSessions);
        stats.put("suspended_count", suspendedSessions);
        stats.put("total_frames_processed", totalFrames);
        
        return stats;
    }
    
    /**
     * 서비스 종료
     */
    public void shutdown() {
        logger.info("Shutting down STTSessionManager");
        
        // 모든 세션 종료
        sessions.keySet().forEach(this::terminateSession);
        
        // 정리 스케줄러 종료
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("STTSessionManager shutdown complete");
    }
}