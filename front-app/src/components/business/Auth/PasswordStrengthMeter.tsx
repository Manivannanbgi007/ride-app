import { cn } from '@/utils/classNames';
import { passwordStrength } from '@/utils/passwordStrength';

const SEGMENT_COLORS = [
  '',
  'bg-danger-500',
  'bg-warning-500',
  'bg-brand-400',
  'bg-brand-600',
] as const;

/**
 * Four segments that fill up as the password gets harder to guess, with a word for screen readers and eyes alike.
 * Only a hint: whether the password is accepted is decided by the form's own rules.
 */
export function PasswordStrengthMeter({ password }: { password: string }) {
  const { level, label } = passwordStrength(password);

  return (
    <div className="flex flex-col gap-1.5" aria-live="polite">
      <div className="flex gap-1.5" aria-hidden="true">
        {[1, 2, 3, 4].map((segment) => (
          <span
            key={segment}
            className={cn(
              'h-1.5 flex-1 rounded-full transition-colors duration-300',
              segment <= level ? SEGMENT_COLORS[level] : 'bg-line',
            )}
          />
        ))}
      </div>
      <p className="text-xs text-ink-soft">
        {level === 0
          ? 'Use at least 8 characters, with a letter and a digit.'
          : `Password strength: ${label}`}
      </p>
    </div>
  );
}
