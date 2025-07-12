// Using crypto.randomUUID() instead of uuid library to avoid dependency issues
// import { v4 as uuidv4 } from 'uuid';

// UUID v4 generator function
function uuidv4(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}
import {
    BaseMessage,
    CommandMessage,
    ResponseMessage,
    EventMessage,
    StateMessage,
    Message,
    MessageType,
    Priority,
    CommandAction,
    CommandPayload,
    ResponsePayload,
    EventPayload,
    StatePayload,
    MovementParameters,
    FollowParameters,
    ChatParameters,
    ItemParameters,
    BlockParameters,
    isValidMessage,
    isValidUUID,
    isValidISO8601,
    isCommandMessage,
    isResponseMessage,
    isEventMessage,
    isStateMessage
} from './types';

/**
 * Message serializer for WebSocket communication
 * Handles JSON serialization/deserialization of protocol messages
 */
export class MessageSerializer {
    private static readonly PROTOCOL_VERSION = '1.0.0';

    /**
     * Serialize a message to JSON string
     */
    public static serialize(message: BaseMessage): string {
        try {
            return JSON.stringify(message);
        } catch (error) {
            throw new Error(`Failed to serialize message: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    /**
     * Deserialize JSON string to BaseMessage
     */
    public static deserialize(json: string): BaseMessage {
        try {
            const parsed = JSON.parse(json);
            
            if (!isValidMessage(parsed)) {
                throw new Error('Invalid message format');
            }

            // Validate UUID format
            if (!isValidUUID(parsed.id)) {
                throw new Error('Invalid message ID format');
            }

            // Validate timestamp format
            if (!isValidISO8601(parsed.timestamp)) {
                throw new Error('Invalid timestamp format');
            }

            return parsed as BaseMessage;
        } catch (error) {
            throw new Error(`Failed to deserialize message: ${error instanceof Error ? error.message : 'Unknown error'}`);
        }
    }

    /**
     * Deserialize JSON string to specific message type
     */
    public static deserializeAs<T extends BaseMessage>(json: string, messageType: MessageType): T {
        const message = this.deserialize(json);
        
        if (message.type !== messageType) {
            throw new Error(`Message type mismatch: expected ${messageType}, got ${message.type}`);
        }

        return message as T;
    }

    /**
     * Check if a JSON string represents a valid message
     */
    public static isValidMessage(json: string): boolean {
        try {
            const parsed = JSON.parse(json);
            return isValidMessage(parsed);
        } catch {
            return false;
        }
    }

    /**
     * Get message type from JSON without full deserialization
     */
    public static getMessageType(json: string): MessageType | null {
        try {
            const parsed = JSON.parse(json);
            return parsed.type || null;
        } catch {
            return null;
        }
    }

    /**
     * Get message ID from JSON without full deserialization
     */
    public static getMessageId(json: string): string | null {
        try {
            const parsed = JSON.parse(json);
            return parsed.id || null;
        } catch {
            return null;
        }
    }

    /**
     * Get correlation ID from JSON without full deserialization
     */
    public static getCorrelationId(json: string): string | null {
        try {
            const parsed = JSON.parse(json);
            return parsed.correlationId || null;
        } catch {
            return null;
        }
    }

    /**
     * Create a command message
     */
    public static createCommandMessage(
        action: CommandAction | string,
        parameters: { [key: string]: any },
        options: {
            timeout?: number;
            priority?: Priority;
            correlationId?: string;
        } = {}
    ): CommandMessage {
        const payload: CommandPayload = {
            action: typeof action === 'string' ? action : String(action),
            parameters,
            timeout: options.timeout || 10000
        };

        const message: CommandMessage = {
            type: MessageType.COMMAND,
            id: uuidv4(),
            timestamp: new Date().toISOString(),
            version: this.PROTOCOL_VERSION,
            priority: options.priority || Priority.NORMAL,
            payload
        };

        // Only add correlationId if it's provided
        if (options.correlationId !== undefined) {
            message.correlationId = options.correlationId;
        }

        return message;
    }

    /**
     * Create a response message
     */
    public static createResponseMessage(
        correlationId: string,
        success: boolean,
        result?: any,
        error?: {
            code: string;
            message: string;
            details?: any;
        },
        executionTime?: number
    ): ResponseMessage {
        const payload: ResponsePayload = {
            success,
            result
        };

        // Only add error if it's provided
        if (error !== undefined) {
            payload.error = error;
        }

        // Only add executionTime if it's provided
        if (executionTime !== undefined) {
            payload.executionTime = executionTime;
        }

        return {
            type: MessageType.RESPONSE,
            id: uuidv4(),
            timestamp: new Date().toISOString(),
            version: this.PROTOCOL_VERSION,
            correlationId,
            payload
        };
    }

    /**
     * Create an event message
     */
    public static createEventMessage(
        eventType: string,
        data: { [key: string]: any },
        source?: string,
        priority?: Priority
    ): EventMessage {
        const payload: EventPayload = {
            eventType,
            data
        };

        // Only add source if it's provided
        if (source !== undefined) {
            payload.source = source;
        }

        return {
            type: MessageType.EVENT,
            id: uuidv4(),
            timestamp: new Date().toISOString(),
            version: this.PROTOCOL_VERSION,
            priority: priority || Priority.NORMAL,
            payload
        };
    }

    /**
     * Create a state message
     */
    public static createStateMessage(
        stateType: string,
        data: { [key: string]: any },
        priority?: Priority
    ): StateMessage {
        const payload: StatePayload = {
            stateType,
            data,
            timestamp: new Date().toISOString()
        };

        return {
            type: MessageType.STATE,
            id: uuidv4(),
            timestamp: new Date().toISOString(),
            version: this.PROTOCOL_VERSION,
            priority: priority || Priority.NORMAL,
            payload
        };
    }
}

/**
 * Utility class for creating specific command messages
 */
export class CommandMessageBuilder {
    
    /**
     * Create a move to command
     */
    public static moveTo(x: number, y: number, z: number, timeout?: number): CommandMessage {
        const parameters: MovementParameters = { x, y, z };
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.MOVE_TO, parameters, options);
    }

    /**
     * Create a follow command
     */
    public static follow(playerName: string, distance?: number, timeout?: number): CommandMessage {
        const parameters: FollowParameters = { playerName };
        if (distance !== undefined) {
            parameters.distance = distance;
        }
        
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.FOLLOW, parameters, options);
    }

    /**
     * Create a stop command
     */
    public static stop(timeout?: number): CommandMessage {
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.STOP, {}, options);
    }

    /**
     * Create a chat command
     */
    public static chat(message: string, target?: string, timeout?: number): CommandMessage {
        const parameters: ChatParameters = { message };
        if (target !== undefined) {
            parameters.target = target;
        }
        
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.CHAT, parameters, options);
    }

    /**
     * Create an attack command
     */
    public static attack(targetId?: string, timeout?: number): CommandMessage {
        const parameters = targetId ? { targetId } : {};
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.ATTACK, parameters, options);
    }

    /**
     * Create a break block command
     */
    public static breakBlock(x: number, y: number, z: number, timeout?: number): CommandMessage {
        const parameters: BlockParameters = { x, y, z };
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.BREAK, parameters, options);
    }

    /**
     * Create a place block command
     */
    public static placeBlock(x: number, y: number, z: number, face?: number, timeout?: number): CommandMessage {
        const parameters: BlockParameters = { x, y, z };
        if (face !== undefined) {
            parameters.face = face;
        }
        
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.PLACE, parameters, options);
    }

    /**
     * Create an equip item command
     */
    public static equipItem(itemName: string, slot?: number, timeout?: number): CommandMessage {
        const parameters: ItemParameters = { itemName };
        if (slot !== undefined) {
            parameters.slot = slot;
        }
        
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.EQUIP_ITEM, parameters, options);
    }

    /**
     * Create a disconnect command
     */
    public static disconnect(timeout?: number): CommandMessage {
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.DISCONNECT, {}, options);
    }

    /**
     * Create a status command
     */
    public static status(timeout?: number): CommandMessage {
        const options: { timeout?: number } = {};
        if (timeout !== undefined) {
            options.timeout = timeout;
        }
        return MessageSerializer.createCommandMessage(CommandAction.STATUS, {}, options);
    }
}

/**
 * Utility class for creating response messages
 */
export class ResponseMessageBuilder {
    
    /**
     * Create a success response
     */
    public static success(correlationId: string, result?: any, executionTime?: number): ResponseMessage {
        return MessageSerializer.createResponseMessage(correlationId, true, result, undefined, executionTime);
    }

    /**
     * Create an error response
     */
    public static error(
        correlationId: string,
        code: string,
        message: string,
        details?: any,
        executionTime?: number
    ): ResponseMessage {
        return MessageSerializer.createResponseMessage(
            correlationId,
            false,
            undefined,
            { code, message, details },
            executionTime
        );
    }

    /**
     * Create a timeout response
     */
    public static timeout(correlationId: string, executionTime?: number): ResponseMessage {
        return this.error(
            correlationId,
            'TIMEOUT',
            'Command execution timed out',
            undefined,
            executionTime
        );
    }

    /**
     * Create a not found response
     */
    public static notFound(correlationId: string, resource: string): ResponseMessage {
        return this.error(
            correlationId,
            'NOT_FOUND',
            `Resource not found: ${resource}`
        );
    }

    /**
     * Create an invalid command response
     */
    public static invalidCommand(correlationId: string, reason: string): ResponseMessage {
        return this.error(
            correlationId,
            'INVALID_COMMAND',
            `Invalid command: ${reason}`
        );
    }
} 