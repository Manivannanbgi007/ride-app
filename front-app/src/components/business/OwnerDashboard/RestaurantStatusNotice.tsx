import { Button } from '@/components/common/Button';
import { Alert } from '@/components/common/Alert';
import { useToast } from '@/context/ToastContext';
import { toApiError } from '@/api/apiErrorHandler';
import { useResubmitRestaurant } from '@/hooks/api/ownerHooks';
import type { RestaurantSummary } from '@/types/restaurant';

/**
 * What the owner must know about the restaurant's status: waiting for approval, rejected (with the reason and a way
 * to send it again), or hidden from customers. Nothing is shown while the restaurant is live.
 */
export function RestaurantStatusNotice({ restaurant }: { restaurant: RestaurantSummary }) {
  const resubmit = useResubmitRestaurant(restaurant.id);
  const { showToast } = useToast();

  const sendAgain = async () => {
    try {
      await resubmit.mutateAsync();
      showToast('Your restaurant was sent for review again.');
    } catch (error) {
      showToast(toApiError(error).userMessage, 'error');
    }
  };

  const reason = restaurant.statusReason ? ` Reason: ${restaurant.statusReason}` : '';
  switch (restaurant.status) {
    case 'PENDING':
      return (
        <Alert tone="info" title="Waiting for approval">
          The platform team is reviewing {restaurant.name}. Customers cannot see it, and no orders
          can come in, until it is approved and live.
        </Alert>
      );
    case 'APPROVED':
      return (
        <Alert tone="info" title="Approved, not live yet">
          {restaurant.name} is approved. The platform team makes it live next; customers cannot
          order from it until then.
        </Alert>
      );
    case 'REJECTED':
      return (
        <Alert tone="error" title="Your restaurant was not approved">
          <span className="block">{reason.trim() || 'No reason was given.'}</span>
          <span className="mt-1 block">
            Correct what is missing under &quot;Restaurant&quot;, then send it for review again.
          </span>
          <Button
            size="sm"
            className="mt-3"
            isLoading={resubmit.isPending}
            onClick={() => void sendAgain()}
          >
            Send for review again
          </Button>
        </Alert>
      );
    case 'INACTIVE':
      return (
        <Alert tone="warning" title="Hidden from customers">
          {restaurant.name} is switched off, so customers cannot see or order from it.{reason}
        </Alert>
      );
    case 'SUSPENDED':
      return (
        <Alert tone="error" title="Suspended">
          {restaurant.name} was suspended by the platform team.{reason} Contact support to have it
          reinstated.
        </Alert>
      );
    default:
      return null;
  }
}
