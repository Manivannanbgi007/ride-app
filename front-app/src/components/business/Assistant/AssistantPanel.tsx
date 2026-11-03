import {
  Maximize2,
  Minimize2,
  Minus,
  RotateCcw,
  Volume2,
  VolumeX,
  WifiOff,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';

import { AssistantAvatar } from '@/components/business/Assistant/AssistantAvatar';
import { AssistantComposer } from '@/components/business/Assistant/AssistantComposer';
import { AssistantIcon } from '@/components/business/Assistant/AssistantIcon';
import { AssistantMessage } from '@/components/business/Assistant/AssistantMessage';
import { SuggestionChips } from '@/components/business/Assistant/SuggestionChips';
import { ThinkingBubble } from '@/components/business/Assistant/ThinkingBubble';
import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { Skeleton } from '@/components/common/Loader';
import { Tooltip } from '@/components/common/Tooltip';
import { useAssistant } from '@/context/AssistantContext';
import { useAssistantStatus, useAssistantSuggestions } from '@/hooks/api/assistantHooks';
import { useVoiceOutput } from '@/hooks/useVoiceOutput';
import type { ThreadItem } from '@/types/assistant';
import { assistantGreeting, assistantRoleLabel, firstNameOf } from '@/utils/assistantUtils';
import { cn } from '@/utils/classNames';
import { loadVoiceReplies, saveVoiceReplies } from '@/utils/speech';

interface RoundButtonProps {
  label: string;
  icon: LucideIcon;
  onClick: () => void;
  pressed?: boolean;
  /** Classes for the button's wrapper (e.g. hidden on a phone) */
  className?: string;
}

/** The header's round outlined buttons (white on the green), each with the shared tooltip. */
function RoundButton({ label, icon: Icon, onClick, pressed, className }: RoundButtonProps) {
  return (
    <Tooltip label={label} placement="bottom" className={className}>
      <button
        type="button"
        aria-label={label}
        aria-pressed={pressed}
        onClick={onClick}
        className={cn(
          'flex size-9 cursor-pointer items-center justify-center rounded-full ring-1 ring-white/60 transition-colors',
          'focus-visible:ring-4 focus-visible:ring-white/50 focus-visible:outline-none',
          pressed ? 'bg-white text-brand-700' : 'text-white hover:bg-white/15',
        )}
      >
        <Icon aria-hidden="true" className="size-4" />
      </button>
    </Tooltip>
  );
}

interface WelcomeProps {
  name: string | null;
  greeting: string;
  children: ReactNode;
}

/** The empty conversation: the assistant greets the user by name (when known) and offers questions to start with. */
function Welcome({ name, greeting, children }: WelcomeProps) {
  return (
    <div className="flex flex-col items-center gap-4 pt-4 text-center motion-safe:animate-page-in">
      <AssistantAvatar size="lg" />
      <div className="flex flex-col gap-1.5">
        <p className="text-lg font-bold">{name ? `Hi ${name}!` : 'Hi there!'}</p>
        <p className="text-sm text-ink-soft">{greeting}</p>
      </div>
      <div className="flex w-full flex-col items-start gap-2 pt-2">
        <p className="px-1 text-xs font-semibold tracking-wide text-ink-soft uppercase">
          Try asking
        </p>
        {children}
      </div>
    </div>
  );
}

/** The newest answer of this session (not one restored from history): only those are read aloud by themselves. */
function lastAnswerId(items: ThreadItem[]): string | null {
  const last = items[items.length - 1];
  return last?.kind === 'assistant' ? last.id : null;
}

/**
 * The assistant's chat panel: a side sheet from the right on desktop (or, maximized, a large window over the dimmed
 * page with the conversation in a readable column), the whole screen on a phone. A brand-green header, and the
 * conversation on a light sheet below it: the assistant's answers beside its picture, the user's questions in green on
 * the right, each with its time. Questions can be typed or spoken; answers can be read aloud (one answer with its
 * Listen button, or every new one with "Read answers aloud"). "Minimize" folds it into a small bar at the bottom
 * right. It exists only while open; the conversation itself lives in the provider, so closing or minimizing never
 * loses it (both stop the microphone and the voice).
 */
export function AssistantPanel() {
  const {
    user,
    open,
    closePanel,
    minimizePanel,
    maximized,
    toggleMaximized,
    items,
    thinkingSince,
    historyLoading,
    send,
    retry,
    cancel,
    newChat,
  } = useAssistant();
  const input = useRef<HTMLTextAreaElement>(null);
  const scroller = useRef<HTMLDivElement>(null);
  const status = useAssistantStatus(user, open);
  const empty = items.length === 0 && thinkingSince === null;
  const suggestions = useAssistantSuggestions(user, open && empty);
  const voice = useVoiceOutput();
  const [autoRead, setAutoRead] = useState(loadVoiceReplies);
  // The answers already on screen when the panel opened are never read by themselves
  const answeredRef = useRef<string | null>(lastAnswerId(items));

  // The cursor is in the question box as soon as the panel opens
  useEffect(() => {
    if (open) {
      input.current?.focus();
    }
  }, [open]);

  // Esc closes
  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !event.defaultPrevented) {
        closePanel();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, closePanel]);

  // The newest message is always in view
  useEffect(() => {
    const element = scroller.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [items, thinkingSince, open]);

  // "Read answers aloud": a new answer is read as soon as it arrives
  const { speak } = voice;
  useEffect(() => {
    const id = lastAnswerId(items);
    if (!id || id === answeredRef.current) {
      return;
    }
    answeredRef.current = id;
    const answer = items[items.length - 1];
    if (autoRead && answer.kind === 'assistant') {
      speak(answer.id, answer.text);
    }
  }, [items, autoRead, speak]);

  if (!open) {
    return null;
  }

  const offline = status.isError || status.data?.available === false;

  const toggleAutoRead = () => {
    const next = !autoRead;
    setAutoRead(next);
    saveVoiceReplies(next);
    if (!next) {
      voice.stop();
    }
  };

  const startNewChat = () => {
    voice.stop();
    newChat();
  };

  // A wide window keeps the conversation in a readable column in its middle
  const wideColumn = maximized && 'sm:px-[max(1.5rem,calc((100%_-_56rem)/2))]';

  return (
    <>
      {maximized && (
        // The page dims behind the large window (a phone's panel fills the screen anyway)
        <div
          aria-hidden="true"
          className="fixed inset-0 z-50 hidden bg-scrim/45 backdrop-blur-[2px] motion-safe:animate-page-in sm:block"
        />
      )}
      <div
        role="dialog"
        aria-label="AI assistant"
        data-size={maximized ? 'maximized' : 'sheet'}
        className={cn(
          'fixed inset-0 z-50 flex flex-col bg-linear-to-b from-assistant-top to-assistant-bottom text-white shadow-card',
          maximized
            ? 'sm:inset-4 sm:rounded-3xl sm:shadow-2xl lg:inset-x-[max(1.5rem,calc((100vw_-_80rem)/2))] lg:inset-y-6'
            : 'sm:inset-y-0 sm:right-0 sm:left-auto sm:w-[39.375rem] sm:max-w-full sm:rounded-l-3xl',
        )}
      >
        <header className="flex items-center gap-3 px-4 pt-[max(1rem,env(safe-area-inset-top))] pb-3">
          <span className="relative shrink-0" aria-hidden="true">
            <span className="flex size-11 items-center justify-center rounded-full bg-white text-brand-700 shadow-card">
              <AssistantIcon className="size-5" />
            </span>
            <span
              className={cn(
                'absolute right-0 bottom-0 size-3 rounded-full ring-2 ring-assistant-top',
                offline ? 'bg-warning-500' : 'bg-brand-400',
              )}
            />
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            {/* Wraps on a narrow phone, so the role chip never runs under the buttons */}
            <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
              <h2 className="text-base font-bold">Assistant</h2>
              <Badge tone="success">{assistantRoleLabel(user.role)}</Badge>
            </div>
            <p className="truncate text-xs">
              {offline
                ? 'Offline'
                : status.data
                  ? `${status.data.message ?? 'Online'}${status.data.model ? ` · ${status.data.model}` : ''}`
                  : 'Connecting...'}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            {voice.supported && (
              <RoundButton
                label="Read answers aloud"
                icon={autoRead ? Volume2 : VolumeX}
                pressed={autoRead}
                onClick={toggleAutoRead}
              />
            )}
            <RoundButton label="New chat" icon={RotateCcw} onClick={startNewChat} />
            <RoundButton label="Minimize assistant" icon={Minus} onClick={minimizePanel} />
            {/* A phone's panel already fills the screen */}
            <RoundButton
              label={maximized ? 'Restore assistant size' : 'Maximize assistant'}
              icon={maximized ? Minimize2 : Maximize2}
              onClick={toggleMaximized}
              className="max-sm:hidden"
            />
            <RoundButton label="Close assistant" icon={X} onClick={closePanel} />
          </div>
        </header>

        {offline && (
          <div
            role="status"
            className="mx-3 mb-1 flex items-center gap-2 rounded-2xl bg-warning-50 px-3 py-2 text-sm text-warning-text"
          >
            <WifiOff aria-hidden="true" className="size-4 shrink-0" />
            <span className="flex-1">Assistant is offline</span>
            <Button
              size="sm"
              variant="outline"
              onClick={() => void status.refetch()}
              isLoading={status.isFetching}
            >
              Retry
            </Button>
          </div>
        )}

        {voice.error && (
          <div
            role="alert"
            className="mx-3 mb-1 flex items-center gap-2 rounded-2xl bg-surface px-3 py-2 text-sm text-ink"
          >
            <VolumeX aria-hidden="true" className="size-4 shrink-0 text-danger-text" />
            <span className="flex-1">{voice.error}</span>
            <button
              type="button"
              aria-label="Dismiss"
              onClick={voice.clearError}
              className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-soft hover:bg-scrim/5 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              <X aria-hidden="true" className="size-4" />
            </button>
          </div>
        )}

        {/* The conversation: a light sheet that rises under the green header */}
        <div
          className={cn(
            'flex min-h-0 flex-1 flex-col overflow-hidden rounded-t-[1.75rem] bg-page text-ink shadow-[0_-10px_30px_-12px_rgb(0_0_0/0.35)]',
            maximized ? 'sm:rounded-b-3xl' : 'sm:rounded-bl-3xl',
          )}
        >
          <div
            ref={scroller}
            role="log"
            aria-live="polite"
            aria-label="Conversation"
            className={cn(
              'flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 pt-5 pb-3',
              wideColumn,
            )}
          >
            {historyLoading && items.length === 0 ? (
              <div className="flex flex-col gap-4" aria-busy="true">
                <Skeleton className="h-10 w-2/3 self-end rounded-2xl" />
                <Skeleton className="h-16 w-4/5 rounded-2xl" />
              </div>
            ) : empty ? (
              <Welcome name={firstNameOf(user)} greeting={assistantGreeting(user.role)}>
                <SuggestionChips suggestions={suggestions.data ?? []} onPick={send} />
              </Welcome>
            ) : (
              items.map((item) => (
                <AssistantMessage
                  key={item.id}
                  item={item}
                  onRetry={retry}
                  voice={voice.supported ? voice : undefined}
                />
              ))
            )}
            {thinkingSince !== null && <ThinkingBubble since={thinkingSince} onCancel={cancel} />}
          </div>

          <div className={cn(wideColumn)}>
            <AssistantComposer
              onSend={send}
              busy={thinkingSince !== null}
              inputRef={input}
              onListeningStart={voice.stop}
            />
          </div>
        </div>
      </div>
    </>
  );
}
