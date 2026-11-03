import { ExternalLink } from 'lucide-react';

import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import type { DayOfWeek, OperatingHours, RestaurantDocument } from '@/types/restaurant';
import { DAY_LABELS, DOCUMENT_TYPE_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

const WEEK: readonly DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

/** '11:00:00' -> '11:00' */
const time = (value: string) => value.slice(0, 5);

/** The weekly opening hours (a day can have several slots); days without hours are closed. */
export function RestaurantHoursCard({ hours }: { hours: readonly OperatingHours[] }) {
  return (
    <Card title="Opening hours" subtitle="In the restaurant's time zone">
      <dl className="flex flex-col gap-2 text-sm">
        {WEEK.map((day) => {
          const slots = hours.filter((slot) => slot.dayOfWeek === day);
          return (
            <div key={day} className="flex justify-between gap-4">
              <dt className="text-ink-soft">{DAY_LABELS[day]}</dt>
              <dd className="font-medium">
                {slots.length === 0
                  ? 'Closed'
                  : slots.map((slot) => `${time(slot.opensAt)}-${time(slot.closesAt)}`).join(', ')}
              </dd>
            </div>
          );
        })}
      </dl>
    </Card>
  );
}

/** The documents the owner uploaded (the food license is required), opened in a new tab. */
export function RestaurantDocumentsCard({
  documents,
}: {
  documents: readonly RestaurantDocument[];
}) {
  return (
    <Card title="Documents">
      {documents.length === 0 ? (
        <EmptyState title="No documents" />
      ) : (
        <ul className="flex flex-col gap-3">
          {documents.map((document) => (
            <li
              key={`${document.type}-${document.url}`}
              className="flex items-center justify-between gap-3 rounded-xl bg-page px-4 py-3 text-sm"
            >
              <span>
                <span className="block font-semibold">{DOCUMENT_TYPE_LABELS[document.type]}</span>
                <span className="text-xs text-ink-soft">
                  Uploaded {formatDateTime(document.submittedAt)}
                </span>
              </span>
              <a
                href={document.url}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 font-semibold text-brand-text hover:underline"
              >
                Open
                <ExternalLink aria-hidden="true" className="size-4" />
              </a>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
