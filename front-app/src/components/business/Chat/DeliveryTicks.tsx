import { Check, CheckCheck } from 'lucide-react';

/** One grey tick: sent. Two green ticks: read by the other side. */
export function DeliveryTicks({ read }: { read: boolean }) {
  return read ? (
    <span className="inline-flex text-brand-500">
      <CheckCheck aria-hidden="true" className="size-4" />
      <span className="sr-only">Read</span>
    </span>
  ) : (
    <span className="inline-flex text-ink-soft">
      <Check aria-hidden="true" className="size-4" />
      <span className="sr-only">Sent</span>
    </span>
  );
}
