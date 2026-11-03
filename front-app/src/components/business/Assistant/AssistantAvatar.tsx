import { AssistantIcon } from '@/components/business/Assistant/AssistantIcon';
import { cn } from '@/utils/classNames';

const SIZES = {
  // 18 px rather than 16: the cloche inside the bubble stays clear at this size
  sm: { box: 'size-8', icon: 'size-[1.125rem]' },
  lg: { box: 'size-16', icon: 'size-7' },
};

/** The assistant's round picture beside its messages (and, larger, on the welcome screen). */
export function AssistantAvatar({ size = 'sm' }: { size?: keyof typeof SIZES }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full bg-brand-500 text-on-brand shadow-brand',
        SIZES[size].box,
        size === 'lg' && 'ring-8 ring-brand-500/15',
      )}
    >
      <AssistantIcon className={SIZES[size].icon} />
    </span>
  );
}
