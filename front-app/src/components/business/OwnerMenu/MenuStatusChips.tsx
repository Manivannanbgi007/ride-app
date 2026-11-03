import { CircleCheck, EyeOff, LayoutGrid, PackageX, type LucideIcon } from 'lucide-react';

import { MENU_STATE_LABELS, type MenuState } from '@/components/business/OwnerMenu/ownerMenuUtils';
import { cn } from '@/utils/classNames';
import { formatNumber } from '@/utils/formatUtils';

const CHIPS: readonly { state?: MenuState; label: string; icon: LucideIcon }[] = [
  { label: 'All dishes', icon: LayoutGrid },
  { state: 'listed', label: MENU_STATE_LABELS.listed, icon: CircleCheck },
  { state: 'soldOut', label: MENU_STATE_LABELS.soldOut, icon: PackageX },
  { state: 'hidden', label: MENU_STATE_LABELS.hidden, icon: EyeOff },
];

interface MenuStatusChipsProps {
  /** How many dishes each chip would show (see menuStateCounts) */
  counts: Record<MenuState | 'all' | 'blocked', number>;
  /** The chosen state; undefined = all dishes */
  value?: MenuState;
  onChange: (state: MenuState | undefined) => void;
}

/**
 * The menu at a glance and the status filter in one: how many dishes customers can order, how many are sold out
 * today and how many are hidden. Choosing a chip shows only those dishes; the chosen one is pressed.
 */
export function MenuStatusChips({ counts, value, onChange }: MenuStatusChipsProps) {
  return (
    <div role="group" aria-label="Show dishes by status" className="flex flex-wrap gap-2">
      {CHIPS.map(({ state, label, icon: Icon }) => {
        const count = state ? counts[state] : counts.all;
        const pressed = value === state;
        return (
          <button
            key={label}
            type="button"
            aria-pressed={pressed}
            aria-label={`${label}: ${formatNumber(count)} ${count === 1 ? 'dish' : 'dishes'}`}
            onClick={() => onChange(state)}
            className={cn(
              'inline-flex h-10 cursor-pointer items-center gap-2 rounded-full pr-2 pl-3.5 text-sm font-semibold ring-1 transition duration-200',
              'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
              pressed
                ? 'bg-brand-500 text-on-brand shadow-brand ring-brand-500'
                : 'bg-surface text-ink ring-line hover:ring-brand-200',
            )}
          >
            <Icon aria-hidden="true" className="size-4 shrink-0" />
            {label}
            <span
              aria-hidden="true"
              className={cn(
                'min-w-7 rounded-full px-2 py-0.5 text-center text-xs tabular-nums',
                pressed ? 'bg-on-brand/10' : 'bg-page text-ink-soft',
              )}
            >
              {formatNumber(count)}
            </span>
          </button>
        );
      })}
      {counts.blocked > 0 && (
        <p className="flex items-center text-xs text-ink-soft">
          {formatNumber(counts.blocked)} of the hidden dishes{' '}
          {counts.blocked === 1 ? 'was' : 'were'} blocked by the platform team.
        </p>
      )}
    </div>
  );
}
