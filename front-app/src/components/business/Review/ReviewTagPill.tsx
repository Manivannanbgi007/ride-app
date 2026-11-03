import type { ReviewTag } from '@/types/review';
import { REVIEW_TAG_LABELS } from '@/utils/constants';
import { cn } from '@/utils/classNames';
import { tagTone, type TagTone } from '@/utils/reviewUtils';

const TONES: Record<TagTone, string> = {
  service: 'bg-info-50 text-info-ink',
  praise: 'bg-brand-100 text-brand-text-strong',
  other: 'bg-sunset-50 text-sunset-text',
};

/** A tag of a review as a soft, colour-coded pill (blue: service, green: praise, orange: the rest). */
export function ReviewTagPill({ tag }: { tag: ReviewTag }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-3 py-1 text-xs font-medium whitespace-nowrap',
        TONES[tagTone(tag)],
      )}
    >
      {REVIEW_TAG_LABELS[tag]}
    </span>
  );
}
