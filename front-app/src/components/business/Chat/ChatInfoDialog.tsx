import { Link } from 'react-router';

import { buttonStyles } from '@/components/common/Button';
import { DescriptionList } from '@/components/common/DescriptionList';
import { Modal } from '@/components/common/Modal';
import type { ChatConversation } from '@/types/chat';
import { USER_ROLE_LABELS, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

interface ChatInfoDialogProps {
  open: boolean;
  onClose: () => void;
  conversation: ChatConversation;
}

/** Who the conversation is with: name, role, member id, when it started, and the customer's page. */
export function ChatInfoDialog({ open, onClose, conversation }: ChatInfoDialogProps) {
  const { participant } = conversation;
  return (
    <Modal open={open} onClose={onClose} title="Conversation info" size="sm">
      <div className="flex flex-col gap-5">
        <DescriptionList
          columns={1}
          items={[
            { label: 'Name', value: participant.name },
            { label: 'Role', value: USER_ROLE_LABELS[participant.role] },
            { label: 'Member id', value: `#${participant.userId}` },
            { label: 'Conversation started', value: formatDateTime(conversation.createdAt) },
          ]}
        />
        {participant.role === 'CUSTOMER' && (
          <Link
            to={routeTo.customerDetails(participant.userId)}
            onClick={onClose}
            className={buttonStyles('outline', 'md', 'self-start')}
          >
            View customer
          </Link>
        )}
      </div>
    </Modal>
  );
}
