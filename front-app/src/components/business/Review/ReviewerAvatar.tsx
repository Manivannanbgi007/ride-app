import { avatarTone } from '@/utils/reviewUtils';
import { cn } from '@/utils/classNames';
import { initialsOf } from '@/utils/formatUtils';

/** The circle's size and its letters'; "none": the caller gives both in `className` (a large profile picture). */
const SIZES = {
  sm: 'size-7 text-[10px]',
  md: 'size-10 text-sm',
  lg: 'size-11 text-sm',
  none: '',
} as const;

interface ReviewerAvatarProps {
  name: string;
  /** Sits on a dark strip: a neutral light circle instead of the name's colour */
  onStrip?: boolean;
  size?: keyof typeof SIZES;
  className?: string;
}

/** The customer's initials in a circle; the colour is the same for the same name (there are no photos). */
export function ReviewerAvatar({
  name,
  onStrip = false,
  size = 'lg',
  className,
}: ReviewerAvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full font-bold',
        SIZES[size],
        onStrip ? 'bg-on-strip/90 text-strip' : avatarTone(name),
        className,
      )}
    >
      {initialsOf(name) || '?'}
    </span>
  );
}
