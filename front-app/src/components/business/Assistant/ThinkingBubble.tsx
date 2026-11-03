import { Square } from 'lucide-react';

import { AssistantAvatar } from '@/components/business/Assistant/AssistantAvatar';
import { Tooltip } from '@/components/common/Tooltip';
import { useNow } from '@/hooks/useNow';

/** Three dots that rise one after the other, like someone typing (still for people who asked for less motion). */
function Dots() {
  return (
    <span aria-hidden="true" className="inline-flex items-center gap-1">
      {[0, 150, 300].map((delay) => (
        <span
          key={delay}
          style={{ animationDelay: `${delay}ms` }}
          className="inline-block size-2 rounded-full bg-brand-500 motion-safe:animate-loader-dot"
        />
      ))}
    </span>
  );
}

interface ThinkingBubbleProps {
  /** When the question was sent (ms) */
  since: number;
  onCancel: () => void;
}

/**
 * Shown while the assistant works (a model on a CPU can need a minute or more): seconds so far, and a stop button
 * (an icon only, as in other chat apps; its tooltip and screen-reader name say Cancel).
 */
export function ThinkingBubble({ since, onCancel }: ThinkingBubbleProps) {
  const now = useNow(1000);
  const seconds = Math.max(0, Math.floor((now - since) / 1000));
  return (
    <div className="flex items-start gap-2.5 motion-safe:animate-page-in">
      <AssistantAvatar />
      <div className="flex items-center gap-3 rounded-2xl rounded-tl-md bg-surface py-2 pr-2 pl-4 text-sm text-ink shadow-card">
        <Dots />
        <p className="flex items-center gap-2" role="status">
          <span>Thinking...</span>
          <span className="text-xs text-ink-soft tabular-nums">{seconds} s</span>
        </p>
        <Tooltip label="Cancel" className="rounded-full">
          <button
            type="button"
            aria-label="Cancel"
            onClick={onCancel}
            className="flex size-8 cursor-pointer items-center justify-center rounded-full bg-surface text-ink-soft ring-1 ring-line transition duration-200 hover:bg-danger-600 hover:text-white hover:ring-danger-600 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none motion-safe:active:scale-95"
          >
            <Square aria-hidden="true" className="size-3 fill-current" />
          </button>
        </Tooltip>
      </div>
    </div>
  );
}
