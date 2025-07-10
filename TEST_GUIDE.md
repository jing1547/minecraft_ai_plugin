# 🧪 마인크래프트 AI 동료 플러그인 테스트 가이드

## 📋 테스트 환경 요구사항

### 필수 소프트웨어
- **Java 17** 이상
- **Maven** (빌드용)
- **마인크래프트 Java Edition** 1.19+ 
- **Spigot 또는 Paper 서버** 1.19+

## 🔨 1단계: 플러그인 빌드

### Maven이 설치된 경우:
```bash
# 프로젝트 루트에서 실행
mvn clean package

# 빌드 완료 후 target 폴더에 JAR 파일 생성됨
ls target/minecraft-ai-companion-*.jar
```

### Maven이 없는 경우:
1. [Maven 다운로드](https://maven.apache.org/download.cgi)
2. 설치 후 환경변수 설정
3. 또는 IDE(IntelliJ IDEA, Eclipse)에서 직접 빌드

## 🖥️ 2단계: 테스트 서버 구성

### Paper 서버 다운로드 (권장):
```bash
# Paper 1.20.4 서버 다운로드
wget https://api.papermc.io/v2/projects/paper/versions/1.20.4/builds/latest/downloads/paper-1.20.4-latest.jar

# 또는 브라우저에서 다운로드:
# https://papermc.io/downloads
```

### 서버 폴더 구조:
```
minecraft-server/
├── paper-1.20.4.jar
├── plugins/
│   └── minecraft-ai-companion-1.0.0-SNAPSHOT.jar
├── server.properties
├── eula.txt
└── start.bat (또는 start.sh)
```

### 서버 시작 스크립트 (start.bat):
```batch
@echo off
java -Xmx2G -Xms1G -jar paper-1.20.4.jar nogui
pause
```

## ⚙️ 3단계: 플러그인 설정

### 1. 플러그인 설치:
1. 빌드된 `minecraft-ai-companion-*.jar` 파일을 `plugins/` 폴더에 복사
2. 서버 시작
3. 플러그인 폴더 생성 확인: `plugins/MinecraftAICompanion/`

### 2. config.yml 설정:
```yaml
# plugins/MinecraftAICompanion/config.yml
general:
  enabled: true
  debug: true  # 테스트 중에는 true로 설정
  language: "ko"
  max-companions-per-player: 1

ai:
  openai:
    api-key: "YOUR_API_KEY_HERE"  # 실제 API 키로 교체
    model: "gpt-3.5-turbo"
    max-tokens: 150
    temperature: 0.7

voice:
  enabled: false  # 일단 비활성화 (기본 기능만 테스트)
```

## 🎮 4단계: 기본 기능 테스트

### 서버 접속 후 테스트할 명령어들:

#### 1. 플러그인 로드 확인:
```
/plugins
# MinecraftAICompanion이 초록색으로 표시되어야 함
```

#### 2. 도움말 확인:
```
/aic
/aic help
```

#### 3. AI 동료 소환:
```
/ai-spawn
/ai-spawn 테스트봇
/ai-spawn 한국어동료
```

#### 4. 동료 정보 확인:
```
/aic info
```

#### 5. 동료 행동 테스트:
- 플레이어 이동 → 동료가 따라오는지 확인
- 멀리 이동 → 자동 텔레포트 확인
- 다른 차원 이동 → 동료가 함께 이동하는지 확인

#### 6. 동료 제거:
```
/ai-remove
```

#### 7. 권한 테스트 (OP가 아닌 플레이어로):
```
/ai-spawn
# 권한 오류 메시지 확인
```

## 🔍 5단계: 고급 테스트

### 1. 여러 플레이어 테스트:
- 2명 이상의 플레이어로 접속
- 각자 동료 소환
- 상호작용 확인

### 2. 서버 재시작 테스트:
1. 동료 소환
2. 서버 종료
3. 서버 재시작
4. 동료 데이터 복구 확인

### 3. 성능 테스트:
- 여러 동료 동시 소환
- 복잡한 지형에서 이동 테스트
- 메모리 사용량 모니터링

## 🐛 6단계: 문제 해결

### 로그 확인:
```bash
# 서버 콘솔에서 실시간 로그 확인
tail -f logs/latest.log

# 플러그인별 로그 필터링
grep "MinecraftAI" logs/latest.log
```

### 일반적인 문제들:

#### 1. 플러그인이 로드되지 않음:
- Java 버전 확인 (17 이상 필요)
- Spigot/Paper 버전 호환성 확인
- 의존성 라이브러리 누락 확인

#### 2. 명령어가 작동하지 않음:
- 권한 설정 확인: `/lp user <플레이어> permission set aicompanion.* true`
- plugin.yml 문법 오류 확인

#### 3. 동료가 소환되지 않음:
- 월드 보호 플러그인 충돌 확인
- 엔티티 스폰 제한 확인
- 메모리 부족 확인

#### 4. 동료가 플레이어를 따라오지 않음:
- 틱 레이트 확인: `/tps`
- 다른 플러그인과의 충돌 확인

## ✅ 테스트 체크리스트

### 기본 기능:
- [ ] 플러그인 정상 로드
- [ ] 동료 소환 성공
- [ ] 동료 이름 표시
- [ ] 플레이어 추적 기능
- [ ] 동료 정보 확인
- [ ] 동료 제거 기능

### 고급 기능:
- [ ] 자동 텔레포트
- [ ] 건강 모니터링
- [ ] 데이터 지속성
- [ ] 권한 시스템
- [ ] 여러 플레이어 지원

### 성능:
- [ ] 메모리 사용량 정상
- [ ] 서버 틱 레이트 안정
- [ ] 로그 오류 없음

## 📞 지원

문제가 발생하면 다음 정보와 함께 문의:
1. 마인크래프트 서버 버전
2. Java 버전
3. 오류 로그
4. 사용한 명령어
5. 예상 동작 vs 실제 동작

## 🎯 다음 단계

기본 테스트 완료 후:
1. **Task 3**: 고급 행동 시스템 구현
2. **Task 4**: AI 의사결정 시스템
3. **Task 5**: 한국어 처리 시스템
4. **Task 6**: 음성 시스템 통합 