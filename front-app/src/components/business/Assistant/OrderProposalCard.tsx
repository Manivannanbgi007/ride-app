import { Clock, ShoppingBag } from 'lucide-react';

import { AssistantLinkButton } from '@/components/business/Assistant/AssistantLinkButton';
import { useProposalButtons } from '@/components/business/Assistant/useProposalButtons';
import { Alert } from '@/components/common/Alert';
import { Button } from '@/components/common/Button';
import type { OrderPendingAction } from '@/types/assistant';
import { formatCountdown } from '@/utils/assistantUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatCurrency } from '@/utils/formatUtils';

interface OrderProposalCardProps {
  action: OrderPendingAction;
}

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div
      className={`flex justify-between gap-3 ${strong ? 'font-bold text-ink' : 'text-ink-soft'}`}
    >
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  );
}

/**
 * The order the assistant prepared, for the user to check. Nothing is ordered until "Confirm order" is pressed;
 * the proposal runs out after a few minutes. How it ended is kept by the provider, so closing the panel or moving
 * to another page never brings the buttons back.
 */
export function OrderProposalCard({ action }: OrderProposalCardProps) {
  const { resolution, remainingMs, expired, pending, problem, run } = useProposalButtons(
    action,
    'order',
  );

  const { summary } = action;
  const currency = summary.currency;
  const money = (value: number | null) => formatCurrency(value, currency);

  return (
    <section
      aria-label="Order summary"
      className="rounded-xl bg-surface p-3 ring-2 ring-brand-500/40"
    >
      <h3 className="mb-1 flex items-center gap-2 text-sm font-semibold text-ink">
        <ShoppingBag aria-hidden="true" className="size-4 text-brand-text" />
        Order summary
      </h3>
      <p className="mb-2 text-sm font-medium [overflow-wrap:anywhere] text-ink">
        {summary.restaurantName}
      </p>

      <ul className="mb-2 flex flex-col gap-1 text-sm">
        {summary.items.map((item, index) => (
          <li key={index} className="flex justify-between gap-3">
            <span className="[overflow-wrap:anywhere] text-ink">{item.name}</span>
            <span className="shrink-0 text-ink-soft tabular-nums">
              {item.quantity} x {money(item.unitPrice)}
              {item.lineTotal !== null && ` = ${money(item.lineTotal)}`}
            </span>
          </li>
        ))}
      </ul>

      <dl className="mb-2 flex flex-col gap-0.5 border-t border-line pt-2 text-xs">
        {summary.deliveryAddress && (
          <div className="flex flex-col">
            <dt className="text-ink-soft">Deliver to</dt>
            <dd className="[overflow-wrap:anywhere] text-ink">{summary.deliveryAddress}</dd>
          </div>
        )}
        {summary.paymentMethod && (
          <div className="flex flex-col">
            <dt className="text-ink-soft">Payment</dt>
            <dd className="text-ink">{summary.paymentMethod}</dd>
          </div>
        )}
        {summary.notes && (
          <div className="flex flex-col">
            <dt className="text-ink-soft">Notes</dt>
            <dd className="[overflow-wrap:anywhere] text-ink">{summary.notes}</dd>
          </div>
        )}
      </dl>

      <dl className="mb-2 flex flex-col gap-0.5 border-t border-line pt-2 text-sm">
        {summary.itemsTotal !== null && <Row label="Subtotal" value={money(summary.itemsTotal)} />}
        {summary.discount !== null && summary.discount !== 0 && (
          <Row label="Discount" value={`- ${money(Math.abs(summary.discount))}`} />
        )}
        {summary.deliveryFee !== null && (
          <Row label="Delivery" value={money(summary.deliveryFee)} />
        )}
        {summary.tax !== null && <Row label="Tax" value={money(summary.tax)} />}
        {summary.total !== null && <Row label="Total" value={money(summary.total)} strong />}
      </dl>

      {summary.warnings.length > 0 && (
        <Alert tone="warning" className="mb-2">
          <ul className="flex flex-col gap-1">
            {summary.warnings.map((warning, index) => (
              <li key={index}>{warning}</li>
            ))}
          </ul>
        </Alert>
      )}

      {resolution?.status === 'confirmed' ? (
        <div className="flex flex-col gap-2">
          <Alert tone="success">{resolution.message}</Alert>
          {resolution.link && <AssistantLinkButton link={resolution.link} variant="primary" />}
        </div>
      ) : resolution?.status === 'cancelled' ? (
        <Alert tone="info">Order request cancelled.</Alert>
      ) : resolution?.status === 'failed' ? (
        <Alert tone="error">{resolution.message}</Alert>
      ) : (
        <div className="flex flex-col gap-2">
          {problem && <Alert tone="error">{problem}</Alert>}
          {expired ? (
            <Alert tone="warning">{ERROR_MESSAGES.ASSISTANT_ORDER_EXPIRED}</Alert>
          ) : (
            <p className="flex items-center gap-1.5 text-xs text-ink-soft">
              <Clock aria-hidden="true" className="size-3.5" />
              <span>expires in {formatCountdown(remainingMs)}</span>
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              onClick={() => void run('confirm')}
              disabled={expired || pending !== null}
              isLoading={pending === 'confirm'}
            >
              Confirm order
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => void run('cancel')}
              disabled={expired || pending !== null}
              isLoading={pending === 'cancel'}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}
