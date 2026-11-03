import { useLayoutEffect, useRef, type ReactNode } from 'react';

import { MessageBubble } from '@/components/business/Chat/MessageBubble';
import { Button } from '@/components/common/Button';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { dayKeyOf, formatDayLabel, type DisplayMessage } from '@/utils/chatUtils';
import { cn } from '@/utils/classNames';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** Closer than this to the bottom (px) counts as "at the bottom": new messages keep it there. */
const BOTTOM_TOLERANCE = 80;

interface MessageListProps {
  messages: readonly DisplayMessage[];
  /** Whose side is "own" (right, blue): the staff's, or the participant's */
  viewer: 'staff' | 'participant';
  isLoading: boolean;
  error: unknown;
  onReload: () => void;
  hasEarlier: boolean;
  isLoadingEarlier: boolean;
  onLoadEarlier: () => void;
  onRetrySend: (clientMessageId: string) => void;
  /** Shown when there are no messages */
  empty: ReactNode;
}

function isOwn(message: DisplayMessage, viewer: MessageListProps['viewer']): boolean {
  return viewer === 'staff' ? message.fromStaff : !message.fromStaff;
}

function sameSender(a: DisplayMessage, b: DisplayMessage): boolean {
  return (
    a.fromStaff === b.fromStaff && (!!a.pending || !!b.pending || a.senderUserId === b.senderUserId)
  );
}

/**
 * The messages, oldest at the top, with a heading at each new day. It stays at the bottom as messages arrive
 * unless the reader scrolled up; "Load earlier messages" adds older ones above without moving what is read.
 * Screen readers are told about new messages (a polite live region).
 */
export function MessageList({
  messages,
  viewer,
  isLoading,
  error,
  onReload,
  hasEarlier,
  isLoadingEarlier,
  onLoadEarlier,
  onRetrySend,
  empty,
}: MessageListProps) {
  const scroller = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  const anchor = useRef<{ height: number; top: number } | null>(null);
  const lastKey = useRef<string | null>(null);

  const first = messages[0];
  const last = messages.at(-1);
  const firstKey = first?.clientMessageId ?? null;
  const latestKey = last?.clientMessageId ?? null;
  const lastIsOwn = last ? isOwn(last, viewer) : false;

  // Older messages were added above: keep the reader on the same message
  useLayoutEffect(() => {
    const element = scroller.current;
    if (element && anchor.current) {
      element.scrollTop = element.scrollHeight - anchor.current.height + anchor.current.top;
      anchor.current = null;
    }
  }, [firstKey]);

  // A new message at the end: follow it, unless the reader scrolled up (their own message always scrolls)
  useLayoutEffect(() => {
    const element = scroller.current;
    if (!element || latestKey === null || latestKey === lastKey.current) {
      return;
    }
    const firstView = lastKey.current === null;
    lastKey.current = latestKey;
    if (firstView || atBottom.current || lastIsOwn) {
      element.scrollTop = element.scrollHeight;
      atBottom.current = true;
    }
  }, [latestKey, lastIsOwn]);

  const loadEarlier = () => {
    const element = scroller.current;
    if (element) {
      anchor.current = { height: element.scrollHeight, top: element.scrollTop };
    }
    onLoadEarlier();
  };

  let body: ReactNode;
  if (isLoading) {
    body = <MessagesSkeleton />;
  } else if (error && messages.length === 0) {
    body = (
      <ErrorMessage
        title={ERROR_MESSAGES.CHAT_MESSAGES_LOAD_FAILED}
        error={error}
        onRetry={onReload}
      />
    );
  } else if (messages.length === 0) {
    body = empty;
  } else {
    body = (
      <>
        {/* Few messages sit at the bottom, near the composer */}
        <div aria-hidden="true" className="flex-1" />
        {hasEarlier && (
          <div className="flex justify-center">
            <Button
              variant="secondary"
              size="sm"
              onClick={loadEarlier}
              isLoading={isLoadingEarlier}
            >
              Load earlier messages
            </Button>
          </div>
        )}
        {messages.map((message, index) => {
          const previous = messages[index - 1];
          const newDay = !previous || dayKeyOf(previous.createdAt) !== dayKeyOf(message.createdAt);
          const startsRun = newDay || !sameSender(previous, message);
          return (
            <div key={message.clientMessageId} className={cn(startsRun && index > 0 && 'mt-2')}>
              {newDay && (
                <p className="my-3 text-center text-xs font-medium text-ink-soft">
                  {formatDayLabel(message.createdAt)}
                </p>
              )}
              <MessageBubble
                message={message}
                own={isOwn(message, viewer)}
                showSender={startsRun && message.fromStaff}
                onRetry={onRetrySend}
              />
            </div>
          );
        })}
      </>
    );
  }

  return (
    <div
      ref={scroller}
      role="log"
      aria-live="polite"
      aria-relevant="additions"
      aria-label="Messages"
      onScroll={(event) => {
        const element = event.currentTarget;
        atBottom.current =
          element.scrollHeight - element.scrollTop - element.clientHeight < BOTTOM_TOLERANCE;
      }}
      className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto px-4 py-5 sm:px-6"
    >
      {body}
    </div>
  );
}

function MessagesSkeleton() {
  return (
    <div role="status" aria-label="Loading messages" className="flex flex-col gap-4">
      <Skeleton className="h-10 w-2/3 rounded-2xl" />
      <Skeleton className="h-10 w-1/2 rounded-2xl" />
      <Skeleton className="ml-auto h-10 w-3/5 rounded-2xl" />
    </div>
  );
}
