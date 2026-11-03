import { Pencil, Star, Trash2 } from 'lucide-react';
import { useState } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import { RateOrderDialog } from '@/components/business/Review/RateOrderDialog';
import { ReviewTagPill } from '@/components/business/Review/ReviewTagPill';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { SkeletonLoader } from '@/components/common/Loader';
import { StarRating } from '@/components/common/StarRating';
import { useToast } from '@/context/ToastContext';
import { useDeleteReview, useOrderReview } from '@/hooks/api/reviewHooks';
import type { OrderDetails } from '@/types/order';
import type { Review } from '@/types/review';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatReviewDate } from '@/utils/reviewUtils';

/** "Rate this order" on a delivered order: the button to rate it, or the customer's review once written. */
export function RateOrder({ order }: { order: OrderDetails }) {
  const delivered = order.status === 'DELIVERED';
  const review = useOrderReview(order.id, delivered);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const remove = useDeleteReview();
  const { showToast } = useToast();

  const deleteReview = async (reviewId: number | undefined) => {
    if (reviewId === undefined) {
      return;
    }
    try {
      await remove.mutateAsync({ reviewId, orderId: order.id });
      showToast('Your review was deleted.');
    } catch (error) {
      showToast(toApiError(error).userMessage, 'error');
    } finally {
      setDeleting(false);
    }
  };

  if (!delivered) {
    return null;
  }

  return (
    <Card title={review.data ? 'Your review' : 'Rate this order'}>
      {review.isPending ? (
        <SkeletonLoader lines={2} />
      ) : review.error ? (
        <ErrorMessage
          title={ERROR_MESSAGES.ORDER_REVIEW_LOAD_FAILED}
          error={review.error}
          onRetry={() => void review.refetch()}
          isRetrying={review.isFetching}
        />
      ) : review.data ? (
        <div className="flex flex-col gap-4">
          <SubmittedReview review={review.data} />
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setEditing(true)}
              leftIcon={<Pencil aria-hidden="true" className="size-4" />}
            >
              Edit review
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setDeleting(true)}
              leftIcon={<Trash2 aria-hidden="true" className="size-4" />}
            >
              Delete review
            </Button>
          </div>
          {editing && (
            <RateOrderDialog order={order} review={review.data} onClose={() => setEditing(false)} />
          )}
          <ConfirmationDialog
            open={deleting}
            title="Delete your review?"
            message="Your rating and comment will be removed from the restaurant and its dishes. You can rate this order again afterwards."
            confirmLabel="Delete review"
            tone="danger"
            isLoading={remove.isPending}
            onConfirm={() => void deleteReview(review.data?.id)}
            onCancel={() => setDeleting(false)}
          />
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-4">
          <p className="text-sm text-ink-soft">
            Enjoyed it? Tell {order.restaurantName} and other customers how it was.
          </p>
          <Button
            onClick={() => setOpen(true)}
            leftIcon={<Star aria-hidden="true" className="size-4" />}
          >
            Rate this order
          </Button>
          {open && <RateOrderDialog order={order} onClose={() => setOpen(false)} />}
        </div>
      )}
    </Card>
  );
}

/** The review as written, read-only. */
function SubmittedReview({ review }: { review: Review }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <StarRating value={review.rating} size="lg" />
        <span className="text-sm text-ink-soft">{formatReviewDate(review.createdAt)}</span>
      </div>
      {review.tags.length > 0 && (
        <ul aria-label="Tags" className="flex flex-wrap gap-2">
          {review.tags.map((tag) => (
            <li key={tag}>
              <ReviewTagPill tag={tag} />
            </li>
          ))}
        </ul>
      )}
      {review.comment && <p className="text-sm break-words">{review.comment}</p>}
      {review.dishRatings.length > 0 && (
        <ul aria-label="Dish ratings" className="flex flex-col gap-1.5 text-sm">
          {review.dishRatings.map((dish) => (
            <li key={dish.menuItemId} className="flex items-center justify-between gap-3">
              {dish.dishName}
              <StarRating value={dish.rating} size="sm" />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
