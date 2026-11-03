import { useState } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import { FormDialog } from '@/components/common/FormDialog';
import { StarRatingInput } from '@/components/common/StarRating';
import { Textarea } from '@/components/common/Textarea';
import { useToast } from '@/context/ToastContext';
import { useCreateReview, useUpdateReview } from '@/hooks/api/reviewHooks';
import type { OrderDetails } from '@/types/order';
import {
  REVIEW_TAGS,
  type CreateReviewRequest,
  type Review,
  type ReviewTag,
  type UpdateReviewRequest,
} from '@/types/review';
import { cn } from '@/utils/classNames';
import { REVIEW_COMMENT_MAX_LENGTH, REVIEW_MAX_TAGS, REVIEW_TAG_LABELS } from '@/utils/constants';

interface RateOrderDialogProps {
  order: OrderDetails;
  /** The customer's own review, to change it; without it the dialog writes a new one */
  review?: Review;
  onClose: () => void;
}

/**
 * Rating a delivered order: the overall stars (required), up to four tags, a comment, and optionally one rating
 * per dish. It is shown only while open, so every opening starts empty (or with the review being changed).
 */
export function RateOrderDialog({ order, review, onClose }: RateOrderDialogProps) {
  const create = useCreateReview();
  const update = useUpdateReview();
  const { showToast } = useToast();
  const [rating, setRating] = useState(review?.rating ?? 0);
  const [tags, setTags] = useState<ReviewTag[]>(review?.tags ?? []);
  const [comment, setComment] = useState(review?.comment ?? '');
  const [dishRatings, setDishRatings] = useState<Record<number, number>>(() =>
    Object.fromEntries((review?.dishRatings ?? []).map((dish) => [dish.menuItemId, dish.rating])),
  );
  const [ratingError, setRatingError] = useState<string>();
  const [serverError, setServerError] = useState<string>();

  const toggleTag = (tag: ReviewTag) =>
    setTags((current) =>
      current.includes(tag)
        ? current.filter((chosen) => chosen !== tag)
        : current.length < REVIEW_MAX_TAGS
          ? [...current, tag]
          : current,
    );

  const submit = async () => {
    setServerError(undefined);
    if (rating < 1) {
      setRatingError('Please choose a rating from 1 to 5 stars.');
      return;
    }
    const rated = order.items.filter((line) => (dishRatings[line.menuItemId] ?? 0) > 0);
    const changes: UpdateReviewRequest = {
      rating,
      ...(comment.trim() ? { comment: comment.trim() } : {}),
      ...(tags.length > 0 ? { tags } : {}),
      ...(rated.length > 0
        ? {
            dishRatings: rated.map((line) => ({
              menuItemId: line.menuItemId,
              rating: dishRatings[line.menuItemId],
            })),
          }
        : {}),
    };
    try {
      if (review) {
        await update.mutateAsync({
          reviewId: review.id,
          review: {
            ...changes,
            ...(review.version !== undefined ? { version: review.version } : {}),
          },
        });
        showToast('Your review was updated.');
      } else {
        await create.mutateAsync({ orderId: order.id, ...changes } satisfies CreateReviewRequest);
        showToast('Thank you for your review!');
      }
      onClose();
    } catch (error) {
      setServerError(toApiError(error).userMessage);
    }
  };

  return (
    <FormDialog
      open
      title={review ? `Edit your review of order #${order.id}` : `Rate order #${order.id}`}
      submitLabel={review ? 'Save changes' : 'Submit review'}
      isSubmitting={create.isPending || update.isPending}
      serverError={serverError}
      onSubmit={submit}
      onClose={onClose}
    >
      <div className="flex flex-col gap-1.5">
        <p id="rate-overall" className="text-sm font-medium">
          How was your order from {order.restaurantName}?
        </p>
        <StarRatingInput
          label="Overall rating"
          size="lg"
          value={rating}
          invalid={Boolean(ratingError)}
          describedBy={ratingError ? 'rate-overall-error' : undefined}
          onChange={(value) => {
            setRating(value);
            setRatingError(undefined);
          }}
        />
        {ratingError && (
          <p id="rate-overall-error" role="alert" className="text-xs text-danger-text">
            {ratingError}
          </p>
        )}
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium">
          What stood out?{' '}
          <span className="font-normal text-ink-soft">
            (up to {REVIEW_MAX_TAGS}, {tags.length} chosen)
          </span>
        </legend>
        <div className="flex flex-wrap gap-2">
          {REVIEW_TAGS.map((tag) => {
            const chosen = tags.includes(tag);
            const full = !chosen && tags.length >= REVIEW_MAX_TAGS;
            return (
              <button
                key={tag}
                type="button"
                aria-pressed={chosen}
                disabled={full}
                onClick={() => toggleTag(tag)}
                className={cn(
                  'cursor-pointer rounded-full border px-3.5 py-1.5 text-sm font-medium transition duration-150',
                  'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
                  'disabled:cursor-not-allowed disabled:opacity-40',
                  chosen
                    ? 'border-brand-500 bg-brand-100 text-brand-text-strong'
                    : 'border-line bg-surface text-ink hover:border-brand-300',
                )}
              >
                {REVIEW_TAG_LABELS[tag]}
              </button>
            );
          })}
        </div>
      </fieldset>

      <Textarea
        label="Comment (optional)"
        placeholder="Tell others what you liked, or what could be better"
        rows={3}
        maxLength={REVIEW_COMMENT_MAX_LENGTH}
        value={comment}
        onChange={(event) => setComment(event.target.value)}
        hint={`${comment.length}/${REVIEW_COMMENT_MAX_LENGTH}`}
      />

      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium">
          Rate the dishes <span className="font-normal text-ink-soft">(optional)</span>
        </legend>
        <ul className="divide-y divide-line">
          {order.items.map((line) => (
            <li
              key={line.menuItemId}
              className="flex flex-wrap items-center justify-between gap-2 py-2"
            >
              <span className="text-sm">{line.name}</span>
              <StarRatingInput
                label={`Rate ${line.name}`}
                clearable
                value={dishRatings[line.menuItemId] ?? 0}
                onChange={(value) =>
                  setDishRatings((current) => ({ ...current, [line.menuItemId]: value }))
                }
              />
            </li>
          ))}
        </ul>
      </fieldset>
    </FormDialog>
  );
}
