import { ClipboardCheck, Clock } from 'lucide-react';

import { AssistantLinkButton } from '@/components/business/Assistant/AssistantLinkButton';
import { useProposalButtons } from '@/components/business/Assistant/useProposalButtons';
import { Alert } from '@/components/common/Alert';
import { Button } from '@/components/common/Button';
import type { ChangePendingAction } from '@/types/assistant';
import { formatCountdown } from '@/utils/assistantUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

interface ActionProposalCardProps {
  action: ChangePendingAction;
}

/**
 * A change the assistant prepared (cancel an order, accept it, mark a dish sold out, mark a delivery picked up,
 * reassign a delivery...), for the user to check. Nothing changes until the Confirm button is pressed; the request
 * runs out after a few minutes. The card shows only what the server sent (title, rows, note, button text).
 */
export function ActionProposalCard({ action }: ActionProposalCardProps) {
  const { resolution, remainingMs, expired, pending, problem, run } = useProposalButtons(
    action,
    'change',
  );
  const { details } = action;

  return (
    <section
      aria-label="Change to confirm"
      className="rounded-xl bg-surface p-3 ring-2 ring-brand-500/40"
    >
      <h3 className="mb-2 flex items-start gap-2 text-sm font-semibold [overflow-wrap:anywhere] text-ink">
        <ClipboardCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-text" />
        {details.title}
      </h3>

      {details.rows.length > 0 && (
        <dl className="mb-2 flex flex-col gap-1 border-t border-line pt-2 text-xs">
          {details.rows.map((row, index) => (
            <div key={`${row.label}-${index}`} className="flex flex-col">
              <dt className="text-ink-soft">{row.label}</dt>
              <dd className="[overflow-wrap:anywhere] text-ink">{row.value}</dd>
            </div>
          ))}
        </dl>
      )}

      {details.note && (
        <Alert tone="info" className="mb-2">
          {details.note}
        </Alert>
      )}

      {resolution?.status === 'confirmed' ? (
        <div className="flex flex-col gap-2">
          <Alert tone="success">{resolution.message}</Alert>
          {resolution.link && <AssistantLinkButton link={resolution.link} variant="primary" />}
        </div>
      ) : resolution?.status === 'cancelled' ? (
        <Alert tone="info">Dismissed. Nothing was changed.</Alert>
      ) : resolution?.status === 'failed' ? (
        <Alert tone="error">{resolution.message}</Alert>
      ) : (
        <div className="flex flex-col gap-2">
          {problem && <Alert tone="error">{problem}</Alert>}
          {expired ? (
            <Alert tone="warning">{ERROR_MESSAGES.ASSISTANT_CHANGE_EXPIRED}</Alert>
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
              {details.confirmLabel}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => void run('cancel')}
              disabled={expired || pending !== null}
              isLoading={pending === 'cancel'}
            >
              Not now
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}
