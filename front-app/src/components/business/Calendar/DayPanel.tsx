import { DayEventList } from '@/components/business/Calendar/DayEventList';
import { Modal } from '@/components/common/Modal';
import type { CalendarEvent } from '@/types/calendar';
import { longDate } from '@/utils/calendarUtils';
import { parseIsoDate } from '@/utils/dateUtils';

interface DayPanelProps {
  /** The day shown; null keeps the panel closed */
  date: string | null;
  events: readonly CalendarEvent[];
  onClose: () => void;
}

/** The details of one day in a dialog: its events by type, with the links to their pages. */
export function DayPanel({ date, events, onClose }: DayPanelProps) {
  return (
    <Modal open={date !== null} onClose={onClose} title={date ? longDate(parseIsoDate(date)) : ''}>
      {date && <DayEventList date={date} events={events} />}
    </Modal>
  );
}
