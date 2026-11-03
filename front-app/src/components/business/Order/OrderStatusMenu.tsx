import { ChevronDown, CreditCard, ExternalLink, History, UserRound, XCircle } from 'lucide-react';
import { useNavigate } from 'react-router';

import { useOrderActionDialogs } from '@/components/business/Order/orderActionsContext';
import { orderActionsFor, type OrderAction } from '@/components/business/Order/orderActions';
import { OrderStatusBadge } from '@/components/business/StatusBadges';
import { ActionMenu, type ActionMenuItem } from '@/components/common/ActionMenu';
import { useActingUser } from '@/hooks/useActingUser';
import { useAppDispatch } from '@/store/hooks';
import { paymentFiltersReplaced } from '@/store/slices/paymentListSlice';
import type { OrderSummary } from '@/types/order';
import { ROUTES, routeTo } from '@/utils/constants';

/** What this admin can do with the order, as menu items (nothing for anybody who may not). */
function useOrderMenuItems(order: OrderSummary): ActionMenuItem[] {
  const user = useActingUser();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { openHistory, openCancel } = useOrderActionDialogs();

  const items: Record<OrderAction, ActionMenuItem> = {
    open: {
      key: 'open',
      label: 'Open order',
      icon: ExternalLink,
      to: routeTo.orderDetails(order.id),
    },
    history: {
      key: 'history',
      label: 'History',
      icon: History,
      onSelect: () => openHistory(order.id),
    },
    payments: {
      key: 'payments',
      label: 'Payments',
      icon: CreditCard,
      onSelect: () => {
        dispatch(paymentFiltersReplaced({ orderId: order.id }));
        void navigate(ROUTES.payments);
      },
    },
    customer: {
      key: 'customer',
      label: 'Customer',
      icon: UserRound,
      to: routeTo.customerDetails(order.customerId),
    },
    cancel: {
      key: 'cancel',
      label: 'Cancel order',
      icon: XCircle,
      tone: 'danger',
      onSelect: () => openCancel(order.id),
    },
  };
  return orderActionsFor(order, user?.role).map((action) => items[action]);
}

/**
 * The "Status" column of the order list: the order's status as a highlighted badge that also opens the order's
 * action menu (see OrderActionsMenu). For somebody with no actions it is just the badge.
 */
export function OrderStatusMenu({ order }: { order: OrderSummary }) {
  const items = useOrderMenuItems(order);
  const badge = <OrderStatusBadge status={order.status} />;
  if (items.length === 0) {
    return badge;
  }
  return (
    <ActionMenu
      label={`Actions for order #${order.id}`}
      title={`Order #${order.id}`}
      items={items}
      dots={false}
    >
      <span className="inline-flex items-center gap-1">
        {badge}
        <ChevronDown
          aria-hidden="true"
          className="size-3.5 text-ink-soft transition-transform duration-200 group-aria-expanded/trigger:rotate-180"
        />
      </span>
    </ActionMenu>
  );
}

/**
 * The "Actions" column of the order list: the "..." button, with the same menu as the status badge: open the order,
 * read its history, see its payments, open its customer, and cancel it while that is still possible. Nothing at all
 * for somebody with no actions.
 */
export function OrderActionsMenu({ order }: { order: OrderSummary }) {
  const items = useOrderMenuItems(order);
  if (items.length === 0) {
    return null;
  }
  return (
    <ActionMenu
      label={`Actions for order #${order.id}`}
      title={`Order #${order.id}`}
      items={items}
    />
  );
}
