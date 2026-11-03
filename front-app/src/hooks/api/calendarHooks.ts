import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { calendarService } from '@/services/calendarService';
import { QUERY_KEYS } from '@/utils/queryKeys';

/**
 * The calendar's events between two days (everything the view shows is in this one range). Every type is asked
 * for, so the type filter works on what is already loaded; while another range loads the previous one stays.
 */
export function useCalendar(from: string, to: string) {
  return useQuery({
    queryKey: QUERY_KEYS.calendar(from, to),
    queryFn: () => calendarService.getCalendar(from, to),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}
