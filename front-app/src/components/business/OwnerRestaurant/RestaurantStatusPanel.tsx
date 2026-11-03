import { ExternalLink, FileText, Store } from 'lucide-react';
import { Link } from 'react-router';

import { RESTAURANT_SECTIONS } from '@/components/business/OwnerRestaurant/restaurantSections';
import { RestaurantStatusBadge } from '@/components/business/StatusBadges';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { RestaurantDetails, RestaurantStatus } from '@/types/restaurant';
import { customerRouteTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';

/** What each status means for the owner, in one line. */
const STATUS_MEANING: Record<RestaurantStatus, string> = {
  PENDING: 'The platform team is reviewing your restaurant. Customers cannot see it yet.',
  APPROVED: 'Approved. The platform team makes it live next.',
  REJECTED: 'Not approved. Correct the details, then send it for review again.',
  ACTIVE: 'Live: customers can find you and order while you are open.',
  INACTIVE: 'Switched off: customers cannot see or order from it.',
  SUSPENDED: 'Suspended by the platform team. Contact support to have it reinstated.',
};

/** A document address the owner typed in: only web addresses are offered as a link. */
const isWebAddress = (url: string) => /^https?:\/\//i.test(url);

/**
 * The restaurant at a glance, next to its form: where it stands with the platform (and why), the dates that matter,
 * its food licence and its page as customers see it, and links to each part of the form. Nothing here is edited:
 * the status is the platform team's to change.
 */
export function RestaurantStatusPanel({ details }: { details: RestaurantDetails }) {
  const licence = details.documents.find(
    (document) => document.type === 'FOOD_LICENSE' && isWebAddress(document.url),
  );

  return (
    <aside aria-label="Your restaurant at a glance" className="flex flex-col gap-4">
      <section
        aria-labelledby="restaurant-status-title"
        className="flex flex-col gap-4 rounded-3xl bg-surface p-5 shadow-card"
      >
        <div className="flex items-center justify-between gap-3">
          <h2 id="restaurant-status-title" className="text-base font-bold text-ink">
            Status
          </h2>
          <RestaurantStatusBadge status={details.status} />
        </div>
        <p className="text-sm text-ink-soft">{STATUS_MEANING[details.status]}</p>
        {details.statusReason && (
          <p className="rounded-xl bg-page p-3 text-sm break-words text-ink">
            <span className="font-semibold">Note from the platform team: </span>
            {details.statusReason}
          </p>
        )}
        <DescriptionList
          columns={1}
          items={[
            { label: 'Registered', value: formatDateTime(details.createdAt) },
            {
              label: 'Approved',
              value: details.approvedAt ? formatDateTime(details.approvedAt) : 'Not yet',
            },
            { label: 'Status changed', value: formatDateTime(details.statusChangedAt) },
            { label: 'Details last changed', value: formatDateTime(details.updatedAt) },
          ]}
        />
        <div className="flex flex-col gap-2 border-t border-line pt-4 text-sm font-semibold">
          {details.status === 'ACTIVE' && (
            <Link
              to={customerRouteTo.restaurantMenu(details.id)}
              target="_blank"
              rel="noopener"
              className="inline-flex items-center gap-2 rounded-md text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              <Store aria-hidden="true" className="size-4" />
              See your page as customers do
              <ExternalLink aria-hidden="true" className="size-3.5" />
              <span className="sr-only">(opens in a new tab)</span>
            </Link>
          )}
          {licence && (
            <a
              href={licence.url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-md text-brand-text hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              <FileText aria-hidden="true" className="size-4" />
              Open the food licence document
              <ExternalLink aria-hidden="true" className="size-3.5" />
              <span className="sr-only">(opens in a new tab)</span>
            </a>
          )}
        </div>
      </section>

      <nav
        aria-label="On this page"
        className="hidden rounded-3xl bg-surface p-5 shadow-card xl:block"
      >
        <h2 className="text-xs font-semibold tracking-wide text-ink-soft uppercase">
          On this page
        </h2>
        <ul className="mt-3 flex flex-col gap-1">
          {RESTAURANT_SECTIONS.map((section) => (
            <li key={section.id}>
              <a
                href={`#${section.id}`}
                className="block rounded-lg px-3 py-2 text-sm font-medium text-ink hover:bg-brand-50 hover:text-brand-text-strong focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
              >
                {section.label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
    </aside>
  );
}
