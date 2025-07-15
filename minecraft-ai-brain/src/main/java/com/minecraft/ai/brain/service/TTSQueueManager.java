package com.minecraft.ai.brain.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.bukkit.entity.Player;

/**
 * TTS 큐 관리자
 * TTS 요청을 큐에 저장하고 순차적으로 처리합니다.
 */
public class TTSQueueManager {
    
    private static final Logger logger = Logger.getLogger(TTSQueueManager.class.getName());
    
    // 큐 설정
    private static final int MAX_QUEUE_SIZE = 100;
    private static final int MAX_REQUESTS_PER_PLAYER = 5;
    private static final long REQUEST_TIMEOUT_MS = 30000; // 30초
    private static final int WORKER_THREADS = 2;
    
    // TTS 서비스
    private final TextToSpeechService ttsService;
    private final AudioPlayerService audioPlayerService;
    
    // 큐와 실행자
    private final PriorityBlockingQueue<TTSRequest> requestQueue;
    private final ExecutorService workerExecutor;
    private final ScheduledExecutorService cleanupExecutor;
    
    // 플레이어별 요청 추적
    private final Map<UUID, AtomicInteger> playerRequestCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastRequestTime = new ConcurrentHashMap<>();
    
    // 통계
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong processedRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong droppedRequests = new AtomicLong(0);
    
    // 상태
    private volatile boolean isRunning = false;
    
    /**
     * TTS 요청
     */
    public static class TTSRequest implements Comparable<TTSRequest> {
        private final UUID requestId;
        private final UUID playerId;
        private final String text;
        private final String emotion;
        private final AudioPlayerService.Position position;
        private final long timestamp;
        private final int priority;
        private final CompletableFuture<Boolean> future;
        
        public TTSRequest(UUID playerId, String text, String emotion, 
                         AudioPlayerService.Position position, int priority) {
            this.requestId = UUID.randomUUID();
            this.playerId = playerId;
            this.text = text;
            this.emotion = emotion;
            this.position = position;
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
            this.future = new CompletableFuture<>();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REQUEST_TIMEOUT_MS;
        }
        
        @Override
        public int compareTo(TTSRequest other) {
            // 높은 우선순위가 먼저
            int priorityCompare = Integer.compare(other.priority, this.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // 같은 우선순위면 먼저 들어온 것이 먼저
            return Long.compare(this.timestamp, other.timestamp);
        }
        
        // Getters
        public UUID getRequestId() { return requestId; }
        public UUID getPlayerId() { return playerId; }
        public String getText() { return text; }
        public String getEmotion() { return emotion; }
        public AudioPlayerService.Position getPosition() { return position; }
        public long getTimestamp() { return timestamp; }
        public int getPriority() { return priority; }
        public CompletableFuture<Boolean> getFuture() { return future; }
    }
    
    /**
     * 생성자
     */
    public TTSQueueManager(TextToSpeechService ttsService, AudioPlayerService audioPlayerService) {
        this.ttsService = ttsService;
        this.audioPlayerService = audioPlayerService;
        this.requestQueue = new PriorityBlockingQueue<>();
        this.workerExecutor = Executors.newFixedThreadPool(WORKER_THREADS);
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    }
    
    /**
     * 큐 매니저 시작
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        
        // 워커 스레드 시작
        for (int i = 0; i < WORKER_THREADS; i++) {
            workerExecutor.submit(this::processQueue);
        }
        
        // 정리 작업 스케줄
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredRequests,
            10, 10, TimeUnit.SECONDS
        );
        
        logger.info("TTS Queue Manager started with " + WORKER_THREADS + " worker threads");
    }
    
    /**
     * 큐 매니저 중지
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        // 모든 대기중인 요청 취소
        while (!requestQueue.isEmpty()) {
            TTSRequest request = requestQueue.poll();
            if (request != null) {
                request.getFuture().complete(false);
            }
        }
        
        // 실행자 종료
        workerExecutor.shutdown();
        cleanupExecutor.shutdown();
        
        try {
            if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("TTS Queue Manager stopped");
    }
    
    /**
     * TTS 요청 큐에 추가
     * @param playerId 플레이어 ID
     * @param text 텍스트
     * @param emotion 감정
     * @param position 재생 위치
     * @param priority 우선순위 (높을수록 먼저 처리)
     * @return 처리 결과 Future
     */
    public CompletableFuture<Boolean> queueRequest(UUID playerId, String text, String emotion,
                                                  AudioPlayerService.Position position, int priority) {
        totalRequests.incrementAndGet();
        
        // 큐 크기 확인
        if (requestQueue.size() >= MAX_QUEUE_SIZE) {
            droppedRequests.incrementAndGet();
            logger.warning("TTS queue is full, dropping request");
            return CompletableFuture.completedFuture(false);
        }
        
        // 플레이어별 요청 제한 확인
        AtomicInteger playerCount = playerRequestCounts.computeIfAbsent(playerId, k -> new AtomicInteger(0));
        if (playerCount.get() >= MAX_REQUESTS_PER_PLAYER) {
            droppedRequests.incrementAndGet();
            logger.warning("Player " + playerId + " has too many pending requests");
            return CompletableFuture.completedFuture(false);
        }
        
        // 요청 생성 및 큐에 추가
        TTSRequest request = new TTSRequest(playerId, text, emotion, position, priority);
        requestQueue.offer(request);
        playerCount.incrementAndGet();
        playerLastRequestTime.put(playerId, System.currentTimeMillis());
        
        logger.info(String.format("Queued TTS request: player=%s, priority=%d, queue_size=%d",
            playerId, priority, requestQueue.size()));
        
        return request.getFuture();
    }
    
    /**
     * 큐 처리 워커
     */
    private void processQueue() {
        logger.info("TTS queue worker started");
        
        while (isRunning) {
            try {
                // 큐에서 요청 가져오기 (최대 1초 대기)
                TTSRequest request = requestQueue.poll(1, TimeUnit.SECONDS);
                
                if (request == null) {
                    continue;
                }
                
                // 만료된 요청 건너뛰기
                if (request.isExpired()) {
                    logger.warning("Skipping expired TTS request: " + request.getRequestId());
                    request.getFuture().complete(false);
                    decrementPlayerCount(request.getPlayerId());
                    failedRequests.incrementAndGet();
                    continue;
                }
                
                // TTS 처리
                processRequest(request);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing TTS queue", e);
            }
        }
        
        logger.info("TTS queue worker stopped");
    }
    
    /**
     * 개별 요청 처리
     */
    private void processRequest(TTSRequest request) {
        try {
            logger.info(String.format("Processing TTS request: id=%s, player=%s",
                request.getRequestId(), request.getPlayerId()));
            
            // TTS 합성
            byte[] audioData = ttsService.synthesizeSpeech(request.getText(), request.getEmotion());
            
            if (audioData != null && audioData.length > 0) {
                // 오디오 재생 큐에 추가
                audioPlayerService.queueAudio(audioData, request.getPosition(), request.getPlayerId());
                
                processedRequests.incrementAndGet();
                request.getFuture().complete(true);
                logger.info("TTS request processed successfully: " + request.getRequestId());
                
            } else {
                failedRequests.incrementAndGet();
                request.getFuture().complete(false);
                logger.warning("TTS synthesis returned empty audio: " + request.getRequestId());
            }
            
        } catch (Exception e) {
            failedRequests.incrementAndGet();
            request.getFuture().complete(false);
            logger.log(Level.SEVERE, "Failed to process TTS request: " + request.getRequestId(), e);
            
        } finally {
            // 플레이어 요청 카운트 감소
            decrementPlayerCount(request.getPlayerId());
        }
    }
    
    /**
     * 플레이어 요청 카운트 감소
     */
    private void decrementPlayerCount(UUID playerId) {
        AtomicInteger count = playerRequestCounts.get(playerId);
        if (count != null) {
            int newCount = count.decrementAndGet();
            if (newCount <= 0) {
                playerRequestCounts.remove(playerId);
            }
        }
    }
    
    /**
     * 만료된 요청 정리
     */
    private void cleanupExpiredRequests() {
        int cleaned = 0;
        
        // 큐에서 만료된 요청 제거
        requestQueue.removeIf(request -> {
            if (request.isExpired()) {
                request.getFuture().complete(false);
                decrementPlayerCount(request.getPlayerId());
                cleaned++;
                return true;
            }
            return false;
        });
        
        if (cleaned > 0) {
            logger.info("Cleaned up " + cleaned + " expired TTS requests");
            droppedRequests.addAndGet(cleaned);
        }
        
        // 비활성 플레이어 정리
        long now = System.currentTimeMillis();
        playerLastRequestTime.entrySet().removeIf(entry -> {
            return now - entry.getValue() > REQUEST_TIMEOUT_MS * 2 &&
                   !playerRequestCounts.containsKey(entry.getKey());
        });
    }
    
    /**
     * 큐 통계 가져오기
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        stats.put("queue_size", requestQueue.size());
        stats.put("total_requests", totalRequests.get());
        stats.put("processed_requests", processedRequests.get());
        stats.put("failed_requests", failedRequests.get());
        stats.put("dropped_requests", droppedRequests.get());
        stats.put("active_players", playerRequestCounts.size());
        stats.put("is_running", isRunning);
        
        // 성공률 계산
        long total = processedRequests.get() + failedRequests.get();
        if (total > 0) {
            double successRate = (double) processedRequests.get() / total * 100;
            stats.put("success_rate", String.format("%.1f%%", successRate));
        }
        
        return stats;
    }
    
    /**
     * 특정 플레이어의 대기중인 요청 수
     */
    public int getPlayerQueueSize(UUID playerId) {
        AtomicInteger count = playerRequestCounts.get(playerId);
        return count != null ? count.get() : 0;
    }
    
    /**
     * 큐 초기화
     */
    public void clearQueue() {
        while (!requestQueue.isEmpty()) {
            TTSRequest request = requestQueue.poll();
            if (request != null) {
                request.getFuture().complete(false);
                decrementPlayerCount(request.getPlayerId());
            }
        }
        
        droppedRequests.addAndGet(requestQueue.size());
        logger.info("TTS queue cleared");
    }
    
    /**
     * 특정 플레이어의 요청 제거
     */
    public void clearPlayerRequests(UUID playerId) {
        int removed = 0;
        
        requestQueue.removeIf(request -> {
            if (request.getPlayerId().equals(playerId)) {
                request.getFuture().complete(false);
                removed++;
                return true;
            }
            return false;
        });
        
        playerRequestCounts.remove(playerId);
        playerLastRequestTime.remove(playerId);
        
        if (removed > 0) {
            droppedRequests.addAndGet(removed);
            logger.info("Removed " + removed + " TTS requests for player: " + playerId);
        }
    }
}