package com.minecraft.ai.brain.service;

import java.util.Arrays;

/**
 * 고급 음성 활동 감지 클래스
 * 에너지, 스펙트럴 특성, 제로 크로싱 레이트를 결합한 다중 기준 VAD
 * Advanced Voice Activity Detection using energy, spectral features, and zero crossing rate
 */
public class VoiceActivityDetector {
    
    // VAD 파라미터
    private static final int FRAME_SIZE = 320; // 20ms at 16kHz (더 안정적인 분석을 위해 증가)
    private static final double INITIAL_SPEECH_THRESHOLD = 0.02; // 더 민감하게 조정
    private static final double INITIAL_NOISE_THRESHOLD = 0.005;
    private static final int SPEECH_HOLD_TIME = 40; // frames (800ms - 말끔 시 여유 시간)
    private static final int HANGOVER_TIME = 15; // frames (300ms)
    private static final int ADAPTATION_FRAMES = 50; // 빠른 적응을 위해 감소
    
    // 스펙트럴 VAD 파라미터
    private static final double SPECTRAL_FLATNESS_THRESHOLD = 0.7; // 음성은 보통 평탄도가 낮음
    private static final double ZCR_THRESHOLD = 0.2; // 더 관대하게 조정
    private static final int SPECTRAL_BANDS = 8;
    
    // 새로운 파라미터들
    private static final int MIN_SPEECH_FRAMES = 5; // 최소 음성 프레임 수 (100ms)
    private static final double ENERGY_SMOOTHING_FACTOR = 0.7; // 에너지 평활화 계수
    
    // 적응형 임계값
    private double speechThreshold = INITIAL_SPEECH_THRESHOLD;
    private double noiseThreshold = INITIAL_NOISE_THRESHOLD;
    private double adaptiveEnergyThreshold = INITIAL_SPEECH_THRESHOLD;
    
    // 상태 관리
    private int speechHoldCounter = 0;
    private int hangoverCounter = 0;
    private boolean isSpeechDetected = false;
    private boolean isInSpeechSegment = false;
    
    // 통계 및 적응형 처리
    private double[] energyHistory = new double[ADAPTATION_FRAMES];
    private double[] zcrHistory = new double[ADAPTATION_FRAMES];
    private int historyIndex = 0;
    private int frameCount = 0;
    
    // 노이즈 프로파일
    private double backgroundNoiseLevel = 0.0;
    private double signalToNoiseRatio = 1.0;
    
    // 에너지 평활화를 위한 변수
    private double smoothedEnergy = 0.0;
    private int consecutiveSpeechFrames = 0;
    private int consecutiveSilenceFrames = 0;
    
    /**
     * 오디오 프레임에서 음성 활동을 감지합니다.
     * @param audioFrame 오디오 프레임 데이터
     * @return 음성이 감지되면 true, 그렇지 않으면 false
     */
    public boolean detectSpeech(byte[] audioFrame) {
        if (audioFrame == null || audioFrame.length < FRAME_SIZE * 2) {
            return false;
        }
        
        frameCount++;
        
        // 기본 특성 계산
        double energy = calculateRMSEnergy(audioFrame);
        double zcr = calculateZeroCrossingRate(audioFrame);
        double spectralFlatness = calculateSpectralFlatness(audioFrame);
        
        // 에너지 평활화 적용
        smoothedEnergy = ENERGY_SMOOTHING_FACTOR * smoothedEnergy + 
                        (1 - ENERGY_SMOOTHING_FACTOR) * energy;
        
        // 특성 히스토리 업데이트
        updateHistory(smoothedEnergy, zcr);
        
        // 적응형 임계값 업데이트 (프레임 수가 충분할 때만)
        if (frameCount >= ADAPTATION_FRAMES / 2) {
            updateAdaptiveThresholds();
        }
        
        // 다중 기준 VAD 결정 (평활화된 에너지 사용)
        boolean energyVAD = smoothedEnergy > adaptiveEnergyThreshold;
        boolean spectralVAD = spectralFlatness < SPECTRAL_FLATNESS_THRESHOLD;
        boolean zcrVAD = zcr > ZCR_THRESHOLD && zcr < 0.8; // 너무 높은 ZCR은 노이즈일 가능성
        
        // 가중치 기반 결합 결정
        double vadScore = calculateVADScore(energyVAD, spectralVAD, zcrVAD, 
                                          smoothedEnergy, zcr, spectralFlatness);
        
        // 연속 프레임 카운팅
        if (vadScore > 0.5) {
            consecutiveSpeechFrames++;
            consecutiveSilenceFrames = 0;
        } else {
            consecutiveSilenceFrames++;
            consecutiveSpeechFrames = 0;
        }
        
        // 최소 연속 프레임 요구사항 적용
        boolean currentDecision = consecutiveSpeechFrames >= MIN_SPEECH_FRAMES;
        
        // 시간적 평활화 적용
        return applyTemporalSmoothing(currentDecision);
    }
    
    /**
     * RMS 에너지를 계산합니다.
     */
    private double calculateRMSEnergy(byte[] audioFrame) {
        if (audioFrame.length < 2) {
            return 0.0;
        }
        
        double sum = 0.0;
        int sampleCount = 0;
        
        for (int i = 0; i < audioFrame.length - 1; i += 2) {
            short sample = (short) ((audioFrame[i + 1] << 8) | (audioFrame[i] & 0xFF));
            sum += (double) sample * sample;
            sampleCount++;
        }
        
        if (sampleCount == 0) {
            return 0.0;
        }
        
        double rms = Math.sqrt(sum / sampleCount) / 32768.0; // 정규화
        return rms;
    }
    
    /**
     * 제로 크로싱 레이트를 계산합니다.
     */
    private double calculateZeroCrossingRate(byte[] audioFrame) {
        if (audioFrame.length < 4) {
            return 0.0;
        }
        
        int zeroCrossings = 0;
        short previousSample = 0;
        
        for (int i = 0; i < audioFrame.length - 1; i += 2) {
            short currentSample = (short) ((audioFrame[i + 1] << 8) | (audioFrame[i] & 0xFF));
            
            if (i > 0) {
                // 부호 변화 확인
                if ((previousSample >= 0 && currentSample < 0) || 
                    (previousSample < 0 && currentSample >= 0)) {
                    zeroCrossings++;
                }
            }
            
            previousSample = currentSample;
        }
        
        int sampleCount = audioFrame.length / 2;
        return (double) zeroCrossings / sampleCount;
    }
    
    /**
     * 스펙트럴 평탄도를 계산합니다 (간단한 구현).
     */
    private double calculateSpectralFlatness(byte[] audioFrame) {
        if (audioFrame.length < FRAME_SIZE * 2) {
            return 1.0; // 기본값
        }
        
        // 주파수 대역별 에너지 계산
        double[] bandEnergies = new double[SPECTRAL_BANDS];
        int samplesPerBand = (audioFrame.length / 2) / SPECTRAL_BANDS;
        
        for (int band = 0; band < SPECTRAL_BANDS; band++) {
            double bandEnergy = 0.0;
            int startIdx = band * samplesPerBand * 2;
            int endIdx = Math.min((band + 1) * samplesPerBand * 2, audioFrame.length - 1);
            
            for (int i = startIdx; i < endIdx; i += 2) {
                short sample = (short) ((audioFrame[i + 1] << 8) | (audioFrame[i] & 0xFF));
                bandEnergy += (double) sample * sample;
            }
            
            bandEnergies[band] = bandEnergy / samplesPerBand;
        }
        
        // 기하 평균과 산술 평균의 비율로 평탄도 계산
        double geometricMean = 1.0;
        double arithmeticMean = 0.0;
        int validBands = 0;
        
        for (double energy : bandEnergies) {
            if (energy > 0) {
                geometricMean *= Math.pow(energy, 1.0 / SPECTRAL_BANDS);
                arithmeticMean += energy;
                validBands++;
            }
        }
        
        if (validBands == 0) {
            return 1.0;
        }
        
        arithmeticMean /= validBands;
        
        return (arithmeticMean > 0) ? geometricMean / arithmeticMean : 0.0;
    }
    
    /**
     * 특성 히스토리를 업데이트합니다.
     */
    private void updateHistory(double energy, double zcr) {
        energyHistory[historyIndex] = energy;
        zcrHistory[historyIndex] = zcr;
        historyIndex = (historyIndex + 1) % ADAPTATION_FRAMES;
    }
    
    /**
     * 적응형 임계값을 업데이트합니다.
     */
    private void updateAdaptiveThresholds() {
        if (frameCount < ADAPTATION_FRAMES) {
            return; // 충분한 히스토리가 쌓일 때까지 대기
        }
        
        // 에너지 히스토리 분석
        double[] sortedEnergies = Arrays.copyOf(energyHistory, energyHistory.length);
        Arrays.sort(sortedEnergies);
        
        // 배경 노이즈 레벨 추정 (하위 30%의 평균)
        int noiseFrames = (int) (sortedEnergies.length * 0.3);
        double noiseSum = 0.0;
        for (int i = 0; i < noiseFrames; i++) {
            noiseSum += sortedEnergies[i];
        }
        backgroundNoiseLevel = noiseSum / noiseFrames;
        
        // 음성 레벨 추정 (상위 30%의 평균)
        int speechFrames = noiseFrames;
        double speechSum = 0.0;
        for (int i = sortedEnergies.length - speechFrames; i < sortedEnergies.length; i++) {
            speechSum += sortedEnergies[i];
        }
        double speechLevel = speechSum / speechFrames;
        
        // 신호 대 잡음비 계산
        signalToNoiseRatio = (backgroundNoiseLevel > 0) ? speechLevel / backgroundNoiseLevel : 1.0;
        
        // 적응형 임계값 설정
        adaptiveEnergyThreshold = backgroundNoiseLevel + 
            (speechLevel - backgroundNoiseLevel) * 0.3; // 30% 지점
        
        // 최소/최대 임계값 제한
        adaptiveEnergyThreshold = Math.max(adaptiveEnergyThreshold, INITIAL_NOISE_THRESHOLD);
        adaptiveEnergyThreshold = Math.min(adaptiveEnergyThreshold, INITIAL_SPEECH_THRESHOLD * 2);
    }
    
    /**
     * VAD 점수를 계산합니다.
     */
    private double calculateVADScore(boolean energyVAD, boolean spectralVAD, boolean zcrVAD,
                                   double energy, double zcr, double spectralFlatness) {
        // 기본 가중치 설정
        double energyWeight = 0.6;  // 에너지를 가장 중요하게
        double spectralWeight = 0.25;
        double zcrWeight = 0.15;
        
        // 신호 대 잡음비에 따른 가중치 조정
        if (signalToNoiseRatio < 2.0) {
            // 낮은 SNR에서는 에너지에 더 의존
            energyWeight = 0.8;
            spectralWeight = 0.15;
            zcrWeight = 0.05;
        } else if (signalToNoiseRatio > 5.0) {
            // 높은 SNR에서는 다른 특성도 고려
            energyWeight = 0.5;
            spectralWeight = 0.3;
            zcrWeight = 0.2;
        }
        
        double score = 0.0;
        
        // 부드러운 점수 계산 (boolean 대신 연속값 사용)
        double energyScore = Math.min(1.0, energy / (adaptiveEnergyThreshold * 2));
        double spectralScore = Math.max(0.0, 1.0 - spectralFlatness);
        double zcrScore = Math.min(1.0, zcr / 0.5);
        
        score = energyScore * energyWeight + 
                spectralScore * spectralWeight + 
                zcrScore * zcrWeight;
        
        return score;
    }
    
    /**
     * 시간적 평활화를 적용합니다.
     */
    private boolean applyTemporalSmoothing(boolean currentDecision) {
        if (currentDecision) {
            speechHoldCounter = SPEECH_HOLD_TIME;
            hangoverCounter = 0;
            
            if (!isInSpeechSegment) {
                isInSpeechSegment = true;
            }
            
            isSpeechDetected = true;
            
        } else {
            if (speechHoldCounter > 0) {
                speechHoldCounter--;
                isSpeechDetected = true;
            } else {
                if (isInSpeechSegment && hangoverCounter < HANGOVER_TIME) {
                    hangoverCounter++;
                    isSpeechDetected = true;
                } else {
                    isSpeechDetected = false;
                    isInSpeechSegment = false;
                    hangoverCounter = 0;
                }
            }
        }
        
        return isSpeechDetected;
    }
    
    /**
     * VAD 상태를 리셋합니다.
     */
    public void reset() {
        speechHoldCounter = 0;
        hangoverCounter = 0;
        isSpeechDetected = false;
        isInSpeechSegment = false;
        frameCount = 0;
        historyIndex = 0;
        
        Arrays.fill(energyHistory, 0.0);
        Arrays.fill(zcrHistory, 0.0);
        
        speechThreshold = INITIAL_SPEECH_THRESHOLD;
        noiseThreshold = INITIAL_NOISE_THRESHOLD;
        adaptiveEnergyThreshold = INITIAL_SPEECH_THRESHOLD;
        backgroundNoiseLevel = 0.0;
        signalToNoiseRatio = 1.0;
        
        // 새로운 변수들도 리셋
        smoothedEnergy = 0.0;
        consecutiveSpeechFrames = 0;
        consecutiveSilenceFrames = 0;
    }
    
    /**
     * 현재 음성 감지 상태를 반환합니다.
     */
    public boolean isSpeechActive() {
        return isSpeechDetected;
    }
    
    /**
     * 음성 세그먼트 내에 있는지 확인합니다.
     */
    public boolean isInSpeechSegment() {
        return isInSpeechSegment;
    }
    
    /**
     * 현재 신호 대 잡음비를 반환합니다.
     */
    public double getSignalToNoiseRatio() {
        return signalToNoiseRatio;
    }
    
    /**
     * 배경 노이즈 레벨을 반환합니다.
     */
    public double getBackgroundNoiseLevel() {
        return backgroundNoiseLevel;
    }
    
    /**
     * 현재 적응형 임계값을 반환합니다.
     */
    public double getAdaptiveThreshold() {
        return adaptiveEnergyThreshold;
    }
    
    /**
     * VAD 통계 정보를 반환합니다.
     */
    public String getVADStats() {
        return String.format(
            "VAD Stats: Active=%s, SNR=%.2f, NoiseLevel=%.4f, Threshold=%.4f, Frames=%d",
            isSpeechDetected, signalToNoiseRatio, backgroundNoiseLevel, 
            adaptiveEnergyThreshold, frameCount
        );
    }
    
    /**
     * 임계값을 수동으로 설정합니다 (테스트용).
     */
    public void setThresholds(double speechThreshold, double noiseThreshold) {
        this.speechThreshold = Math.max(0.0, speechThreshold);
        this.noiseThreshold = Math.max(0.0, noiseThreshold);
        this.adaptiveEnergyThreshold = this.speechThreshold;
    }
    
    /**
     * 현재 설정된 임계값들을 반환합니다.
     */
    public double[] getCurrentThresholds() {
        return new double[]{speechThreshold, noiseThreshold, adaptiveEnergyThreshold};
    }
} 