import { CopyCheck, Moon } from 'lucide-react';

import { Button } from '@/components/common/Button';
import { Switch } from '@/components/common/Switch';
import { TimePicker } from '@/components/common/TimePicker';
import { cn } from '@/utils/classNames';
import { DAY_LABELS } from '@/utils/constants';
import type { HoursRow } from '@/utils/ownerRestaurantUtils';
import { DAYS_OF_WEEK } from '@/utils/storefrontUtils';

interface OperatingHoursEditorProps {
  rows: readonly HoursRow[];
  onChange: (rows: HoursRow[]) => void;
  /** What is wrong with the hours (shown under them) */
  error?: string | null;
  disabled?: boolean;
}

const sameHours = (a: HoursRow, b: HoursRow) =>
  a.open === b.open && a.opensAt === b.opensAt && a.closesAt === b.closesAt;

/** Today as a day of the week (Monday first, like the rows). */
const todayOf = () => DAYS_OF_WEEK[(new Date().getDay() + 6) % 7];

/**
 * The week's opening hours: each day is switched closed or open between two times (in the restaurant's time zone),
 * picked in a small time panel instead of the browser's time box. Today's row is marked. The first day's hours can
 * be copied to the whole week in one click, for the many restaurants open the same hours every day. The parent owns
 * the rows, so the same editor serves "Register a restaurant" and the restaurant's page.
 */
export function OperatingHoursEditor({
  rows,
  onChange,
  error,
  disabled = false,
}: OperatingHoursEditorProps) {
  const change = (day: HoursRow['day'], changes: Partial<HoursRow>) =>
    onChange(rows.map((row) => (row.day === day ? { ...row, ...changes } : row)));

  const [first] = rows;
  const allLikeFirst = !first || rows.every((row) => sameHours(row, first));
  const copyFirstToAll = () =>
    onChange(
      rows.map((row) => ({
        ...row,
        open: first.open,
        opensAt: first.opensAt,
        closesAt: first.closesAt,
      })),
    );
  const today = todayOf();

  return (
    <fieldset className="flex flex-col gap-3" disabled={disabled}>
      <legend className="sr-only">Opening hours</legend>
      {first && (
        <div className="flex justify-end">
          <Button
            variant="outline"
            size="sm"
            disabled={disabled || allLikeFirst}
            leftIcon={<CopyCheck aria-hidden="true" className="size-4" />}
            onClick={copyFirstToAll}
          >
            Copy {DAY_LABELS[first.day]}&apos;s hours to every day
          </Button>
        </div>
      )}
      <ul className="flex flex-col gap-1.5">
        {rows.map((row) => {
          const isToday = row.day === today;
          return (
            <li
              key={row.day}
              className={cn(
                'grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 rounded-2xl px-3 py-2.5 transition-colors sm:grid-cols-[9rem_8rem_minmax(0,1fr)]',
                isToday ? 'bg-brand-50/70 ring-1 ring-brand-200' : 'hover:bg-page',
              )}
            >
              <span className="flex items-center gap-2 text-sm font-semibold text-ink">
                {DAY_LABELS[row.day]}
                {isToday && (
                  <span className="rounded-full bg-brand-500 px-2 py-0.5 text-[10px] font-bold tracking-wide text-on-brand uppercase">
                    Today
                  </span>
                )}
              </span>
              <Switch
                label={`${DAY_LABELS[row.day]}: open`}
                checked={row.open}
                disabled={disabled}
                onChange={(open) => change(row.day, { open })}
                stateText={{ on: 'Open', off: 'Closed' }}
              />
              <div className="col-span-2 sm:col-span-1">
                {row.open ? (
                  <div className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">
                    <TimePicker
                      label={`${DAY_LABELS[row.day]} opens at`}
                      value={row.opensAt}
                      disabled={disabled}
                      onChange={(opensAt) => change(row.day, { opensAt })}
                    />
                    <span aria-hidden="true">to</span>
                    <TimePicker
                      label={`${DAY_LABELS[row.day]} closes at`}
                      value={row.closesAt}
                      disabled={disabled}
                      onChange={(closesAt) => change(row.day, { closesAt })}
                    />
                  </div>
                ) : (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-page px-3 py-1.5 text-sm text-ink-soft ring-1 ring-line">
                    <Moon aria-hidden="true" className="size-3.5" />
                    Closed all day
                  </span>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      {error && (
        <p role="alert" className="text-sm text-danger-text">
          {error}
        </p>
      )}
    </fieldset>
  );
}
