import type { ReactNode } from 'react';

import { Composer } from '@/components/business/Chat/Composer';
import { MessageList } from '@/components/business/Chat/MessageList';
import { useChatThread, useMarkReadWhileOpen } from '@/hooks/api/chatHooks';
import { useLiveStatus } from '@/realtime/liveStatusContext';
import type { ChatScope } from '@/types/chat';

interface ChatThreadProps {
  scope: ChatScope;
  /** The conversation's unread count: while it is above 0 and the page is in front, it is marked read */
  unreadCount: number;
  /** Only messages containing this text (staff) */
  search?: string;
  /** False while there is no conversation yet (a participant who has not written) */
  exists?: boolean;
  empty: ReactNode;
}

/**
 * Messages and the composer of one conversation, for both the staff and the participant. Mount it with a `key`
 * of the conversation: sending state belongs to one conversation.
 */
export function ChatThread({
  scope,
  unreadCount,
  search = '',
  exists = true,
  empty,
}: ChatThreadProps) {
  const thread = useChatThread(scope, { search, enabled: exists });
  const live = useLiveStatus();
  useMarkReadWhileOpen(scope, unreadCount, exists);

  return (
    <>
      {live === 'reconnecting' && (
        <p role="status" className="px-6 pt-2 text-xs text-ink-soft">
          Reconnecting...
        </p>
      )}
      <MessageList
        messages={thread.messages}
        viewer={scope.kind === 'staff' ? 'staff' : 'participant'}
        isLoading={thread.isLoading}
        error={thread.error}
        onReload={thread.refetch}
        hasEarlier={thread.hasEarlier}
        isLoadingEarlier={thread.isLoadingEarlier}
        onLoadEarlier={thread.loadEarlier}
        onRetrySend={thread.retry}
        empty={
          search ? (
            <p className="py-10 text-center text-sm text-ink-soft">
              No messages match your search.
            </p>
          ) : (
            empty
          )
        }
      />
      <Composer onSend={thread.send} />
    </>
  );
}
