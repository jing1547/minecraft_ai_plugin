# 🧪 현재 상태 테스트 시나리오

## 📋 테스트 전 준비사항

### Java 플러그인 설정
1. `minecraft-ai-brain.jar`를 Spigot/Paper 서버의 `plugins/` 폴더에 복사
2. 서버 시작 후 `plugins/MinecraftAIBrain/config.yml`에서 설정 확인
3. Google Cloud 서비스 계정 JSON 파일 (`minecraftsever-463307-4aca77796027.json`) 설정

### Node.js 봇 설정
1. `minecraft-ai-body/` 디렉토리에서 `npm install`
2. `npm run build`로 TypeScript 컴파일

## ✅ 현재 작동 가능한 테스트들

### 1. WebSocket 통신 테스트
```bash
# 터미널 1: Java 서버 시작 (마인크래프트 서버)
java -jar spigot-1.21.4.jar

# 터미널 2: Node.js 봇 시작
cd minecraft-ai-body
npm start
```

**예상 결과:**
- Java 플러그인: "WebSocket server started on port 8080"
- Node.js 봇: "🔌 WebSocket connected to Java server"
- Node.js 봇: "🤝 Sent handshake message to Java server"

### 2. 기본 봇 명령 테스트 (마인크래프트 내)
```bash
/ai status           # 봇 상태 확인
/ai-voice start      # 음성 처리 시작
/ai-voice test       # 음성 시스템 테스트
/ai-debug status     # 시스템 디버그 정보
```

### 3. WebSocket을 통한 봇 제어 테스트
Java 플러그인에서 Node.js 봇으로 명령 전송:
```java
// moveTo 명령
{"type":"COMMAND","action":"moveTo","parameters":{"x":10,"y":64,"z":10}}

// getStatus 명령  
{"type":"COMMAND","action":"getStatus","parameters":{}}

// ping 명령
{"type":"COMMAND","action":"ping","parameters":{}}
```

### 4. STT (음성→텍스트) 테스트
```bash
/ai-voice start      # 음성 캡처 시작
# 음성으로 "헬프" 또는 "!help" 말하기
# 예상: 채팅에 "음성 명령 처리됨: !help" 메시지
```

**지원되는 음성 명령:**
- `!help` - 도움말 표시
- `!status` - 봇 상태
- `!time` - 현재 시간
- `!weather` - 날씨 (더미 응답)

## ❌ 현재 작동하지 않는 기능들

### 1. TTS (텍스트→음성)
- 구현되지 않음
- 봇이 말할 수 없음

### 2. AI 대화
- OpenAI API 미구현
- 스마트한 대화 불가
- 단순 패턴 매칭만 가능

### 3. 고급 봇 기능
- 자동 건축
- 복잡한 전투
- 리소스 자동 수집
- 스킨/모델 변경

## 🔧 디버깅 도구

### Java 플러그인 로그
```bash
tail -f logs/latest.log | grep "MinecraftAIBrain"
```

### Node.js 봇 로그
```bash
# 상세 로그와 함께 실행
DEBUG=* npm start
```

### WebSocket 통신 확인
```bash
# WebSocket 테스트 도구 사용
wscat -c ws://localhost:8080
```

## 🎯 다음 구현 우선순위

1. **TTS 시스템** (Task 6)
2. **OpenAI GPT API** (Task 7)  
3. **시스템 통합** (Task 12)

이 순서로 구현하면 완전한 AI 음성 대화 시스템이 완성됩니다! 