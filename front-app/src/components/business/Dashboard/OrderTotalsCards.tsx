import { ClipboardList, IndianRupee, PackageCheck, PackageX } from 'lucide-react';

import { KpiStrip } from '@/components/common/KpiStrip';
import type { OrderTotals } from '@/utils/dashboardUtils';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

interface OrderTotalsCardsProps {
  /** null: order-service did not answer */
  totals: OrderTotals | null;
  currency: string;
  days: number;
}

/** The four figures at the top, as compact coloured tiles: the period's orders, deliveries, cancellations, revenue. */
export function OrderTotalsCards({ totals, currency, days }: OrderTotalsCardsProps) {
  const hint = `Last ${days} days`;
  return (
    <KpiStrip
      label="The period's orders"
      filled
      items={[
        {
          icon: ClipboardList,
          colour: 'violet',
          value: formatNumber(totals?.orders),
          label: 'Total Orders',
          hint,
        },
        {
          icon: PackageCheck,
          colour: 'green',
          value: formatNumber(totals?.delivered),
          label: 'Total Delivered',
          hint,
        },
        {
          icon: PackageX,
          colour: 'rose',
          value: formatNumber(totals?.cancelled),
          label: 'Total Cancelled',
          hint,
        },
        {
          icon: IndianRupee,
          colour: 'sunset',
          value: formatCurrency(totals?.revenue, currency),
          label: 'Total Revenue',
          hint: `${hint}, delivered orders`,
        },
      ]}
    />
  );
}
