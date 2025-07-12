# Minecraft AI Brain - Java Plugin Component

This is the Java plugin component of the Minecraft AI hybrid architecture. It handles AI conversations, voice processing, and WebSocket communication with the Node.js bot component.

## ⚠️ Java 21 Requirement

**IMPORTANT**: This plugin requires **Java 21 or higher** to build and run, as Paper Minecraft server now requires Java 21+.

### 🔧 Setup Instructions

#### 1. Install Java 21
- **Windows**: Download from [Oracle JDK 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html) or [OpenJDK 21](https://adoptium.net/)
- **macOS**: 
  ```bash
  brew install openjdk@21
  ```
- **Linux**:
  ```bash
  sudo apt update
  sudo apt install openjdk-21-jdk
  ```

#### 2. Set JAVA_HOME Environment Variable
- **Windows**: 
  ```cmd
  set JAVA_HOME=C:\Program Files\Java\jdk-21
  set PATH=%JAVA_HOME%\bin;%PATH%
  ```
- **macOS/Linux**:
  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
  export PATH=$JAVA_HOME/bin:$PATH
  ```

#### 3. Verify Java Installation
```bash
java -version
# Should show version 21.x.x

javac -version
# Should show version 21.x.x
```

## 🔨 Building

Once Java 21 is properly installed:

```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Generate plugin JAR
./gradlew shadowJar
```

The generated plugin JAR will be in `build/libs/minecraft-ai-brain-1.0.0-SNAPSHOT.jar`.

## 📋 Dependencies

- **Paper API**: 1.20.6+ (requires Java 21)
- **Gson**: JSON processing
- **Java-WebSocket**: WebSocket communication
- **SLF4J + Logback**: Logging

## 🚀 Installation

1. Ensure your Minecraft server is running **Paper 1.20.6+** with **Java 21+**
2. Copy the generated JAR to your server's `plugins/` directory
3. Restart the server
4. Configure the plugin in `plugins/MinecraftAIBrain/config.yml`

## 🔧 Configuration

The plugin will create a configuration file at `plugins/MinecraftAIBrain/config.yml` with settings for:
- WebSocket server connection
- AI model configurations
- Voice processing settings
- Logging levels

## 🌐 WebSocket Communication

This plugin communicates with the Node.js AI Body component via WebSocket. Ensure the Node.js component is running and accessible at the configured WebSocket URL.

## 📝 Commands

- `/ai` - Main AI companion command
- `/ai-config` - Configure AI settings
- `/ai-voice` - Voice interaction commands
- `/ai-debug` - Debug AI system

## 🛠️ Development

### Current Status
- ✅ Core plugin structure
- ✅ WebSocket communication framework
- ✅ Command system
- ✅ Event handling
- 🔄 AI integration (in progress)
- 🔄 Voice processing (in progress)

### Contributing
1. Ensure Java 21 is installed
2. Fork the repository
3. Create a feature branch
4. Make your changes
5. Test thoroughly
6. Submit a pull request

## 🐛 Troubleshooting

### "Unsupported class file major version" Error
- This means you're using Java < 21. Install Java 21 and update your JAVA_HOME.

### "Could not resolve Paper API" Error
- Ensure you have internet connection for Maven dependencies
- Paper repositories might be temporarily unavailable

### WebSocket Connection Issues
- Check that the Node.js AI Body component is running
- Verify WebSocket URL configuration
- Check firewall settings

## 📞 Support

For issues and questions:
1. Check this README
2. Review server logs for error messages
3. Ensure Java 21 is properly installed and configured
4. Verify Paper server version compatibility 