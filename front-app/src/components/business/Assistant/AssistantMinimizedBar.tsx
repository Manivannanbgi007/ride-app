import { ChevronUp, X } from 'lucide-react';

import { AssistantIcon } from '@/components/business/Assistant/AssistantIcon';
import { useAssistant } from '@/context/AssistantContext';
import { cn } from '@/utils/classNames';

interface AssistantMinimizedBarProps {
  /** Below the lg breakpoint, sit above a tab bar fixed at the bottom of the screen (the partner area) */
  raised?: boolean;
}

/**
 * The assistant folded away: a small bar at the bottom right instead of the round button. The conversation goes on
 * behind it: the bar says "Thinking..." while an answer is on its way and "New answer" once it is there. A click
 * brings the panel back as it was; the cross closes it (the round button comes back, the conversation is kept).
 */
export function AssistantMinimizedBar({ raised = false }: AssistantMinimizedBarProps) {
  const { items, thinkingSince, answerWhenMinimized, openPanel, closePanel, launcherRef } =
    useAssistant();
  const last = items[items.length - 1];
  const newAnswer = last?.kind === 'assistant' && last.id !== answerWhenMinimized;
  const status =
    thinkingSince !== null
      ? 'Thinking...'
      : newAnswer
        ? 'New answer: open to read it'
        : 'Your chat is waiting';

  return (
    <div
      role="region"
      aria-label="Assistant, minimized"
      className={cn(
        'fixed right-4 z-40 flex w-[min(20rem,calc(100vw-2rem))] items-center gap-1 rounded-2xl bg-linear-to-br from-assistant-top to-assistant-bottom p-1.5 text-white shadow-xl ring-1 ring-white/15',
        'motion-safe:animate-page-in',
        raised ? 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] lg:bottom-4' : 'bottom-4',
      )}
    >
      <button
        ref={launcherRef}
        type="button"
        aria-label="Open the assistant"
        onClick={openPanel}
        className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 rounded-xl p-1.5 text-left transition-colors hover:bg-white/10 focus-visible:ring-4 focus-visible:ring-white/40 focus-visible:outline-none"
      >
        <span className="relative shrink-0" aria-hidden="true">
          <span className="flex size-10 items-center justify-center rounded-full bg-white text-brand-700 shadow-card">
            <AssistantIcon className="size-5" />
          </span>
          {newAnswer && thinkingSince === null && (
            <span className="absolute -top-0.5 -right-0.5 size-3 rounded-full bg-sunset-500 ring-2 ring-assistant-top" />
          )}
        </span>
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="text-sm font-bold">Assistant</span>
          <span
            aria-live="polite"
            className={cn(
              'truncate text-xs text-white/85',
              thinkingSince !== null && 'motion-safe:animate-pulse',
              newAnswer && thinkingSince === null && 'font-semibold text-white',
            )}
          >
            {status}
          </span>
        </span>
        <ChevronUp aria-hidden="true" className="size-4 shrink-0 text-white/80" />
      </button>
      <button
        type="button"
        aria-label="Close assistant"
        onClick={closePanel}
        className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full transition-colors hover:bg-white/15 focus-visible:ring-4 focus-visible:ring-white/40 focus-visible:outline-none"
      >
        <X aria-hidden="true" className="size-4" />
      </button>
    </div>
  );
}
