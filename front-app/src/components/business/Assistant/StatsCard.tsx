import type { StatsCard as StatsCardData } from '@/types/assistant';

export function StatsCard({ card }: { card: StatsCardData }) {
  return (
    <section
      aria-label={card.title || 'Figures'}
      className="rounded-xl bg-page p-3 ring-1 ring-line"
    >
      {card.title && <h3 className="mb-2 text-sm font-semibold text-ink">{card.title}</h3>}
      <dl className="grid grid-cols-2 gap-2">
        {card.stats.map((stat, index) => (
          <div key={index} className="min-w-0 rounded-lg bg-surface p-2.5 ring-1 ring-line">
            <dt className="truncate text-xs text-ink-soft">{stat.label}</dt>
            <dd className="text-lg leading-tight font-bold [overflow-wrap:anywhere] text-ink">
              {stat.value}
            </dd>
            {stat.hint && <dd className="mt-0.5 text-xs text-ink-soft">{stat.hint}</dd>}
          </div>
        ))}
      </dl>
    </section>
  );
}
