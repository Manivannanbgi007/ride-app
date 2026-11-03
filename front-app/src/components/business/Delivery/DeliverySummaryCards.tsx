import {
  AlarmClock,
  Bike,
  PackageCheck,
  PackageX,
  PowerOff,
  ShieldBan,
  Timer,
  Truck,
  UserCheck,
} from 'lucide-react';

import { KpiStrip } from '@/components/common/KpiStrip';
import type { DeliveryReport, PartnerAvailability } from '@/types/delivery';
import { ROUTES } from '@/utils/constants';
import { formatDate } from '@/utils/dateUtils';
import { formatNumber } from '@/utils/formatUtils';

/** The period's deliveries (created in it) and how many are late right now (a link to them when there are any). */
export function DeliverySummaryCards({ report }: { report: DeliveryReport }) {
  const period =
    report.fromDate === report.toDate
      ? formatDate(report.fromDate)
      : `${formatDate(report.fromDate)} - ${formatDate(report.toDate)}`;
  return (
    <KpiStrip
      label="The period's deliveries"
      items={[
        {
          icon: Truck,
          colour: 'sky',
          value: formatNumber(report.total),
          label: 'Deliveries',
          hint: period,
        },
        {
          icon: PackageCheck,
          colour: 'green',
          value: formatNumber(report.delivered),
          label: 'Delivered',
          hint: period,
        },
        {
          icon: PackageX,
          colour: 'rose',
          value: formatNumber(report.cancelled),
          label: 'Cancelled',
          hint: period,
        },
        {
          icon: Timer,
          colour: 'violet',
          value: formatNumber(report.averageDeliveryMinutes),
          label: 'Minutes on average',
          hint: 'Food ready to delivered',
        },
        {
          icon: AlarmClock,
          colour: report.delayedNow > 0 ? 'sunset' : 'teal',
          value: formatNumber(report.delayedNow),
          label: 'Delayed now',
          hint: 'Whatever the period',
          to: report.delayedNow > 0 ? `${ROUTES.deliveries}?tab=delayed` : undefined,
        },
      ]}
    />
  );
}

/** How many partners have each status right now. */
export function PartnerAvailabilityCards({ availability }: { availability: PartnerAvailability }) {
  const { byStatus } = availability;
  return (
    <KpiStrip
      label="Delivery partners right now"
      items={[
        {
          icon: UserCheck,
          colour: 'green',
          value: formatNumber(byStatus.AVAILABLE),
          label: 'Available right now',
        },
        {
          icon: Bike,
          colour: 'sky',
          value: formatNumber(byStatus.BUSY),
          label: 'On a delivery right now',
        },
        {
          icon: PowerOff,
          colour: 'violet',
          value: formatNumber(byStatus.OFFLINE),
          label: 'Offline right now',
        },
        {
          icon: ShieldBan,
          colour: 'rose',
          value: formatNumber(byStatus.SUSPENDED),
          label: 'Suspended right now',
        },
      ]}
    />
  );
}
