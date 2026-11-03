import { toApiError } from '@/api/apiErrorHandler';
import { apiClient } from '@/api/apiClient';
import type { PageResponse } from '@/types/api';
import type {
  ChatConversation,
  ChatMessage,
  ChatMessagePage,
  SendChatMessageRequest,
} from '@/types/chat';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** How many messages one page holds. */
export const CHAT_PAGE_SIZE = 30;

interface MessageQuery {
  /** Only messages before this one (the next older page) */
  beforeId?: number;
  /** Only messages containing this text (staff) */
  q?: string;
}

/** chat-service APIs. The participant's own conversation, and (platform admins) the shared support inbox. */
export const chatService = {
  /** The participant's conversation with Support; null when they have not written yet (404). */
  getMyConversation: async (): Promise<ChatConversation | null> => {
    try {
      return await apiClient.get<ChatConversation>(API_ENDPOINTS.chat.conversation);
    } catch (error) {
      if (toApiError(error).kind === 'notFound') {
        return null;
      }
      throw error;
    }
  },

  getMyMessages: ({ beforeId }: MessageQuery = {}) =>
    apiClient.get<ChatMessagePage>(API_ENDPOINTS.chat.messages, {
      params: { beforeId, limit: CHAT_PAGE_SIZE },
    }),

  /** The same clientMessageId again returns the same message (a retry never duplicates). */
  sendMyMessage: (request: SendChatMessageRequest) =>
    apiClient.post<ChatMessage>(API_ENDPOINTS.chat.messages, request),

  markMyConversationRead: () => apiClient.post<void>(API_ENDPOINTS.chat.read),

  /** Staff: conversations, pinned first then the newest. */
  getConversations: (search: string, page: number, size: number) =>
    apiClient.get<PageResponse<ChatConversation>>(API_ENDPOINTS.admin.chat.conversations, {
      params: { search: search || undefined, page, size },
    }),

  /** Opens the conversation with a user (created when there is none yet). */
  startConversation: (userId: number) =>
    apiClient.post<ChatConversation>(API_ENDPOINTS.admin.chat.conversations, { userId }),

  getConversation: (conversationId: number) =>
    apiClient.get<ChatConversation>(API_ENDPOINTS.admin.chat.conversation(conversationId)),

  getMessages: (conversationId: number, { beforeId, q }: MessageQuery = {}) =>
    apiClient.get<ChatMessagePage>(API_ENDPOINTS.admin.chat.messages(conversationId), {
      params: { beforeId, limit: CHAT_PAGE_SIZE, q: q || undefined },
    }),

  sendMessage: (conversationId: number, request: SendChatMessageRequest) =>
    apiClient.post<ChatMessage>(API_ENDPOINTS.admin.chat.messages(conversationId), request),

  markRead: (conversationId: number) =>
    apiClient.post<void>(API_ENDPOINTS.admin.chat.read(conversationId)),

  setPinned: (conversationId: number, pinned: boolean) =>
    apiClient.put<void>(API_ENDPOINTS.admin.chat.pin(conversationId), { pinned }),

  setMuted: (conversationId: number, muted: boolean) =>
    apiClient.put<void>(API_ENDPOINTS.admin.chat.mute(conversationId), { muted }),

  /** How many conversations have messages the staff has not read. */
  getUnreadCount: async (): Promise<number> => {
    const response = await apiClient.get<{ count: number }>(API_ENDPOINTS.admin.chat.unreadCount);
    return response.count;
  },
};
