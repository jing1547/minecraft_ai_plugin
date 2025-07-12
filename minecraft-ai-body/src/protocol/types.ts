/**
 * WebSocket Protocol Types for Minecraft AI Communication
 */

export enum MessageType {
    COMMAND = 'command',
    RESPONSE = 'response',
    EVENT = 'event',
    STATE = 'state'
}

export enum Priority {
    LOW = 'low',
    NORMAL = 'normal',
    HIGH = 'high',
    URGENT = 'urgent'
}

export enum CommandAction {
    MOVE_TO = 'moveTo',
    FOLLOW = 'follow',
    STOP = 'stop',
    ATTACK = 'attack',
    USE = 'use',
    BREAK = 'break',
    PLACE = 'place',
    EQUIP_ITEM = 'equipItem',
    CRAFT_ITEM = 'craftItem',
    DROP_ITEM = 'dropItem',
    CHAT = 'chat',
    WHISPER = 'whisper',
    DISCONNECT = 'disconnect',
    STATUS = 'status',
    DEBUG = 'debug'
}

/**
 * Base message interface for all WebSocket communication
 */
export interface BaseMessage {
    type: MessageType;
    id: string;
    timestamp: string;
    version: string;
    correlationId?: string;
    priority?: Priority;
    payload: any;
}

/**
 * Command message payload structure
 */
export interface CommandPayload {
    action: string;
    parameters: { [key: string]: any };
    timeout?: number;
}

/**
 * Command message interface
 */
export interface CommandMessage extends BaseMessage {
    type: MessageType.COMMAND;
    payload: CommandPayload;
}

/**
 * Response message payload structure
 */
export interface ResponsePayload {
    success: boolean;
    result?: any;
    error?: {
        code: string;
        message: string;
        details?: any;
    };
    executionTime?: number;
}

/**
 * Response message interface
 */
export interface ResponseMessage extends BaseMessage {
    type: MessageType.RESPONSE;
    payload: ResponsePayload;
    correlationId: string; // Always required for responses
}

/**
 * Event message payload structure
 */
export interface EventPayload {
    eventType: string;
    data: { [key: string]: any };
    source?: string;
}

/**
 * Event message interface
 */
export interface EventMessage extends BaseMessage {
    type: MessageType.EVENT;
    payload: EventPayload;
}

/**
 * State message payload structure
 */
export interface StatePayload {
    stateType: string;
    data: { [key: string]: any };
    timestamp: string;
}

/**
 * State message interface
 */
export interface StateMessage extends BaseMessage {
    type: MessageType.STATE;
    payload: StatePayload;
}

/**
 * Union type for all message types
 */
export type Message = CommandMessage | ResponseMessage | EventMessage | StateMessage;

/**
 * Movement command parameters
 */
export interface MovementParameters {
    x: number;
    y: number;
    z: number;
}

/**
 * Follow command parameters
 */
export interface FollowParameters {
    playerName: string;
    distance?: number;
}

/**
 * Chat command parameters
 */
export interface ChatParameters {
    message: string;
    target?: string;
}

/**
 * Item action parameters
 */
export interface ItemParameters {
    itemId?: string;
    itemName?: string;
    slot?: number;
    count?: number;
}

/**
 * Block action parameters
 */
export interface BlockParameters {
    x: number;
    y: number;
    z: number;
    face?: number;
}

/**
 * Type guard functions
 */
export function isCommandMessage(message: BaseMessage): message is CommandMessage {
    return message.type === MessageType.COMMAND;
}

export function isResponseMessage(message: BaseMessage): message is ResponseMessage {
    return message.type === MessageType.RESPONSE;
}

export function isEventMessage(message: BaseMessage): message is EventMessage {
    return message.type === MessageType.EVENT;
}

export function isStateMessage(message: BaseMessage): message is StateMessage {
    return message.type === MessageType.STATE;
}

/**
 * Validation functions
 */
export function isValidMessage(obj: any): obj is BaseMessage {
    return obj &&
           typeof obj.type === 'string' &&
           typeof obj.id === 'string' &&
           typeof obj.timestamp === 'string' &&
           typeof obj.version === 'string' &&
           obj.payload !== undefined;
}

export function isValidUUID(uuid: string): boolean {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    return uuidRegex.test(uuid);
}

export function isValidISO8601(dateString: string): boolean {
    const date = new Date(dateString);
    return !isNaN(date.getTime()) && dateString === date.toISOString();
} 