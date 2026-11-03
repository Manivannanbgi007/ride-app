import { Link } from 'react-router';

import { CouponAvailabilityBadge } from '@/components/business/StatusBadges';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { Coupon, CouponDetails } from '@/types/coupon';
import { REDEMPTION_STATUSES } from '@/types/coupon';
import {
  COUPON_STATUS_LABELS,
  DEFAULT_CURRENCY,
  DISCOUNT_TYPE_LABELS,
  REDEMPTION_STATUS_LABELS,
  routeTo,
} from '@/utils/constants';
import { formatDate, formatDateTime } from '@/utils/dateUtils';
import { formatCurrency, formatNumber } from '@/utils/formatUtils';

const limit = (value: number | null) => (value === null ? 'Unlimited' : formatNumber(value));

/** The coupon's terms: discount, minimum, restaurant, dates and limits. */
export function CouponTermsCard({ coupon }: { coupon: Coupon }) {
  return (
    <Card
      title={coupon.summary}
      subtitle={coupon.description ?? undefined}
      actions={<CouponAvailabilityBadge availability={coupon.availability} />}
    >
      <DescriptionList
        items={[
          { label: 'Status', value: COUPON_STATUS_LABELS[coupon.status] },
          { label: 'Discount', value: DISCOUNT_TYPE_LABELS[coupon.discountType] },
          {
            label: 'Value',
            value:
              coupon.discountType === 'PERCENTAGE'
                ? `${coupon.discountValue}%`
                : formatCurrency(coupon.discountValue, DEFAULT_CURRENCY),
          },
          {
            label: 'At most',
            value:
              coupon.maxDiscount === null
                ? null
                : formatCurrency(coupon.maxDiscount, DEFAULT_CURRENCY),
          },
          {
            label: 'Minimum items total',
            value: formatCurrency(coupon.minOrderAmount, DEFAULT_CURRENCY),
          },
          {
            label: 'Restaurant',
            value:
              coupon.restaurantId === null ? (
                'Every restaurant'
              ) : (
                <Link
                  to={routeTo.restaurantDetails(coupon.restaurantId)}
                  className="text-brand-text hover:underline"
                >
                  #{coupon.restaurantId}
                </Link>
              ),
          },
          { label: 'First day', value: formatDate(coupon.validFrom) },
          { label: 'Last day (included)', value: formatDate(coupon.validUntil) },
          { label: 'Uses in total', value: limit(coupon.usageLimit) },
          { label: 'Uses per customer', value: limit(coupon.perCustomerLimit) },
          {
            label: 'Created',
            value: `${formatDateTime(coupon.createdAt)} by admin #${coupon.createdBy}`,
          },
          { label: 'Last change', value: formatDateTime(coupon.updatedAt) },
        ]}
      />
    </Card>
  );
}

/** How the coupon is used: uses per status, discount given, different customers. */
export function CouponUsageCard({ details }: { details: CouponDetails }) {
  const { coupon } = details;
  return (
    <Card
      title="Usage"
      subtitle={`${formatNumber(coupon.timesUsed)} of ${limit(coupon.usageLimit).toLowerCase()} uses taken`}
    >
      <dl className="grid grid-cols-2 gap-3">
        <Figure
          label="Discount given"
          value={formatCurrency(details.discountGiven, DEFAULT_CURRENCY)}
        />
        <Figure label="Customers" value={formatNumber(details.customers)} />
        {REDEMPTION_STATUSES.map((status) => (
          <Figure
            key={status}
            label={REDEMPTION_STATUS_LABELS[status]}
            value={formatNumber(details.usesByStatus[status])}
          />
        ))}
      </dl>
    </Card>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-page px-4 py-3">
      <dt className="text-xs text-ink-soft">{label}</dt>
      <dd className="mt-1 text-lg font-bold">{value}</dd>
    </div>
  );
}
