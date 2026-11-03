import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { AssistantContext, type AssistantContextValue } from '@/context/AssistantContext';
import { useToast } from '@/context/ToastContext';
import {
  useAssistantChat,
  useAssistantHistory,
  useClearAssistantConversation,
} from '@/hooks/api/assistantHooks';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import type { ActionResolution, AssistantHistoryMessage, ThreadItem } from '@/types/assistant';
import type { ActingUser } from '@/types/user';
import {
  describeAssistantError,
  loadAssistantMaximized,
  loadConversationId,
  saveAssistantMaximized,
  saveConversationId,
} from '@/utils/assistantUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** From Tailwind's `sm` breakpoint the panel is a side sheet; below it, it fills the screen. */
const SHEET_SCREEN = '(min-width: 40rem)';

interface Run {
  id: number;
  text: string;
  controller: AbortController;
}

function fromHistory(message: AssistantHistoryMessage): ThreadItem {
  const sentAt = message.at ? Date.parse(message.at) : Number.NaN;
  const at = Number.isNaN(sentAt) ? null : sentAt;
  return message.role === 'USER'
    ? { kind: 'user', id: `h-${message.id}`, text: message.text, at }
    : {
        kind: 'assistant',
        id: `h-${message.id}`,
        text: message.text,
        at,
        cards: message.cards,
        links: message.links,
        pendingAction: message.pendingAction,
        toolsUsed: [],
      };
}

interface AssistantProviderProps {
  user: ActingUser;
  children: ReactNode;
}

/**
 * The assistant's conversation for one signed-in user: whether the panel is open, minimized to a small bar or
 * maximized to a large window, the messages, the question in flight (with its Cancel), and how each order proposal
 * ended. It lives above the pages, so changing page keeps
 * the conversation; the conversation id is kept for the browser tab, so a reload brings the thread back.
 * (The layouts give it `key={user}`: another user always starts with a clean state.)
 */
export function AssistantProvider({ user, children }: AssistantProviderProps) {
  const { showToast } = useToast();
  const sheet = useMediaQuery(SHEET_SCREEN);
  const chat = useAssistantChat();
  const clear = useClearAssistantConversation();

  const [open, setOpen] = useState(false);
  const [minimized, setMinimized] = useState(false);
  const [answerWhenMinimized, setAnswerWhenMinimized] = useState<string | null>(null);
  const [maximized, setMaximized] = useState(loadAssistantMaximized);
  const [restoreId, setRestoreId] = useState(() => loadConversationId(user.id));
  const [local, setLocal] = useState<ThreadItem[]>([]);
  const [thinkingSince, setThinkingSince] = useState<number | null>(null);
  const [resolutions, setResolutions] = useState<Record<string, ActionResolution>>({});
  const launcherRef = useRef<HTMLButtonElement>(null);
  const conversationRef = useRef<string | null>(restoreId);
  const runRef = useRef<Run | null>(null);
  const counter = useRef(0);

  const history = useAssistantHistory(user, restoreId, open);
  const historyGone = restoreId !== null && history.data === null;

  // The server forgot the conversation (expired): the next question starts a new one
  useEffect(() => {
    if (historyGone) {
      conversationRef.current = null;
      saveConversationId(user.id, null);
    }
  }, [historyGone, user.id]);

  // Leaving the page with a question in flight: its answer is no longer wanted
  useEffect(
    () => () => {
      const run = runRef.current;
      runRef.current = null;
      run?.controller.abort();
    },
    [],
  );

  const items = useMemo<ThreadItem[]>(
    () => [...(restoreId && history.data ? history.data.messages.map(fromHistory) : []), ...local],
    [restoreId, history.data, local],
  );

  const stopRun = useCallback(() => {
    const run = runRef.current;
    runRef.current = null;
    run?.controller.abort();
    setThinkingSince(null);
    return run;
  }, []);

  const ask = useCallback(
    async (rawText: string, replacing?: string) => {
      const text = rawText.trim();
      if (!text || runRef.current) {
        return;
      }
      const run: Run = { id: ++counter.current, text, controller: new AbortController() };
      runRef.current = run;
      setThinkingSince(Date.now());
      setLocal((previous) =>
        replacing
          ? previous.filter((item) => item.id !== replacing)
          : [...previous, { kind: 'user', id: `u-${run.id}`, text, at: Date.now() }],
      );
      try {
        const answer = await chat.mutateAsync({
          request: { conversationId: conversationRef.current, message: text },
          signal: run.controller.signal,
        });
        if (runRef.current?.id !== run.id) {
          return; // cancelled meanwhile: the late answer is ignored
        }
        if (answer.conversationId) {
          conversationRef.current = answer.conversationId;
          saveConversationId(user.id, answer.conversationId);
        }
        setLocal((previous) => [
          ...previous,
          {
            kind: 'assistant',
            id: `a-${run.id}`,
            text: answer.reply.text,
            at: Date.now(),
            cards: answer.reply.cards,
            links: answer.reply.links,
            pendingAction: answer.reply.pendingAction,
            toolsUsed: answer.reply.toolsUsed,
          },
        ]);
      } catch (error) {
        if (runRef.current?.id !== run.id) {
          return; // cancelled: the Cancel button already wrote its note
        }
        const info = describeAssistantError(error);
        setLocal((previous) => [
          ...previous,
          {
            kind: 'error',
            id: `e-${run.id}`,
            retryText: text,
            title: info.title,
            message: info.message,
            retryable: info.retryable,
          },
        ]);
      } finally {
        if (runRef.current?.id === run.id) {
          runRef.current = null;
          setThinkingSince(null);
        }
      }
    },
    [chat, user.id],
  );

  const send = useCallback((text: string) => void ask(text), [ask]);

  const retry = useCallback(
    (errorId: string) => {
      const entry = local.find((item) => item.id === errorId);
      if (entry?.kind === 'error') {
        void ask(entry.retryText, errorId);
      }
    },
    [ask, local],
  );

  const cancel = useCallback(() => {
    const run = stopRun();
    if (run) {
      setLocal((previous) => [
        ...previous,
        {
          kind: 'error',
          id: `e-${run.id}`,
          retryText: run.text,
          title: ERROR_MESSAGES.ASSISTANT_CANCELLED,
          message: ERROR_MESSAGES.ASSISTANT_CANCELLED_DETAIL,
          retryable: true,
        },
      ]);
    }
  }, [stopRun]);

  const newChat = useCallback(() => {
    stopRun();
    setLocal([]);
    setResolutions({});
    setRestoreId(null);
    const conversationId = conversationRef.current;
    conversationRef.current = null;
    saveConversationId(user.id, null);
    if (conversationId) {
      clear.mutate(conversationId, {
        onError: () => showToast(ERROR_MESSAGES.ASSISTANT_CLEAR_FAILED, 'error'),
      });
    }
  }, [clear, showToast, stopRun, user.id]);

  const resolveAction = useCallback((actionId: string, resolution: ActionResolution) => {
    setResolutions((previous) => ({ ...previous, [actionId]: resolution }));
  }, []);

  const openPanel = useCallback(() => {
    setOpen(true);
    setMinimized(false);
  }, []);
  const closePanel = useCallback(() => {
    setOpen(false);
    setMinimized(false);
  }, []);
  const minimizePanel = useCallback(() => {
    // The newest answer now: one arriving later makes the bar say "New answer"
    const last = items[items.length - 1];
    setAnswerWhenMinimized(last?.kind === 'assistant' ? last.id : null);
    setOpen(false);
    setMinimized(true);
  }, [items]);
  const toggleMaximized = useCallback(() => {
    setMaximized((previous) => {
      saveAssistantMaximized(!previous);
      return !previous;
    });
  }, []);
  const onNavigated = useCallback(() => {
    if (!sheet) {
      setOpen(false);
    }
  }, [sheet]);

  const value = useMemo<AssistantContextValue>(
    () => ({
      user,
      open,
      openPanel,
      closePanel,
      minimized,
      minimizePanel,
      answerWhenMinimized,
      maximized,
      toggleMaximized,
      launcherRef,
      items,
      thinkingSince,
      historyLoading: restoreId !== null && history.isPending && open,
      send,
      retry,
      cancel,
      newChat,
      resolutions,
      resolveAction,
      onNavigated,
    }),
    [
      user,
      open,
      openPanel,
      closePanel,
      minimized,
      minimizePanel,
      answerWhenMinimized,
      maximized,
      toggleMaximized,
      items,
      thinkingSince,
      restoreId,
      history.isPending,
      send,
      retry,
      cancel,
      newChat,
      resolutions,
      resolveAction,
      onNavigated,
    ],
  );

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
}
