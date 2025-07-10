# 🤖 마인크래프트 AI 동료 플러그인

한국어 음성 상호작용이 가능한 지능형 AI 동료 플러그인입니다.

## ✨ 주요 기능

- 🎮 **자율적 게임플레이**: AI가 독립적으로 자원 수집, 건축, 탐험, 전투 수행
- 🗣️ **한국어 음성 인식**: 실시간 마이크 입력을 통한 음성 명령 처리
- 🔊 **한국어 음성 출력**: 자연스러운 한국어 TTS로 AI 동료와 대화
- 🤝 **협력 게임플레이**: 플레이어와 함께 건축, 탐험, 전투 진행
- 👤 **Yes Steve Model 통합**: 시각적 아바타 표현 및 애니메이션
- 🧠 **고급 AI 의사결정**: OpenAI GPT를 활용한 상황별 적절한 판단

## 🛠️ 설치 요구사항

### 서버 환경
- **Minecraft**: 1.19+ (Java Edition)
- **서버**: Spigot 또는 Paper 서버
- **Java**: 17 이상
- **메모리**: 최소 4GB RAM 권장

### API 키 설정
다음 API 키들이 필요합니다:

1. **OpenAI API Key** - AI 의사결정용
2. **Google Cloud Speech-to-Text API Key** - 음성 인식용
3. **Google Cloud Text-to-Speech API Key** - 음성 출력용

## 📦 설치 방법

### 1. 플러그인 빌드
```bash
# 프로젝트 클론
git clone https://github.com/minecraft-ai/companion-plugin.git
cd minecraft-ai-companion

# Maven으로 빌드
mvn clean package

# 생성된 JAR 파일을 서버의 plugins 폴더에 복사
cp target/minecraft-ai-companion-1.0.0-SNAPSHOT.jar /path/to/your/server/plugins/
```

### 2. 설정 파일 구성
서버를 한 번 실행한 후 `plugins/MinecraftAICompanion/config.yml` 파일을 편집:

```yaml
ai:
  openai:
    api-key: "your-openai-api-key-here"
    
voice:
  stt:
    google-api-key: "your-google-speech-api-key-here"
  tts:
    google:
      api-key: "your-google-tts-api-key-here"
```

### 3. 서버 재시작
설정 완료 후 서버를 재시작합니다.

## 🎮 사용법

### 기본 명령어
- `/ai-spawn [이름]` - AI 동료 소환
- `/ai-remove` - AI 동료 제거
- `/ai-voice on/off` - 음성 모드 토글
- `/ai-settings <설정> <값>` - AI 동료 설정 변경
- `/aicompanion help` - 도움말 보기

### 음성 상호작용
1. `/ai-voice on` 명령어로 음성 모드 활성화
2. 마이크에 대고 한국어로 말하기
3. AI 동료가 음성으로 응답

### 예시 음성 명령
- "나무를 캐와줘"
- "집을 같이 지어보자"
- "몬스터가 나타나면 도와줘"
- "다이아몬드를 찾아보자"

## ⚙️ 설정 옵션

### AI 설정
```yaml
ai:
  companion:
    personality: "친근하고 도움이 되는 마인크래프트 전문가"
    game-knowledge-level: 8
```

### 게임플레이 설정
```yaml
gameplay:
  behavior:
    auto-follow: true
    follow-distance: 3.0
    auto-collect-resources: true
    auto-attack-monsters: true
```

### 성능 최적화
```yaml
performance:
  ai-update-interval: 20
  max-memory-usage: 512
```

## 🔧 개발 정보

### 프로젝트 구조
```
src/
├── main/
│   ├── java/com/minecraft/ai/companion/
│   │   ├── MinecraftAICompanionPlugin.java
│   │   ├── core/           # 핵심 시스템
│   │   ├── ai/             # AI 처리
│   │   ├── voice/          # 음성 처리
│   │   ├── commands/       # 명령어 처리
│   │   └── listeners/      # 이벤트 리스너
│   └── resources/
│       ├── plugin.yml
│       └── config.yml
└── test/
```

### 의존성
- Spigot API 1.20.4
- OpenAI Java Client
- Google Cloud Speech/TTS
- Apache HTTP Client
- Gson (JSON 처리)

## 🤝 기여하기

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 라이센스

이 프로젝트는 MIT 라이센스 하에 배포됩니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

## 🆘 문제 해결

### 자주 묻는 질문

**Q: AI 동료가 소환되지 않아요**
A: `config.yml`에서 API 키가 올바르게 설정되었는지 확인하세요.

**Q: 음성 인식이 작동하지 않아요**
A: Google Cloud Speech API 키와 인터넷 연결을 확인하세요.

**Q: 서버 성능이 저하돼요**
A: `config.yml`에서 `performance` 섹션의 설정값을 조정하세요.

### 지원

- 📧 이메일: support@minecraft-ai.com
- 🐛 버그 리포트: [GitHub Issues](https://github.com/minecraft-ai/companion-plugin/issues)
- 💬 커뮤니티: [Discord Server](https://discord.gg/minecraft-ai)

---

**Made with ❤️ for the Minecraft community**
