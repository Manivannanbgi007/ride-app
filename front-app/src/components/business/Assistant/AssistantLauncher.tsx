import { useEffect, useRef } from 'react';

import { AssistantIcon } from '@/components/business/Assistant/AssistantIcon';
import { AssistantMinimizedBar } from '@/components/business/Assistant/AssistantMinimizedBar';
import { Tooltip } from '@/components/common/Tooltip';
import { useAssistant } from '@/context/AssistantContext';
import { cn } from '@/utils/classNames';

interface AssistantLauncherProps {
  /** Below the lg breakpoint, sit above a tab bar fixed at the bottom of the screen (the partner area) */
  raised?: boolean;
}

/**
 * The round button at the bottom right that opens the assistant, or, while the assistant is minimized, the small bar
 * in its place. Focus returns to whichever is shown when the panel closes or is minimized.
 */
export function AssistantLauncher({ raised = false }: AssistantLauncherProps) {
  const { open, openPanel, minimized, launcherRef } = useAssistant();
  const wasOpen = useRef(false);

  useEffect(() => {
    if (wasOpen.current && !open) {
      launcherRef.current?.focus();
    }
    wasOpen.current = open;
  }, [open, launcherRef]);

  if (minimized) {
    return <AssistantMinimizedBar raised={raised} />;
  }
  return (
    <Tooltip
      label="Ask the assistant"
      placement="top"
      className={cn(
        'fixed right-4 z-40',
        raised ? 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] lg:bottom-4' : 'bottom-4',
      )}
    >
      <button
        ref={launcherRef}
        type="button"
        aria-label="Ask the assistant"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={openPanel}
        className="flex size-14 cursor-pointer items-center justify-center rounded-full bg-brand-500 text-on-brand shadow-brand-lg transition duration-200 hover:bg-brand-400 focus-visible:ring-4 focus-visible:ring-brand-500/40 focus-visible:outline-none motion-safe:hover:-translate-y-0.5 motion-safe:active:scale-95"
      >
        <AssistantIcon className="size-6" />
      </button>
    </Tooltip>
  );
}
