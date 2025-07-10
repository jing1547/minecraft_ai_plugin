@echo off
echo 🔨 마인크래프트 AI 플러그인 빌드 스크립트
echo.

REM Java 버전 확인
echo [1/4] Java 버전 확인 중...
java -version
if %ERRORLEVEL% neq 0 (
    echo ❌ Java가 설치되어 있지 않습니다!
    echo Java 17 이상을 설치해주세요: https://adoptium.net/
    pause
    exit /b 1
)

REM Maven 확인
echo.
echo [2/4] Maven 확인 중...
mvn -version
if %ERRORLEVEL% neq 0 (
    echo ❌ Maven이 설치되어 있지 않습니다!
    echo Maven을 설치하거나 IDE에서 빌드해주세요.
    echo Maven 다운로드: https://maven.apache.org/download.cgi
    echo.
    echo 💡 대안: IntelliJ IDEA 또는 Eclipse에서 프로젝트를 열고 빌드하세요.
    pause
    exit /b 1
)

REM 빌드 시작
echo.
echo [3/4] 플러그인 빌드 중...
mvn clean package
if %ERRORLEVEL% neq 0 (
    echo ❌ 빌드 실패!
    pause
    exit /b 1
)

REM 결과 확인
echo.
echo [4/4] 빌드 결과 확인 중...
if exist target\minecraft-ai-companion-*.jar (
    echo ✅ 빌드 성공!
    echo.
    echo 📦 생성된 JAR 파일:
    dir target\minecraft-ai-companion-*.jar
    echo.
    echo 🎯 다음 단계:
    echo 1. target 폴더의 JAR 파일을 마인크래프트 서버의 plugins 폴더에 복사
    echo 2. 서버 재시작
    echo 3. /plugins 명령어로 플러그인 로드 확인
    echo 4. /ai-spawn 명령어로 AI 동료 소환 테스트
    echo.
    echo 📖 자세한 테스트 방법은 TEST_GUIDE.md 파일을 참고하세요.
) else (
    echo ❌ JAR 파일이 생성되지 않았습니다!
)

echo.
pause 