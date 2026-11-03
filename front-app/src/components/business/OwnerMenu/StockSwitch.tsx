import { cn } from '@/utils/classNames';

interface StockSwitchProps {
  /** The dish this switch is about (read out by screen readers) */
  dishName: string;
  /** true = in stock; false = sold out for today */
  inStock: boolean;
  disabled?: boolean;
  /** Called with the stock the owner asks for (the opposite of `inStock`) */
  onChange: (inStock: boolean) => void;
}

/** The quick "In stock / Sold out" switch of a dish: the dish stays on the menu either way. */
export function StockSwitch({ dishName, inStock, disabled = false, onChange }: StockSwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={inStock}
      aria-label={`In stock: ${dishName}`}
      disabled={disabled}
      onClick={() => onChange(!inStock)}
      className={cn(
        'group/switch inline-flex h-9 cursor-pointer items-center gap-2.5 rounded-xl pr-1 text-sm font-semibold',
        'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
        'disabled:cursor-not-allowed disabled:opacity-50',
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'relative inline-block h-6 w-11 shrink-0 rounded-full transition-colors duration-200',
          inStock ? 'bg-brand-500' : 'bg-ink-soft/50',
        )}
      >
        <span
          className={cn(
            'absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow-sm transition-transform duration-200',
            inStock && 'translate-x-5',
          )}
        />
      </span>
      {/* The words repeat the state for people who do not tell the two colours apart */}
      <span aria-hidden="true" className="w-16 text-left">
        {inStock ? 'In stock' : 'Sold out'}
      </span>
    </button>
  );
}
