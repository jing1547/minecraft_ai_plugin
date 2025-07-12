# Minecraft AI Protocol Specification

## Version 1.0.0

### Overview

This document describes the communication protocol between the Minecraft AI Brain (Java Plugin) and the Minecraft AI Body (Node.js Mineflayer Bot). The protocol is based on WebSocket communication with JSON message format.

### Architecture

```
┌─────────────────────┐    WebSocket     ┌─────────────────────┐
│  Minecraft AI Brain │ ◄──────────────► │  Minecraft AI Body  │
│   (Java Plugin)     │     JSON/TCP     │  (Node.js Bot)      │
│                     │                  │                     │
│  - Command Issuer   │                  │  - Command Executor │
│  - Event Processor  │                  │  - Event Generator  │
│  - State Manager    │                  │  - State Reporter   │
└─────────────────────┘                  └─────────────────────┘
```

### Connection Flow

1. **Brain starts WebSocket server** on configured port (default: 8765)
2. **Body connects to Brain** using WebSocket client
3. **Handshake process**:
   - Body sends connection request with protocol version
   - Brain validates and responds with acceptance/rejection
   - Both sides exchange initial state information
4. **Heartbeat mechanism** begins (every 30 seconds)
5. **Normal operation** with command/response/event/state messages

### Message Format

All messages follow a common base schema:

```json
{
  "type": "command|response|event|state",
  "id": "uuid-v4",
  "timestamp": "ISO-8601-datetime",
  "version": "1.0.0",
  "correlationId": "uuid-v4",
  "priority": "low|normal|high|urgent",
  "payload": {
    // Message-specific content
  }
}
```

### Message Types

#### 1. Command Messages (Brain → Body)

Commands are sent from Brain to Body to instruct the bot to perform actions.

**Structure:**
```json
{
  "type": "command",
  "payload": {
    "action": "moveTo|follow|stop|attack|use|break|place|equipItem|craftItem|dropItem|chat|whisper|disconnect|status|debug",
    "parameters": {
      // Action-specific parameters
    },
    "timeout": 10000
  }
}
```

**Examples:**

Move to location:
```json
{
  "type": "command",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-01-11T10:30:00Z",
  "version": "1.0.0",
  "priority": "normal",
  "payload": {
    "action": "moveTo",
    "parameters": {
      "x": 100,
      "y": 64,
      "z": 200,
      "sprint": true,
      "allowParkour": true
    },
    "timeout": 30000
  }
}
```

Follow player:
```json
{
  "type": "command",
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "timestamp": "2025-01-11T10:31:00Z",
  "version": "1.0.0",
  "priority": "normal",
  "payload": {
    "action": "follow",
    "parameters": {
      "target": "PlayerName",
      "distance": 3,
      "continuous": true
    },
    "timeout": 60000
  }
}
```

#### 2. Response Messages (Body → Brain)

Responses are sent from Body to Brain to report command execution results.

**Structure:**
```json
{
  "type": "response",
  "payload": {
    "success": true,
    "commandId": "uuid-v4",
    "action": "moveTo",
    "result": {
      // Action-specific result data
    },
    "error": {
      "code": "error-code",
      "message": "Error description",
      "details": {}
    },
    "duration": 5000,
    "metadata": {}
  }
}
```

**Examples:**

Successful movement:
```json
{
  "type": "response",
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "timestamp": "2025-01-11T10:30:05Z",
  "version": "1.0.0",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "priority": "normal",
  "payload": {
    "success": true,
    "commandId": "550e8400-e29b-41d4-a716-446655440000",
    "action": "moveTo",
    "result": {
      "finalPosition": {
        "x": 100.5,
        "y": 64.0,
        "z": 200.2
      },
      "pathLength": 45.7
    },
    "duration": 5000
  }
}
```

Failed command:
```json
{
  "type": "response",
  "id": "550e8400-e29b-41d4-a716-446655440003",
  "timestamp": "2025-01-11T10:30:10Z",
  "version": "1.0.0",
  "correlationId": "550e8400-e29b-41d4-a716-446655440001",
  "priority": "normal",
  "payload": {
    "success": false,
    "commandId": "550e8400-e29b-41d4-a716-446655440001",
    "action": "follow",
    "error": {
      "code": "TARGET_NOT_FOUND",
      "message": "Target player 'PlayerName' not found",
      "details": {
        "availablePlayers": ["OtherPlayer1", "OtherPlayer2"]
      }
    },
    "duration": 100
  }
}
```

#### 3. Event Messages (Bidirectional)

Events are sent to notify about game state changes or system events.

**Structure:**
```json
{
  "type": "event",
  "payload": {
    "eventType": "playerJoined|playerLeft|entitySpawned|entityDespawned|blockChanged|damageReceived|healthChanged|inventoryChanged|connectionLost|connectionRestored|chatMessage|death|respawn|dimensionChanged|weatherChanged|timeChanged",
    "source": "brain|body",
    "data": {
      // Event-specific data
    },
    "location": {
      "x": 100,
      "y": 64,
      "z": 200,
      "dimension": "overworld"
    },
    "severity": "info|warning|error|critical"
  }
}
```

**Examples:**

Player joined:
```json
{
  "type": "event",
  "id": "550e8400-e29b-41d4-a716-446655440004",
  "timestamp": "2025-01-11T10:32:00Z",
  "version": "1.0.0",
  "priority": "normal",
  "payload": {
    "eventType": "playerJoined",
    "source": "brain",
    "data": {
      "playerName": "NewPlayer",
      "uuid": "123e4567-e89b-12d3-a456-426614174000"
    },
    "severity": "info"
  }
}
```

Damage received:
```json
{
  "type": "event",
  "id": "550e8400-e29b-41d4-a716-446655440005",
  "timestamp": "2025-01-11T10:33:00Z",
  "version": "1.0.0",
  "priority": "high",
  "payload": {
    "eventType": "damageReceived",
    "source": "body",
    "data": {
      "damage": 4,
      "damageType": "mob",
      "attacker": "zombie",
      "remainingHealth": 16
    },
    "location": {
      "x": 105.2,
      "y": 64.0,
      "z": 203.7,
      "dimension": "overworld"
    },
    "severity": "warning"
  }
}
```

#### 4. State Messages (Bidirectional)

State messages are used to synchronize system state between Brain and Body.

**Structure:**
```json
{
  "type": "state",
  "payload": {
    "stateType": "botStatus|playerStatus|worldStatus|connectionStatus|inventoryStatus|healthStatus|locationStatus|targetStatus",
    "data": {
      // State-specific data
    },
    "partial": false,
    "sequenceNumber": 123,
    "lastUpdated": "2025-01-11T10:34:00Z"
  }
}
```

**Examples:**

Bot status update:
```json
{
  "type": "state",
  "id": "550e8400-e29b-41d4-a716-446655440006",
  "timestamp": "2025-01-11T10:34:00Z",
  "version": "1.0.0",
  "priority": "normal",
  "payload": {
    "stateType": "botStatus",
    "data": {
      "position": {
        "x": 100.5,
        "y": 64.0,
        "z": 200.2
      },
      "health": 20,
      "food": 18,
      "isMoving": false,
      "currentAction": "idle",
      "connectedPlayers": ["Player1", "Player2"]
    },
    "partial": false,
    "sequenceNumber": 123,
    "lastUpdated": "2025-01-11T10:34:00Z"
  }
}
```

### Connection Management

#### Handshake Process

1. **Connection Request (Body → Brain):**
```json
{
  "type": "event",
  "payload": {
    "eventType": "connectionRequest",
    "source": "body",
    "data": {
      "protocolVersion": "1.0.0",
      "clientInfo": {
        "name": "minecraft-ai-body",
        "version": "1.0.0",
        "capabilities": ["movement", "combat", "building"]
      }
    }
  }
}
```

2. **Connection Response (Brain → Body):**
```json
{
  "type": "response",
  "payload": {
    "success": true,
    "result": {
      "protocolVersion": "1.0.0",
      "sessionId": "session-uuid",
      "serverInfo": {
        "name": "minecraft-ai-brain",
        "version": "1.0.0",
        "capabilities": ["ai-conversation", "player-tracking"]
      }
    }
  }
}
```

#### Heartbeat Mechanism

- **Interval:** 30 seconds
- **Timeout:** 60 seconds (2 missed heartbeats)
- **Heartbeat message:**

```json
{
  "type": "event",
  "payload": {
    "eventType": "heartbeat",
    "source": "brain|body",
    "data": {
      "timestamp": "2025-01-11T10:35:00Z"
    }
  }
}
```

### Error Handling

#### Common Error Codes

- `PROTOCOL_VERSION_MISMATCH`: Incompatible protocol versions
- `INVALID_MESSAGE_FORMAT`: Malformed JSON or missing required fields
- `COMMAND_TIMEOUT`: Command execution exceeded timeout
- `TARGET_NOT_FOUND`: Specified target does not exist
- `PERMISSION_DENIED`: Insufficient permissions for action
- `RESOURCE_UNAVAILABLE`: Required resource is not available
- `INTERNAL_ERROR`: Internal system error

#### Error Response Format

```json
{
  "type": "response",
  "payload": {
    "success": false,
    "error": {
      "code": "COMMAND_TIMEOUT",
      "message": "Command execution exceeded timeout of 10000ms",
      "details": {
        "commandId": "550e8400-e29b-41d4-a716-446655440000",
        "timeoutMs": 10000,
        "elapsedMs": 12000
      }
    }
  }
}
```

### Implementation Notes

1. **Message Ordering:** Messages should be processed in the order they are received
2. **Idempotency:** Commands with the same ID should be idempotent
3. **Timeouts:** All commands should have configurable timeouts
4. **Buffering:** Implement message buffering during connection interruptions
5. **Reconnection:** Automatic reconnection with exponential backoff
6. **Logging:** All messages should be logged for debugging purposes

### Security Considerations

1. **Authentication:** WebSocket connections should be authenticated
2. **Authorization:** Commands should be authorized based on permissions
3. **Input Validation:** All message payloads should be validated
4. **Rate Limiting:** Implement rate limiting to prevent abuse
5. **Connection Limits:** Limit the number of concurrent connections

### Future Extensions

- Support for multiple bot instances
- End-to-end encryption
- Message compression
- Binary message format for performance
- Plugin system for custom message types 