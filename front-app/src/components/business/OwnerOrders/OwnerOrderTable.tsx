import {
  ArrowRight,
  Banknote,
  ChevronDown,
  CircleCheckBig,
  CircleX,
  Clock3,
  CreditCard,
  MapPin,
  MessageSquareText,
  Phone,
  ShoppingBag,
  Smartphone,
  UserRound,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { useId, useState, type ReactNode } from 'react';
import { Link } from 'react-router';

import { DishPhoto } from '@/components/business/OwnerOrders/DishPhoto';
import {
  avatarToneOf,
  ORDER_STATUS_TONES,
  type StatusTone,
} from '@/components/business/OwnerOrders/orderStatusTones';
import { OwnerOrderActions } from '@/components/business/OwnerOrders/OwnerOrderActions';
import {
  deliveryTimes,
  formatAddress,
  ownerOrderActions,
  phoneDigits,
} from '@/components/business/OwnerOrders/ownerOrderUtils';
import { useDishLooks, type DishLook } from '@/components/business/OwnerOrders/useDishLooks';
import { PaymentStatusBadge } from '@/components/business/StatusBadges';
import { FoodTypeMark } from '@/components/business/Storefront';
import { Alert } from '@/components/common/Alert';
import { buttonStyles } from '@/components/common/Button';
import { Skeleton } from '@/components/common/Loader';
import { useOwnerOrder } from '@/hooks/api/ownerHooks';
import { useNow } from '@/hooks/useNow';
import type { FoodType } from '@/types/catalog';
import type {
  OrderDetails,
  OrderStatus,
  OrderSummary,
  OrderSummaryItem,
  PaymentMethod,
} from '@/types/order';
import { cn } from '@/utils/classNames';
import {
  ACTOR_TYPE_LABELS,
  DEFAULT_CURRENCY,
  ORDER_STATUS_LABELS,
  ownerRouteTo,
  PAYMENT_METHOD_LABELS,
} from '@/utils/constants';
import {
  formatDateTime,
  formatInstantDate,
  formatRelativeTime,
  formatTime,
} from '@/utils/dateUtils';
import { formatCurrency, formatMinutes, initialsOf } from '@/utils/formatUtils';

/** Orders the kitchen is still working on: their waiting time matters more than their date. */
const FINISHED: readonly OrderStatus[] = ['DELIVERED', 'CANCELLED', 'REJECTED'];

const PAYMENT_ICONS: Record<PaymentMethod, LucideIcon> = {
  CASH_ON_DELIVERY: Banknote,
  CARD: CreditCard,
  UPI: Smartphone,
  WALLET: Wallet,
};

/**
 * The columns. On a wide screen one line per order under a header, lined up so orders can be compared at a glance
 * (totals right-aligned, digits of equal width); the last column holds the kitchen's next step (when the list has
 * one) and the details button. Below that the same cells stack into a card: order and total first, then the
 * customer, then the facts with the buttons (beside them on a tablet, under them on a phone).
 */
const COLUMNS = {
  plain:
    'xl:grid-cols-[6.5rem_minmax(0,1.6fr)_minmax(0,1.15fr)_5.5rem_minmax(0,1.3fr)_7.5rem_2.75rem]',
  // Room for two step buttons side by side (Accept, Reject) and the details button
  withActions:
    'xl:grid-cols-[6rem_minmax(0,1.6fr)_minmax(0,0.95fr)_5.5rem_minmax(0,1.1fr)_6.5rem_17.5rem]',
} as const;
/** The same cells, with the ordered dishes (photo and name) under the customer, on their own line on a wide screen. */
const ROW_AREAS = {
  plain: cn(
    "grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 gap-y-3 [grid-template-areas:'order_total'_'customer_customer'_'meta_meta'_'end_end']",
    "sm:items-center sm:[grid-template-areas:'order_total'_'customer_customer'_'meta_end']",
    "xl:gap-x-5 xl:[grid-template-areas:'order_customer_placed_items_payment_total_end']",
  ),
  withDishes: cn(
    "grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 gap-y-3 [grid-template-areas:'order_total'_'customer_customer'_'dishes_dishes'_'meta_meta'_'end_end']",
    "sm:items-center sm:[grid-template-areas:'order_total'_'customer_customer'_'dishes_dishes'_'meta_end']",
    "xl:gap-x-5 xl:[grid-template-areas:'order_customer_placed_items_payment_total_end'_'._dishes_dishes_dishes_dishes_dishes_dishes']",
  ),
} as const;

/** How many dishes a row shows with their photo; the rest are counted ("+2 more") and listed in the details. */
const DISHES_SHOWN = 4;

interface OwnerOrderTableProps {
  restaurantId: number;
  orders: readonly OrderSummary[];
  /** What the list is called for screen readers ("New orders") */
  label: string;
  /** Another page is loading: the current rows are dimmed meanwhile */
  isUpdating?: boolean;
}

/**
 * The restaurant's orders of one status, one row each: who ordered, when, what it came to, how it was paid, and the
 * kitchen's next step where there is one. What was in an order, its customer and where it is now open below its
 * row, read when asked for (only that order is loaded).
 */
export function OwnerOrderTable({
  restaurantId,
  orders,
  label,
  isUpdating = false,
}: OwnerOrderTableProps) {
  // "12 minutes ago" keeps counting while the owner looks at the page
  const now = useNow();
  // The dishes' pictures come from the restaurant's menu (one request for the whole list)
  const looks = useDishLooks(restaurantId);
  const columns = orders.some((order) => ownerOrderActions(order.status).length > 0)
    ? COLUMNS.withActions
    : COLUMNS.plain;

  return (
    <div className={cn('flex flex-col gap-3 transition-opacity', isUpdating && 'opacity-60')}>
      {/* Column headings for the eye; each cell also names itself for screen readers */}
      <div
        aria-hidden="true"
        className={cn(
          'hidden gap-x-5 pr-5 pl-7 text-xs font-semibold tracking-wide text-ink-soft uppercase xl:grid',
          columns,
        )}
      >
        <span>Order</span>
        <span>Customer</span>
        <span>Placed</span>
        <span>Items</span>
        <span>Payment</span>
        <span className="text-right">Total</span>
        <span />
      </div>
      <ul aria-label={label} className="flex flex-col gap-3">
        {orders.map((order) => (
          <OwnerOrderRow
            key={order.id}
            order={order}
            restaurantId={restaurantId}
            columns={columns}
            now={now}
            looks={looks}
          />
        ))}
      </ul>
    </div>
  );
}

function OwnerOrderRow({
  order,
  restaurantId,
  columns,
  now,
  looks,
}: {
  order: OrderSummary;
  restaurantId: number;
  columns: string;
  now: number;
  looks: ReadonlyMap<number, DishLook>;
}) {
  const [open, setOpen] = useState(false);
  const detailsId = useId();
  const customer = order.customerName?.trim() || null;
  const finished = FINISHED.includes(order.status);
  const tone = ORDER_STATUS_TONES[order.status];
  const PaymentIcon = PAYMENT_ICONS[order.paymentMethod];
  const dishes = order.items ?? [];

  return (
    <li
      className={cn(
        'relative overflow-hidden rounded-2xl bg-surface shadow-card ring-1 transition-[box-shadow,translate,--tw-ring-color] duration-200',
        open
          ? cn('ring-2', tone.ring)
          : 'ring-line/70 hover:shadow-lg motion-safe:hover:-translate-y-0.5',
      )}
    >
      {/* The status's colour down the left edge: the rows of one status look alike at a glance */}
      <span aria-hidden="true" className={cn('absolute inset-y-0 left-0 w-1.5', tone.stripe)} />
      <div
        className={cn(
          dishes.length > 0 ? ROW_AREAS.withDishes : ROW_AREAS.plain,
          columns,
          'p-4 pl-6 sm:pr-5 sm:pl-7',
        )}
      >
        <div className="flex flex-wrap items-center gap-2 [grid-area:order] xl:flex-col xl:items-start xl:gap-1.5">
          <Link
            to={ownerRouteTo.orderDetails(order.id)}
            className={cn(
              'rounded-md text-lg font-extrabold tabular-nums hover:underline focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
              tone.text,
            )}
          >
            #{order.id}
          </Link>
          <span
            className={cn(
              'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs leading-none font-semibold whitespace-nowrap',
              tone.soft,
              tone.text,
            )}
          >
            <span aria-hidden="true" className={cn('size-1.5 rounded-full', tone.stripe)} />
            {ORDER_STATUS_LABELS[order.status]}
          </span>
        </div>

        <div className="flex min-w-0 items-center gap-3 [grid-area:customer]">
          <span
            aria-hidden="true"
            className={cn(
              'flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white shadow-sm ring-2 ring-surface',
              avatarToneOf(order.customerId),
            )}
          >
            {customer ? initialsOf(customer) : <UserRound className="size-5" />}
          </span>
          <p className="min-w-0">
            <span className="sr-only">Customer: </span>
            {customer ? (
              <span title={customer} className="line-clamp-2 font-semibold break-words text-ink">
                {customer}
              </span>
            ) : (
              <span className="text-ink-soft">Customer #{order.customerId}</span>
            )}
          </p>
        </div>

        {dishes.length > 0 && (
          <OrderDishes orderId={order.id} dishes={dishes} looks={looks} tone={tone} />
        )}

        {/* On a phone the facts share a line; on a wide screen each one is its own column */}
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm [grid-area:meta] xl:contents">
          <p className="flex items-center gap-2 xl:items-start xl:[grid-area:placed]">
            <Clock3 aria-hidden="true" className={cn('size-4 shrink-0 xl:mt-0.5', tone.text)} />
            <span className="sr-only">Placed: </span>
            <PlacedAt createdAt={order.createdAt} finished={finished} now={now} />
          </p>
          <p className="flex items-center gap-2 xl:[grid-area:items]">
            <ShoppingBag aria-hidden="true" className={cn('size-4 shrink-0', tone.text)} />
            <span className="sr-only">Items: </span>
            <span className="text-ink tabular-nums">
              {order.itemCount === 1 ? '1 item' : `${order.itemCount} items`}
            </span>
          </p>
          <p className="flex flex-wrap items-center gap-2 xl:[grid-area:payment]">
            <span className="sr-only">Payment: </span>
            <PaymentIcon aria-hidden="true" className={cn('size-4 shrink-0', tone.text)} />
            <span className="text-ink">{PAYMENT_METHOD_LABELS[order.paymentMethod]}</span>
            <PaymentStatusBadge status={order.paymentStatus} />
          </p>
        </div>

        <p className="text-right [grid-area:total]">
          <span className="sr-only">Total: </span>
          <span className="text-xl font-extrabold text-ink tabular-nums">
            {formatCurrency(order.totalAmount, DEFAULT_CURRENCY)}
          </span>
        </p>

        {/* The next step (if any) and the details: full width to tap on a phone, on the right from a tablet up */}
        <div className="flex flex-col gap-2 [grid-area:end] sm:flex-row sm:items-center sm:justify-end">
          <OwnerOrderActions orderId={order.id} status={order.status} />
          <button
            type="button"
            aria-expanded={open}
            aria-controls={detailsId}
            aria-label={`${open ? 'Hide' : 'Show'} the details of order #${order.id}`}
            onClick={() => setOpen((value) => !value)}
            className={cn(
              'flex w-full shrink-0 cursor-pointer items-center justify-center gap-2 rounded-xl py-2 text-sm font-semibold ring-1 transition-colors duration-200',
              'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
              'sm:w-auto sm:px-4 xl:size-10 xl:rounded-full xl:p-0',
              open
                ? cn(tone.soft, tone.text, 'ring-transparent')
                : cn('bg-page text-ink ring-line', tone.hover),
            )}
          >
            <span className="xl:hidden">{open ? 'Hide details' : 'Details'}</span>
            <ChevronDown
              aria-hidden="true"
              className={cn('size-4 transition-transform duration-200', open && 'rotate-180')}
            />
          </button>
        </div>
      </div>

      {open && (
        <div id={detailsId} className="border-t border-line p-4 pl-6 sm:py-5 sm:pr-5 sm:pl-7">
          <OrderDetailsPanel restaurantId={restaurantId} orderId={order.id} looks={looks} />
        </div>
      )}
    </li>
  );
}

/** Orders in the kitchen: how long ago first (a waiting customer); finished ones: the day first. */
function PlacedAt({
  createdAt,
  finished,
  now,
}: {
  createdAt: string;
  finished: boolean;
  now: number;
}) {
  const relative = formatRelativeTime(createdAt, new Date(now));
  return (
    <time
      dateTime={createdAt}
      title={formatDateTime(createdAt)}
      className="flex flex-wrap gap-x-1.5 xl:flex-col"
    >
      {finished ? (
        <>
          <span className="font-medium text-ink">{formatInstantDate(createdAt)}</span>
          <span className="text-ink-soft xl:text-xs">
            {formatTime(createdAt)}
            <span className="hidden 2xl:inline"> · {relative}</span>
          </span>
        </>
      ) : (
        <>
          <span className="font-semibold text-ink">{relative}</span>
          <span className="text-ink-soft xl:text-xs">{formatTime(createdAt)}</span>
        </>
      )}
    </time>
  );
}

/**
 * What the order is made of, at a glance: each dish with its photo from the menu, its name and how many. The first
 * few are shown; the rest are counted and listed in the details.
 */
function OrderDishes({
  orderId,
  dishes,
  looks,
  tone,
}: {
  orderId: number;
  dishes: readonly OrderSummaryItem[];
  looks: ReadonlyMap<number, DishLook>;
  tone: StatusTone;
}) {
  const shown = dishes.slice(0, DISHES_SHOWN);
  const more = dishes.length - shown.length;
  return (
    <ul
      aria-label={`Dishes of order #${orderId}`}
      className="flex flex-wrap gap-2 [grid-area:dishes]"
    >
      {shown.map((dish) => (
        <li
          key={dish.menuItemId}
          className="flex max-w-full items-center gap-2 rounded-full bg-page py-1 pr-2 pl-1 text-sm ring-1 ring-line"
        >
          <DishPhoto
            look={looks.get(dish.menuItemId)}
            className="size-10 shrink-0 rounded-full ring-2 ring-surface"
          />
          <span className="min-w-0 truncate font-medium text-ink" title={dish.name}>
            {dish.name}
          </span>
          <span
            className={cn(
              'shrink-0 rounded-full px-2 py-0.5 text-xs font-bold tabular-nums',
              tone.soft,
              tone.text,
            )}
          >
            <span aria-hidden="true">×{dish.quantity}</span>
            <span className="sr-only">, quantity {dish.quantity}</span>
          </span>
        </li>
      ))}
      {more > 0 && (
        <li className="flex items-center rounded-full bg-page px-3 py-1 text-xs font-semibold text-ink-soft ring-1 ring-line">
          +{more} more
        </li>
      )}
    </ul>
  );
}

/** Read when its row is opened: what was ordered and paid, where the order is, and the customer's contact. */
function OrderDetailsPanel({
  restaurantId,
  orderId,
  looks,
}: {
  restaurantId: number;
  orderId: number;
  looks: ReadonlyMap<number, DishLook>;
}) {
  const order = useOwnerOrder(restaurantId, orderId);

  if (order.isPending) {
    return (
      <div role="status" aria-label="Loading the order" className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }
  if (!order.data) {
    return (
      <Alert tone="error" title="The order could not be loaded">
        <button
          type="button"
          onClick={() => void order.refetch()}
          className="cursor-pointer font-semibold text-brand-text hover:underline"
        >
          Try again
        </button>
      </Alert>
    );
  }
  return <OrderDetailsSummary order={order.data} looks={looks} />;
}

function OrderDetailsSummary({
  order,
  looks,
}: {
  order: OrderDetails;
  looks: ReadonlyMap<number, DishLook>;
}) {
  const money = (amount: number) => formatCurrency(amount, order.price.currency);
  const address = formatAddress(order.deliveryAddress);
  const tone = ORDER_STATUS_TONES[order.status];

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1.45fr)_minmax(0,1fr)]">
      <section aria-labelledby={`items-${order.id}`} className="flex flex-col gap-3">
        <h3 id={`items-${order.id}`} className="flex items-center gap-2 text-sm font-bold text-ink">
          <span
            aria-hidden="true"
            className={cn(
              'flex size-7 items-center justify-center rounded-lg text-white',
              tone.fill,
            )}
          >
            <ShoppingBag className="size-3.5" />
          </span>
          What was ordered
        </h3>
        <ul className="divide-y divide-line overflow-hidden rounded-xl bg-surface ring-1 ring-line">
          {order.items.map((line) => (
            <li key={line.menuItemId} className="flex items-center gap-3 px-3 py-2.5 text-sm">
              <DishPhoto
                look={looks.get(line.menuItemId)}
                className="size-14 shrink-0 rounded-xl"
              />
              {/* The quantity under the name: on a phone the name keeps the width */}
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="flex items-center gap-2">
                  {isFoodType(line.foodType) && <FoodTypeMark foodType={line.foodType} />}
                  <span className="font-semibold [overflow-wrap:anywhere] text-ink">
                    {line.name}
                  </span>
                </span>
                <span className="text-xs text-ink-soft tabular-nums">
                  {line.quantity} × {money(line.unitPrice)}
                </span>
              </span>
              <span className="shrink-0 text-right font-semibold text-ink tabular-nums">
                {money(line.lineTotal)}
              </span>
            </li>
          ))}
        </ul>
        <dl
          className={cn(
            'flex flex-col gap-1.5 rounded-2xl p-4 text-sm sm:ml-auto sm:w-80',
            tone.soft,
          )}
        >
          <PriceRow label="Items" value={money(order.price.itemsTotal)} />
          {order.price.discountAmount > 0 && (
            <PriceRow
              label={`Coupon ${order.couponCode ?? ''}`.trim()}
              value={`- ${money(order.price.discountAmount)}`}
            />
          )}
          <PriceRow label="Delivery fee" value={money(order.price.deliveryFee)} />
          <PriceRow label="Tax" value={money(order.price.taxAmount)} />
          <div
            className={cn(
              'mt-1 flex justify-between border-t border-line pt-2 text-base font-extrabold',
              tone.text,
            )}
          >
            <dt>Total</dt>
            <dd className="tabular-nums">{money(order.price.totalAmount)}</dd>
          </div>
        </dl>
      </section>

      <div className="flex flex-col gap-4">
        <OrderStateBlock order={order} tone={tone} />

        <section aria-label="Customer" className="flex flex-col gap-3 text-sm">
          <Fact icon={UserRound} label="Customer" tone={tone}>
            <span className="font-semibold [overflow-wrap:anywhere]">
              {order.customerName ?? `Customer #${order.customerId}`}
            </span>
          </Fact>
          {order.customerPhone && (
            <Fact icon={Phone} label="Phone" tone={tone}>
              <a
                href={`tel:${phoneDigits(order.customerPhone)}`}
                className="font-medium text-brand-text hover:underline"
              >
                {order.customerPhone}
              </a>
            </Fact>
          )}
          {address && (
            <Fact
              icon={MapPin}
              label={order.status === 'DELIVERED' ? 'Delivered to' : 'Deliver to'}
              tone={tone}
            >
              <span className="[overflow-wrap:anywhere]">{address}</span>
            </Fact>
          )}
          {order.notes && (
            <Fact icon={MessageSquareText} label="Customer note" tone={tone}>
              <span className="[overflow-wrap:anywhere]">{order.notes}</span>
            </Fact>
          )}
        </section>

        <Link
          to={ownerRouteTo.orderDetails(order.id)}
          className={buttonStyles('outline', 'sm', 'self-start')}
        >
          Open the order page
          <ArrowRight aria-hidden="true" className="size-4" />
        </Link>
      </div>
    </div>
  );
}

/**
 * Where the order is, from its own record and timeline: delivered (when, and how long it took), turned down or
 * cancelled (by whom and why), or still on its way through the kitchen (its step and since when).
 */
function OrderStateBlock({ order, tone }: { order: OrderDetails; tone: StatusTone }) {
  if (order.status === 'DELIVERED') {
    const { placedAt, deliveredAt, minutes } = deliveryTimes(order);
    return (
      <StateBlock tone={tone} icon={CircleCheckBig} label="Delivery">
        <p className={cn('font-bold', tone.text)}>
          {deliveredAt ? `Delivered ${formatDateTime(deliveredAt)}` : 'Delivered'}
        </p>
        {minutes !== null && (
          <p className="text-ink">
            {minutes === 0 ? 'In under a minute' : formatMinutes(minutes)} from order to door
          </p>
        )}
        <p className="text-ink-soft">Placed {formatDateTime(placedAt)}</p>
      </StateBlock>
    );
  }
  const latest = order.timeline.at(-1);
  if (order.status === 'CANCELLED' || order.status === 'REJECTED') {
    const by = order.cancelledBy ? ` by ${ACTOR_TYPE_LABELS[order.cancelledBy].toLowerCase()}` : '';
    return (
      <StateBlock tone={tone} icon={CircleX} label={ORDER_STATUS_LABELS[order.status]}>
        <p className={cn('font-bold', tone.text)}>
          {ORDER_STATUS_LABELS[order.status]}
          {by}
          {latest ? `, ${formatDateTime(latest.at)}` : ''}
        </p>
        <p className="[overflow-wrap:anywhere] text-ink">
          {order.cancellationReason
            ? `Reason: ${order.cancellationReason}`
            : 'No reason was given.'}
        </p>
      </StateBlock>
    );
  }
  return (
    <StateBlock tone={tone} icon={Clock3} label="Where the order is">
      <p className={cn('font-bold', tone.text)}>Now: {ORDER_STATUS_LABELS[order.status]}</p>
      {latest && <p className="text-ink">Since {formatDateTime(latest.at)}</p>}
      <p className="text-ink-soft">Placed {formatDateTime(deliveryTimes(order).placedAt)}</p>
    </StateBlock>
  );
}

/** Where the order stands, in its status's colours: a tinted box with the status's icon tile. */
function StateBlock({
  tone,
  icon: Icon,
  label,
  children,
}: {
  tone: StatusTone;
  icon: LucideIcon;
  label: string;
  children: ReactNode;
}) {
  return (
    <section aria-label={label} className={cn('flex gap-3 rounded-2xl p-4', tone.soft)}>
      <span
        aria-hidden="true"
        className={cn(
          'flex size-9 shrink-0 items-center justify-center rounded-xl text-white shadow-sm',
          tone.fill,
        )}
      >
        <Icon className="size-4.5" />
      </span>
      <div className="flex min-w-0 flex-col gap-1 text-sm">{children}</div>
    </section>
  );
}

function PriceRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-ink-soft">{label}</dt>
      <dd className="text-ink tabular-nums">{value}</dd>
    </div>
  );
}

function Fact({
  icon: Icon,
  label,
  tone,
  children,
}: {
  icon: LucideIcon;
  label: string;
  tone: StatusTone;
  children: ReactNode;
}) {
  return (
    <div className="flex gap-3">
      <span
        aria-hidden="true"
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full',
          tone.soft,
          tone.text,
        )}
      >
        <Icon className="size-4" />
      </span>
      <div className="flex min-w-0 flex-col">
        <span className="text-xs text-ink-soft">{label}</span>
        <span className="text-ink">{children}</span>
      </div>
    </div>
  );
}

function isFoodType(value: string | null): value is FoodType {
  return value === 'VEG' || value === 'NON_VEG' || value === 'EGG';
}

/** Card-shaped grey blocks while the first page of orders loads: the list keeps its shape. */
export function OwnerOrderTableLoader({ rows = 4 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading orders" className="flex flex-col gap-3">
      {Array.from({ length: rows }, (_, index) => (
        <div
          key={index}
          className="flex items-center gap-4 rounded-2xl bg-surface p-5 ring-1 ring-line/70"
        >
          <Skeleton className="h-6 w-14" />
          <Skeleton className="size-10 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-1/2" />
          </div>
          <Skeleton className="h-6 w-20" />
        </div>
      ))}
    </div>
  );
}
