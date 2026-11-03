import { MessageSquare } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { ChatPanel, ConversationList, NewChatDialog } from '@/components/business/Chat';
import { EmptyState } from '@/components/common/EmptyState';
import { useIdParam } from '@/hooks/useIdParam';
import { cn } from '@/utils/classNames';
import { ROUTES, routeTo } from '@/utils/constants';

/**
 * The support inbox (/admin/chat/:conversationId?): the conversations on the left, the open one on the right. The
 * open conversation is in the address, so a reload or a shared link comes back to it. On a phone one card shows
 * at a time: the list, or the chat with a back button.
 */
export default function ChatPage() {
  const conversationId = useIdParam('conversationId');
  const hasParam = useParams().conversationId !== undefined;
  const navigate = useNavigate();
  const [newChatOpen, setNewChatOpen] = useState(false);
  const open = hasParam;

  return (
    <div className="flex flex-col gap-4">
      <h1 className="sr-only">Chat</h1>
      <div className="grid h-[calc(100dvh-9.5rem)] min-h-[32rem] gap-6 lg:grid-cols-[minmax(20rem,26rem)_1fr]">
        <ConversationList
          selectedId={conversationId}
          onSelect={(id) => void navigate(routeTo.chatConversation(id))}
          onNewChat={() => setNewChatOpen(true)}
          className={cn(open ? 'hidden lg:flex' : 'flex')}
        />
        <section
          aria-label="Chat"
          className={cn(
            'min-h-0 flex-col rounded-3xl bg-surface shadow-card',
            open ? 'flex' : 'hidden lg:flex',
          )}
        >
          {conversationId !== null ? (
            <ChatPanel conversationId={conversationId} onBack={() => void navigate(ROUTES.chat)} />
          ) : (
            <EmptyState
              icon={MessageSquare}
              title={hasParam ? 'Conversation not found' : 'Select a conversation'}
              message={
                hasParam
                  ? 'Choose one from the list.'
                  : 'Choose a conversation from the list, or start a new one with +.'
              }
            />
          )}
        </section>
      </div>
      <NewChatDialog
        open={newChatOpen}
        onClose={() => setNewChatOpen(false)}
        onStarted={(conversation) => {
          setNewChatOpen(false);
          void navigate(routeTo.chatConversation(conversation.id));
        }}
      />
    </div>
  );
}
