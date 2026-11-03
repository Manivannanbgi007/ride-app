import type { ReactNode } from 'react';

import { cn } from '@/utils/classNames';
import { NOT_AVAILABLE } from '@/utils/constants';

export interface DescriptionItem {
  label: string;
  /** null or empty shows "—" */
  value: ReactNode;
}

interface DescriptionListProps {
  items: readonly DescriptionItem[];
  columns?: 1 | 2;
}

/** Labelled values of one record ("Email: ...", "Phone: ..."). */
export function DescriptionList({ items, columns = 2 }: DescriptionListProps) {
  return (
    <dl className={cn('grid gap-x-8 gap-y-4', columns === 2 && 'sm:grid-cols-2')}>
      {items.map((item) => (
        <div key={item.label}>
          <dt className="text-xs text-ink-soft">{item.label}</dt>
          <dd className="mt-0.5 text-sm font-medium break-words">
            {item.value === null || item.value === undefined || item.value === ''
              ? NOT_AVAILABLE
              : item.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}
