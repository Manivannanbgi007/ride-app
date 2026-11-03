import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { Select } from '@/components/common/Select';
import { REVIEW_TAGS, type ReviewFilters } from '@/types/review';
import { REVIEW_TAG_LABELS } from '@/utils/constants';
import { oneOf } from '@/utils/validationUtils';

const TAG_OPTIONS = REVIEW_TAGS.map((tag) => ({ value: tag, label: REVIEW_TAG_LABELS[tag] }));
const RATING_OPTIONS = [5, 4, 3, 2, 1].map((rating) => ({
  value: String(rating),
  label: `${rating} ${rating === 1 ? 'star' : 'stars'}`,
}));

interface ReviewFilterBarProps {
  filters: Pick<ReviewFilters, 'tag' | 'rating'>;
  onChange: (changes: Partial<Pick<ReviewFilters, 'tag' | 'rating'>>) => void;
  onClear: () => void;
}

/** The tag and the rating a review must have. */
export function ReviewFilterBar({ filters, onChange, onClear }: ReviewFilterBarProps) {
  return (
    <div className="grid items-start gap-4 sm:grid-cols-[1fr_1fr_auto]">
      <Select
        label="Tag"
        placeholder="Any tag"
        options={TAG_OPTIONS}
        value={filters.tag ?? ''}
        onChange={(event) => onChange({ tag: oneOf(event.target.value, REVIEW_TAGS) })}
      />
      <Select
        label="Rating"
        placeholder="Any rating"
        options={RATING_OPTIONS}
        value={filters.rating?.toString() ?? ''}
        onChange={(event) =>
          onChange({ rating: event.target.value ? Number(event.target.value) : undefined })
        }
      />
      <div className="flex items-end sm:pt-7">
        <ClearFiltersButton onClick={onClear} />
      </div>
    </div>
  );
}
