import type { DayOfWeek } from '@/types/restaurant';
import type { MenuSection, OpeningSlot } from '@/types/storefront';
import { DISPLAY_LOCALE } from '@/utils/constants';

export const DAYS_OF_WEEK: readonly DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

/** '05:30:00' or '17:05' -> '5:30 am' / '5:05 pm' */
export function formatTimeOfDay(time: string): string {
  const [hours = 0, minutes = 0] = time.split(':').map(Number);
  const suffix = hours < 12 ? 'am' : 'pm';
  const hour12 = hours % 12 === 0 ? 12 : hours % 12;
  return `${hour12}:${String(minutes).padStart(2, '0')} ${suffix}`;
}

/** The day of the week now in a time zone (the restaurant's: its hours are in its own time). */
export function dayOfWeekIn(timeZone: string, now: Date = new Date()): DayOfWeek {
  let weekday: string;
  try {
    weekday = new Intl.DateTimeFormat('en-US', { weekday: 'long', timeZone }).format(now);
  } catch {
    // An unknown time zone: the visitor's own day is the best guess
    weekday = new Intl.DateTimeFormat('en-US', { weekday: 'long' }).format(now);
  }
  return weekday.toUpperCase() as DayOfWeek;
}

/** Each day's opening times as text: 'MONDAY' -> '11:00 am - 3:00 pm, 7:00 pm - 11:00 pm' or 'Closed'. */
export function hoursByDay(slots: readonly OpeningSlot[]): Record<DayOfWeek, string> {
  return Object.fromEntries(
    DAYS_OF_WEEK.map((day) => {
      const times = slots
        .filter((slot) => slot.dayOfWeek === day)
        .map((slot) => `${formatTimeOfDay(slot.opensAt)} - ${formatTimeOfDay(slot.closesAt)}`);
      return [day, times.length > 0 ? times.join(', ') : 'Closed'];
    }),
  ) as Record<DayOfWeek, string>;
}

export interface MenuFilter {
  vegOnly: boolean;
  /** Part of a dish's name or description */
  text: string;
}

/** The menu with only the dishes that match; sections left empty are dropped. */
export function filterMenu(sections: readonly MenuSection[], filter: MenuFilter): MenuSection[] {
  const text = filter.text.trim().toLocaleLowerCase(DISPLAY_LOCALE);
  return sections
    .map((section) => ({
      ...section,
      items: section.items.filter(
        (item) =>
          (!filter.vegOnly || item.foodType === 'VEG') &&
          (!text ||
            item.name.toLocaleLowerCase(DISPLAY_LOCALE).includes(text) ||
            (item.description?.toLocaleLowerCase(DISPLAY_LOCALE).includes(text) ?? false)),
      ),
    }))
    .filter((section) => section.items.length > 0);
}

/**
 * Restaurants and dishes have no photos yet: each gets a coloured tile instead, always the same colour for the
 * same restaurant (by its id).
 */
const TILE_STYLES = [
  'bg-brand-50 text-brand-text',
  'bg-info-50 text-info-500',
  'bg-warning-50 text-warning-text',
  'bg-danger-50 text-danger-text',
] as const;

export function tileStyleOf(id: number): string {
  return TILE_STYLES[Math.abs(id) % TILE_STYLES.length];
}

/** The id of a menu section's heading (the category links above the menu jump to it). */
export function sectionAnchorOf(categoryId: number): string {
  return `menu-section-${categoryId}`;
}
