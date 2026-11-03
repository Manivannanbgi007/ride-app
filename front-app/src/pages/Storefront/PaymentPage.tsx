import { CheckCircle2, ShieldCheck } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { PriceSummary } from '@/components/business/Cart';
import { PaymentForm, type PaymentDetails } from '@/components/business/Checkout';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button, buttonStyles } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { Spinner, SkeletonLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useMyOrder } from '@/hooks/api/myOrderHooks';
import {
  usePayOrder,
  useRazorpayConfig,
  useStartRazorpayCheckout,
  useVerifyRazorpayPayment,
} from '@/hooks/api/paymentHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import type { OrderDetails } from '@/types/order';
import type { RazorpayVerifyRequest } from '@/types/payment';
import type { OnlinePaymentMethod } from '@/types/shopping';
import { customerRouteTo, PAYMENT_METHOD_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency } from '@/utils/formatUtils';
import {
  loadRazorpayCheckout,
  openRazorpayCheckout,
  RazorpayLoadError,
  type RazorpayCheckoutOutcome,
} from '@/utils/razorpayCheckout';
import { isPastPayment, keepsIdempotencyKey, paymentDeadline } from '@/utils/shoppingUtils';
import { newUuid } from '@/utils/idUtils';

/** How long the page waits for order-service to confirm a successful payment (it arrives through Kafka). */
const CONFIRMATION_WAIT_MS = 30_000;

/**
 * After paying: 'paid' = payment-service confirmed the payment; 'pending' = Razorpay reported it but it is not
 * verified yet (the webhook or a later check completes it). Either way the page waits for the order to move on.
 */
type Confirmation = 'paid' | 'pending';

/** Paying an order placed with UPI, card or wallet, then waiting until the order is confirmed. */
export default function PaymentPage() {
  const orderId = useIdParam('orderId');
  return orderId === null ? <NotFoundPage /> : <PaymentView orderId={orderId} />;
}

function PaymentView({ orderId }: { orderId: number }) {
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  // While a payment is being confirmed, the order is asked for every second
  const order = useMyOrder(orderId, confirmation ? 1000 : false);
  // Which gateway payment-service uses: Razorpay Checkout or the built-in simulated one
  const gateway = useRazorpayConfig();

  return (
    <div className="mx-auto flex max-w-column flex-col gap-6">
      <PageHeader title="Payment" subtitle={`Order #${orderId}`} />
      <AsyncContent
        isLoading={order.isPending || gateway.isPending}
        error={order.error ?? gateway.error}
        errorTitle={ERROR_MESSAGES.MY_ORDER_LOAD_FAILED}
        onRetry={() => {
          void order.refetch();
          void gateway.refetch();
        }}
        isRetrying={order.isFetching || gateway.isFetching}
        loading={<SkeletonLoader lines={6} className="rounded-2xl bg-surface p-6" />}
      >
        {order.data &&
          gateway.data &&
          (confirmation ? (
            <WaitingForConfirmation order={order.data} pending={confirmation === 'pending'} />
          ) : (
            <PayStep
              order={order.data}
              razorpay={gateway.data.enabled}
              testMode={gateway.data.testMode}
              onPaid={setConfirmation}
            />
          ))}
      </AsyncContent>
    </div>
  );
}

interface PayStepProps {
  order: OrderDetails;
  /** true: pay in Razorpay Checkout; false: the simulated gateway's form */
  razorpay: boolean;
  testMode: boolean;
  onPaid: (confirmation: Confirmation) => void;
}

function PayStep({ order, razorpay, testMode, onPaid }: PayStepProps) {
  if (order.status !== 'PAYMENT_PENDING') {
    return <NotWaitingForPayment order={order} />;
  }
  if (order.paymentMethod === 'CASH_ON_DELIVERY') {
    return (
      <Alert tone="info" title="Cash on delivery">
        This order is paid in cash when it arrives.{' '}
        <Link to={customerRouteTo.orderDetails(order.id)} className="font-semibold underline">
          See the order
        </Link>
      </Alert>
    );
  }
  const method: OnlinePaymentMethod = order.paymentMethod;
  return razorpay ? (
    <RazorpayPayment order={order} method={method} testMode={testMode} onPaid={onPaid} />
  ) : (
    <SimulatedPayment order={order} method={method} onPaid={() => onPaid('paid')} />
  );
}

/** The payment card next to the order's price summary. */
function PayLayout({
  order,
  title,
  children,
}: {
  order: OrderDetails;
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="grid gap-6 md:grid-cols-[1fr_18rem]">
      <Card title={title}>
        <div className="flex flex-col gap-4">
          {children}
          <p className="text-xs text-ink-soft">
            Pay by {formatDateTime(paymentDeadline(order).toISOString())}: an order not paid within
            15 minutes is cancelled.
          </p>
        </div>
      </Card>
      <Card title={order.restaurantName}>
        <PriceSummary price={order.price} couponCode={order.couponCode} />
      </Card>
    </div>
  );
}

/** The built-in simulated gateway (PAYMENT_PROVIDER=SIMULATED): the details are typed here. */
function SimulatedPayment({
  order,
  method,
  onPaid,
}: {
  order: OrderDetails;
  method: OnlinePaymentMethod;
  onPaid: () => void;
}) {
  const payOrder = usePayOrder();
  const [failure, setFailure] = useState<string | null>(null);
  // One key per payment attempt; a declined payment is retried with a new one (payment-service rule)
  const [idempotencyKey, setIdempotencyKey] = useState(() => newUuid());
  const money = formatCurrency(order.price.totalAmount, order.price.currency);

  const pay = async (details: PaymentDetails) => {
    setFailure(null);
    try {
      const payment = await payOrder.mutateAsync({
        request: { orderId: order.id, paymentMethod: method, ...details },
        idempotencyKey,
      });
      if (payment.status === 'FAILED') {
        setIdempotencyKey(newUuid());
        setFailure(payment.failureReason ?? 'The payment was declined.');
      } else {
        onPaid();
      }
    } catch (error) {
      if (!keepsIdempotencyKey(error)) {
        setIdempotencyKey(newUuid());
      }
      setFailure(toApiError(error).userMessage);
    }
  };

  return (
    <PayLayout order={order} title={`Pay with ${PAYMENT_METHOD_LABELS[method]}`}>
      {failure && (
        <Alert tone="error" title="The payment did not go through">
          {failure} You can try again.
        </Alert>
      )}
      <PaymentForm
        method={method}
        payLabel={`Pay ${money}`}
        isPaying={payOrder.isPending}
        onPay={pay}
      />
    </PayLayout>
  );
}

/** Where a Razorpay payment is: idle (ready to pay), starting (preparing Checkout), open, or verifying. */
type RazorpayStage = 'idle' | 'starting' | 'open' | 'verifying';

const STAGE_LABELS: Record<Exclude<RazorpayStage, 'idle'>, string> = {
  starting: 'Opening Razorpay...',
  open: 'Complete the payment in the Razorpay window',
  verifying: 'Confirming your payment...',
};

interface Notice {
  tone: 'info' | 'warning' | 'error';
  title: string;
  text: string;
}

/**
 * Razorpay Checkout (PAYMENT_PROVIDER=RAZORPAY): payment-service creates the Razorpay order for the order's total,
 * the customer pays in Razorpay's window, and payment-service verifies the result (signature + Razorpay's own record)
 * before the order counts as paid. What Checkout reports is never trusted on its own.
 */
function RazorpayPayment({
  order,
  method,
  testMode,
  onPaid,
}: {
  order: OrderDetails;
  method: OnlinePaymentMethod;
  testMode: boolean;
  onPaid: (confirmation: Confirmation) => void;
}) {
  const customer = useAppSelector(selectCustomer);
  const startCheckout = useStartRazorpayCheckout();
  const verifyPayment = useVerifyRazorpayPayment();
  const [stage, setStage] = useState<RazorpayStage>('idle');
  const [notice, setNotice] = useState<Notice | null>(null);
  // Checkout reported a payment that could not be verified yet (server unreachable): it can be checked again
  const [unverified, setUnverified] = useState<RazorpayVerifyRequest | null>(null);
  const money = formatCurrency(order.price.totalAmount, order.price.currency);

  const confirm = async (result: RazorpayVerifyRequest) => {
    setStage('verifying');
    setNotice(null);
    try {
      const payment = await verifyPayment.mutateAsync(result);
      setUnverified(null);
      onPaid(payment.status === 'SUCCESS' ? 'paid' : 'pending');
    } catch (error) {
      const apiError = toApiError(error);
      setStage('idle');
      if (apiError.kind === 'validation') {
        // The signature did not match: nothing was recorded as paid
        setUnverified(null);
        setNotice({
          tone: 'error',
          title: 'The payment could not be verified',
          text: apiError.userMessage,
        });
        return;
      }
      setUnverified(result);
      setNotice({
        tone: 'warning',
        title: 'Payment not confirmed yet',
        text: 'Razorpay reported your payment, but we could not confirm it just now. Check again in a moment: you will not be charged twice.',
      });
    }
  };

  const pay = async () => {
    setNotice(null);
    setStage('starting');
    let outcome: RazorpayCheckoutOutcome;
    try {
      const [session, Razorpay] = await Promise.all([
        startCheckout.mutateAsync(order.id),
        loadRazorpayCheckout(),
      ]);
      setStage('open');
      outcome = await openRazorpayCheckout(Razorpay, session, {
        name: customer?.fullName,
        email: customer?.email,
      });
    } catch (error) {
      setStage('idle');
      setNotice({
        tone: 'error',
        title: 'The payment could not start',
        text:
          error instanceof RazorpayLoadError
            ? 'Razorpay Checkout could not be loaded. Check your internet connection (or a script blocker) and try again.'
            : toApiError(error).userMessage,
      });
      return;
    }

    if (outcome.kind === 'paid') {
      await confirm(outcome.result);
      return;
    }
    setStage('idle');
    setNotice(
      outcome.kind === 'cancelled'
        ? {
            tone: 'info',
            title: 'Payment cancelled',
            text: 'You closed the Razorpay window before the payment finished. You can pay again.',
          }
        : {
            tone: 'error',
            title: 'The payment did not go through',
            text: `${outcome.reason} You can try again.`,
          },
    );
  };

  const busy = stage !== 'idle';
  return (
    <PayLayout order={order} title={`Pay with ${PAYMENT_METHOD_LABELS[method]}`}>
      {notice && (
        <Alert tone={notice.tone} title={notice.title}>
          {notice.text}
        </Alert>
      )}
      <p className="flex items-start gap-2 text-sm text-ink-soft">
        <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-500" />
        You pay in Razorpay&apos;s secure window. Card details and UPI PINs go to Razorpay only,
        never to this site.
      </p>
      {testMode && (
        <Alert tone="info" title="Razorpay Test Mode">
          No real money moves. Try UPI success@razorpay (or failure@razorpay), or a Razorpay test
          card.
        </Alert>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button onClick={() => void pay()} isLoading={busy}>
          {`Pay ${money}`}
        </Button>
        {unverified && !busy && (
          <Button variant="secondary" onClick={() => void confirm(unverified)}>
            Check payment again
          </Button>
        )}
      </div>
      {stage !== 'idle' && (
        <p className="text-sm text-ink-soft" role="status">
          {STAGE_LABELS[stage]}
        </p>
      )}
    </PayLayout>
  );
}

/** The order is past the payment step (paid already, cash, or cancelled). */
function NotWaitingForPayment({ order }: { order: OrderDetails }) {
  const link = (
    <Link to={customerRouteTo.orderDetails(order.id)} className="font-semibold underline">
      See the order
    </Link>
  );
  if (isPastPayment(order.status)) {
    return (
      <Alert tone="success" title="This order is paid">
        Nothing more to pay. {link}
      </Alert>
    );
  }
  return (
    <Alert tone="error" title="This order was cancelled">
      {order.cancellationReason ?? 'It can no longer be paid.'} {link}
    </Alert>
  );
}

/**
 * After paying: order-service confirms the order a moment later (usually within seconds). pending = the payment is
 * not verified yet (Razorpay reported it, payment-service could not check it yet): the webhook completes it.
 */
function WaitingForConfirmation({ order, pending }: { order: OrderDetails; pending: boolean }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [tookLong, setTookLong] = useState(false);
  const confirmed = isPastPayment(order.status);

  useEffect(() => {
    const timer = window.setTimeout(() => setTookLong(true), CONFIRMATION_WAIT_MS);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (confirmed) {
      showToast(`Payment received: order #${order.id} is placed.`);
      void navigate(customerRouteTo.orderDetails(order.id), { replace: true });
    }
  }, [confirmed, navigate, order.id, showToast]);

  let text = 'Confirming your order with the restaurant...';
  if (pending) {
    text =
      'Razorpay has not confirmed your payment yet. The order updates by itself; if the payment does not go through, any money taken is refunded automatically.';
  } else if (tookLong) {
    text =
      'Confirming the order is taking longer than usual. Your payment is safe; the order updates by itself.';
  }

  return (
    <Card>
      <div className="flex flex-col items-center gap-4 py-8 text-center" role="status">
        {tookLong && !pending ? (
          <CheckCircle2 aria-hidden="true" className="size-12 text-brand-500" />
        ) : (
          <Spinner size="lg" label="Confirming your order" />
        )}
        <h2 className="text-xl font-bold">
          {pending ? 'Confirming your payment' : 'Payment received'}
        </h2>
        <p className="max-w-md text-sm text-ink-soft">{text}</p>
        {tookLong && (
          <Link to={customerRouteTo.orderDetails(order.id)} className={buttonStyles()}>
            See the order
          </Link>
        )}
      </div>
    </Card>
  );
}
