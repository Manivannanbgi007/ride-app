import { Loader2, Power } from 'lucide-react';

import { cn } from '@/utils/classNames';

interface OnlineSwitchProps {
  /** Online (AVAILABLE or on a delivery) */
  checked: boolean;
  disabled?: boolean;
  /** The change is on its way to the server */
  isLoading?: boolean;
  onChange: (online: boolean) => void;
}

/**
 * The big online/offline switch of the partner's hero: a real switch for screen readers ("Available for
 * deliveries", on or off), drawn as a pill with a sliding knob. Meant for a coloured background (white on colour).
 */
export function OnlineSwitch({
  checked,
  disabled = false,
  isLoading = false,
  onChange,
}: OnlineSwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label="Available for deliveries"
      disabled={disabled || isLoading}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-12 w-28 shrink-0 cursor-pointer items-center rounded-full p-1 transition-colors duration-300',
        'focus-visible:ring-4 focus-visible:ring-white/50 focus-visible:outline-none',
        'disabled:cursor-not-allowed disabled:opacity-60',
        checked ? 'bg-white shadow-lg shadow-black/20' : 'bg-white/20 ring-1 ring-white/40',
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'absolute text-[11px] font-extrabold tracking-wider uppercase transition-all duration-300',
          checked ? 'left-4 text-brand-700' : 'right-4 text-white/90',
        )}
      >
        {checked ? 'Online' : 'Offline'}
      </span>
      <span
        aria-hidden="true"
        className={cn(
          'relative z-10 flex size-10 items-center justify-center rounded-full shadow-md transition-all duration-300 ease-out',
          checked
            ? 'translate-x-16 bg-brand-500 text-on-brand'
            : 'translate-x-0 bg-white text-ink-soft',
        )}
      >
        {isLoading ? (
          <Loader2 className="size-5 animate-spin" />
        ) : (
          <Power className="size-5" strokeWidth={2.5} />
        )}
      </span>
    </button>
  );
}
