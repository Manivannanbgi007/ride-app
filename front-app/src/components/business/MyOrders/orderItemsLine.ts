import type { OrderSummary, OrderSummaryItem } from '@/types/order';

/** How many dishes are named before "+2 more". */
const ITEMS_NAMED = 3;

/** "2 × Chicken Biryani, 1 × Malai Kulfi +1 more" */
function itemsLine(items: readonly OrderSummaryItem[]): string {
  const named = items
    .slice(0, ITEMS_NAMED)
    .map((item) => `${item.quantity} × ${item.name}`)
    .join(', ');
  const more = items.length - ITEMS_NAMED;
  return more > 0 ? `${named} +${more} more` : named;
}

/** What an order held, in one line: its dishes when the list has them, otherwise how many ("3 dishes"). */
export function orderItemsLine(order: Pick<OrderSummary, 'items' | 'itemCount'>): string {
  const items = order.items ?? [];
  return items.length > 0
    ? itemsLine(items)
    : `${order.itemCount} ${order.itemCount === 1 ? 'dish' : 'dishes'}`;
}
