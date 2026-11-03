import { Route } from 'lucide-react';

import { RouteTimeline } from '@/components/business/PartnerArea/RouteTimeline';
import type { Delivery } from '@/types/delivery';

/**
 * The route of a delivery that is over (delivered or cancelled): the restaurant and the customer, with "Navigate" and
 * "Call". Its outcome, times and money are in the page's hero and history. Details can be missing for a moment while
 * the order's events are on their way.
 */
export function PartnerDeliveryInfo({ delivery }: { delivery: Delivery }) {
  return (
    <section
      aria-labelledby="delivery-route"
      className="rounded-3xl bg-surface p-5 shadow-card sm:p-6"
    >
      <div className="mb-5 flex items-center gap-3">
        <span
          aria-hidden="true"
          className="flex size-9 items-center justify-center rounded-xl bg-brand-50 text-brand-text-strong"
        >
          <Route className="size-4.5" />
        </span>
        <h2 id="delivery-route" className="text-base font-bold tracking-tight">
          Route
        </h2>
      </div>
      <RouteTimeline delivery={delivery} activeLeg={null} />
    </section>
  );
}
