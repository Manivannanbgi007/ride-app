import { MessageSquare, Plus } from 'lucide-react';
import { useMemo, useState } from 'react';

import { ConversationRow } from '@/components/business/Chat/ConversationRow';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { SearchInput } from '@/components/common/SearchInput';
import { Tooltip } from '@/components/common/Tooltip';
import { useStaffConversations } from '@/hooks/api/chatHooks';
import type { ChatConversation } from '@/types/chat';
import { cn } from '@/utils/classNames';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

interface ConversationListProps {
  selectedId: number | null;
  onSelect: (conversationId: number) => void;
  onNewChat: () => void;
  className?: string;
}

/**
 * The staff inbox: a search box (name or last message), a green "+" that starts a chat with a user, and the
 * conversations in two sections, pinned and recent (a section with nothing in it is left out).
 */
export function ConversationList({
  selectedId,
  onSelect,
  onNewChat,
  className,
}: ConversationListProps) {
  const [search, setSearch] = useState('');
  const query = useStaffConversations(search);

  const { pinned, recent } = useMemo(() => {
    const byId = new Map<number, ChatConversation>();
    for (const page of query.data?.pages ?? []) {
      for (const conversation of page.content) {
        byId.set(conversation.id, conversation);
      }
    }
    const all = [...byId.values()];
    return { pinned: all.filter((c) => c.pinned), recent: all.filter((c) => !c.pinned) };
  }, [query.data]);

  const hasAny = pinned.length + recent.length > 0;

  return (
    <section
      aria-label="Conversations"
      className={cn('flex min-h-0 flex-col rounded-3xl bg-surface shadow-card', className)}
    >
      <div className="flex items-center gap-3 p-4 sm:p-5">
        <div className="min-w-0 flex-1">
          <SearchInput
            aria-label="Search conversations"
            placeholder="Search here..."
            value={search}
            onSearch={setSearch}
            className="border-transparent bg-page"
          />
        </div>
        <Tooltip label="New chat" icon={Plus}>
          <button
            type="button"
            aria-label="New chat"
            onClick={onNewChat}
            className="flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-xl bg-brand-500 text-on-brand shadow-brand transition duration-200 hover:bg-brand-400 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none motion-safe:active:scale-95"
          >
            <Plus aria-hidden="true" className="size-6" />
          </button>
        </Tooltip>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-4 sm:px-3">
        {query.isPending ? (
          <ListSkeleton />
        ) : query.isError && !hasAny ? (
          <ErrorMessage
            title={ERROR_MESSAGES.CONVERSATIONS_LOAD_FAILED}
            error={query.error}
            onRetry={() => void query.refetch()}
            isRetrying={query.isFetching}
          />
        ) : !hasAny ? (
          <EmptyState
            icon={MessageSquare}
            title={search ? 'No conversations found' : 'No conversations yet'}
            message={search ? 'Try another name.' : 'Start one with +'}
          />
        ) : (
          <>
            <Section
              title="Pinned Message"
              conversations={pinned}
              selectedId={selectedId}
              onSelect={onSelect}
            />
            <Section
              title="Recent Message"
              conversations={recent}
              selectedId={selectedId}
              onSelect={onSelect}
            />
            {query.hasNextPage && (
              <div className="flex justify-center py-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => void query.fetchNextPage()}
                  isLoading={query.isFetchingNextPage}
                >
                  Load more
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}

interface SectionProps {
  title: string;
  conversations: readonly ChatConversation[];
  selectedId: number | null;
  onSelect: (conversationId: number) => void;
}

function Section({ title, conversations, selectedId, onSelect }: SectionProps) {
  if (conversations.length === 0) {
    return null;
  }
  return (
    <section aria-label={title} className="mb-2">
      <h2 className="px-3 pt-2 pb-1 text-sm text-ink-soft">{title}</h2>
      <ul className="flex flex-col">
        {conversations.map((conversation) => (
          <li key={conversation.id}>
            <ConversationRow
              conversation={conversation}
              selected={conversation.id === selectedId}
              onSelect={onSelect}
            />
          </li>
        ))}
      </ul>
    </section>
  );
}

function ListSkeleton() {
  return (
    <div role="status" aria-label="Loading conversations" className="flex flex-col gap-4 p-3">
      {Array.from({ length: 5 }, (_, index) => (
        <div key={index} className="flex items-center gap-3">
          <Skeleton className="size-11 shrink-0 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-3 w-3/4" />
          </div>
        </div>
      ))}
    </div>
  );
}
