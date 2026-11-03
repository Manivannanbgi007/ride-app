import type { TableCard as TableCardData } from '@/types/assistant';
import { cn } from '@/utils/classNames';
import { formatNumber } from '@/utils/formatUtils';

const cellText = (value: string | number | null | undefined): string =>
  typeof value === 'number' ? formatNumber(value) : (value ?? '');

export function TableCard({ card }: { card: TableCardData }) {
  const name = card.title || 'Table';
  return (
    <section aria-label={name} className="rounded-xl bg-page p-3 ring-1 ring-line">
      {card.title && <h3 className="mb-2 text-sm font-semibold text-ink">{card.title}</h3>}
      {/* A wide table scrolls sideways inside the card, never the whole panel */}
      <div
        className="overflow-x-auto rounded-lg ring-1 ring-line focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
        tabIndex={0}
        role="region"
        aria-label={`${name} (scrollable)`}
      >
        <table className="w-full border-collapse bg-surface text-xs">
          <thead>
            <tr>
              {card.columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  className={cn(
                    'border-b border-line px-2.5 py-1.5 font-semibold whitespace-nowrap text-ink-soft',
                    column.align === 'right' ? 'text-right' : 'text-left',
                  )}
                >
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {card.rows.map((row, rowIndex) => (
              <tr key={rowIndex} className="border-b border-line last:border-b-0">
                {card.columns.map((column) => (
                  <td
                    key={column.key}
                    className={cn(
                      'px-2.5 py-1.5 text-ink',
                      column.align === 'right' ? 'text-right tabular-nums' : 'text-left',
                    )}
                  >
                    {cellText(row[column.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
