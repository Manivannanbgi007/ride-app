import { ChatAvatar } from '@/components/business/Chat/ChatAvatar';
import { DeliveryTicks } from '@/components/business/Chat/DeliveryTicks';
import type { ChatConversation } from '@/types/chat';
import { formatConversationTime } from '@/utils/chatUtils';
import { cn } from '@/utils/classNames';

interface ConversationRowProps {
  conversation: ChatConversation;
  selected: boolean;
  onSelect: (conversationId: number) => void;
}

/**
 * One conversation in the list: avatar (green dot when online), name, a one-line preview (bold when unread), the
 * time, and below it the unread count, or the delivery ticks of the last message when Support wrote it.
 */
export function ConversationRow({ conversation, selected, onSelect }: ConversationRowProps) {
  const { participant, lastMessage, unreadCount } = conversation;
  const unread = unreadCount > 0;
  return (
    <button
      type="button"
      aria-current={selected ? 'true' : undefined}
      onClick={() => onSelect(conversation.id)}
      className={cn(
        'flex w-full cursor-pointer items-center gap-3 rounded-2xl px-3 py-3 text-left transition-colors',
        'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
        selected ? 'bg-brand-50' : 'hover:bg-page',
      )}
    >
      <ChatAvatar name={participant.name} online={participant.online} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold">{participant.name}</span>
        <span
          className={cn(
            'mt-0.5 block truncate text-xs',
            unread ? 'font-bold text-ink' : 'text-ink-soft',
          )}
        >
          {lastMessage ? lastMessage.preview : 'No messages yet'}
        </span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1.5 self-stretch">
        <span className="text-[11px] whitespace-nowrap text-ink-soft">
          {lastMessage ? formatConversationTime(lastMessage.at) : ''}
        </span>
        {unread ? (
          <span
            aria-label={`${unreadCount} unread ${unreadCount === 1 ? 'message' : 'messages'}`}
            className="flex h-6 min-w-6 items-center justify-center rounded-md bg-info-500 px-1.5 text-xs font-bold text-white"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        ) : (
          lastMessage?.fromStaff && <DeliveryTicks read={lastMessage.read} />
        )}
      </span>
    </button>
  );
}
