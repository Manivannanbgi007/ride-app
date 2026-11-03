import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import { useCurrentUser } from '@/hooks/api/userHooks';
import { useActingUser } from '@/hooks/useActingUser';
import { usePageVisible } from '@/hooks/usePageVisible';
import { useLiveStatus } from '@/realtime/liveStatusContext';
import { chatService } from '@/services/chatService';
import type {
  ChatConversation,
  ChatMessage,
  ChatMessagePage,
  ChatScope,
  PendingChatMessage,
} from '@/types/chat';
import {
  flattenMessagePages,
  mergeWithPending,
  newClientMessageId,
  type DisplayMessage,
} from '@/utils/chatUtils';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** The staff's lists are read again this often, whatever the live connection says (a safety net). */
const SAFETY_REFRESH_MS = 30_000;
const UNREAD_REFRESH_MS = 60_000;
/** Without a live connection a participant's chat polls this often. */
const FALLBACK_POLL_MS = 15_000;
const MARK_READ_DELAY_MS = 400;

/** The staff inbox, pinned first then the newest, page by page. */
export function useStaffConversations(search: string) {
  return useInfiniteQuery({
    queryKey: QUERY_KEYS.chat.conversations(search),
    queryFn: ({ pageParam }) => chatService.getConversations(search, pageParam, DEFAULT_PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.page + 1),
    placeholderData: keepPreviousData,
    refetchInterval: SAFETY_REFRESH_MS,
  });
}

export function useStaffConversation(conversationId: number | null) {
  return useQuery({
    queryKey: QUERY_KEYS.chat.conversation(conversationId ?? 0),
    queryFn: () => chatService.getConversation(conversationId as number),
    enabled: conversationId !== null,
    refetchInterval: SAFETY_REFRESH_MS,
  });
}

/** Conversations with messages the staff has not read (the number on "Chat"). */
export function useChatUnreadCount() {
  return useQuery({
    queryKey: QUERY_KEYS.chat.unreadCount,
    queryFn: () => chatService.getUnreadCount(),
    refetchInterval: UNREAD_REFRESH_MS,
  });
}

export function useStartConversation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: number) => chatService.startConversation(userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.chat.conversationsAll }),
  });
}

/** Pin or mute one conversation (for the acting admin): the screen changes at once and goes back on failure. */
function useConversationFlag(conversationId: number, flag: 'pinned' | 'muted') {
  const queryClient = useQueryClient();
  const key = QUERY_KEYS.chat.conversation(conversationId);
  return useMutation({
    mutationFn: (value: boolean) =>
      flag === 'pinned'
        ? chatService.setPinned(conversationId, value)
        : chatService.setMuted(conversationId, value),
    onMutate: async (value) => {
      await queryClient.cancelQueries({ queryKey: key });
      const before = queryClient.getQueryData<ChatConversation>(key);
      queryClient.setQueryData<ChatConversation>(key, (current) =>
        current ? { ...current, [flag]: value } : current,
      );
      return { before };
    },
    onError: (_error, _value, context) => {
      queryClient.setQueryData(key, context?.before);
    },
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.chat.conversationsAll }),
        queryClient.invalidateQueries({ queryKey: key }),
      ]),
  });
}

export const usePinConversation = (conversationId: number) =>
  useConversationFlag(conversationId, 'pinned');

export const useMuteConversation = (conversationId: number) =>
  useConversationFlag(conversationId, 'muted');

/**
 * The signed-in participant's own conversation with Support (null when they have not written yet); its unread
 * count is the badge on "Support". `pollMs` is how often it is read again while on screen.
 */
export function useMyConversation(pollMs: number = UNREAD_REFRESH_MS) {
  const actingUser = useActingUser();
  return useQuery({
    queryKey: QUERY_KEYS.chat.myConversation(actingUser?.id ?? 0),
    queryFn: () => chatService.getMyConversation(),
    enabled: actingUser !== null,
    refetchInterval: pollMs,
  });
}

/** What to tell the user when a message could not be sent. */
export function sendErrorMessage(error: unknown): string {
  const { kind, userMessage } = toApiError(error);
  if (kind === 'tooManyRequests') {
    return ERROR_MESSAGES.CHAT_TOO_FAST;
  }
  if (kind === 'unavailable') {
    return ERROR_MESSAGES.CHAT_UNAVAILABLE;
  }
  return userMessage;
}

interface ThreadOptions {
  /** Only messages containing this text (staff); an active search shows no unconfirmed messages */
  search?: string;
  /** False while the conversation does not exist yet (a participant who has not written) */
  enabled?: boolean;
}

export interface ChatThread {
  messages: DisplayMessage[];
  isLoading: boolean;
  error: unknown;
  refetch: () => void;
  hasEarlier: boolean;
  isLoadingEarlier: boolean;
  loadEarlier: () => void;
  /** Sends a message at once (a bubble shows while it travels); returns its client id */
  send: (body: string) => string;
  /** Sends a failed message again with the same client id, so the server never makes two */
  retry: (clientMessageId: string) => void;
}

/**
 * One conversation's messages and sending. Messages come page by page (newest first, 30 at a time); sending is
 * optimistic: the bubble appears at once with its client-made id, is replaced by the server's message when that
 * arrives, and on failure stays as "Failed to send" until retried (the retry reuses the id). Messages are
 * de-duplicated by id and client id and kept in id order.
 */
export function useChatThread(scope: ChatScope, options: ThreadOptions = {}): ChatThread {
  const { search = '', enabled = true } = options;
  const queryClient = useQueryClient();
  const userId = useActingUser()?.id ?? 0;
  const { data: me } = useCurrentUser();
  const live = useLiveStatus();
  const isStaff = scope.kind === 'staff';
  const conversationId = scope.kind === 'staff' ? scope.conversationId : 0;

  const pollMs = isStaff || live === 'open' ? SAFETY_REFRESH_MS : FALLBACK_POLL_MS;
  const query = useInfiniteQuery({
    queryKey: isStaff
      ? QUERY_KEYS.chat.messages(conversationId, search)
      : [...QUERY_KEYS.chat.myMessages(userId), search],
    queryFn: ({ pageParam }): Promise<ChatMessagePage> =>
      isStaff
        ? chatService.getMessages(conversationId, { beforeId: pageParam, q: search })
        : chatService.getMyMessages({ beforeId: pageParam }),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) =>
      last.hasMore && last.nextBeforeId !== null ? last.nextBeforeId : undefined,
    enabled,
    refetchInterval: pollMs,
  });

  const [pending, setPending] = useState<PendingChatMessage[]>([]);
  // Messages the server confirmed in this session: shown until the next read of the list contains them
  const [confirmed, setConfirmed] = useState<ChatMessage[]>([]);

  const { mutateAsync } = useMutation({
    mutationFn: (request: { body: string; clientMessageId: string }) =>
      isStaff
        ? chatService.sendMessage(conversationId, request)
        : chatService.sendMyMessage(request),
  });

  const submit = useCallback(
    async (body: string, clientMessageId: string) => {
      try {
        const message = await mutateAsync({ body, clientMessageId });
        setConfirmed((current) => [...current, message]);
        setPending((current) =>
          current.filter((entry) => entry.clientMessageId !== clientMessageId),
        );
        void queryClient.invalidateQueries({
          queryKey: isStaff ? QUERY_KEYS.chat.staffAll : QUERY_KEYS.chat.mineAll(userId),
        });
      } catch (error) {
        setPending((current) =>
          current.map((entry) =>
            entry.clientMessageId === clientMessageId
              ? { ...entry, status: 'failed', error: sendErrorMessage(error) }
              : entry,
          ),
        );
      }
    },
    [mutateAsync, queryClient, isStaff, userId],
  );

  const send = useCallback(
    (body: string) => {
      const clientMessageId = newClientMessageId();
      setPending((current) => [
        ...current,
        { clientMessageId, body, createdAt: new Date().toISOString(), status: 'sending' },
      ]);
      void submit(body, clientMessageId);
      return clientMessageId;
    },
    [submit],
  );

  const pendingRef = useRef(pending);
  useEffect(() => {
    pendingRef.current = pending;
  }, [pending]);

  const retry = useCallback(
    (clientMessageId: string) => {
      const entry = pendingRef.current.find((item) => item.clientMessageId === clientMessageId);
      if (!entry) {
        return;
      }
      setPending((current) =>
        current.map((item) =>
          item.clientMessageId === clientMessageId
            ? { ...item, status: 'sending', error: undefined }
            : item,
        ),
      );
      void submit(entry.body, clientMessageId);
    },
    [submit],
  );

  const messages = useMemo(() => {
    if (search) {
      return flattenMessagePages(query.data?.pages);
    }
    const server = flattenMessagePages([
      ...confirmedPages(confirmed),
      ...(query.data?.pages ?? []),
    ]);
    return mergeWithPending(server, pending, {
      senderName: me?.fullName ?? '',
      fromStaff: isStaff,
    });
  }, [query.data, confirmed, pending, search, me, isStaff]);

  return {
    messages,
    isLoading: enabled && query.isPending,
    error: query.error,
    refetch: () => void query.refetch(),
    hasEarlier: query.hasNextPage,
    isLoadingEarlier: query.isFetchingNextPage,
    loadEarlier: () => void query.fetchNextPage(),
    send,
    retry,
  };
}

/** The confirmed messages as a page that the server's pages override (they are the newer truth). */
function confirmedPages(confirmed: readonly ChatMessage[]): ChatMessagePage[] {
  return [{ messages: [...confirmed], hasMore: false, nextBeforeId: null }];
}

/** Marks the open conversation read (POST read) and refreshes what shows its unread state. */
export function useMarkChatRead(scope: ChatScope) {
  const queryClient = useQueryClient();
  const userId = useActingUser()?.id ?? 0;
  return useMutation({
    mutationFn: () =>
      scope.kind === 'staff'
        ? chatService.markRead(scope.conversationId)
        : chatService.markMyConversationRead(),
    onSuccess: () =>
      Promise.all(
        scope.kind === 'staff'
          ? [
              queryClient.invalidateQueries({ queryKey: QUERY_KEYS.chat.conversationsAll }),
              queryClient.invalidateQueries({
                queryKey: QUERY_KEYS.chat.conversation(scope.conversationId),
              }),
              queryClient.invalidateQueries({ queryKey: QUERY_KEYS.chat.unreadCount }),
            ]
          : [queryClient.invalidateQueries({ queryKey: QUERY_KEYS.chat.myConversation(userId) })],
      ),
  });
}

/**
 * Marks the conversation read while it is on screen and in front: when it opens with unread messages, and when a
 * new one arrives meanwhile (a moment later, so several messages are marked once).
 */
export function useMarkReadWhileOpen(scope: ChatScope, unreadCount: number, enabled = true) {
  const { mutate } = useMarkChatRead(scope);
  const visible = usePageVisible();
  const running = useRef(false);

  useEffect(() => {
    if (!enabled || !visible || unreadCount <= 0) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      if (running.current) {
        return;
      }
      running.current = true;
      mutate(undefined, {
        onSettled: () => {
          running.current = false;
        },
      });
    }, MARK_READ_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [enabled, visible, unreadCount, mutate]);
}
