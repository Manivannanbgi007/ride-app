import { createContext, useContext } from 'react';

export interface OrderActionDialogs {
  openHistory: (orderId: number) => void;
  openCancel: (orderId: number) => void;
}

export const OrderActionsContext = createContext<OrderActionDialogs | null>(null);

/** The dialogs of the order list's menus: `const { openHistory, openCancel } = useOrderActionDialogs();` */
export function useOrderActionDialogs(): OrderActionDialogs {
  const dialogs = useContext(OrderActionsContext);
  if (!dialogs) {
    throw new Error('Order menus need an <OrderActionsProvider> around them');
  }
  return dialogs;
}
