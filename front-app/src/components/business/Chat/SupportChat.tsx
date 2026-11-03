import { MessageSquare } from 'lucide-react';

import { ChatHeader } from '@/components/business/Chat/ChatHeader';
import { ChatThread } from '@/components/business/Chat/ChatThread';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { useMyConversation } from '@/hooks/api/chatHooks';
import { useLiveStatus } from '@/realtime/liveStatusContext';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** Read again this often while the page is open: a safety net with live updates, the main way without them. */
const WITH_LIVE_MS = 30_000;
const WITHOUT_LIVE_MS = 15_000;

/**
 * The customer's one conversation with Support: the same messages and composer as the staff's chat, with no list.
 * The conversation is created by the first message.
 */
export function SupportChat() {
  const live = useLiveStatus();
  const conversation = useMyConversation(live === 'open' ? WITH_LIVE_MS : WITHOUT_LIVE_MS);

  return (
    <section
      aria-label="Support chat"
      className="mx-auto flex h-[calc(100dvh-13rem)] min-h-[28rem] w-full max-w-column flex-col rounded-3xl bg-surface shadow-card"
    >
      <ChatHeader name="Support" description="We usually reply within a few hours" />
      {conversation.isPending ? (
        <div role="status" aria-label="Loading conversation" className="flex flex-col gap-4 p-6">
          <Skeleton className="h-10 w-2/3 rounded-2xl" />
          <Skeleton className="ml-auto h-10 w-1/2 rounded-2xl" />
        </div>
      ) : conversation.isError ? (
        <ErrorMessage
          title={ERROR_MESSAGES.CONVERSATION_LOAD_FAILED}
          error={conversation.error}
          onRetry={() => void conversation.refetch()}
          isRetrying={conversation.isFetching}
        />
      ) : (
        <ChatThread
          scope={{ kind: 'participant' }}
          exists={conversation.data !== null}
          unreadCount={conversation.data?.unreadCount ?? 0}
          empty={
            <EmptyState
              icon={MessageSquare}
              title="Start a conversation with Support"
              message="Write your message below and our team will reply here."
            />
          }
        />
      )}
    </section>
  );
}
