package com.minecraft.ai.brain.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Utility class for audio processing operations
 * Handles audio preprocessing such as noise reduction and normalization
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
     * Get audio format information string
     * @return Format information
     */
    public static String getAudioFormatInfo() {
        return String.format("Audio Format: %d Hz, %d-bit, %d channel(s)", 
                           SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS);
    }
} 