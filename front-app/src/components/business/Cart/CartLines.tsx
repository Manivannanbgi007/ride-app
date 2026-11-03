import { FoodTypeMark } from '@/components/business/Storefront';
import { QuantityStepper } from '@/components/common/QuantityStepper';
import type { CartLine } from '@/types/shopping';
import { cn } from '@/utils/classNames';
import { formatCurrency } from '@/utils/formatUtils';
import { MAX_QUANTITY_PER_DISH } from '@/utils/shoppingUtils';

interface CartLinesProps {
  lines: readonly CartLine[];
  currency: string;
  /** 0 = remove */
  onChangeQuantity: (menuItemId: number, quantity: number) => void;
  /** The dish being changed (its buttons wait) */
  busyItemId?: number;
}

/** The dishes in the cart; one that can no longer be ordered is marked, with only "remove" left. */
export function CartLines({ lines, currency, onChangeQuantity, busyItemId }: CartLinesProps) {
  return (
    <ul className="divide-y divide-line">
      {lines.map((line) => {
        const name = line.name ?? `Dish #${line.menuItemId}`;
        return (
          <li
            key={line.menuItemId}
            className={cn(
              'flex flex-wrap items-center gap-4 py-4',
              !line.orderable && 'opacity-70',
            )}
          >
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                {line.foodType && <FoodTypeMark foodType={line.foodType} />}
                <p className="font-semibold">{name}</p>
              </div>
              <p className="mt-0.5 text-sm text-ink-soft">
                {line.orderable
                  ? `${formatCurrency(line.unitPrice, currency)} each`
                  : 'Cannot be ordered right now: remove it to check out'}
              </p>
            </div>
            {line.orderable ? (
              <QuantityStepper
                quantity={line.quantity}
                max={MAX_QUANTITY_PER_DISH}
                itemName={name}
                isBusy={busyItemId === line.menuItemId}
                onChange={(quantity) => onChangeQuantity(line.menuItemId, quantity)}
              />
            ) : (
              <button
                type="button"
                onClick={() => onChangeQuantity(line.menuItemId, 0)}
                disabled={busyItemId === line.menuItemId}
                className="rounded-xl px-3 py-2 text-sm font-semibold text-danger-text hover:bg-danger-50"
              >
                Remove
              </button>
            )}
            <p className="w-24 text-right font-semibold">
              {line.lineTotal === null ? '—' : formatCurrency(line.lineTotal, currency)}
            </p>
          </li>
        );
      })}
    </ul>
  );
}
