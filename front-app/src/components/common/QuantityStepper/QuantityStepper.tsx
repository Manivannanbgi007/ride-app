import { Minus, Plus, Trash2 } from 'lucide-react';

import { Spinner } from '@/components/common/Loader';
import { Tooltip } from '@/components/common/Tooltip';
import { cn } from '@/utils/classNames';

interface QuantityStepperProps {
  quantity: number;
  max: number;
  /** Read out with the buttons: "Add one more Veg Biryani" */
  itemName: string;
  /** 0 = remove */
  onChange: (quantity: number) => void;
  /** While a change is being saved */
  isBusy?: boolean;
  className?: string;
}

/** − 2 + : how many of something. At 1 the minus button removes it. */
export function QuantityStepper({
  quantity,
  max,
  itemName,
  onChange,
  isBusy = false,
  className,
}: QuantityStepperProps) {
  const buttonClass =
    'flex size-8 items-center justify-center rounded-lg text-brand-text hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-40';
  const removeLabel = quantity === 1 ? `Remove ${itemName}` : `Remove one ${itemName}`;
  const RemoveIcon = quantity === 1 ? Trash2 : Minus;
  const addLabel = `Add one more ${itemName}`;
  return (
    <div
      role="group"
      aria-label={`Quantity of ${itemName}`}
      className={cn(
        'inline-flex items-center gap-1 rounded-xl border border-brand-500 bg-surface p-0.5',
        className,
      )}
    >
      <Tooltip label={removeLabel} icon={RemoveIcon} placement="top">
        <button
          type="button"
          disabled={isBusy}
          aria-label={removeLabel}
          onClick={() => onChange(quantity - 1)}
          className={buttonClass}
        >
          <RemoveIcon aria-hidden="true" className="size-4" />
        </button>
      </Tooltip>
      <span className="flex w-7 justify-center text-sm font-bold" aria-live="polite">
        {isBusy ? <Spinner size="sm" label="Saving" /> : quantity}
      </span>
      <Tooltip label={addLabel} icon={Plus} placement="top">
        <button
          type="button"
          disabled={isBusy || quantity >= max}
          aria-label={addLabel}
          onClick={() => onChange(quantity + 1)}
          className={buttonClass}
        >
          <Plus aria-hidden="true" className="size-4" />
        </button>
      </Tooltip>
    </div>
  );
}
