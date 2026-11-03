import { DISPLAY_LOCALE, NOT_AVAILABLE } from '@/utils/constants';

/** 12345 -> '12,345'; null (unknown) -> '—' */
export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined
    ? NOT_AVAILABLE
    : value.toLocaleString(DISPLAY_LOCALE);
}

/** 15405.5 -> '₹15,405.50'; null (unknown) -> '—' */
export function formatCurrency(value: number | null | undefined, currency: string): string {
  if (value === null || value === undefined) {
    return NOT_AVAILABLE;
  }
  return new Intl.NumberFormat(DISPLAY_LOCALE, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

/** For chart axes: 18591.1 -> '₹18.6K' */
export function formatCompactCurrency(value: number, currency: string): string {
  return new Intl.NumberFormat(DISPLAY_LOCALE, {
    style: 'currency',
    currency,
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

/** An enum value without a label of its own: 'MENU_ITEM_BLOCKED_BY_ADMIN' -> 'Menu item blocked by admin' */
export function humanizeEnum(value: string): string {
  const words = value.toLowerCase().replaceAll('_', ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** 'Platform Administrator' -> 'PA' (avatars without a picture) */
export function initialsOf(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return words
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('');
}

/** 81.4 -> '81%'; null (cannot be worked out) -> '—' */
export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? NOT_AVAILABLE : `${Math.round(value)}%`;
}

/** A waiting time: 12 -> '12 min', 95 -> '1 h 35 min', 1507 -> '1 d 1 h' (long waits stay readable). */
export function formatMinutes(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return minutes % 60 === 0 ? `${hours} h` : `${hours} h ${minutes % 60} min`;
  }
  const days = Math.floor(hours / 24);
  return hours % 24 === 0 ? `${days} d` : `${days} d ${hours % 24} h`;
}
