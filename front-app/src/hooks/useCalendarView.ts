import { useState } from 'react';

import { CALENDAR_VIEWS, type CalendarView } from '@/types/calendar';

export const CALENDAR_VIEW_STORAGE_KEY = 'calendar.view';

function readSaved(): CalendarView {
  try {
    const saved = localStorage.getItem(CALENDAR_VIEW_STORAGE_KEY);
    return CALENDAR_VIEWS.find((view) => view === saved) ?? 'MONTH';
  } catch {
    return 'MONTH';
  }
}

/** The calendar's view (Date, Week, Month, Year), kept in this browser; Month when nothing is saved. */
export function useCalendarView(): [CalendarView, (view: CalendarView) => void] {
  const [view, setView] = useState<CalendarView>(readSaved);
  const change = (next: CalendarView) => {
    setView(next);
    try {
      localStorage.setItem(CALENDAR_VIEW_STORAGE_KEY, next);
    } catch {
      // Private window or blocked storage: the choice just is not remembered
    }
  };
  return [view, change];
}
