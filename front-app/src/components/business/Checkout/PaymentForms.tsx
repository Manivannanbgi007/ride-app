import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import type { OnlinePaymentMethod, PayOrderRequest } from '@/types/shopping';
import { env } from '@/utils/env';
import {
  cardDigits,
  cardFormSchema,
  upiFormSchema,
  walletFormSchema,
  type CardForm,
  type UpiForm,
  type WalletForm,
} from '@/utils/validationUtils';

/** What the forms hand over: the method's own details (the order and method are added by the page). */
export type PaymentDetails = Pick<PayOrderRequest, 'card' | 'upiId' | 'walletId'>;

interface PaymentFormProps {
  method: OnlinePaymentMethod;
  /** "Pay ₹562.90" */
  payLabel: string;
  isPaying: boolean;
  onPay: (details: PaymentDetails) => Promise<void>;
}

const MONTHS = Array.from({ length: 12 }, (_, index) => {
  const month = String(index + 1).padStart(2, '0');
  return { value: String(index + 1), label: month };
});

function yearOptions(today: Date) {
  return Array.from({ length: 16 }, (_, index) => {
    const year = String(today.getFullYear() + index);
    return { value: year, label: year };
  });
}

/** The details the order's payment method needs (chosen at checkout; it cannot change here). */
export function PaymentForm(props: PaymentFormProps) {
  return (
    <div className="flex flex-col gap-4">
      {props.method === 'CARD' && <CardPaymentForm {...props} />}
      {props.method === 'UPI' && <UpiPaymentForm {...props} />}
      {props.method === 'WALLET' && <WalletPaymentForm {...props} />}
      {env.features.paymentTestHints && <TestHints method={props.method} />}
    </div>
  );
}

function CardPaymentForm({ payLabel, isPaying, onPay }: PaymentFormProps) {
  const today = new Date();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CardForm>({
    resolver: zodResolver(cardFormSchema(today)),
    defaultValues: { number: '', expiryMonth: '', expiryYear: '' },
  });
  const submit = handleSubmit(({ number, expiryMonth, expiryYear }) =>
    onPay({
      card: {
        number: cardDigits(number),
        expiryMonth: Number(expiryMonth),
        expiryYear: Number(expiryYear),
      },
    }),
  );
  return (
    <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-4">
      <Input
        label="Card number"
        inputMode="numeric"
        autoComplete="cc-number"
        placeholder="1234 5678 9012 3456"
        maxLength={23}
        error={errors.number?.message}
        {...register('number')}
      />
      <div className="grid grid-cols-2 gap-4">
        <Select
          label="Expiry month"
          placeholder="Month"
          autoComplete="cc-exp-month"
          options={MONTHS}
          error={errors.expiryMonth?.message}
          {...register('expiryMonth')}
        />
        <Select
          label="Expiry year"
          placeholder="Year"
          autoComplete="cc-exp-year"
          options={yearOptions(today)}
          error={errors.expiryYear?.message}
          {...register('expiryYear')}
        />
      </div>
      <Button type="submit" isLoading={isPaying}>
        {payLabel}
      </Button>
    </form>
  );
}

function UpiPaymentForm({ payLabel, isPaying, onPay }: PaymentFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<UpiForm>({ resolver: zodResolver(upiFormSchema), defaultValues: { upiId: '' } });
  const submit = handleSubmit(({ upiId }) => onPay({ upiId }));
  return (
    <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-4">
      <Input
        label="UPI id"
        placeholder="name@bank"
        autoCapitalize="none"
        maxLength={100}
        error={errors.upiId?.message}
        {...register('upiId')}
      />
      <Button type="submit" isLoading={isPaying}>
        {payLabel}
      </Button>
    </form>
  );
}

function WalletPaymentForm({ payLabel, isPaying, onPay }: PaymentFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<WalletForm>({
    resolver: zodResolver(walletFormSchema),
    defaultValues: { walletId: '' },
  });
  const submit = handleSubmit(({ walletId }) => onPay({ walletId }));
  return (
    <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-4">
      <Input
        label="Wallet id"
        maxLength={64}
        error={errors.walletId?.message}
        {...register('walletId')}
      />
      <Button type="submit" isLoading={isPaying}>
        {payLabel}
      </Button>
    </form>
  );
}

const HINTS: Record<OnlinePaymentMethod, string> = {
  CARD: 'Card 4111 1111 1111 1111 is paid; 4000 0000 0000 0002 is declined.',
  UPI: 'Any UPI id such as you@okbank is paid; fail@upi is declined.',
  WALLET: 'Any wallet id is paid; empty-wallet is declined.',
};

/** Not in production: the simulated provider's test values (payment-service has no real provider yet). */
function TestHints({ method }: { method: OnlinePaymentMethod }) {
  return (
    <p className="rounded-xl bg-warning-50 px-4 py-3 text-xs text-warning-text">
      <span className="font-semibold">Test payments ({env.environmentName}): </span>
      {HINTS[method]}
    </p>
  );
}
