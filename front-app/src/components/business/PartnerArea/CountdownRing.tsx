import { cn } from '@/utils/classNames';
import { formatCountdown } from '@/utils/partnerAreaUtils';

/** Less than this left: the ring turns red and pulses. */
export const HURRY_SECONDS = 30;

const RADIUS = 26;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

interface CountdownRingProps {
  secondsLeft: number;
  totalSeconds: number;
}

/**
 * The time left to accept an offer, as a ring that empties. Decoration only: the sentence next to it ("Accept
 * within 1:45, ...") is what screen readers read.
 */
export function CountdownRing({ secondsLeft, totalSeconds }: CountdownRingProps) {
  const hurry = secondsLeft <= HURRY_SECONDS;
  const left = Math.min(1, Math.max(0, secondsLeft / totalSeconds));
  return (
    <span
      aria-hidden="true"
      className={cn(
        'relative flex size-18 shrink-0 items-center justify-center',
        hurry && secondsLeft > 0 && 'motion-safe:animate-pulse',
      )}
    >
      <svg viewBox="0 0 64 64" className="absolute inset-0 size-full -rotate-90">
        <circle cx="32" cy="32" r={RADIUS} fill="none" strokeWidth="6" className="stroke-line" />
        <circle
          cx="32"
          cy="32"
          r={RADIUS}
          fill="none"
          strokeWidth="6"
          strokeLinecap="round"
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={CIRCUMFERENCE * (1 - left)}
          className={cn(
            'transition-[stroke-dashoffset] duration-1000 ease-linear',
            hurry ? 'stroke-danger-500' : 'stroke-brand-500',
          )}
        />
      </svg>
      <span className="flex flex-col items-center leading-none">
        <span
          className={cn(
            'text-base font-extrabold tabular-nums',
            hurry ? 'text-danger-text' : 'text-ink',
          )}
        >
          {formatCountdown(secondsLeft)}
        </span>
        <span className="mt-0.5 text-[10px] font-semibold tracking-wider text-ink-soft uppercase">
          left
        </span>
      </span>
    </span>
  );
}
