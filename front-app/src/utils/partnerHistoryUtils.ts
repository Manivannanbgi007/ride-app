import type { DeliverySummary } from '@/types/delivery';
import { dateRangeError } from '@/utils/validationUtils';

/** The moment a delivery is listed under: when it was delivered, else offered, else when the food was ready. */
export function deliveryMomentOf(row: DeliverySummary): string {
  return row.deliveredAt ?? row.assignedAt ?? row.readyAt;
}

/** A moment as the calendar day it falls on in the viewer's time zone: 'YYYY-MM-DD' (what the date pickers use). */
export function localDayOf(isoInstant: string): string {
  const date = new Date(isoInstant);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export interface HistoryFilters {
  /** Order number, restaurant or city; empty for no search */
  search?: string;
  /** 'YYYY-MM-DD', both days included */
  fromDate?: string;
  toDate?: string;
}

/** Whether a row matches the search: "42" or "#42" (order number), or part of the restaurant or a city. */
function matchesSearch(row: DeliverySummary, search: string): boolean {
  const text = search.trim().toLowerCase().replace(/^#/, '');
  if (!text) {
    return true;
  }
  return [String(row.orderId), row.restaurantName, row.pickupCity, row.dropoffCity].some((part) =>
    part?.toLowerCase().includes(text),
  );
}

/**
 * The rows that match the search and fall inside the date range. An impossible range ("until" before "from", from an
 * old link) is ignored instead of hiding everything.
 */
export function filterHistory(
  rows: readonly DeliverySummary[],
  { search = '', fromDate, toDate }: HistoryFilters,
): DeliverySummary[] {
  const rangeOk = !dateRangeError(fromDate, toDate);
  return rows.filter((row) => {
    if (!matchesSearch(row, search)) {
      return false;
    }
    if (!rangeOk) {
      return true;
    }
    const day = localDayOf(deliveryMomentOf(row));
    return (!fromDate || day >= fromDate) && (!toDate || day <= toDate);
  });
}

export interface HistorySummary {
  delivered: number;
  cancelled: number;
  /** Delivered with money to collect at the door */
  cashDeliveries: number;
  /** What was collected in cash on the delivered orders */
  cashCollected: number;
  /** Delivered and paid online: nothing to collect */
  paidOnline: number;
}

/** The totals over a list of deliveries (the summary strip). Only delivered orders count towards cash. */
export function summarizeHistory(rows: readonly DeliverySummary[]): HistorySummary {
  const summary: HistorySummary = {
    delivered: 0,
    cancelled: 0,
    cashDeliveries: 0,
    cashCollected: 0,
    paidOnline: 0,
  };
  for (const row of rows) {
    if (row.status === 'CANCELLED') {
      summary.cancelled += 1;
    } else if (row.status === 'DELIVERED') {
      summary.delivered += 1;
      const cash = row.cashToCollect ?? 0;
      if (cash > 0) {
        summary.cashDeliveries += 1;
        summary.cashCollected += cash;
      } else {
        summary.paidOnline += 1;
      }
    }
  }
  return summary;
}
