import { DayEventList } from '@/components/business/Calendar/DayEventList';
import type { CalendarEvent } from '@/types/calendar';
import { longDate } from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';
import { parseIsoDate } from '@/utils/dateUtils';

interface DayViewProps {
  date: string;
  today: string;
  /** The events of this day */
  events: readonly CalendarEvent[];
}

/** One day as an agenda: what happens, with the details and the links. */
export function DayView({ date, today, events }: DayViewProps) {
  return (
    <div className="mx-auto flex max-w-column flex-col gap-4">
      <p
        className={cn(
          'rounded-xl px-4 py-3 text-sm font-semibold',
          date === today ? 'bg-brand-50 text-brand-text-strong' : 'bg-page text-ink-soft',
        )}
      >
        {longDate(parseIsoDate(date))}
        {date === today && ' (today)'}
      </p>
      <DayEventList date={date} events={events} />
    </div>
  );
}
