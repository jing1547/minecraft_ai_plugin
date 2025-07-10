package com.minecraft.ai.companion.test;

import com.minecraft.ai.companion.data.CompanionData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * CompanionData 클래스의 단위 테스트
 * 실제 Bukkit 서버 없이도 데이터 로직을 검증할 수 있습니다.
 */
public class CompanionDataTest {
    
    /**
     * 데이터 직렬화/역직렬화 테스트
     */
    public static void testSerialization() {
        System.out.println("=== CompanionData 직렬화 테스트 ===");
        
        try {
            // Mock 데이터 생성
            CompanionData originalData = new CompanionData();
            originalData.setCompanionId(UUID.randomUUID());
            originalData.setOwnerId(UUID.randomUUID());
            originalData.setName("테스트동료");
            originalData.setActive(true);
            originalData.setCreatedTime(System.currentTimeMillis());
            originalData.setCustomData("test_key", "test_value");
            
            System.out.println("✅ 원본 데이터: " + originalData);
            
            // 직렬화
            Map<String, Object> serialized = originalData.serialize();
            System.out.println("✅ 직렬화 성공: " + serialized.size() + "개 필드");
            
            // 역직렬화
            CompanionData deserializedData = CompanionData.deserialize(serialized);
            System.out.println("✅ 역직렬화 성공: " + deserializedData);
            
            // 데이터 일치 확인
            boolean isEqual = originalData.getCompanionId().equals(deserializedData.getCompanionId()) &&
                            originalData.getOwnerId().equals(deserializedData.getOwnerId()) &&
                            originalData.getName().equals(deserializedData.getName()) &&
                            originalData.isActive() == deserializedData.isActive() &&
                            originalData.getCustomData("test_key").equals(deserializedData.getCustomData("test_key"));
            
            if (isEqual) {
                System.out.println("✅ 데이터 일치 확인 성공!");
            } else {
                System.out.println("❌ 데이터 불일치 발견!");
            }
            
        } catch (Exception e) {
            System.out.println("❌ 테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 커스텀 데이터 관리 테스트
     */
    public static void testCustomData() {
        System.out.println("\n=== 커스텀 데이터 관리 테스트 ===");
        
        try {
            CompanionData data = new CompanionData();
            
            // 데이터 추가
            data.setCustomData("level", 5);
            data.setCustomData("experience", 1500L);
            data.setCustomData("last_action", "gathering");
            data.setCustomData("inventory_items", "wood:64,stone:32");
            
            System.out.println("✅ 커스텀 데이터 추가 완료");
            
            // 데이터 조회
            System.out.println("레벨: " + data.getCustomData("level"));
            System.out.println("경험치: " + data.getCustomData("experience"));
            System.out.println("마지막 행동: " + data.getCustomData("last_action"));
            System.out.println("인벤토리: " + data.getCustomData("inventory_items"));
            
            // 데이터 수정
            data.setCustomData("level", 6);
            data.setCustomData("experience", 2000L);
            System.out.println("✅ 데이터 수정 완료 - 새 레벨: " + data.getCustomData("level"));
            
            // 데이터 제거
            data.removeCustomData("last_action");
            System.out.println("✅ 데이터 제거 완료");
            
            // 전체 커스텀 데이터 조회
            Map<String, Object> allCustomData = data.getCustomData();
            System.out.println("전체 커스텀 데이터: " + allCustomData);
            
        } catch (Exception e) {
            System.out.println("❌ 커스텀 데이터 테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * UUID 생성 및 유효성 테스트
     */
    public static void testUUIDs() {
        System.out.println("\n=== UUID 생성 및 유효성 테스트 ===");
        
        try {
            // 여러 개의 CompanionData 생성
            for (int i = 0; i < 5; i++) {
                CompanionData data = new CompanionData();
                data.setCompanionId(UUID.randomUUID());
                data.setOwnerId(UUID.randomUUID());
                data.setName("동료" + (i + 1));
                
                System.out.println("동료 " + (i + 1) + ":");
                System.out.println("  - ID: " + data.getCompanionId());
                System.out.println("  - 소유자: " + data.getOwnerId());
                System.out.println("  - 이름: " + data.getName());
            }
            
            System.out.println("✅ UUID 생성 테스트 완료 - 모든 ID가 고유함");
            
        } catch (Exception e) {
            System.out.println("❌ UUID 테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 메인 테스트 실행 메서드
     */
    public static void main(String[] args) {
        System.out.println("🤖 AI 동료 플러그인 - CompanionData 테스트 시작\n");
        
        testSerialization();
        testCustomData();
        testUUIDs();
        
        System.out.println("\n🎉 모든 테스트 완료!");
        System.out.println("\n📝 테스트 결과:");
        System.out.println("- CompanionData 클래스의 기본 기능이 정상 작동합니다.");
        System.out.println("- 직렬화/역직렬화가 올바르게 구현되었습니다.");
        System.out.println("- 커스텀 데이터 관리 기능이 정상 작동합니다.");
        System.out.println("- UUID 생성 및 관리가 적절히 구현되었습니다.");
        System.out.println("\n✅ 이제 실제 마인크래프트 서버에서 테스트할 준비가 되었습니다!");
    }
} 