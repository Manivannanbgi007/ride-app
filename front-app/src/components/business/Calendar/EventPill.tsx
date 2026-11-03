import { PILL_TONES } from '@/components/business/Calendar/calendarStyles';
import { Tooltip } from '@/components/common/Tooltip';
import type { CalendarEvent } from '@/types/calendar';
import { cn } from '@/utils/classNames';
import { describeEvent, pillText } from '@/utils/calendarUtils';

interface EventPillProps {
  event: CalendarEvent;
  /** Opens the day's details */
  onOpen: () => void;
  /** The pill is the cut piece of a longer bar: its left / right end is square, as the bar goes on */
  continuesBefore?: boolean;
  continuesAfter?: boolean;
  className?: string;
}

/**
 * One event as a rounded, tinted pill (a button: it opens the day, and a keyboard reaches it). The tooltip and the
 * screen-reader name carry the full text; the pill itself shows it as far as it fits.
 */
export function EventPill({
  event,
  onOpen,
  continuesBefore = false,
  continuesAfter = false,
  className,
}: EventPillProps) {
  const full = describeEvent(event);
  return (
    <Tooltip label={full} placement="top" className="w-full min-w-0">
      <button
        type="button"
        aria-label={full}
        onClick={onOpen}
        className={cn(
          'flex h-6 w-full min-w-0 cursor-pointer items-center rounded-full px-2.5 text-xs font-semibold ring-1 ring-inset',
          'transition duration-150 hover:brightness-95 focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
          PILL_TONES[event.tone],
          continuesBefore && 'rounded-l-none',
          continuesAfter && 'rounded-r-none',
          className,
        )}
      >
        <span className="truncate">{pillText(event)}</span>
      </button>
    </Tooltip>
  );
}

/**
 * A coupon campaign as a bar across the days it covers. Where the week row cuts it, the end is square: the
 * bar goes on in the next row.
 */
export function CouponBar({
  event,
  onOpen,
  continuesBefore,
  continuesAfter,
}: Omit<EventPillProps, 'className'>) {
  return (
    <EventPill
      event={event}
      onOpen={onOpen}
      continuesBefore={continuesBefore}
      continuesAfter={continuesAfter}
    />
  );
}
