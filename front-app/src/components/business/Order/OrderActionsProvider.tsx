import { useMemo, useState, type ReactNode } from 'react';

import { OrderHistoryDialog } from '@/components/business/Order/OrderHistoryDialog';
import {
  OrderActionsContext,
  type OrderActionDialogs,
} from '@/components/business/Order/orderActionsContext';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import { useCancelOrder } from '@/hooks/api/orderHooks';

type OpenDialog = { kind: 'history' | 'cancel'; orderId: number } | null;

/**
 * The history and cancel dialogs of the order list, once for the whole table: a row's status and its "..." button
 * (two columns) ask for them by order number, and only one is open at a time. Cancelling asks for a reason first
 * (a confirmation), is audited by the order service and refunds an order paid online; a refusal from the server is
 * shown in the dialog, which stays open.
 */
export function OrderActionsProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState<OpenDialog>(null);
  const { showToast } = useToast();
  const cancelOrder = useCancelOrder();

  const dialogs = useMemo<OrderActionDialogs>(
    () => ({
      openHistory: (orderId) => setOpen({ kind: 'history', orderId }),
      openCancel: (orderId) => setOpen({ kind: 'cancel', orderId }),
    }),
    [],
  );
  const close = () => setOpen(null);
  const orderId = open?.orderId ?? 0;

  return (
    <OrderActionsContext.Provider value={dialogs}>
      {children}
      {open?.kind === 'history' && <OrderHistoryDialog orderId={orderId} open onClose={close} />}
      {open?.kind === 'cancel' && (
        <ReasonDialog
          open
          title={`Cancel order #${orderId}?`}
          description="The customer and the restaurant are told, and an order paid online is refunded in full. This cannot be undone."
          confirmLabel="Cancel order"
          tone="danger"
          onSubmit={async (reason) => {
            await cancelOrder.mutateAsync({ orderId, reason });
            showToast(`Order #${orderId} was cancelled.`);
          }}
          onClose={close}
        />
      )}
    </OrderActionsContext.Provider>
  );
}
