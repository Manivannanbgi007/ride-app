import { useState } from 'react';

import { getUserMessage } from '@/api/apiErrorHandler';
import { ChatAvatar } from '@/components/business/Chat/ChatAvatar';
import { Badge } from '@/components/common/Badge';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { Modal } from '@/components/common/Modal';
import { SearchInput } from '@/components/common/SearchInput';
import { useStartConversation } from '@/hooks/api/chatHooks';
import { useUsers } from '@/hooks/api/userHooks';
import type { ChatConversation } from '@/types/chat';
import { USER_ROLE_LABELS } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const PICKER_SIZE = 20;

interface NewChatDialogProps {
  open: boolean;
  onClose: () => void;
  /** The conversation with the chosen user (new or the one that already existed) */
  onStarted: (conversation: ChatConversation) => void;
}

/** "New chat": find a user (platform admins are left out: they are Support), pick them, and open the conversation. */
export function NewChatDialog({ open, onClose, onStarted }: NewChatDialogProps) {
  return (
    <Modal open={open} onClose={onClose} title="New chat" size="md">
      <UserPicker onStarted={onStarted} />
    </Modal>
  );
}

function UserPicker({ onStarted }: { onStarted: (conversation: ChatConversation) => void }) {
  const [search, setSearch] = useState('');
  const users = useUsers({ search: search || undefined, sort: 'fullName,asc' }, 0, PICKER_SIZE);
  const start = useStartConversation();

  const candidates = (users.data?.content ?? []).filter((user) => user.role !== 'PLATFORM_ADMIN');

  return (
    <div className="flex flex-col gap-4">
      <SearchInput
        aria-label="Search users"
        placeholder="Search by name, email or phone"
        value={search}
        onSearch={setSearch}
        autoFocus
      />
      {start.isError && (
        <p role="alert" className="text-sm text-danger-text">
          {getUserMessage(start.error)}
        </p>
      )}
      <div className="max-h-80 min-h-40 overflow-y-auto">
        {users.isPending ? (
          <div role="status" aria-label="Loading users" className="flex flex-col gap-3">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} className="h-12 w-full" />
            ))}
          </div>
        ) : users.isError ? (
          <ErrorMessage
            title={ERROR_MESSAGES.CHAT_USERS_LOAD_FAILED}
            error={users.error}
            onRetry={() => void users.refetch()}
          />
        ) : candidates.length === 0 ? (
          <p className="px-2 py-8 text-center text-sm text-ink-soft">No users found.</p>
        ) : (
          <ul className="flex flex-col">
            {candidates.map((user) => (
              <li key={user.id}>
                <button
                  type="button"
                  disabled={start.isPending}
                  onClick={() =>
                    start.mutate(user.id, { onSuccess: (conversation) => onStarted(conversation) })
                  }
                  className="flex w-full cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-page focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none disabled:cursor-wait disabled:opacity-60"
                >
                  <ChatAvatar name={user.fullName} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{user.fullName}</span>
                    <span className="block truncate text-xs text-ink-soft">{user.email}</span>
                  </span>
                  <Badge tone="info">{USER_ROLE_LABELS[user.role]}</Badge>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
