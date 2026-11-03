import { Bell, BellOff, CheckCheck, Info, Pin, PinOff, Search, X } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useState, type ReactNode } from 'react';

import { ChatHeader } from '@/components/business/Chat/ChatHeader';
import { ChatInfoDialog } from '@/components/business/Chat/ChatInfoDialog';
import { ChatThread } from '@/components/business/Chat/ChatThread';
import { ActionMenu, type ActionMenuItem } from '@/components/common/ActionMenu';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { SearchInput } from '@/components/common/SearchInput';
import { Tooltip } from '@/components/common/Tooltip';
import {
  useMarkChatRead,
  useMuteConversation,
  usePinConversation,
  useStaffConversation,
} from '@/hooks/api/chatHooks';
import { useToast } from '@/context/ToastContext';
import type { ChatConversation } from '@/types/chat';
import { cn } from '@/utils/classNames';
import { USER_ROLE_LABELS } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

interface ChatPanelProps {
  conversationId: number;
  onBack: () => void;
}

/** The staff's chat card: header with its actions, the search, the messages and the composer. */
export function ChatPanel({ conversationId, onBack }: ChatPanelProps) {
  const query = useStaffConversation(conversationId);

  if (query.isPending) {
    return (
      <div role="status" aria-label="Loading conversation" className="flex flex-col gap-4 p-6">
        <Skeleton className="h-12 w-1/2" />
        <Skeleton className="h-10 w-2/3 rounded-2xl" />
        <Skeleton className="ml-auto h-10 w-1/2 rounded-2xl" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <ErrorMessage
        title={ERROR_MESSAGES.CONVERSATION_LOAD_FAILED}
        error={query.error}
        onRetry={() => void query.refetch()}
        isRetrying={query.isFetching}
      />
    );
  }
  return <OpenConversation key={conversationId} conversation={query.data} onBack={onBack} />;
}

function OpenConversation({
  conversation,
  onBack,
}: {
  conversation: ChatConversation;
  onBack: () => void;
}) {
  const { participant } = conversation;
  const [searching, setSearching] = useState(false);
  const [search, setSearch] = useState('');
  const [infoOpen, setInfoOpen] = useState(false);
  const { showToast } = useToast();
  const mute = useMuteConversation(conversation.id);
  const pin = usePinConversation(conversation.id);
  const markRead = useMarkChatRead({ kind: 'staff', conversationId: conversation.id });

  const failed = () => showToast(ERROR_MESSAGES.CHAT_ACTION_FAILED, 'error');
  const closeSearch = () => {
    setSearching(false);
    setSearch('');
  };

  const menuItems: ActionMenuItem[] = [
    {
      key: 'pin',
      label: conversation.pinned ? 'Unpin' : 'Pin',
      icon: conversation.pinned ? PinOff : Pin,
      onSelect: () => pin.mutate(!conversation.pinned, { onError: failed }),
    },
    ...(conversation.unreadCount > 0
      ? [
          {
            key: 'read',
            label: 'Mark as read',
            icon: CheckCheck,
            onSelect: () => markRead.mutate(undefined, { onError: failed }),
          },
        ]
      : []),
  ];

  return (
    <>
      <ChatHeader
        name={participant.name}
        description={USER_ROLE_LABELS[participant.role]}
        online={participant.online}
        onBack={onBack}
        actions={
          <>
            <HeaderButton
              label={conversation.muted ? 'Unmute conversation' : 'Mute conversation'}
              icon={conversation.muted ? BellOff : Bell}
              pressed={conversation.muted}
              onClick={() => mute.mutate(!conversation.muted, { onError: failed })}
            />
            <HeaderButton
              label={searching ? 'Close search' : 'Search in conversation'}
              icon={Search}
              pressed={searching}
              onClick={() => (searching ? closeSearch() : setSearching(true))}
            />
            <HeaderButton label="Conversation info" icon={Info} onClick={() => setInfoOpen(true)} />
            <ActionMenu label={`Actions for ${participant.name}`} items={menuItems} />
          </>
        }
      />
      {searching && (
        <div className="flex items-center gap-2 border-b border-line px-4 py-3 sm:px-6">
          <div className="min-w-0 flex-1">
            <SearchInput
              aria-label="Search messages"
              placeholder="Search in this conversation"
              value={search}
              onSearch={setSearch}
              autoFocus
            />
          </div>
          <HeaderButton label="Clear search" icon={X} onClick={closeSearch} />
        </div>
      )}
      <ChatThread
        scope={{ kind: 'staff', conversationId: conversation.id }}
        unreadCount={conversation.unreadCount}
        search={search}
        empty={
          <p className="py-10 text-center text-sm text-ink-soft">
            No messages yet. Write the first one below.
          </p>
        }
      />
      <ChatInfoDialog
        open={infoOpen}
        onClose={() => setInfoOpen(false)}
        conversation={conversation}
      />
    </>
  );
}

interface HeaderButtonProps {
  label: string;
  icon: LucideIcon;
  onClick: () => void;
  /** A switch that is on (muted, search open) */
  pressed?: boolean;
}

/** A plain icon button of the chat header, with the shared tooltip. */
function HeaderButton({ label, icon: Icon, onClick, pressed }: HeaderButtonProps): ReactNode {
  return (
    <Tooltip label={label} icon={Icon}>
      <button
        type="button"
        aria-label={label}
        aria-pressed={pressed}
        onClick={onClick}
        className={cn(
          'flex size-9 cursor-pointer items-center justify-center rounded-xl transition-colors',
          'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
          pressed
            ? 'bg-brand-50 text-brand-text-strong'
            : 'text-ink-soft hover:bg-page hover:text-ink',
        )}
      >
        <Icon aria-hidden="true" className="size-5" />
      </button>
    </Tooltip>
  );
}
