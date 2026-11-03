import { LayoutGrid, List, type LucideIcon } from 'lucide-react';

import { Tooltip } from '@/components/common/Tooltip';
import type { FoodViewMode } from '@/hooks/useFoodViewMode';
import { cn } from '@/utils/classNames';

const MODES: readonly { mode: FoodViewMode; label: string; icon: LucideIcon }[] = [
  { mode: 'list', label: 'List view', icon: List },
  { mode: 'grid', label: 'Grid view', icon: LayoutGrid },
];

/** Two icon buttons: the menu items as a table or as cards. The active one is tinted in the brand colour. */
export function FoodViewToggle({
  value,
  onChange,
}: {
  value: FoodViewMode;
  onChange: (mode: FoodViewMode) => void;
}) {
  return (
    <div role="group" aria-label="View" className="flex items-center gap-2">
      {MODES.map(({ mode, label, icon: Icon }) => (
        <Tooltip key={mode} label={label} icon={Icon}>
          <button
            type="button"
            aria-label={label}
            aria-pressed={value === mode}
            onClick={() => onChange(mode)}
            className={cn(
              'flex size-11 cursor-pointer items-center justify-center rounded-xl shadow-xs transition-colors',
              'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
              value === mode
                ? 'bg-brand-100 text-brand-text-strong'
                : 'bg-surface text-ink-soft hover:bg-brand-50 hover:text-brand-text',
            )}
          >
            <Icon aria-hidden="true" className="size-5" />
          </button>
        </Tooltip>
      ))}
    </div>
  );
}
