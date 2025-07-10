package com.agjagjn.minecraft_ai.entity;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.minecraft.ai.companion.data.CompanionData;

/**
 * AI 동료 시스템의 공통 인터페이스
 * 
 * FakePlayerCompanion과 VillagerCompanion 모두 이 인터페이스를 구현하여
 * 동일한 방식으로 관리할 수 있도록 합니다.
 */
public interface AICompanionInterface {
    
    /**
     * 동료 제거
     */
    void remove();
    
    /**
     * 동료 명령 처리
     * 
     * @param command 명령어
     */
    void processCommand(String command);
    
    /**
     * 소유자 플레이어 반환
     * 
     * @return 소유자 플레이어
     */
    Player getOwner();
    
    /**
     * 동료 데이터 반환
     * 
     * @return 동료 데이터
     */
    CompanionData getCompanionData();
    
    /**
     * 동료가 유효한지 확인
     * 
     * @return 유효성 여부
     */
    boolean isValid();
    
    /**
     * 동료의 현재 위치 반환
     * 
     * @return 현재 위치
     */
    Location getLocation();
    
    /**
     * 동료의 상태 정보 반환
     * 
     * @return 상태 정보
     */
    String getStatus();
    
    /**
     * 동료를 특정 위치로 텔레포트
     * 
     * @param location 목표 위치
     */
    void teleport(Location location);
    
    /**
     * 동료 유형 반환
     * 
     * @return 동료 유형 ("FakePlayer" 또는 "Villager")
     */
    String getType();
} 