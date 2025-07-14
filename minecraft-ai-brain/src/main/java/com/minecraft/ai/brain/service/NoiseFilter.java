package com.minecraft.ai.brain.service;

import java.util.Arrays;

/**
 * 고급 노이즈 필터링 클래스
 * 스펙트럴 감산(Spectral Subtraction) 알고리즘을 사용하여 배경 소음을 제거합니다.
 * Advanced noise filtering class using spectral subtraction for background noise removal
 */
public class NoiseFilter {
    
    // 노이즈 필터링 파라미터
    private static final double NOISE_THRESHOLD = 0.02;
    private static final double ALPHA = 2.0; // 과감소 계수 (over-subtraction factor)
    private static final double BETA = 0.01; // 최소 신호 레벨
    private static final int NOISE_ESTIMATION_FRAMES = 50; // 노이즈 추정을 위한 프레임 수
    private static final int FRAME_SIZE = 256; // FFT 프레임 크기 (16ms at 16kHz)
    private static final int OVERLAP = 128; // 프레임 오버랩
    
    // 적응형 노이즈 레벨 추정
    private double adaptiveNoiseLevel = 0.0;
    private int frameCount = 0;
    private double[] noiseSpectrum;
    private boolean noiseProfileEstimated = false;
    
    // 윈도우 함수 (Hamming window)
    private final double[] hammingWindow;
    
    public NoiseFilter() {
        this.hammingWindow = generateHammingWindow(FRAME_SIZE);
        this.noiseSpectrum = new double[FRAME_SIZE / 2 + 1];
    }
    
    /**
     * 오디오 데이터에서 노이즈를 제거합니다.
     * @param audioData 원본 오디오 데이터
     * @return 노이즈가 제거된 오디오 데이터
     */
    public byte[] filterNoise(byte[] audioData) {
        if (audioData == null || audioData.length < FRAME_SIZE * 2) {
            return audioData;
        }
        
        // 바이트 배열을 더블 배열로 변환
        double[] samples = bytesToDoubles(audioData);
        
        // 프레임별로 처리
        double[] filteredSamples = processFrames(samples);
        
        // 더블 배열을 바이트 배열로 변환
        return doublesToBytes(filteredSamples);
    }
    
    /**
     * 프레임별로 스펙트럴 감산을 적용합니다.
     */
    private double[] processFrames(double[] samples) {
        double[] output = new double[samples.length];
        
        for (int i = 0; i < samples.length - FRAME_SIZE; i += OVERLAP) {
            // 현재 프레임 추출
            double[] frame = Arrays.copyOfRange(samples, i, i + FRAME_SIZE);
            
            // 윈도우 함수 적용
            applyWindow(frame, hammingWindow);
            
            // FFT 적용 (실수부만 사용하는 간단한 구현)
            double[] magnitude = calculateMagnitudeSpectrum(frame);
            
            // 노이즈 프로파일 업데이트
            updateNoiseProfile(magnitude);
            
            // 스펙트럴 감산 적용
            double[] filteredMagnitude = applySpectralSubtraction(magnitude);
            
            // IFFT로 시간 도메인으로 복원 (간단한 구현)
            double[] filteredFrame = reconstructTimeSignal(filteredMagnitude);
            
            // 오버랩-애드 방식으로 출력에 합성
            overlapAdd(output, filteredFrame, i);
        }
        
        return output;
    }
    
    /**
     * 윈도우 함수를 프레임에 적용합니다.
     */
    private void applyWindow(double[] frame, double[] window) {
        for (int i = 0; i < frame.length; i++) {
            frame[i] *= window[i];
        }
    }
    
    /**
     * 매그니튜드 스펙트럼을 계산합니다 (간단한 DFT 구현).
     */
    private double[] calculateMagnitudeSpectrum(double[] frame) {
        int N = frame.length;
        double[] magnitude = new double[N / 2 + 1];
        
        for (int k = 0; k < magnitude.length; k++) {
            double real = 0.0;
            double imag = 0.0;
            
            for (int n = 0; n < N; n++) {
                double angle = -2.0 * Math.PI * k * n / N;
                real += frame[n] * Math.cos(angle);
                imag += frame[n] * Math.sin(angle);
            }
            
            magnitude[k] = Math.sqrt(real * real + imag * imag);
        }
        
        return magnitude;
    }
    
    /**
     * 노이즈 프로파일을 업데이트합니다.
     */
    private void updateNoiseProfile(double[] magnitude) {
        frameCount++;
        
        if (frameCount <= NOISE_ESTIMATION_FRAMES) {
            // 초기 프레임들에서 노이즈 프로파일 추정
            for (int i = 0; i < noiseSpectrum.length && i < magnitude.length; i++) {
                noiseSpectrum[i] = (noiseSpectrum[i] * (frameCount - 1) + magnitude[i]) / frameCount;
            }
        } else if (!noiseProfileEstimated) {
            noiseProfileEstimated = true;
        } else {
            // 적응형 업데이트 (음성이 없을 때만)
            double currentEnergy = calculateEnergy(magnitude);
            if (currentEnergy < adaptiveNoiseLevel * 1.5) {
                double adaptationRate = 0.1;
                for (int i = 0; i < noiseSpectrum.length && i < magnitude.length; i++) {
                    noiseSpectrum[i] = (1 - adaptationRate) * noiseSpectrum[i] + 
                                      adaptationRate * magnitude[i];
                }
            }
        }
        
        // 적응형 노이즈 레벨 업데이트
        adaptiveNoiseLevel = calculateEnergy(noiseSpectrum);
    }
    
    /**
     * 스펙트럴 감산을 적용합니다.
     */
    private double[] applySpectralSubtraction(double[] magnitude) {
        double[] filtered = new double[magnitude.length];
        
        for (int i = 0; i < magnitude.length; i++) {
            if (i < noiseSpectrum.length) {
                // 스펙트럴 감산 공식: |Y(f)| = |X(f)| - α|N(f)|
                double subtracted = magnitude[i] - ALPHA * noiseSpectrum[i];
                
                // 최소 신호 레벨 보장: |Y(f)| >= β|X(f)|
                double minimum = BETA * magnitude[i];
                filtered[i] = Math.max(subtracted, minimum);
            } else {
                filtered[i] = magnitude[i];
            }
        }
        
        return filtered;
    }
    
    /**
     * 필터링된 매그니튜드 스펙트럼을 시간 신호로 복원합니다 (간단한 IDFT).
     */
    private double[] reconstructTimeSignal(double[] magnitude) {
        int N = FRAME_SIZE;
        double[] timeSignal = new double[N];
        
        for (int n = 0; n < N; n++) {
            double sample = 0.0;
            
            for (int k = 0; k < magnitude.length; k++) {
                double angle = 2.0 * Math.PI * k * n / N;
                sample += magnitude[k] * Math.cos(angle) / N;
            }
            
            timeSignal[n] = sample;
        }
        
        return timeSignal;
    }
    
    /**
     * 오버랩-애드 방식으로 프레임을 합성합니다.
     */
    private void overlapAdd(double[] output, double[] frame, int startIndex) {
        for (int i = 0; i < frame.length && startIndex + i < output.length; i++) {
            output[startIndex + i] += frame[i];
        }
    }
    
    /**
     * 스펙트럼의 에너지를 계산합니다.
     */
    private double calculateEnergy(double[] spectrum) {
        double energy = 0.0;
        for (double value : spectrum) {
            energy += value * value;
        }
        return Math.sqrt(energy / spectrum.length);
    }
    
    /**
     * Hamming 윈도우를 생성합니다.
     */
    private double[] generateHammingWindow(int size) {
        double[] window = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (size - 1));
        }
        return window;
    }
    
    /**
     * 바이트 배열을 더블 배열로 변환합니다.
     */
    private double[] bytesToDoubles(byte[] audioData) {
        double[] samples = new double[audioData.length / 2];
        
        for (int i = 0; i < samples.length; i++) {
            // 16비트 샘플을 short로 변환
            short sample = (short) ((audioData[i * 2 + 1] << 8) | (audioData[i * 2] & 0xFF));
            // 정규화하여 -1.0 ~ 1.0 범위로 변환
            samples[i] = sample / 32768.0;
        }
        
        return samples;
    }
    
    /**
     * 더블 배열을 바이트 배열로 변환합니다.
     */
    private byte[] doublesToBytes(double[] samples) {
        byte[] audioData = new byte[samples.length * 2];
        
        for (int i = 0; i < samples.length; i++) {
            // -1.0 ~ 1.0 범위를 16비트 정수로 변환
            short sample = (short) Math.max(-32768, Math.min(32767, samples[i] * 32768));
            
            // 리틀 엔디안으로 바이트 배열에 저장
            audioData[i * 2] = (byte) (sample & 0xFF);
            audioData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        
        return audioData;
    }
    
    /**
     * 현재 노이즈 레벨을 반환합니다.
     */
    public double getCurrentNoiseLevel() {
        return adaptiveNoiseLevel;
    }
    
    /**
     * 노이즈 프로파일이 추정되었는지 확인합니다.
     */
    public boolean isNoiseProfileEstimated() {
        return noiseProfileEstimated;
    }
    
    /**
     * 노이즈 필터를 리셋합니다.
     */
    public void reset() {
        adaptiveNoiseLevel = 0.0;
        frameCount = 0;
        noiseProfileEstimated = false;
        Arrays.fill(noiseSpectrum, 0.0);
    }
    
    /**
     * 필터링 통계 정보를 반환합니다.
     */
    public String getFilterStats() {
        return String.format("NoiseFilter Stats: Level=%.4f, Frames=%d, ProfileEstimated=%s",
                adaptiveNoiseLevel, frameCount, noiseProfileEstimated);
    }
} 