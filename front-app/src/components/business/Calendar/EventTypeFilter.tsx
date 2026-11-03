import { DOT_TONES, TYPE_TONES } from '@/components/business/Calendar/calendarStyles';
import { CALENDAR_EVENT_TYPES, type CalendarEventType } from '@/types/calendar';
import { EVENT_TYPE_LABELS } from '@/utils/calendarUtils';
import { cn } from '@/utils/classNames';

interface EventTypeFilterProps {
  selected: ReadonlySet<CalendarEventType>;
  onToggle: (type: CalendarEventType) => void;
}

/** The legend and the filter in one: a chip with a colour dot per type of event, each switching that type on or off. */
export function EventTypeFilter({ selected, onToggle }: EventTypeFilterProps) {
  return (
    <div role="group" aria-label="Event types" className="flex flex-wrap items-center gap-2">
      {CALENDAR_EVENT_TYPES.map((type) => {
        const isOn = selected.has(type);
        return (
          <button
            key={type}
            type="button"
            aria-pressed={isOn}
            onClick={() => onToggle(type)}
            className={cn(
              'inline-flex cursor-pointer items-center gap-2 rounded-full border px-4 py-1.5 text-sm font-semibold transition-colors',
              'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
              isOn
                ? 'border-brand-500 bg-brand-50 text-ink'
                : 'border-line text-ink-soft hover:border-brand-300 hover:text-ink',
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                'size-2.5 rounded-full',
                isOn ? DOT_TONES[TYPE_TONES[type]] : 'bg-ink-soft/40',
              )}
            />
            {EVENT_TYPE_LABELS[type]}
          </button>
        );
      })}
    </div>
  );
}
