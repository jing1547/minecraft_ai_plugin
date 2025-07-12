# Minecraft AI Protocol Definition

This directory contains the shared protocol definition files for communication between the Minecraft AI Brain (Java Plugin) and the Minecraft AI Body (Node.js Mineflayer Bot).

## Directory Structure

```
protocol/
├── README.md                           # This file
├── version.json                        # Protocol version information
├── schemas/                            # JSON Schema definitions
│   ├── base-message.json              # Base message schema
│   ├── commands/                       # Command message schemas
│   │   ├── command-message.json       # Command message base schema
│   │   └── movement-commands.json     # Movement command parameters
│   ├── responses/                      # Response message schemas
│   │   └── response-message.json      # Response message schema
│   ├── events/                         # Event message schemas
│   │   └── event-message.json         # Event message schema
│   └── states/                         # State message schemas
│       └── state-message.json         # State message schema
└── docs/                               # Documentation
    └── protocol-specification.md      # Complete protocol specification
```

## Protocol Overview

The protocol defines communication between two components:

- **Brain (Java Plugin)**: Issues commands, processes events, manages state
- **Body (Node.js Bot)**: Executes commands, generates events, reports state

Communication is based on WebSocket with JSON message format.

## Message Types

1. **Command Messages** (Brain → Body): Instructions for the bot to perform actions
2. **Response Messages** (Body → Brain): Results of command execution
3. **Event Messages** (Bidirectional): Notifications about game state changes
4. **State Messages** (Bidirectional): System state synchronization

## Schema Files

### Base Schema
- `base-message.json`: Common fields for all message types (type, id, timestamp, version, payload)

### Command Schemas
- `command-message.json`: Base schema for command messages
- `movement-commands.json`: Parameters for movement-related commands (moveTo, follow, stop)

### Response Schemas
- `response-message.json`: Schema for command execution results

### Event Schemas
- `event-message.json`: Schema for game and system events

### State Schemas
- `state-message.json`: Schema for state synchronization messages

## Usage

### For Java Development

1. Use the schema files to generate Java classes or validate JSON messages
2. Implement message serialization/deserialization using Gson
3. Reference the protocol specification for implementation details

### For Node.js Development

1. Use the schema files to generate TypeScript interfaces
2. Implement message validation using JSON Schema validators
3. Reference the protocol specification for implementation details

### Schema Validation

All schemas are JSON Schema Draft 07 compliant and can be used with any JSON Schema validator:

```javascript
// Example using ajv (Node.js)
const Ajv = require('ajv');
const ajv = new Ajv();

const baseSchema = require('./schemas/base-message.json');
const validate = ajv.compile(baseSchema);

const message = {
  type: 'command',
  id: '550e8400-e29b-41d4-a716-446655440000',
  timestamp: '2025-01-11T10:30:00Z',
  version: '1.0.0',
  payload: { /* ... */ }
};

const valid = validate(message);
if (!valid) {
  console.log(validate.errors);
}
```

## Version Information

Current protocol version: **1.0.0**

See `version.json` for detailed version information and changelog.

## Documentation

Complete protocol specification is available in `docs/protocol-specification.md`, including:

- Connection flow and handshake process
- Message format and examples
- Error handling and common error codes
- Security considerations
- Implementation notes

## Future Extensions

The protocol is designed to be extensible. Future versions may include:

- Support for multiple bot instances
- End-to-end encryption
- Message compression
- Binary message format for performance
- Plugin system for custom message types

## Contributing

When modifying the protocol:

1. Update the appropriate schema files
2. Increment the version number in `version.json`
3. Update the protocol specification documentation
4. Add examples for new message types
5. Update implementation code in both Brain and Body components 