# Development Guide

This document provides comprehensive guidelines for developing the Minecraft AI Companion plugin.

## Table of Contents
- [Development Environment Setup](#development-environment-setup)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Building and Deployment](#building-and-deployment)
- [Debugging](#debugging)
- [Contributing Workflow](#contributing-workflow)

## Development Environment Setup

### Prerequisites

1. **Java Development Kit (JDK) 17+**
   ```bash
   # Verify Java installation
   java -version
   javac -version
   ```

2. **Node.js 18+**
   ```bash
   # Verify Node.js installation
   node --version
   npm --version
   ```

3. **Git**
   ```bash
   # Verify Git installation
   git --version
   ```

4. **IDE/Editor** (Recommended)
   - IntelliJ IDEA (Java development)
   - Visual Studio Code (Node.js development)
   - Eclipse (Alternative for Java)

### Initial Setup

1. **Clone the Repository**
   ```bash
   git clone https://github.com/your-repo/minecraft-ai-plugin.git
   cd minecraft-ai-plugin
   ```

2. **Setup Java Plugin (AI Brain)**
   ```bash
   cd minecraft-ai-brain
   
   # Make gradlew executable (Linux/Mac)
   chmod +x gradlew
   
   # Build the project
   ./gradlew build
   
   # Run tests
   ./gradlew test
   ```

3. **Setup Node.js Bot (AI Body)**
   ```bash
   cd minecraft-ai-body
   
   # Install dependencies
   npm install
   
   # Build TypeScript
   npm run build
   
   # Run tests
   npm test
   ```

4. **Setup Test Minecraft Server**
   ```bash
   # Create test server directory
   mkdir test-server
   cd test-server
   
   # Download Paper server (example)
   curl -o paper.jar https://api.papermc.io/v2/projects/paper/versions/1.21.4/builds/latest/downloads/paper-1.21.4-latest.jar
   
   # Accept EULA
   echo "eula=true" > eula.txt
   
   # Start server once to generate files
   java -jar paper.jar --nogui
   ```

### IDE Configuration

#### IntelliJ IDEA (Java)

1. **Import Project**
   - Open IntelliJ IDEA
   - File → Open → Select `minecraft-ai-brain` directory
   - Import as Gradle project

2. **Configure SDK**
   - File → Project Structure → Project
   - Set Project SDK to Java 21+

3. **Install Plugins**
   - Minecraft Development for IntelliJ (helpful for Bukkit development)

#### Visual Studio Code (Node.js)

1. **Open Project**
   ```bash
   cd minecraft-ai-body
   code .
   ```

2. **Install Extensions**
   - TypeScript and JavaScript Language Features
   - ESLint
   - Prettier
   - GitLens

3. **Configure Workspace Settings**
   ```json
   {
     "typescript.preferences.importModuleSpecifier": "relative",
     "editor.formatOnSave": true,
     "editor.codeActionsOnSave": {
       "source.fixAll.eslint": true
     }
   }
   ```

## Project Structure

### Java Plugin Structure
```
minecraft-ai-brain/
├── build.gradle                 # Build configuration
├── settings.gradle              # Gradle settings
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/minecraft/ai/brain/
│   │   │       ├── MinecraftAIBrainPlugin.java
│   │   │       ├── handlers/    # Event handlers
│   │   │       ├── websocket/   # WebSocket communication
│   │   │       └── utils/       # Utility classes
│   │   └── resources/
│   │       ├── plugin.yml       # Plugin configuration
│   │       └── config.yml       # Default configuration
│   └── test/
│       └── java/                # Unit tests
└── gradle/                      # Gradle wrapper files
```

### Node.js Bot Structure
```
minecraft-ai-body/
├── package.json                 # NPM configuration
├── tsconfig.json               # TypeScript configuration
├── src/
│   ├── index.ts                # Application entry point
│   ├── MinecraftAIBody.ts      # Main bot class
│   ├── handlers/               # Command handlers
│   ├── utils/                  # Utility functions
│   └── types/                  # TypeScript type definitions
├── dist/                       # Compiled JavaScript (generated)
└── test/                       # Unit tests
```

## Coding Standards

### Java Coding Standards

#### Naming Conventions
```java
// Classes: PascalCase
public class PlayerEventHandler { }

// Methods and variables: camelCase
private String playerName;
public void handlePlayerJoin() { }

// Constants: UPPER_SNAKE_CASE
public static final String DEFAULT_BOT_NAME = "AICompanion";

// Packages: lowercase with dots
package com.minecraft.ai.brain.handlers;
```

#### Code Formatting
```java
// Use 4 spaces for indentation
public class ExampleClass {
    private final String field;
    
    public ExampleClass(String field) {
        this.field = field;
    }
    
    public void exampleMethod() {
        if (condition) {
            // Code here
        }
    }
}
```

#### Best Practices
- Use `@Override` annotation for overridden methods
- Prefer composition over inheritance
- Use proper exception handling
- Add Javadoc for public APIs
- Use Optional for nullable return values

```java
/**
 * Handles player interaction events for AI companions.
 * 
 * @param player The player involved in the interaction
 * @param action The action performed
 * @return Optional result of the interaction
 */
public Optional<InteractionResult> handleInteraction(Player player, Action action) {
    try {
        // Implementation
        return Optional.of(result);
    } catch (Exception e) {
        logger.error("Failed to handle interaction", e);
        return Optional.empty();
    }
}
```

### TypeScript/Node.js Coding Standards

#### Naming Conventions
```typescript
// Classes: PascalCase
class MinecraftAIBody { }

// Functions and variables: camelCase
const botUsername = 'AICompanion';
function connectToServer() { }

// Constants: UPPER_SNAKE_CASE
const DEFAULT_RECONNECT_DELAY = 5000;

// Interfaces: PascalCase with 'I' prefix (optional)
interface IBotConfiguration { }
// or
interface BotConfiguration { }
```

#### Type Definitions
```typescript
// Use interfaces for object shapes
interface PlayerPosition {
  x: number;
  y: number;
  z: number;
  dimension: string;
}

// Use type aliases for unions and complex types
type BotAction = 'move' | 'attack' | 'build' | 'chat';

// Use enums for fixed sets of values
enum ConnectionState {
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  ERROR = 'error'
}
```

#### Best Practices
- Use strict TypeScript configuration
- Prefer `const` assertions for immutable data
- Use async/await over Promises chains
- Handle errors explicitly
- Document complex functions with JSDoc

```typescript
/**
 * Moves the bot to a specific location with pathfinding.
 * 
 * @param position - Target position to move to
 * @param options - Movement options (speed, pathfinding settings)
 * @returns Promise that resolves when movement is complete
 */
async function moveTo(
  position: PlayerPosition, 
  options: MovementOptions = {}
): Promise<MovementResult> {
  try {
    // Implementation
    return { success: true, finalPosition: position };
  } catch (error) {
    logger.error('Movement failed:', error);
    throw new Error(`Failed to move to position: ${error.message}`);
  }
}
```

## Testing Guidelines

### Java Testing (JUnit 5)

#### Test Structure
```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerEventHandlerTest {
    
    private PlayerEventHandler handler;
    private Player mockPlayer;
    
    @BeforeAll
    void setUp() {
        handler = new PlayerEventHandler();
        mockPlayer = mock(Player.class);
    }
    
    @Test
    @DisplayName("Should handle player join event")
    void shouldHandlePlayerJoinEvent() {
        // Given
        PlayerJoinEvent event = new PlayerJoinEvent(mockPlayer, "Player joined");
        
        // When
        handler.onPlayerJoin(event);
        
        // Then
        assertThat(handler.getActivePlayerCount()).isEqualTo(1);
    }
    
    @Test
    void shouldHandleNullPlayer() {
        // Given & When & Then
        assertThatThrownBy(() -> handler.handlePlayer(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Player cannot be null");
    }
}
```

#### Testing Best Practices
- Use descriptive test names
- Follow Given-When-Then pattern
- Test edge cases and error conditions
- Use mocks for external dependencies
- Aim for high test coverage (>80%)

### Node.js Testing (Jest)

#### Test Structure
```typescript
describe('MinecraftAIBody', () => {
  let aiBody: MinecraftAIBody;
  let mockBot: jest.Mocked<Bot>;
  
  beforeEach(() => {
    mockBot = createMockBot();
    aiBody = new MinecraftAIBody(mockBot);
  });
  
  afterEach(() => {
    jest.clearAllMocks();
  });
  
  describe('movement', () => {
    it('should move to specified coordinates', async () => {
      // Given
      const position = { x: 100, y: 64, z: 200, dimension: 'overworld' };
      
      // When
      const result = await aiBody.moveTo(position);
      
      // Then
      expect(result.success).toBe(true);
      expect(mockBot.pathfinder.setGoal).toHaveBeenCalled();
    });
    
    it('should handle movement failure gracefully', async () => {
      // Given
      const position = { x: 100, y: 64, z: 200, dimension: 'overworld' };
      mockBot.pathfinder.setGoal.mockRejectedValue(new Error('Path not found'));
      
      // When & Then
      await expect(aiBody.moveTo(position)).rejects.toThrow('Path not found');
    });
  });
});
```

#### Testing Best Practices
- Use Jest for test framework
- Mock external dependencies (Mineflayer, WebSocket)
- Test async operations properly
- Use beforeEach/afterEach for setup/cleanup
- Test both success and failure scenarios

## Building and Deployment

### Java Plugin Build

```bash
cd minecraft-ai-brain

# Clean and build
./gradlew clean build

# Build without tests (faster)
./gradlew build -x test

# Generate JAR for distribution
./gradlew shadowJar

# Output location
ls build/libs/minecraft-ai-brain-*.jar
```

### Node.js Bot Build

```bash
cd minecraft-ai-body

# Install dependencies
npm ci

# Build TypeScript
npm run build

# Create production bundle
npm run build:prod

# Output location
ls dist/
```

### Development Workflow

1. **Feature Development**
   ```bash
   # Create feature branch
   git checkout -b feature/new-feature
   
   # Make changes
   # ...
   
   # Test changes
   ./gradlew test  # Java
   npm test        # Node.js
   
   # Commit changes
   git add .
   git commit -m "feat: add new feature"
   ```

2. **Integration Testing**
   ```bash
   # Build both components
   cd minecraft-ai-brain && ./gradlew build
   cd ../minecraft-ai-body && npm run build
   
   # Test with local server
   # Copy plugin to test server
   # Start bot and test functionality
   ```

## Debugging

### Java Plugin Debugging

1. **Remote Debugging**
   ```bash
   # Start Minecraft server with debug flags
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar paper.jar
   ```

2. **IntelliJ Debug Configuration**
   - Run → Edit Configurations
   - Add "Remote JVM Debug"
   - Host: localhost, Port: 5005

3. **Logging**
   ```java
   private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
   
   logger.debug("Debug information: {}", value);
   logger.info("Information message");
   logger.warn("Warning message");
   logger.error("Error occurred", exception);
   ```

### Node.js Bot Debugging

1. **VS Code Debugging**
   ```json
   // .vscode/launch.json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "name": "Debug Bot",
         "type": "node",
         "request": "launch",
         "program": "${workspaceFolder}/src/index.ts",
         "outFiles": ["${workspaceFolder}/dist/**/*.js"],
         "env": {
           "NODE_ENV": "development"
         }
       }
     ]
   }
   ```

2. **Console Debugging**
   ```bash
   # Debug with Node.js inspector
   node --inspect-brk dist/index.js
   
   # Or use npm script
   npm run debug
   ```

3. **Logging**
   ```typescript
   import { createLogger, format, transports } from 'winston';
   
   const logger = createLogger({
     level: 'debug',
     format: format.combine(
       format.timestamp(),
       format.errors({ stack: true }),
       format.json()
     ),
     transports: [
       new transports.Console(),
       new transports.File({ filename: 'bot.log' })
     ]
   });
   
   logger.debug('Debug message', { data: value });
   logger.info('Information message');
   logger.error('Error occurred', error);
   ```

## Contributing Workflow

### Git Workflow

1. **Branch Naming**
   - `feature/description` - New features
   - `bugfix/description` - Bug fixes
   - `hotfix/description` - Critical fixes
   - `docs/description` - Documentation updates

2. **Commit Messages**
   ```
   type(scope): description
   
   Optional body explaining the change
   
   Optional footer with breaking changes or issue references
   ```
   
   Examples:
   ```
   feat(websocket): add heartbeat mechanism
   fix(movement): resolve pathfinding deadlock
   docs(api): update protocol specification
   test(combat): add unit tests for PvP system
   ```

3. **Pull Request Process**
   - Create descriptive PR title and description
   - Include testing instructions
   - Request appropriate reviewers
   - Ensure CI passes
   - Address review feedback

### Code Review Checklist

- [ ] Code follows project coding standards
- [ ] Tests are included and pass
- [ ] Documentation is updated
- [ ] No breaking changes (or properly documented)
- [ ] Performance considerations addressed
- [ ] Security implications considered
- [ ] Error handling is appropriate
- [ ] Logging is appropriate

### Release Process

1. **Version Bumping**
   ```bash
   # Update version in relevant files
   # - build.gradle (Java)
   # - package.json (Node.js)
   # - plugin.yml (Java)
   ```

2. **Create Release**
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

3. **Build Distribution**
   ```bash
   # Build both components for distribution
   ./gradlew build
   npm run build:prod
   
   # Create release artifacts
   # Upload to GitHub releases
   ```

This development guide ensures consistent code quality and development practices across the project. 