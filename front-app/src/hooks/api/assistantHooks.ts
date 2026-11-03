import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { assistantService } from '@/services/assistantService';
import type { AssistantChatRequest } from '@/types/assistant';
import type { ActingUser } from '@/types/user';
import { QUERY_KEYS } from '@/utils/queryKeys';

const STATUS_STALE_MS = 30_000;
const SUGGESTIONS_STALE_MS = 5 * 60_000;

/** Whether the assistant is up (looked at when the panel opens, and again on "Retry"). */
export function useAssistantStatus(user: ActingUser, enabled: boolean) {
  return useQuery({
    queryKey: QUERY_KEYS.assistant.status(user.id),
    queryFn: () => assistantService.getStatus(),
    enabled,
    staleTime: STATUS_STALE_MS,
  });
}

/** The questions to offer for the user's role. */
export function useAssistantSuggestions(user: ActingUser, enabled: boolean) {
  return useQuery({
    queryKey: QUERY_KEYS.assistant.suggestions(user.id, user.role),
    queryFn: () => assistantService.getSuggestions(),
    enabled,
    staleTime: SUGGESTIONS_STALE_MS,
  });
}

/** The stored conversation (after a page refresh); data is null when the server no longer has it. */
export function useAssistantHistory(
  user: ActingUser,
  conversationId: string | null,
  enabled: boolean,
) {
  return useQuery({
    queryKey: QUERY_KEYS.assistant.conversation(user.id, conversationId ?? ''),
    queryFn: () => assistantService.getConversation(conversationId as string),
    enabled: enabled && conversationId !== null,
    staleTime: Infinity,
    gcTime: 0,
  });
}

interface ChatVariables {
  request: AssistantChatRequest;
  /** Aborts the call (the Cancel button) */
  signal: AbortSignal;
}

/** One question to the assistant (it can take a minute or more: see ASSISTANT_CHAT_TIMEOUT_MS). */
export function useAssistantChat() {
  return useMutation({
    mutationFn: ({ request, signal }: ChatVariables) => assistantService.chat(request, signal),
  });
}

/** "New chat": the stored conversation is deleted on the server. */
export function useClearAssistantConversation() {
  return useMutation({
    mutationFn: (conversationId: string) => assistantService.deleteConversation(conversationId),
  });
}

/**
 * Confirm what the assistant prepared (an order, a cancellation, an order or delivery step...). The change can show on
 * any screen of the user's area, so every list except the assistant's own is read again.
 */
export function useConfirmAssistantAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (actionId: string) => assistantService.confirmAction(actionId),
    onSuccess: () =>
      queryClient.invalidateQueries({
        predicate: (query) => query.queryKey[0] !== QUERY_KEYS.assistant.all[0],
      }),
  });
}

export function useCancelAssistantAction() {
  return useMutation({
    mutationFn: (actionId: string) => assistantService.cancelAction(actionId),
  });
}
