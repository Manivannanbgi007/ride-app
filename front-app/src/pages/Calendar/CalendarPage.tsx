import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  CalendarHeader,
  CalendarPartialAlert,
  CalendarSkeleton,
  DayPanel,
  DayView,
  EventTypeFilter,
  MonthGrid,
  WeekView,
  YearView,
} from '@/components/business/Calendar';
import { AsyncContent } from '@/components/common/AsyncContent';
import { EmptyState } from '@/components/common/EmptyState';
import { TabPanel } from '@/components/common/Tabs';
import { useCalendar } from '@/hooks/api/calendarHooks';
import { useCalendarView } from '@/hooks/useCalendarView';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { useNow } from '@/hooks/useNow';
import { CALENDAR_EVENT_TYPES, type CalendarEventType } from '@/types/calendar';
import {
  filterEventsByType,
  indexEventsByDay,
  shiftDate,
  todayInZone,
  visibleRange,
} from '@/utils/calendarUtils';
import { ROUTES } from '@/utils/constants';
import { parseIsoDate } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** Below Tailwind's `sm` breakpoint (40rem): the month becomes a compact grid and the week a list. */
const PHONE = '(max-width: 39.999rem)';

/** The days with no event of a type are not asked for again when switching a chip: the types are filtered here. */
const ALL_TYPES: ReadonlySet<CalendarEventType> = new Set(CALENDAR_EVENT_TYPES);

/**
 * "Calendar": what happens on the platform by day, from the real data only: orders per day, coupon campaigns (as
 * bars from their first to their last day) and new restaurants. One request per visible range; the chips filter
 * what was loaded. "Today" is the day in the business time zone the API reports, not the browser's.
 */
export default function CalendarPage() {
  const navigate = useNavigate();
  const isPhone = useMediaQuery(PHONE);
  const [view, setView] = useCalendarView();
  // The day the admin chose; null follows today (which moves on at midnight of the business time zone)
  const [chosenDate, setChosenDate] = useState<string | null>(null);
  const [types, setTypes] = useState(ALL_TYPES);
  const [panelDay, setPanelDay] = useState<string | null>(null);
  const now = useNow(60_000);

  // The zone comes with the data; until then (and if it never arrives) the device's day is used
  const [timeZone, setTimeZone] = useState<string | undefined>();
  const today = todayInZone(timeZone, new Date(now));
  const date = chosenDate ?? today;
  const range = visibleRange(view, date);
  const calendar = useCalendar(range.from, range.to);
  const reportedZone = calendar.data?.timeZone;
  if (reportedZone && reportedZone !== timeZone) {
    setTimeZone(reportedZone);
  }

  const events = useMemo(
    () => filterEventsByType(calendar.data?.events ?? [], types),
    [calendar.data, types],
  );
  const byDay = useMemo(
    () => indexEventsByDay(events, range.from, range.to),
    [events, range.from, range.to],
  );

  const toggleType = (type: CalendarEventType) =>
    setTypes((current) => {
      const next = new Set(current);
      if (!next.delete(type)) {
        next.add(type);
      }
      return next;
    });

  const selectDay = (day: string) => setChosenDate(day);
  const openDay = (day: string) => {
    setChosenDate(day);
    setPanelDay(day);
  };

  const content = () => {
    switch (view) {
      case 'MONTH':
        return (
          <MonthGrid
            date={date}
            today={today}
            events={events}
            isPhone={isPhone}
            onDayClick={openDay}
            onDayMove={selectDay}
            onOpenDay={openDay}
          />
        );
      case 'WEEK':
        return (
          <WeekView
            date={date}
            today={today}
            events={events}
            isPhone={isPhone}
            onOpenDay={openDay}
          />
        );
      case 'DATE':
        return <DayView date={date} today={today} events={byDay.get(date) ?? []} />;
      case 'YEAR':
        return (
          <YearView
            date={date}
            today={today}
            events={events}
            onOpenMonth={(firstDay) => {
              setChosenDate(firstDay);
              setView('MONTH');
            }}
            onOpenDay={(day) => {
              setChosenDate(day);
              setView('DATE');
            }}
          />
        );
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <h1 className="sr-only">Calendar</h1>
      <section className="flex flex-col gap-6 rounded-3xl bg-surface p-4 shadow-card sm:p-8">
        <CalendarHeader
          view={view}
          onViewChange={setView}
          date={date}
          todayDay={parseIsoDate(today).getDate()}
          onDateChange={setChosenDate}
          onPrevious={() => setChosenDate(shiftDate(view, date, -1))}
          onNext={() => setChosenDate(shiftDate(view, date, 1))}
          onToday={() => setChosenDate(null)}
          onNewCoupon={() => void navigate(ROUTES.couponNew)}
        />
        <EventTypeFilter selected={types} onToggle={toggleType} />
        {calendar.data && <CalendarPartialAlert unavailable={calendar.data.unavailable} />}

        <AsyncContent
          isLoading={calendar.isPending}
          error={calendar.error}
          errorTitle={ERROR_MESSAGES.CALENDAR_LOAD_FAILED}
          onRetry={() => void calendar.refetch()}
          isRetrying={calendar.isFetching}
          loading={<CalendarSkeleton />}
        >
          <TabPanel id={view}>
            <div
              aria-busy={calendar.isPlaceholderData}
              className={calendar.isPlaceholderData ? 'opacity-60 transition-opacity' : undefined}
            >
              {types.size === 0 ? (
                <EmptyState
                  title="No event types selected"
                  message="Turn on Orders, Coupons or Restaurants above to see them here."
                />
              ) : (
                content()
              )}
            </div>
          </TabPanel>
        </AsyncContent>
      </section>

      <DayPanel
        date={panelDay}
        events={panelDay ? (indexEventsByDay(events, panelDay, panelDay).get(panelDay) ?? []) : []}
        onClose={() => setPanelDay(null)}
      />
    </div>
  );
}
