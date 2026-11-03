import { ChevronDown, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { useState } from 'react';

import { MonthPicker } from '@/components/business/Calendar/MonthPicker';
import { Button } from '@/components/common/Button';
import { IconButton } from '@/components/common/IconButton';
import { Tabs, type TabItem } from '@/components/common/Tabs';
import { CALENDAR_VIEWS, type CalendarView } from '@/types/calendar';
import { viewTitle, withMonthYear } from '@/utils/calendarUtils';
import { parseIsoDate } from '@/utils/dateUtils';

const VIEW_LABELS: Record<CalendarView, string> = {
  DATE: 'Date',
  WEEK: 'Week',
  MONTH: 'Month',
  YEAR: 'Year',
};

const VIEW_TABS: readonly TabItem<CalendarView>[] = CALENDAR_VIEWS.map((view) => ({
  id: view,
  label: VIEW_LABELS[view],
}));

/** The unit the previous / next buttons step by, for their names: "Previous month". */
const STEP_NAMES: Record<CalendarView, string> = {
  DATE: 'day',
  WEEK: 'week',
  MONTH: 'month',
  YEAR: 'year',
};

interface CalendarHeaderProps {
  view: CalendarView;
  onViewChange: (view: CalendarView) => void;
  /** The day the view is around */
  date: string;
  /** The day number shown on the "Today" button (today in the business time zone) */
  todayDay: number;
  onDateChange: (date: string) => void;
  onPrevious: () => void;
  onNext: () => void;
  onToday: () => void;
  onNewCoupon: () => void;
}

/**
 * The calendar's top: the title (with the month and year picker under it), the previous / next buttons, the four
 * views as tabs, the "Today" button and "New coupon".
 */
export function CalendarHeader({
  view,
  onViewChange,
  date,
  todayDay,
  onDateChange,
  onPrevious,
  onNext,
  onToday,
  onNewCoupon,
}: CalendarHeaderProps) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const shown = parseIsoDate(date);
  const step = STEP_NAMES[view];

  return (
    <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-4">
      <div className="relative flex items-center gap-3">
        <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
          <button
            type="button"
            aria-haspopup="dialog"
            aria-expanded={pickerOpen}
            onClick={() => setPickerOpen((open) => !open)}
            className="flex cursor-pointer items-center gap-3 rounded-xl px-1 text-left focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
          >
            {viewTitle(view, date)}
            <ChevronDown aria-hidden="true" className="size-5 shrink-0 text-ink-soft" />
          </button>
        </h2>
        <div className="flex items-center gap-1.5">
          <IconButton label={`Previous ${step}`} icon={ChevronLeft} onClick={onPrevious} />
          <IconButton label={`Next ${step}`} icon={ChevronRight} onClick={onNext} />
        </div>
        {pickerOpen && (
          <MonthPicker
            year={shown.getFullYear()}
            month={shown.getMonth()}
            onPick={(year, month) => {
              onDateChange(withMonthYear(date, year, month));
              // Picking a month shows that month, whatever view was open
              onViewChange('MONTH');
              setPickerOpen(false);
            }}
            onClose={() => setPickerOpen(false)}
          />
        )}
      </div>

      <div className="order-last w-full sm:order-none sm:w-auto">
        <Tabs label="Calendar view" tabs={VIEW_TABS} activeId={view} onChange={onViewChange} />
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button variant="secondary" onClick={onToday}>
          Today ({todayDay})
        </Button>
        <Button onClick={onNewCoupon} leftIcon={<Plus aria-hidden="true" className="size-4" />}>
          New coupon
        </Button>
      </div>
    </div>
  );
}
