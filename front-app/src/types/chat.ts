/** chat-service: the support chat. Every customer, restaurant owner or delivery partner has one conversation with "Support"; platform admins share the inbox. */
import type { UserRole } from '@/types/user';

/** Who can have a conversation with Support (everybody but the platform admins, who are Support). */
export type ChatParticipantRole = Exclude<UserRole, 'PLATFORM_ADMIN'>;

export interface ChatParticipant {
  userId: number;
  name: string;
  role: ChatParticipantRole;
  /** null when the presence is not known */
  online: boolean | null;
}

export interface ChatLastMessage {
  preview: string;
  at: string;
  fromStaff: boolean;
  /** Read by the other side */
  read: boolean;
}

/** One conversation as the staff inbox lists it, and as the participant sees their own. */
export interface ChatConversation {
  id: number;
  participant: ChatParticipant;
  lastMessage: ChatLastMessage | null;
  unreadCount: number;
  pinned: boolean;
  muted: boolean;
  createdAt: string;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  clientMessageId: string;
  senderUserId: number;
  senderName: string;
  senderRole: UserRole;
  fromStaff: boolean;
  body: string;
  createdAt: string;
  /** null while the other side has not read it */
  readAt: string | null;
}

/** One page of messages, oldest first; the next (older) page starts before nextBeforeId. */
export interface ChatMessagePage {
  messages: ChatMessage[];
  hasMore: boolean;
  nextBeforeId: number | null;
}

export interface SendChatMessageRequest {
  body: string;
  /** Made by the browser: sending it again (a retry) never creates a second message */
  clientMessageId: string;
}

/** A message the browser has not had confirmed yet. */
export interface PendingChatMessage {
  clientMessageId: string;
  body: string;
  createdAt: string;
  status: 'sending' | 'failed';
  /** For a failed one: what to tell the user */
  error?: string;
}

/** Which conversation a chat screen works on: the staff's (by id) or the participant's own. */
export type ChatScope = { kind: 'staff'; conversationId: number } | { kind: 'participant' };

export const CHAT_MESSAGE_MAX_LENGTH = 2000;
