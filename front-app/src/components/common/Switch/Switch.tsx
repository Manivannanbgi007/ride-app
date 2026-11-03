import { cn } from '@/utils/classNames';

export interface SwitchProps {
  /** Read out by screen readers ("Monday: open") */
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  /** Words next to the switch that repeat its state ("Open" / "Closed"), for people who do not tell the colours apart */
  stateText?: { on: string; off: string };
}

/** An on/off switch: a pill whose knob slides to the right (brand green) when on. */
export function Switch({ label, checked, onChange, disabled = false, stateText }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'inline-flex cursor-pointer items-center gap-2.5 rounded-full text-sm font-semibold',
        'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
        'disabled:cursor-not-allowed disabled:opacity-50',
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'relative inline-block h-6 w-11 shrink-0 rounded-full transition-colors duration-200',
          checked ? 'bg-brand-500' : 'bg-ink-soft/40',
        )}
      >
        <span
          className={cn(
            'absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow-sm transition-transform duration-200',
            checked && 'translate-x-5',
          )}
        />
      </span>
      {stateText && (
        <span aria-hidden="true" className={cn('w-14 text-left', !checked && 'text-ink-soft')}>
          {checked ? stateText.on : stateText.off}
        </span>
      )}
    </button>
  );
}
