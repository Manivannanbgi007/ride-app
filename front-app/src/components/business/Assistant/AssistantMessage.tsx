import { AlertCircle, Check, Copy, RotateCw, Square, Volume2, Wrench } from 'lucide-react';
import { useState, type ReactNode } from 'react';

import { ActionProposalCard } from '@/components/business/Assistant/ActionProposalCard';
import { AssistantAvatar } from '@/components/business/Assistant/AssistantAvatar';
import { AssistantLinkButton } from '@/components/business/Assistant/AssistantLinkButton';
import { CardView } from '@/components/business/Assistant/CardView';
import { MarkdownText } from '@/components/business/Assistant/MarkdownText';
import { OrderProposalCard } from '@/components/business/Assistant/OrderProposalCard';
import { Button } from '@/components/common/Button';
import { isOrderProposal, type ThreadItem } from '@/types/assistant';
import { formatMessageTime, toolsUsedText } from '@/utils/assistantUtils';
import { cn } from '@/utils/classNames';

/** Reading answers aloud, when the browser can (see useVoiceOutput). */
export interface MessageVoice {
  speakingId: string | null;
  toggle: (id: string, text: string) => void;
}

interface AssistantMessageProps {
  item: ThreadItem;
  onRetry: (errorId: string) => void;
  /** Omitted when the browser cannot speak: no Listen button then */
  voice?: MessageVoice;
}

const COPIED_MS = 2000;

/** Small text button under an answer (Listen, Copy). */
const ACTION =
  'inline-flex cursor-pointer items-center gap-1.5 rounded-full px-2 py-1 font-medium transition-colors hover:bg-brand-50 hover:text-brand-text-strong focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none';

/** Four bars that move while an answer is read aloud. */
function SpeakingBars() {
  return (
    <span aria-hidden="true" className="inline-flex h-3 items-end gap-0.5">
      {[0, 120, 240, 360].map((delay) => (
        <span
          key={delay}
          style={{ animationDelay: `${delay}ms` }}
          className="inline-block h-full w-0.5 rounded-full bg-brand-500 motion-safe:animate-pulse"
        />
      ))}
    </span>
  );
}

function MessageTime({ at }: { at: number | null }) {
  if (at === null) {
    return null;
  }
  return <time dateTime={new Date(at).toISOString()}>{formatMessageTime(at)}</time>;
}

/** Copies the answer's text; says "Copied" for a moment. Not drawn when the browser has no clipboard access. */
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  if (typeof navigator === 'undefined' || !navigator.clipboard) {
    return null;
  }
  const copy = () => {
    navigator.clipboard.writeText(text).then(
      () => {
        setCopied(true);
        window.setTimeout(() => setCopied(false), COPIED_MS);
      },
      () => setCopied(false),
    );
  };
  return (
    <button
      type="button"
      aria-label={copied ? 'Copied' : 'Copy this answer'}
      onClick={copy}
      className={ACTION}
    >
      {copied ? (
        <Check aria-hidden="true" className="size-3.5 text-brand-text" />
      ) : (
        <Copy aria-hidden="true" className="size-3.5" />
      )}
      {copied ? 'Copied' : 'Copy'}
    </button>
  );
}

/** The assistant's side of the conversation: its picture on the left, then the content. */
function AssistantRow({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-start gap-2.5 motion-safe:animate-page-in">
      <AssistantAvatar />
      <div className="flex min-w-0 flex-1 flex-col items-start gap-2">{children}</div>
    </div>
  );
}

/**
 * One entry of the conversation: the user's question (right, in the brand green), the assistant's answer (left,
 * beside its picture, with its cards, links, time, Listen and Copy), or an error with Retry.
 */
export function AssistantMessage({ item, onRetry, voice }: AssistantMessageProps) {
  if (item.kind === 'user') {
    return (
      <div className="flex flex-col items-end gap-1 motion-safe:animate-page-in">
        <p className="max-w-[85%] rounded-2xl rounded-tr-md bg-brand-500 px-4 py-2.5 text-sm [overflow-wrap:anywhere] break-words whitespace-pre-wrap text-on-brand shadow-brand">
          {item.text}
        </p>
        <span className="px-1 text-[11px] text-ink-soft">
          <MessageTime at={item.at} />
        </span>
      </div>
    );
  }

  if (item.kind === 'error') {
    return (
      <AssistantRow>
        <div
          role="alert"
          className="flex w-full max-w-[92%] flex-col items-start gap-2 rounded-2xl rounded-tl-md bg-danger-50 px-4 py-3 text-sm text-ink ring-1 ring-danger-500/30"
        >
          <p className="flex items-start gap-2 font-semibold text-danger-text">
            <AlertCircle aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            {item.title}
          </p>
          <p className="text-ink-soft">{item.message}</p>
          {item.retryable && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => onRetry(item.id)}
              leftIcon={<RotateCw aria-hidden="true" className="size-4" />}
            >
              Retry
            </Button>
          )}
        </div>
      </AssistantRow>
    );
  }

  const tools = toolsUsedText(item.toolsUsed);
  const hasText = item.text.trim().length > 0;
  const speaking = voice?.speakingId === item.id;
  return (
    <AssistantRow>
      {hasText && (
        <div className="max-w-[92%] rounded-2xl rounded-tl-md bg-surface px-4 py-3 text-sm text-ink shadow-card">
          <MarkdownText text={item.text} />
        </div>
      )}
      {item.cards.map((card, index) => (
        <div key={index} className="w-full">
          <CardView card={card} />
        </div>
      ))}
      {item.pendingAction && (
        <div className="w-full">
          {isOrderProposal(item.pendingAction) ? (
            <OrderProposalCard action={item.pendingAction} />
          ) : (
            <ActionProposalCard action={item.pendingAction} />
          )}
        </div>
      )}
      {item.links.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {item.links.map((link, index) => (
            <AssistantLinkButton key={`${link.path}-${index}`} link={link} />
          ))}
        </div>
      )}
      <div className="-ml-1 flex flex-wrap items-center gap-x-1 gap-y-0.5 text-[11px] text-ink-soft">
        {item.at !== null && (
          <span className="px-1">
            <MessageTime at={item.at} />
          </span>
        )}
        {hasText && voice && (
          <button
            type="button"
            aria-label={speaking ? 'Stop reading aloud' : 'Listen to this answer'}
            aria-pressed={speaking}
            onClick={() => voice.toggle(item.id, item.text)}
            className={cn(ACTION, speaking && 'bg-brand-50 text-brand-text-strong')}
          >
            {speaking ? (
              <>
                <Square aria-hidden="true" className="size-3 fill-current" />
                Stop
                <SpeakingBars />
              </>
            ) : (
              <>
                <Volume2 aria-hidden="true" className="size-3.5" />
                Listen
              </>
            )}
          </button>
        )}
        {hasText && <CopyButton text={item.text} />}
      </div>
      {tools && (
        <p className="-mt-1 flex items-center gap-1 px-1 text-[11px] text-ink-soft">
          <Wrench aria-hidden="true" className="size-3" />
          <span>Used: {tools}</span>
        </p>
      )}
    </AssistantRow>
  );
}
