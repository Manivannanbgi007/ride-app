import type { ReactNode } from 'react';

import { Card } from '@/components/common/Card';
import { Table, type TableColumn } from '@/components/common/Table';
import type { OrderDetails, OrderLine } from '@/types/order';
import { formatCurrency } from '@/utils/formatUtils';

/**
 * What was ordered, at the prices of the moment it was ordered, and how the total is made up. `itemPhoto` puts a
 * picture before each dish's name (the restaurant's page shows its menu's photos).
 */
export function OrderItemsCard({
  order,
  itemPhoto,
}: {
  order: OrderDetails;
  itemPhoto?: (line: OrderLine) => ReactNode;
}) {
  const { price } = order;
  const money = (amount: number) => formatCurrency(amount, price.currency);
  const columns: readonly TableColumn<OrderLine>[] = [
    {
      header: 'Item',
      render: (line) =>
        itemPhoto ? (
          <span className="flex items-center gap-3">
            {itemPhoto(line)}
            <span className="font-medium">{line.name}</span>
          </span>
        ) : (
          <span className="font-medium">{line.name}</span>
        ),
    },
    { header: 'Price', align: 'right', render: (line) => money(line.unitPrice) },
    { header: 'Qty', align: 'right', render: (line) => line.quantity },
    {
      header: 'Total',
      align: 'right',
      render: (line) => <span className="font-semibold">{money(line.lineTotal)}</span>,
    },
  ];

  return (
    <Card title="Items" subtitle={`${order.items.length} item(s) from ${order.restaurantName}`}>
      <Table
        caption="Ordered items"
        columns={columns}
        rows={order.items}
        rowKey={(line) => line.menuItemId}
      />
      <dl className="mt-4 ml-auto flex max-w-xs flex-col gap-2 text-sm">
        <PriceRow label="Items" value={money(price.itemsTotal)} />
        {price.discountAmount > 0 && (
          <PriceRow
            label={`Coupon ${order.couponCode ?? ''}`.trim()}
            value={`- ${money(price.discountAmount)}`}
          />
        )}
        <PriceRow label="Delivery fee" value={money(price.deliveryFee)} />
        <PriceRow label="Tax" value={money(price.taxAmount)} />
        <div className="flex justify-between border-t border-line pt-2 text-base font-bold">
          <dt>Total</dt>
          <dd>{money(price.totalAmount)}</dd>
        </div>
      </dl>
    </Card>
  );
}

function PriceRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <dt className="text-ink-soft">{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
