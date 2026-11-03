import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import OwnerOrdersPage from '@/pages/Owner/OwnerOrdersPage';
import { ownerService } from '@/services/ownerService';
import { orderDetails, orderSummary, pageOf } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderOwnerPage } from '@/test/ownerTestUtils';
import type { OrderStatus } from '@/types/order';
import { presetRange } from '@/utils/dateRangeUtils';
import { toIsoDate } from '@/utils/dateUtils';

vi.mock('@/services/ownerService', () => ({
  ownerService: {
    getOrders: vi.fn(),
    countOrders: vi.fn(),
    getOrder: vi.fn(),
    acceptOrder: vi.fn(),
    rejectOrder: vi.fn(),
    startPreparing: vi.fn(),
    markReady: vi.fn(),
  },
}));

const RESTAURANT_ID = 3;

/** Shows the address, so a test can check what the page keeps in it. */
function Address() {
  return <output data-testid="address">{useLocation().search}</output>;
}

const renderOrders = (route = '/owner/orders') =>
  renderOwnerPage(
    <>
      <OwnerOrdersPage />
      <Address />
    </>,
    '/owner/orders',
    { route },
  );

const conflict = (message: string) =>
  new ApiError({
    kind: 'conflict',
    status: 409,
    userMessage: message,
    technicalMessage: 'conflict',
  });

const rowOf = (orderId: number) =>
  screen.getByRole('link', { name: `#${orderId}` }).closest('li') as HTMLElement;

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([orderSummary()]));
  vi.mocked(ownerService.countOrders).mockResolvedValue(0);
});

describe('OwnerOrdersPage: the list', () => {
  it('shows every tab in the same table: who ordered, details on request, the next step where there is one', async () => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(
      pageOf([orderSummary({ status: 'DELIVERED', customerName: 'Priya Sharma' })]),
    );
    const { unmount } = renderOrders('/owner/orders?tab=delivered');

    expect(await screen.findByRole('list', { name: 'Delivered orders' })).toBeInTheDocument();
    expect(screen.getByText('Priya Sharma')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Show the details of order #42' }),
    ).toBeInTheDocument();
    unmount();

    vi.mocked(ownerService.getOrders).mockResolvedValue(
      pageOf([orderSummary({ status: 'PREPARING', customerName: 'Priya Sharma' })]),
    );
    renderOrders('/owner/orders?tab=preparing');
    expect(await screen.findByRole('list', { name: 'Preparing orders' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark ready order #42' })).toBeInTheDocument();
  });

  it('sorts by the order the owner chooses, keeps it in the address, and forgets it with the filters', async () => {
    renderOrders('/owner/orders?tab=delivered');
    await screen.findByRole('link', { name: '#42' });

    await chooseOption(screen.getByRole('combobox', { name: 'Order' }), 'Oldest first');

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'DELIVERED', sort: 'createdAt,asc' },
        0,
        20,
      ),
    );
    expect(screen.getByTestId('address')).toHaveTextContent('sort=oldest');

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(screen.getByTestId('address')).not.toHaveTextContent('sort'));
  });

  it('opens on the new orders, oldest first, and shows what the kitchen needs to know', async () => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(
      pageOf([
        orderSummary({ itemCount: 3, paymentMethod: 'CASH_ON_DELIVERY', paymentStatus: 'PENDING' }),
      ]),
    );
    renderOrders();

    expect(await screen.findByRole('link', { name: '#42' })).toHaveAttribute(
      'href',
      '/owner/orders/42',
    );
    expect(ownerService.getOrders).toHaveBeenCalledWith(
      RESTAURANT_ID,
      { status: 'PLACED', sort: 'createdAt,asc' },
      0,
      20,
    );
    const row = rowOf(42);
    expect(within(row).getByText('3 items')).toBeInTheDocument();
    expect(within(row).getByText('Cash on delivery')).toBeInTheDocument();
    expect(within(row).getByText('Pending')).toBeInTheDocument();
    expect(within(row).getByText(/562\.90/)).toBeInTheDocument();
    expect(row.querySelector('time')).toHaveAttribute('dateTime', '2026-10-08T10:15:00Z');
    expect(screen.getByRole('tab', { name: 'New', selected: true })).toBeInTheDocument();
  });

  it.each([
    ['Accepted', 'accepted', { status: 'ACCEPTED' }],
    ['Preparing', 'preparing', { status: 'PREPARING' }],
    ['Ready for pickup', 'ready', { status: 'READY_FOR_PICKUP' }],
    ['On the way', 'on-the-way', { status: 'OUT_FOR_DELIVERY' }],
    ['Delivered', 'delivered', { status: 'DELIVERED' }],
    ['Rejected', 'rejected', { status: 'REJECTED' }],
    ['Cancelled', 'cancelled', { status: 'CANCELLED' }],
  ])(
    'the "%s" tab asks for its status, latest first, and is kept in the address',
    async (label, param, filters) => {
      renderOrders();
      await screen.findByRole('link', { name: '#42' });

      await userEvent.click(screen.getByRole('tab', { name: label }));

      await waitFor(() =>
        expect(ownerService.getOrders).toHaveBeenLastCalledWith(RESTAURANT_ID, filters, 0, 20),
      );
      expect(screen.getByTestId('address')).toHaveTextContent(`?tab=${param}`);
      expect(screen.getByRole('tab', { name: label, selected: true })).toBeInTheDocument();
    },
  );

  it('keeps the address short on the default tab', async () => {
    renderOrders('/owner/orders?tab=accepted');
    await screen.findByRole('link', { name: '#42' });

    await userEvent.click(screen.getByRole('tab', { name: 'New' }));

    await waitFor(() => expect(screen.getByTestId('address')).toBeEmptyDOMElement());
  });

  it('restores the tab, the days and the page from the address', async () => {
    renderOrders('/owner/orders?tab=delivered&from=2026-10-01&to=2026-10-08&page=3');

    await screen.findByRole('link', { name: '#42' });

    expect(ownerService.getOrders).toHaveBeenCalledWith(
      RESTAURANT_ID,
      { status: 'DELIVERED', fromDate: '2026-10-01', toDate: '2026-10-08' },
      2,
      20,
    );
    expect(screen.getByRole('tab', { name: 'Delivered', selected: true })).toBeInTheDocument();
  });

  it('opens "New" for an unknown tab in the address', async () => {
    renderOrders('/owner/orders?tab=nonsense');

    await screen.findByRole('link', { name: '#42' });

    expect(ownerService.getOrders).toHaveBeenCalledWith(
      RESTAURANT_ID,
      { status: 'PLACED', sort: 'createdAt,asc' },
      0,
      20,
    );
  });

  it('goes to the next page, and back to the first when the tab changes', async () => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([orderSummary()], 45));
    renderOrders();
    await screen.findByRole('link', { name: '#42' });
    expect(screen.getByText('Showing 1-20 of 45')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'PLACED', sort: 'createdAt,asc' },
        1,
        20,
      ),
    );
    expect(screen.getByTestId('address')).toHaveTextContent('?page=2');

    await userEvent.click(screen.getByRole('tab', { name: 'Accepted' }));

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'ACCEPTED' },
        0,
        20,
      ),
    );
    expect(screen.getByTestId('address')).not.toHaveTextContent('page');
  });

  it('filters by the days the orders were placed, and clears the days', async () => {
    renderOrders('/owner/orders?tab=delivered');
    await screen.findByRole('link', { name: '#42' });

    // "Yesterday" from the quick ranges of the date range picker
    const yesterday = presetRange('yesterday');
    await userEvent.click(screen.getByRole('button', { name: 'Placed on: Any day' }));
    await userEvent.click(
      within(screen.getByRole('dialog', { name: 'Choose: Placed on' })).getByRole('button', {
        name: 'Yesterday',
      }),
    );

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'DELIVERED', ...yesterday },
        0,
        20,
      ),
    );
    expect(screen.getByTestId('address')).toHaveTextContent(`from=${yesterday.fromDate}`);
    expect(screen.getByRole('button', { name: /^Placed on: Yesterday/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'DELIVERED' },
        0,
        20,
      ),
    );
    expect(screen.getByTestId('address')).toHaveTextContent('?tab=delivered');
    expect(screen.getByTestId('address')).not.toHaveTextContent('from');
  });

  it('picks a custom range in the calendar', async () => {
    renderOrders('/owner/orders?tab=delivered&from=2026-10-01&to=2026-10-08');
    await screen.findByRole('link', { name: '#42' });

    await userEvent.click(screen.getByRole('button', { name: /^Placed on: Custom · 8 days/ }));
    const calendar = within(screen.getByRole('dialog', { name: 'Choose: Placed on' }));
    await userEvent.click(calendar.getByRole('button', { name: 'Monday, 12 October 2026' }));
    await userEvent.click(calendar.getByRole('button', { name: 'Thursday, 15 October 2026' }));
    await userEvent.click(calendar.getByRole('button', { name: 'Apply' }));

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'DELIVERED', fromDate: '2026-10-12', toDate: '2026-10-15' },
        0,
        20,
      ),
    );
    expect(screen.getByTestId('address')).toHaveTextContent('from=2026-10-12&to=2026-10-15');
  });

  it('reads a link with only a first day as "from that day until today"', async () => {
    renderOrders('/owner/orders?tab=delivered&from=2026-10-01');

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenCalledWith(
        RESTAURANT_ID,
        { status: 'DELIVERED', fromDate: '2026-10-01', toDate: toIsoDate(new Date()) },
        0,
        20,
      ),
    );
  });

  it('does not ask for an impossible range of days, and says why', () => {
    renderOrders('/owner/orders?from=2026-10-08&to=2026-10-01');

    expect(screen.getByText('The "to" date is before the "from" date.')).toBeInTheDocument();
    expect(
      screen.getByText('Choose dates that make a valid range to see the orders.'),
    ).toBeInTheDocument();
    expect(ownerService.getOrders).not.toHaveBeenCalled();
    expect(ownerService.countOrders).not.toHaveBeenCalled();
  });
});

describe('OwnerOrdersPage: the status tabs', () => {
  const COUNTS: Partial<Record<OrderStatus, number>> = {
    PLACED: 2,
    ACCEPTED: 1,
    PREPARING: 0,
    READY_FOR_PICKUP: 3,
    OUT_FOR_DELIVERY: 0,
    DELIVERED: 12,
    REJECTED: 0,
    CANCELLED: 4,
  };

  beforeEach(() => {
    vi.mocked(ownerService.countOrders).mockImplementation((_restaurantId, filters) =>
      Promise.resolve(COUNTS[filters.status as OrderStatus] ?? 0),
    );
  });

  it('shows how many orders each status has, read out with the tab', async () => {
    renderOrders();

    const newTab = screen.getByRole('tab', { name: 'New', selected: true });
    await waitFor(() => expect(newTab).toHaveAccessibleDescription('2 orders'));
    expect(screen.getByRole('tab', { name: 'Accepted' })).toHaveAccessibleDescription('1 order');
    expect(screen.getByRole('tab', { name: 'Ready for pickup' })).toHaveAccessibleDescription(
      '3 orders',
    );
    expect(screen.getByRole('tab', { name: 'Delivered' })).toHaveAccessibleDescription('12 orders');
    // One count per status, without the list's sort order
    expect(ownerService.countOrders).toHaveBeenCalledTimes(8);
    expect(ownerService.countOrders).toHaveBeenCalledWith(RESTAURANT_ID, { status: 'PLACED' });
  });

  it('sums the kitchen up in the page header, and heads the list with its count', async () => {
    renderOrders();

    const glance = within(await screen.findByRole('list', { name: 'Orders at a glance' }));
    // New 2; accepted 1 + preparing 0; ready 3 + on the way 0
    await waitFor(() =>
      expect(glance.getByText('Waiting for you').previousElementSibling).toHaveTextContent('2'),
    );
    expect(glance.getByText('In the kitchen').previousElementSibling).toHaveTextContent('1');
    expect(glance.getByText('Ready or on the way').previousElementSibling).toHaveTextContent('3');
    expect(
      screen.getByRole('heading', { name: 'New orders' }).nextElementSibling,
    ).toHaveTextContent('2 orders');
  });

  it('counts the orders of the chosen days only', async () => {
    renderOrders('/owner/orders?tab=delivered&from=2026-10-01&to=2026-10-08');

    await waitFor(() =>
      expect(ownerService.countOrders).toHaveBeenCalledWith(RESTAURANT_ID, {
        status: 'CANCELLED',
        fromDate: '2026-10-01',
        toDate: '2026-10-08',
      }),
    );
  });

  it('moves between the statuses with the arrow keys, Home and End', async () => {
    renderOrders();
    const newTab = screen.getByRole('tab', { name: 'New' });
    newTab.focus();

    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Accepted', selected: true })).toHaveFocus();

    await userEvent.keyboard('{End}');
    expect(screen.getByRole('tab', { name: 'Cancelled', selected: true })).toHaveFocus();

    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'New', selected: true })).toHaveFocus();

    await userEvent.keyboard('{Home}');
    expect(screen.getByRole('tab', { name: 'New', selected: true })).toHaveFocus();
  });
});

describe('OwnerOrdersPage: loading, empty and error', () => {
  it('shows a placeholder while the orders load', () => {
    vi.mocked(ownerService.getOrders).mockReturnValue(new Promise(() => undefined));
    renderOrders();

    expect(screen.getByRole('status', { name: 'Loading orders' })).toBeInTheDocument();
  });

  it.each([
    ['new', 'No new orders right now'],
    ['accepted', 'No accepted orders waiting'],
    ['preparing', 'Nothing is being prepared'],
    ['delivered', 'No delivered orders yet'],
    ['cancelled', 'No cancelled orders'],
  ])('says what an empty "%s" tab means', async (tab, title) => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([]));
    renderOrders(`/owner/orders?tab=${tab}`);

    expect(await screen.findByText(title)).toBeInTheDocument();
  });

  it('says when the days leave nothing, and clears them from there', async () => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([]));
    renderOrders('/owner/orders?from=2026-10-01');

    const empty = await screen.findByText('No new orders on these dates');
    await userEvent.click(
      within(empty.parentElement as HTMLElement).getByRole('button', { name: 'Clear filters' }),
    );

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'PLACED', sort: 'createdAt,asc' },
        0,
        20,
      ),
    );
  });

  it('offers the first page when the page asked for has no orders left', async () => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([], 0, 2));
    renderOrders('/owner/orders?page=3');

    await userEvent.click(await screen.findByRole('button', { name: 'Back to the first page' }));

    await waitFor(() =>
      expect(ownerService.getOrders).toHaveBeenLastCalledWith(
        RESTAURANT_ID,
        { status: 'PLACED', sort: 'createdAt,asc' },
        0,
        20,
      ),
    );
  });

  it('explains a failure and tries again on request', async () => {
    vi.mocked(ownerService.getOrders)
      .mockRejectedValueOnce(
        new ApiError({
          kind: 'server',
          status: 500,
          userMessage: 'Something went wrong on our side.',
          technicalMessage: 'boom',
        }),
      )
      .mockResolvedValue(pageOf([orderSummary()]));
    renderOrders();

    expect(await screen.findByText('Unable to load the orders')).toBeInTheDocument();
    expect(screen.getByText('Something went wrong on our side.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('link', { name: '#42' })).toBeInTheDocument();
  });
});

describe('OwnerOrdersPage: the next step of each order', () => {
  const buttonsOf = (orderId: number) =>
    within(rowOf(orderId))
      .queryAllByRole('button')
      .map((button) => button.getAttribute('aria-label'))
      // The details button is on every row; these are the kitchen's steps
      .filter((label) => !label?.includes('the details of order'));

  it.each<[OrderStatus, string[]]>([
    ['PLACED', ['Accept order #42', 'Reject order #42']],
    ['ACCEPTED', ['Start preparing order #42']],
    ['PREPARING', ['Mark ready order #42']],
    ['READY_FOR_PICKUP', []],
    ['OUT_FOR_DELIVERY', []],
    ['DELIVERED', []],
    ['REJECTED', []],
    ['CANCELLED', []],
  ])('an order that is %s offers %j', async (status, labels) => {
    vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([orderSummary({ status })]));
    renderOrders();
    await screen.findByRole('link', { name: '#42' });

    expect(buttonsOf(42)).toEqual(labels);
  });

  it.each([
    ['PLACED', 'Accept order #42', 'acceptOrder', 'Order #42 accepted.'],
    ['ACCEPTED', 'Start preparing order #42', 'startPreparing', 'Order #42 is being prepared.'],
    ['PREPARING', 'Mark ready order #42', 'markReady', 'Order #42 is ready for pickup.'],
  ] as const)(
    '%s: "%s" calls the service, tells the owner and reloads the list',
    async (status, button, method, toast) => {
      vi.mocked(ownerService.getOrders).mockResolvedValue(pageOf([orderSummary({ status })]));
      vi.mocked(ownerService[method]).mockResolvedValue(orderDetails({ status: 'ACCEPTED' }));
      renderOrders();

      await userEvent.click(await screen.findByRole('button', { name: button }));

      expect(ownerService[method]).toHaveBeenCalledWith(RESTAURANT_ID, 42);
      expect(await screen.findByText(toast)).toBeInTheDocument();
      // The list is asked for again: the order moves to its next tab
      await waitFor(() => expect(ownerService.getOrders).toHaveBeenCalledTimes(2));
    },
  );

  it('blocks the buttons while the request runs', async () => {
    vi.mocked(ownerService.acceptOrder).mockReturnValue(new Promise(() => undefined));
    renderOrders();

    await userEvent.click(await screen.findByRole('button', { name: 'Accept order #42' }));

    expect(screen.getByRole('button', { name: 'Accept order #42' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Reject order #42' })).toBeDisabled();
    expect(ownerService.acceptOrder).toHaveBeenCalledTimes(1);
  });

  it('shows the refusal of the server and keeps the order and its buttons', async () => {
    vi.mocked(ownerService.acceptOrder).mockRejectedValue(
      conflict('Order 42 is already CANCELLED and cannot be accepted.'),
    );
    renderOrders();

    await userEvent.click(await screen.findByRole('button', { name: 'Accept order #42' }));

    expect(
      await screen.findByText('Order 42 is already CANCELLED and cannot be accepted.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '#42' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Accept order #42' })).toBeEnabled();
  });
});

describe('OwnerOrdersPage: rejecting an order', () => {
  const openRejectDialog = async () => {
    await userEvent.click(await screen.findByRole('button', { name: 'Reject order #42' }));
    return screen.getByRole('dialog', { name: 'Reject order #42?' });
  };

  it('needs a reason, then sends it', async () => {
    vi.mocked(ownerService.rejectOrder).mockResolvedValue(orderDetails({ status: 'REJECTED' }));
    renderOrders();
    const dialog = await openRejectDialog();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject order' }));

    expect(
      await within(dialog).findByText('Please tell the customer why you cannot make this order.'),
    ).toBeInTheDocument();
    expect(ownerService.rejectOrder).not.toHaveBeenCalled();

    await userEvent.type(
      within(dialog).getByLabelText('Reason for the customer'),
      '  Out of rice ',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject order' }));

    await waitFor(() =>
      expect(ownerService.rejectOrder).toHaveBeenCalledWith(RESTAURANT_ID, 42, 'Out of rice'),
    );
    expect(
      await screen.findByText('Order #42 rejected. The customer is told why.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('keeps the dialog open and shows the refusal of the server', async () => {
    vi.mocked(ownerService.rejectOrder).mockRejectedValue(
      conflict('Order 42 is already ACCEPTED and cannot be rejected.'),
    );
    renderOrders();
    const dialog = await openRejectDialog();

    await userEvent.type(within(dialog).getByLabelText('Reason for the customer'), 'Out of rice');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject order' }));

    expect(
      await within(dialog).findByText('Order 42 is already ACCEPTED and cannot be rejected.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Reject order #42?' })).toBeInTheDocument();
  });

  it('does nothing when the owner cancels the dialog', async () => {
    renderOrders();
    const dialog = await openRejectDialog();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(ownerService.rejectOrder).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
