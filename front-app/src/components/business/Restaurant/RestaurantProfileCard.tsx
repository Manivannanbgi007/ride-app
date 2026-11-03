import { RestaurantStatusBadge } from '@/components/business/StatusBadges';
import { Alert } from '@/components/common/Alert';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { RestaurantDetails } from '@/types/restaurant';
import { formatDateTime } from '@/utils/dateUtils';

/** What the owner submitted: the business, how to reach it, and where it is; with its current status. */
export function RestaurantProfileCard({ restaurant }: { restaurant: RestaurantDetails }) {
  const { address } = restaurant;
  return (
    <Card title="Business details" actions={<RestaurantStatusBadge status={restaurant.status} />}>
      <div className="flex flex-col gap-5">
        {restaurant.statusReason && (
          <Alert title={`Last status change, ${formatDateTime(restaurant.statusChangedAt)}`}>
            {restaurant.statusReason}
          </Alert>
        )}
        {restaurant.description && <p className="text-sm">{restaurant.description}</p>}
        <DescriptionList
          items={[
            { label: 'Legal business name', value: restaurant.legalBusinessName },
            { label: 'Cuisines', value: restaurant.cuisines.join(', ') },
            { label: 'Food license number', value: restaurant.foodLicenseNumber },
            { label: 'Tax registration', value: restaurant.taxRegistrationNumber },
            { label: 'Phone', value: restaurant.phone },
            { label: 'Email', value: restaurant.email },
            {
              label: 'Address',
              value: [address.line1, address.line2, address.city, address.state, address.postalCode]
                .filter(Boolean)
                .join(', '),
            },
            { label: 'Time zone', value: restaurant.timeZone },
            { label: 'Submitted', value: formatDateTime(restaurant.createdAt) },
            {
              label: 'Approved',
              value: restaurant.approvedAt ? formatDateTime(restaurant.approvedAt) : 'Not yet',
            },
          ]}
        />
      </div>
    </Card>
  );
}
