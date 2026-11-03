import { Bike, ReceiptText, Store, Tag, Utensils, type LucideIcon } from 'lucide-react';
import { useState } from 'react';

import { AssistantLinkButton } from '@/components/business/Assistant/AssistantLinkButton';
import { Badge } from '@/components/common/Badge';
import type { EntitiesCard as EntitiesCardData, EntityItem, EntityType } from '@/types/assistant';
import { safeImageUrl } from '@/utils/assistantUtils';

/** What stands in for a missing picture: the kind of thing the row is about */
const PLACEHOLDER_ICONS: Record<EntityType, LucideIcon> = {
  RESTAURANT: Store,
  FOOD: Utensils,
  OFFER: Tag,
  ORDER: ReceiptText,
  DELIVERY: Bike,
};

function EntityImage({ url, entityType }: { url: string | null; entityType: EntityType }) {
  const [failed, setFailed] = useState(false);
  const source = safeImageUrl(url);
  const Placeholder = PLACEHOLDER_ICONS[entityType];
  return source && !failed ? (
    <img
      src={source}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      className="size-14 shrink-0 rounded-xl bg-line object-cover"
    />
  ) : (
    <span
      aria-hidden="true"
      className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text"
    >
      <Placeholder className="size-6" />
    </span>
  );
}

function EntityRow({ item, entityType }: { item: EntityItem; entityType: EntityType }) {
  return (
    <li className="flex gap-3 rounded-lg bg-surface p-2.5 ring-1 ring-line">
      <EntityImage url={item.imageUrl} entityType={entityType} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="text-sm leading-tight font-semibold [overflow-wrap:anywhere] text-ink">
          {item.title}
        </p>
        {item.subtitle && (
          <p className="text-xs [overflow-wrap:anywhere] text-ink-soft">{item.subtitle}</p>
        )}
        {item.badges.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {item.badges.map((badge, index) => (
              <Badge key={index} tone="success">
                {badge}
              </Badge>
            ))}
          </div>
        )}
        {item.meta.length > 0 && (
          <dl className="flex flex-col gap-0.5 text-xs text-ink-soft">
            {item.meta.map((entry, index) => (
              <div key={index} className="flex gap-1">
                <dt className="shrink-0">{entry.label}:</dt>
                <dd className="font-medium [overflow-wrap:anywhere] text-ink">{entry.value}</dd>
              </div>
            ))}
          </dl>
        )}
        {item.link && <AssistantLinkButton link={item.link} className="mt-1 self-start" />}
      </div>
    </li>
  );
}

export function EntitiesCard({ card }: { card: EntitiesCardData }) {
  return (
    <section
      aria-label={card.title || 'Results'}
      className="rounded-xl bg-page p-3 ring-1 ring-line"
    >
      {card.title && <h3 className="mb-2 text-sm font-semibold text-ink">{card.title}</h3>}
      <ul className="flex flex-col gap-2">
        {card.items.map((item, index) => (
          <EntityRow key={`${item.id}-${index}`} item={item} entityType={card.entityType} />
        ))}
      </ul>
    </section>
  );
}
