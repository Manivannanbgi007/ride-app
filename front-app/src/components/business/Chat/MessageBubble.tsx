import { DeliveryTicks } from '@/components/business/Chat/DeliveryTicks';
import { formatClockTime, type DisplayMessage } from '@/utils/chatUtils';
import { cn } from '@/utils/classNames';

interface MessageBubbleProps {
  message: DisplayMessage;
  /** Written by the viewer's side: on the right, in blue, with its time and ticks */
  own: boolean;
  /** The first bubble of a run by Support shows who wrote it */
  showSender: boolean;
  onRetry: (clientMessageId: string) => void;
}

/**
 * One message. The text is drawn as plain text (never as HTML) and wrapped, so nothing a user types can add
 * markup or links to the page.
 */
export function MessageBubble({ message, own, showSender, onRetry }: MessageBubbleProps) {
  const { pending } = message;
  return (
    <div className={cn('flex flex-col gap-1', own ? 'items-end' : 'items-start')}>
      {showSender && (
        <span className="px-1 text-xs font-semibold text-ink-soft">{message.senderName}</span>
      )}
      <p
        className={cn(
          'max-w-[85%] rounded-2xl px-4 py-2.5 text-sm [overflow-wrap:anywhere] break-words whitespace-pre-wrap sm:max-w-[75%]',
          own ? 'rounded-br-md bg-info-100 text-info-ink' : 'rounded-bl-md bg-page text-ink',
          pending?.status === 'sending' && 'opacity-70',
          pending?.status === 'failed' && 'ring-1 ring-danger-500',
        )}
      >
        {message.body}
      </p>
      <div className="flex items-center gap-1.5 px-1 text-[11px] text-ink-soft">
        {pending?.status === 'failed' ? (
          <span className="flex flex-col items-end gap-0.5">
            <button
              type="button"
              onClick={() => onRetry(message.clientMessageId)}
              className="cursor-pointer font-semibold text-danger-text underline-offset-2 hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              Failed to send. Retry
            </button>
            {pending.error && <span role="alert">{pending.error}</span>}
          </span>
        ) : pending ? (
          <span>Sending…</span>
        ) : (
          <>
            <time dateTime={message.createdAt}>{formatClockTime(message.createdAt)}</time>
            {own && <DeliveryTicks read={message.readAt !== null} />}
          </>
        )}
      </div>
    </div>
  );
}
