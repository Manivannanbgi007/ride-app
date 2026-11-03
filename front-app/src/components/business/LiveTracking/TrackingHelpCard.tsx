import { ChevronRight, LifeBuoy, ReceiptText, type LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';

/** Where the customer goes from here: the full order, or help from support. */
export function TrackingHelpCard({ orderId }: { orderId: number }) {
  return (
    <nav
      aria-label="More about this order"
      className="flex flex-col gap-1 rounded-3xl bg-surface p-2 shadow-card motion-safe:animate-page-in"
    >
      <HelpLink
        to={customerRouteTo.orderDetails(orderId)}
        icon={ReceiptText}
        label="Order details"
        hint="Prices, payment and everything that happened"
      />
      <HelpLink
        to={CUSTOMER_ROUTES.support}
        icon={LifeBuoy}
        label="Help & support"
        hint="A problem with this order? We are here to help"
      />
    </nav>
  );
}

function HelpLink({
  to,
  icon: Icon,
  label,
  hint,
}: {
  to: string;
  icon: LucideIcon;
  label: string;
  hint: string;
}) {
  return (
    <Link
      to={to}
      className="group flex items-center gap-3 rounded-2xl px-3 py-3 transition-colors hover:bg-page focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
    >
      <span
        aria-hidden="true"
        className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text"
      >
        <Icon className="size-5" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{label}</span>
        <span className="block text-xs text-ink-soft">{hint}</span>
      </span>
      <ChevronRight
        aria-hidden="true"
        className="size-4 shrink-0 text-ink-soft transition-transform group-hover:translate-x-0.5"
      />
    </Link>
  );
}
