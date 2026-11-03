import type { LucideIcon } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

import { TooltipBubble, type TooltipPlacement } from '@/components/common/Tooltip/TooltipBubble';

/** Room a tooltip needs above or under its trigger before it goes to the other side (px). */
const ROOM_NEEDED = 56;

/** How long the tooltip takes to slip away (its "out" animation). */
const LEAVE_MS = 140;

interface TooltipOptions {
  label: string;
  icon?: LucideIcon;
  /** right: beside the trigger (sidebar); bottom: under it (header); top: above it (also where there is no room under) */
  placement?: TooltipPlacement;
  /** Off: no tooltip (e.g. the sidebar is open and shows its titles) */
  enabled?: boolean;
}

interface TooltipTrigger {
  onMouseEnter: (event: { currentTarget: Element }) => void;
  onMouseLeave: () => void;
  onFocus: (event: { currentTarget: Element }) => void;
  onBlur: () => void;
}

/**
 * A tooltip for a link or button: spread `trigger` on the element and render `tooltip` next to it. It appears on
 * hover and on keyboard focus, and is drawn over the page (no panel edge can cut it).
 *
 *   const { trigger, tooltip, hide } = useTooltip({ label: 'Payments', placement: 'right' });
 *   return <><NavLink {...trigger} onClick={hide} ... />{tooltip}</>;
 */
export function useTooltip({ label, icon, placement = 'bottom', enabled = true }: TooltipOptions): {
  trigger: TooltipTrigger;
  tooltip: ReactNode;
  hide: () => void;
} {
  const [state, setState] = useState<{
    x: number;
    y: number;
    side: TooltipPlacement;
    leaving: boolean;
  } | null>(null);
  const leaveTimer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(leaveTimer.current), []);

  const show = (event: { currentTarget: Element }) => {
    if (!enabled) {
      return;
    }
    window.clearTimeout(leaveTimer.current);
    const box = event.currentTarget.getBoundingClientRect();
    // Above or under: the other side when there is no room (a row near the window's edge)
    let side = placement;
    if (side === 'top' && box.top < ROOM_NEEDED) {
      side = 'bottom';
    } else if (side === 'bottom' && window.innerHeight - box.bottom < ROOM_NEEDED) {
      side = 'top';
    }
    const x = side === 'right' ? box.right + 12 : box.left + box.width / 2;
    const y =
      side === 'right' ? box.top + box.height / 2 : side === 'top' ? box.top - 10 : box.bottom + 10;
    setState({ x, y, side, leaving: false });
  };

  const hide = () => {
    setState((current) => current && { ...current, leaving: true });
    window.clearTimeout(leaveTimer.current);
    leaveTimer.current = window.setTimeout(() => setState(null), LEAVE_MS);
  };

  const tooltip =
    enabled && state
      ? createPortal(
          <TooltipBubble
            label={label}
            icon={icon}
            placement={state.side}
            x={state.x}
            y={state.y}
            leaving={state.leaving}
          />,
          document.body,
        )
      : null;

  return {
    trigger: { onMouseEnter: show, onMouseLeave: hide, onFocus: show, onBlur: hide },
    tooltip,
    hide,
  };
}
