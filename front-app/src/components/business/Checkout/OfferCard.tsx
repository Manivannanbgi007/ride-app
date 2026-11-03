import { TicketPercent } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import type { Offer } from '@/types/shopping';
import { customerRouteTo, DEFAULT_CURRENCY } from '@/utils/constants';
import { formatDate } from '@/utils/dateUtils';
import { formatCurrency } from '@/utils/formatUtils';

/** An offer: what it gives, its code, its conditions, and the action given (apply / sign in). */
export function OfferCard({ offer, action }: { offer: Offer; action?: ReactNode }) {
  return (
    <article className="flex flex-col gap-3 rounded-2xl bg-surface p-5 shadow-card">
      <div className="flex items-start gap-3">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-text">
          <TicketPercent aria-hidden="true" className="size-5" />
        </span>
        <div className="min-w-0">
          <h3 className="text-lg font-extrabold">{offer.summary}</h3>
          {offer.description && <p className="text-sm text-ink-soft">{offer.description}</p>}
        </div>
      </div>
      <p className="w-fit rounded-lg border border-dashed border-brand-500 px-3 py-1 font-mono text-sm font-bold tracking-wide">
        {offer.code}
      </p>
      <ul className="text-xs text-ink-soft">
        {offer.minOrderAmount > 0 && (
          <li>Orders from {formatCurrency(offer.minOrderAmount, DEFAULT_CURRENCY)}</li>
        )}
        <li>Valid until {formatDate(offer.validUntil)}</li>
        {offer.restaurantId !== null && (
          <li>
            Only at{' '}
            <Link
              to={customerRouteTo.restaurantMenu(offer.restaurantId)}
              className="font-semibold text-brand-text hover:underline"
            >
              one restaurant
            </Link>
          </li>
        )}
      </ul>
      {action && <div className="mt-auto pt-1">{action}</div>}
    </article>
  );
}
