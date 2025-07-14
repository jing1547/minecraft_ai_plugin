package com.minecraft.ai.brain.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Utility class for audio processing operations
 * Handles audio preprocessing such as noise reduction and normalization
 * Enhanced with advanced noise filtering and voice activity detection
 */
public class AudioProcessor {
    
    // Audio format constants
    public static final int SAMPLE_RATE = 16000; // 16kHz
    public static final int BITS_PER_SAMPLE = 16;
    public static final int CHANNELS = 1; // Mono
    public static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;
    
    // Audio processing parameters
    private static final double NOISE_THRESHOLD = 0.1;
    private static final double NORMALIZATION_FACTOR = 0.8;
    
    // Advanced processing components (thread-safe singletons)
    private static final ThreadLocal<NoiseFilter> noiseFilter = 
        ThreadLocal.withInitial(NoiseFilter::new);
    private static final ThreadLocal<VoiceActivityDetector> vadDetector = 
        ThreadLocal.withInitial(VoiceActivityDetector::new);
    
    /**
     * Preprocess audio data with noise reduction and normalization
     * @param audioData Raw audio data
     * @return Processed audio data
     */
    public static byte[] preprocessAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return new byte[0];
        }
        
        // Apply noise reduction
        byte[] noiseFree = reduceNoise(audioData);
        
        // Apply volume normalization
        byte[] normalized = normalizeVolume(noiseFree);
        
        return normalized;
    }
    
    /**
     * Reduce background noise from audio data
     * @param audioData Raw audio data
     * @return Noise-reduced audio data
     */
    public static byte[] reduceNoise(byte[] audioData) {
        if (audioData.length < 2) {
            return audioData;
        }
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Convert bytes to 16-bit sample
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            
            // Simple noise gate - suppress low amplitude signals
            double amplitude = Math.abs(sample) / 32768.0;
            if (amplitude < NOISE_THRESHOLD) {
                sample = 0; // Silence low amplitude noise
            }
            
            // Convert back to bytes
            output.write(sample & 0xFF);
            output.write((sample >> 8) & 0xFF);
        }
        
        return output.toByteArray();
    }
    
    /**
     * Normalize audio volume to prevent clipping and ensure consistent levels
     * @param audioData Raw audio data
     * @return Normalized audio data
     */
    public static byte[] normalizeVolume(byte[] audioData) {
        if (audioData.length < 2) {
            return audioData;
        }
        
        // Find peak amplitude
        short maxAmplitude = 0;
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            maxAmplitude = (short) Math.max(maxAmplitude, Math.abs(sample));
        }
        
        // Skip normalization if audio is too quiet
        if (maxAmplitude < 1000) {
            return audioData;
        }
        
        // Calculate normalization factor
        double scaleFactor = (32767.0 * NORMALIZATION_FACTOR) / maxAmplitude;
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Convert bytes to 16-bit sample
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            
            // Apply normalization
            sample = (short) Math.max(-32768, Math.min(32767, sample * scaleFactor));
            
            // Convert back to bytes
            output.write(sample & 0xFF);
            output.write((sample >> 8) & 0xFF);
        }
        
        return output.toByteArray();
    }
    
    /**
     * Convert audio data to the required format for Speech-to-Text
     * @param audioData Raw audio data
     * @param sourceSampleRate Source sample rate
     * @param sourceChannels Number of source channels
     * @return Converted audio data
     */
    public static byte[] convertAudioFormat(byte[] audioData, int sourceSampleRate, int sourceChannels) {
        // For now, assume input is already in the correct format
        // In a real implementation, you would use audio processing libraries
        // like javax.sound.sampled or external libraries for format conversion
        
        if (sourceSampleRate == SAMPLE_RATE && sourceChannels == CHANNELS) {
            return audioData; // Already in correct format
        }
        
        // Simple conversion - this is a placeholder
        // Real implementation would need proper resampling and channel mixing
        return audioData;
    }
    
    /**
     * Detect voice activity in audio data
     * @param audioData Audio data to analyze
     * @return true if voice activity is detected, false otherwise
     */
    public static boolean detectVoiceActivity(byte[] audioData) {
        if (audioData.length < 2) {
            return false;
        }
        
        double totalEnergy = 0;
        int sampleCount = 0;
        
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            totalEnergy += sample * sample;
            sampleCount++;
        }
        
        double averageEnergy = totalEnergy / sampleCount;
        double threshold = 1000000; // Adjust based on testing
        
        return averageEnergy > threshold;
    }
    
    /**
     * Calculate audio level (RMS) for visualization
     * @param audioData Audio data
     * @return Audio level between 0.0 and 1.0
     */
    public static double calculateAudioLevel(byte[] audioData) {
        if (audioData.length < 2) {
            return 0.0;
        }
        
        double sum = 0;
        int sampleCount = 0;
        
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
            sampleCount++;
        }
        
        double rms = Math.sqrt(sum / sampleCount);
        return Math.min(1.0, rms / 32768.0);
    }
    
    /**
     * Apply a simple high-pass filter to remove low-frequency noise
     * @param audioData Audio data
     * @return Filtered audio data
     */
    public static byte[] applyHighPassFilter(byte[] audioData) {
        if (audioData.length < 4) {
            return audioData;
        }
        
        // Simple high-pass filter implementation
        // This is a very basic implementation - real filters would be more sophisticated
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        short previousSample = 0;
        
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            
            // Simple high-pass filter: current - previous * 0.95
            short filtered = (short) (sample - (previousSample * 0.95));
            previousSample = sample;
            
            // Convert back to bytes
            output.write(filtered & 0xFF);
            output.write((filtered >> 8) & 0xFF);
        }
        
        return output.toByteArray();
    }
    
    /**
     * Check if audio data is valid
     * @param audioData Audio data to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidAudioData(byte[] audioData) {
        return audioData != null && audioData.length > 0 && audioData.length % 2 == 0;
    }
    
    /**
     * Advanced audio preprocessing with spectral noise filtering
     * @param audioData Raw audio data
     * @return Processed audio data with advanced noise reduction
     */
    public static byte[] advancedPreprocessAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return new byte[0];
        }
        
        // Apply advanced spectral noise filtering
        byte[] noiseFiltered = noiseFilter.get().filterNoise(audioData);
        
        // Apply traditional processing
        byte[] normalized = normalizeVolume(noiseFiltered);
        
        return normalized;
    }
    
    /**
     * Enhanced voice activity detection using advanced VAD
     * @param audioData Audio data to analyze
     * @return true if voice activity is detected, false otherwise
     */
    public static boolean detectVoiceActivityAdvanced(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return false;
        }
        
        return vadDetector.get().detectSpeech(audioData);
    }
    
    /**
     * Segment audio into voice and non-voice regions
     * @param audioData Audio data to segment
     * @param frameSize Size of each frame for analysis
     * @return Array of boolean values indicating voice activity for each frame
     */
    public static boolean[] segmentVoiceActivity(byte[] audioData, int frameSize) {
        if (audioData == null || audioData.length < frameSize) {
            return new boolean[0];
        }
        
        int numFrames = audioData.length / frameSize;
        boolean[] voiceSegments = new boolean[numFrames];
        
        for (int i = 0; i < numFrames; i++) {
            int startIndex = i * frameSize;
            int endIndex = Math.min(startIndex + frameSize, audioData.length);
            
            byte[] frame = Arrays.copyOfRange(audioData, startIndex, endIndex);
            voiceSegments[i] = vadDetector.get().detectSpeech(frame);
        }
        
        return voiceSegments;
    }
    
    /**
     * Get advanced audio processing statistics
     * @return Statistics string including noise level, SNR, etc.
     */
    public static String getAdvancedProcessingStats() {
        NoiseFilter filter = noiseFilter.get();
        VoiceActivityDetector vad = vadDetector.get();
        
        return String.format("Advanced Audio Processing Stats:\n%s\n%s",
                filter.getFilterStats(), vad.getVADStats());
    }
    
    /**
     * Reset advanced processing components
     */
    public static void resetAdvancedProcessing() {
        noiseFilter.get().reset();
        vadDetector.get().reset();
    }
    
    /**
     * Configure VAD thresholds for specific environments
     * @param speechThreshold Speech detection threshold
     * @param noiseThreshold Noise level threshold
     */
    public static void configureVADThresholds(double speechThreshold, double noiseThreshold) {
        vadDetector.get().setThresholds(speechThreshold, noiseThreshold);
    }
    
    /**
     * Get current signal-to-noise ratio
     * @return Current SNR from VAD
     */
    public static double getCurrentSNR() {
        return vadDetector.get().getSignalToNoiseRatio();
    }
    
    /**
     * Get current background noise level
     * @return Background noise level from VAD
     */
    public static double getBackgroundNoiseLevel() {
        return vadDetector.get().getBackgroundNoiseLevel();
    }
    
    /**
     * Check if noise profile is estimated for optimal filtering
     * @return true if noise profile is ready
     */
    public static boolean isNoiseProfileReady() {
        return noiseFilter.get().isNoiseProfileEstimated();
    }
    
    /**
     * Process audio with comprehensive analysis
     * Combines advanced filtering, VAD, and traditional processing
     * @param audioData Raw audio data
     * @return ProcessingResult containing processed audio and analysis
     */
    public static ProcessingResult comprehensiveAudioProcessing(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return new ProcessingResult(new byte[0], false, 0.0, 0.0, 0.0);
        }
        
        // Get pre-processing stats
        double originalLevel = calculateAudioLevel(audioData);
        
        // Apply advanced processing
        byte[] processed = advancedPreprocessAudio(audioData);
        
        // Perform VAD analysis
        boolean voiceDetected = detectVoiceActivityAdvanced(audioData);
        
        // Get post-processing stats
        double snr = getCurrentSNR();
        double noiseLevel = getBackgroundNoiseLevel();
        
        return new ProcessingResult(processed, voiceDetected, originalLevel, snr, noiseLevel);
    }
    
    /**
     * Result class for comprehensive audio processing
     */
    public static class ProcessingResult {
        private final byte[] processedAudio;
        private final boolean voiceDetected;
        private final double originalLevel;
        private final double signalToNoiseRatio;
        private final double noiseLevel;
        
        public ProcessingResult(byte[] processedAudio, boolean voiceDetected, 
                              double originalLevel, double signalToNoiseRatio, double noiseLevel) {
            this.processedAudio = processedAudio;
            this.voiceDetected = voiceDetected;
            this.originalLevel = originalLevel;
            this.signalToNoiseRatio = signalToNoiseRatio;
            this.noiseLevel = noiseLevel;
        }
        
        public byte[] getProcessedAudio() { return processedAudio; }
        public boolean isVoiceDetected() { return voiceDetected; }
        public double getOriginalLevel() { return originalLevel; }
        public double getSignalToNoiseRatio() { return signalToNoiseRatio; }
        public double getNoiseLevel() { return noiseLevel; }
        
        @Override
        public String toString() {
            return String.format("ProcessingResult{voice=%s, originalLevel=%.3f, SNR=%.2f, noise=%.4f}",
                    voiceDetected, originalLevel, signalToNoiseRatio, noiseLevel);
        }
    }
    
    /**
     * Get audio format information string
     * @return Format information
     */
    public static String getAudioFormatInfo() {
        return String.format("Audio Format: %d Hz, %d-bit, %d channel(s)", 
                           SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS);
    }
} 