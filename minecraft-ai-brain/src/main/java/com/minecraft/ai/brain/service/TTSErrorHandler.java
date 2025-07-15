package com.minecraft.ai.brain.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * TTS 에러 처리 및 복구 관리자
 * 에러 추적, 자동 재시도, 폴백 처리를 담당합니다.
 */
public class TTSErrorHandler {
    
    private static final Logger logger = Logger.getLogger(TTSErrorHandler.class.getName());
    
    // 에러 임계값 설정
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1초
    private static final long ERROR_WINDOW_MS = 60000; // 1분
    private static final int MAX_ERRORS_PER_WINDOW = 10;
    
    // 에러 추적
    private final Map<ErrorType, ErrorStats> errorStats = new ConcurrentHashMap<>();
    private final AtomicInteger totalErrors = new AtomicInteger(0);
    private final AtomicLong lastErrorTime = new AtomicLong(0);
    
    // 서비스 상태
    private volatile boolean serviceHealthy = true;
    private volatile String lastErrorMessage = "";
    
    /**
     * 에러 유형
     */
    public enum ErrorType {
        AUTHENTICATION_ERROR("인증 오류"),
        NETWORK_ERROR("네트워크 오류"),
        RATE_LIMIT_ERROR("요청 제한 초과"),
        INVALID_INPUT_ERROR("잘못된 입력"),
        SYNTHESIS_ERROR("음성 합성 오류"),
        UNKNOWN_ERROR("알 수 없는 오류");
        
        private final String description;
        
        ErrorType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 에러 통계
     */
    private static class ErrorStats {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong firstOccurrence = new AtomicLong(0);
        private final AtomicLong lastOccurrence = new AtomicLong(0);
        
        public void record() {
            long now = System.currentTimeMillis();
            count.incrementAndGet();
            lastOccurrence.set(now);
            
            if (firstOccurrence.get() == 0) {
                firstOccurrence.set(now);
            }
        }
        
        public boolean isWithinWindow(long windowMs) {
            long now = System.currentTimeMillis();
            return (now - firstOccurrence.get()) <= windowMs;
        }
        
        public void reset() {
            count.set(0);
            firstOccurrence.set(0);
            lastOccurrence.set(0);
        }
        
        public int getCount() { return count.get(); }
        public long getFirstOccurrence() { return firstOccurrence.get(); }
        public long getLastOccurrence() { return lastOccurrence.get(); }
    }
    
    /**
     * 에러 기록
     * @param errorType 에러 유형
     * @param exception 발생한 예외
     * @param context 에러 컨텍스트 (예: 합성하려던 텍스트)
     */
    public void recordError(ErrorType errorType, Exception exception, String context) {
        totalErrors.incrementAndGet();
        lastErrorTime.set(System.currentTimeMillis());
        lastErrorMessage = exception.getMessage();
        
        ErrorStats stats = errorStats.computeIfAbsent(errorType, k -> new ErrorStats());
        stats.record();
        
        // 에러 창 내에서 너무 많은 에러가 발생하면 서비스를 비정상으로 표시
        if (stats.isWithinWindow(ERROR_WINDOW_MS) && stats.getCount() > MAX_ERRORS_PER_WINDOW) {
            serviceHealthy = false;
            logger.severe(String.format("TTS 서비스 비정상: %s - %d개 에러 발생 (창: %dms)",
                errorType.getDescription(), stats.getCount(), ERROR_WINDOW_MS));
        }
        
        logger.log(Level.WARNING, String.format("TTS 에러 [%s]: %s - Context: %s",
            errorType.getDescription(), exception.getMessage(), context), exception);
    }
    
    /**
     * 에러 유형 판별
     * @param exception 발생한 예외
     * @return 에러 유형
     */
    public ErrorType classifyError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return ErrorType.UNKNOWN_ERROR;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // 인증 관련 에러
        if (lowerMessage.contains("authentication") || 
            lowerMessage.contains("credentials") ||
            lowerMessage.contains("unauthorized") ||
            lowerMessage.contains("403")) {
            return ErrorType.AUTHENTICATION_ERROR;
        }
        
        // 네트워크 관련 에러
        if (lowerMessage.contains("connection") || 
            lowerMessage.contains("timeout") ||
            lowerMessage.contains("network") ||
            exception instanceof java.net.ConnectException ||
            exception instanceof java.net.SocketTimeoutException) {
            return ErrorType.NETWORK_ERROR;
        }
        
        // 요청 제한 에러
        if (lowerMessage.contains("rate limit") || 
            lowerMessage.contains("quota") ||
            lowerMessage.contains("429")) {
            return ErrorType.RATE_LIMIT_ERROR;
        }
        
        // 입력 관련 에러
        if (lowerMessage.contains("invalid") || 
            lowerMessage.contains("illegal") ||
            lowerMessage.contains("400")) {
            return ErrorType.INVALID_INPUT_ERROR;
        }
        
        // 합성 관련 에러
        if (lowerMessage.contains("synthesis") || 
            lowerMessage.contains("tts") ||
            lowerMessage.contains("speech")) {
            return ErrorType.SYNTHESIS_ERROR;
        }
        
        return ErrorType.UNKNOWN_ERROR;
    }
    
    /**
     * 재시도 가능 여부 확인
     * @param errorType 에러 유형
     * @param attemptCount 현재 시도 횟수
     * @return 재시도 가능 여부
     */
    public boolean canRetry(ErrorType errorType, int attemptCount) {
        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
            return false;
        }
        
        // 인증 에러나 잘못된 입력은 재시도해도 소용없음
        if (errorType == ErrorType.AUTHENTICATION_ERROR || 
            errorType == ErrorType.INVALID_INPUT_ERROR) {
            return false;
        }
        
        // 서비스가 비정상이면 재시도 안함
        if (!serviceHealthy) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 재시도 지연 시간 계산
     * @param attemptCount 현재 시도 횟수
     * @return 지연 시간 (밀리초)
     */
    public long getRetryDelay(int attemptCount) {
        // 지수 백오프: 1초, 2초, 4초...
        return RETRY_DELAY_MS * (long) Math.pow(2, attemptCount - 1);
    }
    
    /**
     * 에러 통계 초기화
     */
    public void resetErrorStats() {
        errorStats.clear();
        totalErrors.set(0);
        lastErrorTime.set(0);
        serviceHealthy = true;
        lastErrorMessage = "";
        logger.info("TTS 에러 통계가 초기화되었습니다.");
    }
    
    /**
     * 특정 에러 유형의 통계 초기화
     * @param errorType 에러 유형
     */
    public void resetErrorType(ErrorType errorType) {
        ErrorStats stats = errorStats.get(errorType);
        if (stats != null) {
            stats.reset();
            
            // 모든 에러가 초기화되었으면 서비스를 정상으로 표시
            boolean hasActiveErrors = errorStats.values().stream()
                .anyMatch(s -> s.isWithinWindow(ERROR_WINDOW_MS) && s.getCount() > MAX_ERRORS_PER_WINDOW);
            
            if (!hasActiveErrors) {
                serviceHealthy = true;
            }
        }
    }
    
    /**
     * 서비스 상태 확인
     * @return 서비스가 정상인지 여부
     */
    public boolean isServiceHealthy() {
        // 시간이 지나면 자동으로 복구 시도
        if (!serviceHealthy) {
            boolean shouldRecover = errorStats.values().stream()
                .noneMatch(s -> s.isWithinWindow(ERROR_WINDOW_MS * 2) && 
                          s.getCount() > MAX_ERRORS_PER_WINDOW);
            
            if (shouldRecover) {
                serviceHealthy = true;
                logger.info("TTS 서비스가 자동으로 복구되었습니다.");
            }
        }
        
        return serviceHealthy;
    }
    
    /**
     * 에러 통계 가져오기
     * @return 에러 통계 맵
     */
    public Map<String, Object> getErrorStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        stats.put("total_errors", totalErrors.get());
        stats.put("last_error_time", lastErrorTime.get());
        stats.put("last_error_message", lastErrorMessage);
        stats.put("service_healthy", serviceHealthy);
        
        Map<String, Map<String, Object>> errorTypeStats = new ConcurrentHashMap<>();
        for (Map.Entry<ErrorType, ErrorStats> entry : errorStats.entrySet()) {
            ErrorType type = entry.getKey();
            ErrorStats stat = entry.getValue();
            
            Map<String, Object> typeStats = new ConcurrentHashMap<>();
            typeStats.put("count", stat.getCount());
            typeStats.put("first_occurrence", stat.getFirstOccurrence());
            typeStats.put("last_occurrence", stat.getLastOccurrence());
            typeStats.put("within_window", stat.isWithinWindow(ERROR_WINDOW_MS));
            
            errorTypeStats.put(type.name(), typeStats);
        }
        
        stats.put("error_types", errorTypeStats);
        
        return stats;
    }
    
    /**
     * 폴백 텍스트 생성
     * @param originalText 원본 텍스트
     * @param errorType 에러 유형
     * @return 폴백 메시지
     */
    public String getFallbackMessage(String originalText, ErrorType errorType) {
        switch (errorType) {
            case AUTHENTICATION_ERROR:
                return "[TTS 인증 오류] 음성 합성 서비스를 사용할 수 없습니다.";
                
            case NETWORK_ERROR:
                return "[TTS 네트워크 오류] 잠시 후 다시 시도해주세요.";
                
            case RATE_LIMIT_ERROR:
                return "[TTS 요청 제한] 너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.";
                
            case INVALID_INPUT_ERROR:
                return "[TTS 입력 오류] 텍스트를 처리할 수 없습니다.";
                
            case SYNTHESIS_ERROR:
                return "[TTS 합성 오류] 음성을 생성할 수 없습니다.";
                
            default:
                return "[TTS 오류] 음성 합성에 실패했습니다.";
        }
    }
    
    /**
     * 재시도를 포함한 TTS 실행
     * @param task TTS 작업
     * @param context 컨텍스트
     * @return 성공 시 오디오 데이터, 실패 시 null
     */
    public byte[] executeWithRetry(TTSTask task, String context) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++;
            
            try {
                // TTS 작업 실행
                byte[] result = task.execute();
                
                // 성공하면 에러 카운트 리셋
                if (attempt > 1) {
                    logger.info(String.format("TTS 재시도 성공 (시도: %d/%d)", attempt, MAX_RETRY_ATTEMPTS));
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                ErrorType errorType = classifyError(e);
                recordError(errorType, e, context);
                
                if (canRetry(errorType, attempt)) {
                    long delay = getRetryDelay(attempt);
                    logger.warning(String.format("TTS 재시도 예정 (시도: %d/%d, 지연: %dms, 에러: %s)",
                        attempt, MAX_RETRY_ATTEMPTS, delay, errorType.getDescription()));
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        
        // 모든 재시도 실패
        logger.severe(String.format("TTS 최종 실패 (시도: %d회): %s",
            attempt, lastException != null ? lastException.getMessage() : "Unknown error"));
        
        return null;
    }
    
    /**
     * TTS 작업 인터페이스
     */
    public interface TTSTask {
        byte[] execute() throws Exception;
    }
}