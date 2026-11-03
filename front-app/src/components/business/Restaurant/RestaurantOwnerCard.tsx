import { Link } from 'react-router';

import { Alert } from '@/components/common/Alert';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import type { RestaurantOwner } from '@/types/restaurant';
import { routeTo } from '@/utils/constants';

/**
 * The owner's account (from user-service). When user-service cannot be reached the restaurant is still shown,
 * with only the owner's number.
 */
export function RestaurantOwnerCard({ owner }: { owner: RestaurantOwner }) {
  return (
    <Card
      title="Owner"
      actions={
        <Link
          to={routeTo.customerDetails(owner.userId)}
          className="text-sm font-semibold text-brand-text hover:underline"
        >
          Account #{owner.userId}
        </Link>
      }
    >
      {owner.lookupStatus === 'FOUND' ? (
        <DescriptionList
          columns={1}
          items={[
            { label: 'Name', value: owner.fullName },
            { label: 'Email', value: owner.email },
            { label: 'Phone', value: owner.phone },
            { label: 'Account', value: owner.accountStatus },
          ]}
        />
      ) : (
        <Alert tone="warning">
          {owner.lookupStatus === 'NOT_FOUND'
            ? 'The owner account no longer exists.'
            : 'The owner details cannot be loaded right now; try again later.'}
        </Alert>
      )}
    </Card>
  );
}
