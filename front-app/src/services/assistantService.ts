import { toApiError } from '@/api/apiErrorHandler';
import { apiClient } from '@/api/apiClient';
import type {
  AssistantChatRequest,
  AssistantChatResponse,
  AssistantConversation,
  AssistantHistoryMessage,
  AssistantStatus,
  ConfirmActionResponse,
} from '@/types/assistant';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';
import {
  isObject,
  readArray,
  readHistoryMessage,
  readLink,
  readNumber,
  readReply,
  readString,
} from '@/utils/assistantUtils';

/** A CPU model can need up to 90 s for an answer: this call waits longer than every other call does. */
export const ASSISTANT_CHAT_TIMEOUT_MS = 180_000;

const body = (value: unknown): Record<string, unknown> => (isObject(value) ? value : {});

/**
 * ai-mcp-client APIs (the AI assistant). Every answer is read defensively: missing lists are empty, cards of an
 * unknown kind and links that leave the application are dropped.
 */
export const assistantService = {
  chat: async (
    request: AssistantChatRequest,
    signal?: AbortSignal,
  ): Promise<AssistantChatResponse> => {
    const data = body(
      await apiClient.post<unknown>(API_ENDPOINTS.assistant.chat, request, {
        timeout: ASSISTANT_CHAT_TIMEOUT_MS,
        signal,
      }),
    );
    return {
      conversationId: readString(data.conversationId, request.conversationId ?? ''),
      messageId: readString(data.messageId),
      reply: readReply(data.reply),
      model: readString(data.model) || null,
      durationMs: readNumber(data.durationMs) ?? 0,
    };
  },

  /** The stored conversation; null when the server no longer has it (404). */
  getConversation: async (conversationId: string): Promise<AssistantConversation | null> => {
    try {
      const data = body(
        await apiClient.get<unknown>(API_ENDPOINTS.assistant.conversation, {
          params: { conversationId },
        }),
      );
      const messages = readArray(data.messages).flatMap((entry): AssistantHistoryMessage[] => {
        const message = readHistoryMessage(entry);
        return message ? [message] : [];
      });
      return { conversationId: readString(data.conversationId, conversationId), messages };
    } catch (error) {
      if (toApiError(error).kind === 'notFound') {
        return null;
      }
      throw error;
    }
  },

  deleteConversation: (conversationId: string) =>
    apiClient.delete(API_ENDPOINTS.assistant.conversation, { params: { conversationId } }),

  confirmAction: async (actionId: string): Promise<ConfirmActionResponse> => {
    const data = body(await apiClient.post<unknown>(API_ENDPOINTS.assistant.confirm(actionId)));
    const order = isObject(data.order) ? data.order : null;
    return {
      status: data.status === 'ALREADY_CREATED' ? 'ALREADY_CREATED' : 'CREATED',
      order: order
        ? {
            id: readString(order.id),
            status: readString(order.status),
            total: readNumber(order.total),
            currency: readString(order.currency),
          }
        : null,
      message: readString(data.message),
      link: readLink(data.link),
    };
  },

  cancelAction: (actionId: string) =>
    apiClient.post<void>(API_ENDPOINTS.assistant.cancel(actionId)),

  getSuggestions: async (): Promise<string[]> => {
    const data = body(await apiClient.get<unknown>(API_ENDPOINTS.assistant.suggestions));
    return readArray(data.suggestions)
      .map((text) => readString(text).trim())
      .filter(Boolean);
  },

  getStatus: async (): Promise<AssistantStatus> => {
    const data = body(await apiClient.get<unknown>(API_ENDPOINTS.assistant.status));
    return {
      available: data.available === true,
      model: readString(data.model) || null,
      message: readString(data.message) || null,
    };
  },
};
