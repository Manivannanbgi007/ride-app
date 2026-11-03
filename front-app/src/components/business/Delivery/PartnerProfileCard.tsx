import { Link } from 'react-router';

import { PartnerStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { Partner } from '@/types/delivery';
import { routeTo, VEHICLE_TYPE_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatNumber, initialsOf } from '@/utils/formatUtils';

/** A delivery partner: contact, vehicle, city, status and how much they deliver. */
export function PartnerProfileCard({ partner }: { partner: Partner }) {
  return (
    <Card>
      <div className="mb-6 flex items-center gap-4">
        <span className="flex size-16 items-center justify-center rounded-full bg-info-50 text-xl font-bold text-info-500">
          {initialsOf(partner.fullName)}
        </span>
        <div>
          <h2 className="text-xl font-bold">{partner.fullName}</h2>
          <div className="mt-1 flex gap-2">
            <PartnerStatusBadge status={partner.status} />
          </div>
        </div>
      </div>
      <div className="flex flex-col gap-5">
        {partner.statusReason && <Alert tone="warning">{partner.statusReason}</Alert>}
        {!partner.readyToWork && (
          <Alert tone="info">
            The partner has not given a vehicle and city yet, so they cannot go online.
          </Alert>
        )}
        <DescriptionList
          items={[
            { label: 'Phone', value: partner.phone },
            { label: 'City', value: partner.city },
            {
              label: 'Vehicle',
              value: partner.vehicleType
                ? `${VEHICLE_TYPE_LABELS[partner.vehicleType]} ${partner.vehicleNumber ?? ''}`
                : null,
            },
            { label: 'Deliveries done', value: formatNumber(partner.completedDeliveries) },
            {
              label: 'Current delivery',
              value: partner.currentDeliveryId ? (
                <Link
                  to={routeTo.deliveryDetails(partner.currentDeliveryId)}
                  className="text-brand-text hover:underline"
                >
                  #{partner.currentDeliveryId}
                </Link>
              ) : (
                'None'
              ),
            },
            {
              label: 'Available since',
              value: partner.availableSince ? formatDateTime(partner.availableSince) : null,
            },
            {
              label: 'Account',
              value: (
                <Link
                  to={routeTo.customerDetails(partner.userId)}
                  className="text-brand-text hover:underline"
                >
                  User #{partner.userId}
                </Link>
              ),
            },
            { label: 'Joined', value: formatDateTime(partner.createdAt) },
          ]}
        />
      </div>
    </Card>
  );
}
