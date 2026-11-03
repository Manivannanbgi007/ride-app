import { ArrowLeft } from 'lucide-react';
import type { ReactNode } from 'react';

import { ChatAvatar } from '@/components/business/Chat/ChatAvatar';
import { Tooltip } from '@/components/common/Tooltip';

interface ChatHeaderProps {
  name: string;
  /** Under the name, muted: the role, or "We usually reply within a few hours" */
  description?: string;
  /** true: "Online" with a green dot; false: "Offline"; null or left out: nothing (not known) */
  online?: boolean | null;
  /** Icon buttons at the right */
  actions?: ReactNode;
  /** Phones: goes back to the list */
  onBack?: () => void;
}

/** The chat card's top: avatar, name, role and status, and the conversation's actions. */
export function ChatHeader({ name, description, online, actions, onBack }: ChatHeaderProps) {
  return (
    <header className="flex items-center gap-3 border-b border-line px-4 py-4 sm:px-6">
      {onBack && (
        <Tooltip label="Back to conversations" icon={ArrowLeft} className="lg:hidden">
          <button
            type="button"
            aria-label="Back to conversations"
            onClick={onBack}
            className="flex size-9 cursor-pointer items-center justify-center rounded-xl text-ink-soft hover:bg-page hover:text-ink focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
          >
            <ArrowLeft aria-hidden="true" className="size-5" />
          </button>
        </Tooltip>
      )}
      <ChatAvatar name={name} online={online} sizeClass="size-12" />
      <div className="min-w-0 flex-1">
        <h2 className="truncate text-lg leading-tight font-bold">{name}</h2>
        <p className="flex flex-wrap items-center gap-x-2 text-xs text-ink-soft">
          {description && <span>{description}</span>}
          {online === true && (
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-info-500" />
              Online
            </span>
          )}
          {online === false && <span>Offline</span>}
        </p>
      </div>
      {actions && <div className="flex shrink-0 items-center gap-1">{actions}</div>}
    </header>
  );
}
