import type { ReactNode } from 'react';

import { cn } from '@/utils/classNames';
import { stagger } from '@/utils/motion';

export interface TableColumn<T> {
  header: string;
  /** What the column shows for a row */
  render: (row: T) => ReactNode;
  /** 'right' for amounts and counts */
  align?: 'left' | 'center' | 'right';
  className?: string;
}

interface TableProps<T> {
  /** Read out by screen readers: what the table lists */
  caption: string;
  columns: readonly TableColumn<T>[];
  rows: readonly T[];
  rowKey: (row: T) => string | number;
  /** Another page is loading: the current rows are dimmed meanwhile */
  isUpdating?: boolean;
  /** Tighter cells and a smaller minimum width, for short tables in a half-width card (report rankings) */
  compact?: boolean;
}

/**
 * A list of records. Loading, empty and error states are not handled here but around it (AsyncContent), so every
 * table shows them the same way. The rows rise in one after the other (a new page's too); the row under the pointer
 * is tinted and marked by a brand bar at its start.
 */
export function Table<T>({
  caption,
  columns,
  rows,
  rowKey,
  isUpdating = false,
  compact = false,
}: TableProps<T>) {
  return (
    <div className={cn('overflow-x-auto transition-opacity', isUpdating && 'opacity-60')}>
      <table
        className={cn(
          // separate: the header's rounded ends (cells, not rows, carry the lines then)
          'w-full border-separate border-spacing-0 text-left text-sm',
          compact ? 'min-w-[480px]' : 'min-w-[720px]',
        )}
      >
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.header}
                scope="col"
                className={cn(
                  'bg-brand-50 py-3 text-[11px] font-bold tracking-wider whitespace-nowrap text-brand-text-strong uppercase',
                  'first:rounded-l-xl last:rounded-r-xl',
                  compact ? 'px-3' : 'px-4',
                  column.align === 'right' && 'text-right',
                  column.align === 'center' && 'text-center',
                  column.className,
                )}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr
              key={rowKey(row)}
              style={stagger(index)}
              className="transition-colors duration-200 stagger hover:bg-brand-50/60 motion-safe:animate-rise-in [&:hover>td:first-child]:shadow-[inset_3px_0_0_var(--color-brand-500)] [&:last-child>td]:border-b-0"
            >
              {columns.map((column) => (
                <td
                  key={column.header}
                  className={cn(
                    'border-b border-line align-middle',
                    compact ? 'px-3 py-3' : 'px-4 py-4',
                    column.align === 'right' && 'text-right',
                    column.align === 'center' && 'text-center',
                    column.className,
                  )}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
