import { CheckCircle2, IndianRupee, Undo2, XCircle } from 'lucide-react';

import { KpiStrip } from '@/components/common/KpiStrip';
import type { PaymentSummary } from '@/types/payment';
import { formatDate } from '@/utils/dateUtils';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

/** The period's money, as one compact row: collected, refunded, and how many payments went through or failed. */
export function PaymentSummaryCards({ summary }: { summary: PaymentSummary }) {
  const period =
    summary.fromDate === summary.toDate
      ? formatDate(summary.fromDate)
      : `${formatDate(summary.fromDate)} - ${formatDate(summary.toDate)}`;
  const failed = summary.byStatus.FAILED?.count ?? 0;
  return (
    <KpiStrip
      label="The period's payments"
      items={[
        {
          icon: IndianRupee,
          colour: 'green',
          value: formatCurrency(summary.collectedAmount, summary.currency),
          label: 'Collected online',
          hint: period,
        },
        {
          icon: Undo2,
          colour: 'sky',
          value: formatCurrency(summary.refundedAmount, summary.currency),
          label: 'Refunded',
          hint: period,
        },
        {
          icon: CheckCircle2,
          colour: 'violet',
          value: formatNumber(summary.byStatus.SUCCESS?.count ?? 0),
          label: 'Successful payments',
          hint: period,
        },
        {
          icon: XCircle,
          colour: failed > 0 ? 'rose' : 'teal',
          value: formatNumber(failed),
          label: 'Failed payments',
          hint: period,
        },
      ]}
    />
  );
}
