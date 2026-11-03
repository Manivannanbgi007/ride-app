import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import type { TooltipPlacement } from '@/components/common/Tooltip/TooltipBubble';
import { useTooltip } from '@/components/common/Tooltip/useTooltip';
import { cn } from '@/utils/classNames';

interface TooltipProps {
  label: string;
  icon?: LucideIcon;
  placement?: TooltipPlacement;
  /** Layout of the wrapper (it is inline-flex, so the button inside keeps its size) */
  className?: string;
  children: ReactNode;
}

/**
 * The shared tooltip around a button or link: it appears on hover and keyboard focus, and goes away on click.
 * It listens on a wrapper, so a disabled button (which gets no mouse events) can still say why it is off.
 * Where the trigger must be the element itself (the header, the sidebar), use `useTooltip` directly.
 */
export function Tooltip({ label, icon, placement, className, children }: TooltipProps) {
  const { trigger, tooltip, hide } = useTooltip({ label, icon, placement });
  return (
    <>
      <span {...trigger} onClickCapture={hide} className={cn('inline-flex', className)}>
        {children}
      </span>
      {tooltip}
    </>
  );
}
