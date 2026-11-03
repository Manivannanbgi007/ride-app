import { Banknote, CreditCard, Smartphone, Wallet, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import type { PaymentMethod } from '@/types/order';
import { cn } from '@/utils/classNames';
import { PAYMENT_METHOD_LABELS } from '@/utils/constants';
import { PALETTE, type PaletteColour } from '@/utils/palette';

/** Each method's icon and colour, so a list can be scanned by method at a glance. */
const METHOD_LOOKS: Record<PaymentMethod, { icon: LucideIcon; colour: PaletteColour }> = {
  CASH_ON_DELIVERY: { icon: Banknote, colour: 'amber' },
  CARD: { icon: CreditCard, colour: 'violet' },
  UPI: { icon: Smartphone, colour: 'sky' },
  WALLET: { icon: Wallet, colour: 'teal' },
};

interface PaymentMethodLabelProps {
  method: PaymentMethod;
  /** Under the name: the card or account, or the payment's status */
  children?: ReactNode;
}

/**
 * A payment method in a list row: its icon in a small tile of the method's colour (it grows a little while the row
 * is pointed at), its name, and what is given as children under it.
 */
export function PaymentMethodLabel({ method, children }: PaymentMethodLabelProps) {
  const { icon: Icon, colour } = METHOD_LOOKS[method];
  return (
    <span className="flex min-w-0 items-center gap-2.5">
      <span
        aria-hidden="true"
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-xl transition-transform duration-200 motion-safe:[tr:hover_&]:scale-110',
          PALETTE[colour].soft,
          PALETTE[colour].text,
        )}
      >
        <Icon className="size-4" />
      </span>
      <span className="flex min-w-0 flex-col items-start gap-1">
        <span className="leading-tight font-semibold whitespace-nowrap">
          {PAYMENT_METHOD_LABELS[method]}
        </span>
        {children}
      </span>
    </span>
  );
}
