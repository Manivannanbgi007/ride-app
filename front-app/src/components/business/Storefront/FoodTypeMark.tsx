import type { FoodType } from '@/types/catalog';
import { FOOD_TYPE_LABELS } from '@/utils/constants';
import { cn } from '@/utils/classNames';

const STYLES: Record<FoodType, { border: string; dot: string }> = {
  VEG: { border: 'border-brand-600', dot: 'bg-brand-600' },
  NON_VEG: { border: 'border-danger-600', dot: 'bg-danger-600' },
  EGG: { border: 'border-warning-700', dot: 'bg-warning-500' },
};

/** The veg / non-veg mark Indian menus use: a coloured square with a dot. Read out as text. */
export function FoodTypeMark({ foodType }: { foodType: FoodType }) {
  const style = STYLES[foodType];
  return (
    <span
      role="img"
      aria-label={FOOD_TYPE_LABELS[foodType]}
      title={FOOD_TYPE_LABELS[foodType]}
      className={cn(
        'inline-flex size-4 shrink-0 items-center justify-center rounded-sm border-2 bg-surface',
        style.border,
      )}
    >
      <span className={cn('size-1.5 rounded-full', style.dot)} />
    </span>
  );
}
