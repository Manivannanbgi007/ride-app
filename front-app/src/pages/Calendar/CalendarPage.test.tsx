import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import CalendarPage from '@/pages/Calendar/CalendarPage';
import { calendarService } from '@/services/calendarService';
import { calendarEvent, calendarResponse, couponEvent } from '@/test/fixtures';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/calendarService', () => ({ calendarService: { getCalendar: vi.fn() } }));

const serverError = () =>
  new ApiError({
    kind: 'server',
    status: 500,
    userMessage: 'Something went wrong on the server.',
    technicalMessage: 'boom',
  });

/** Friday 9 October 2026, 13:30 in Kolkata */
const NOW = new Date('2026-10-09T08:00:00Z');

const EVENTS = [
  couponEvent({ id: 'coupon-7', title: 'SAVE20', start: '2026-10-08', end: '2026-10-14' }),
  couponEvent({
    id: 'coupon-8',
    title: 'WELCOME',
    start: '2026-10-09',
    end: '2026-10-10',
    ref: { couponId: 8 },
    status: 'INACTIVE',
    tone: 'neutral',
  }),
  couponEvent({
    id: 'coupon-9',
    title: 'FLASH',
    start: '2026-10-09',
    end: '2026-10-09',
    ref: { couponId: 9 },
  }),
  calendarEvent({ id: 'orders-placed-2026-10-09' }),
  calendarEvent({
    id: 'orders-delivered-2026-10-09',
    kind: 'ORDERS_DELIVERED',
    title: '9 delivered',
    count: 9,
    tone: 'success',
  }),
  calendarEvent({
    id: 'orders-placed-2026-10-20',
    title: '4 orders',
    count: 4,
    start: '2026-10-20',
    end: '2026-10-20',
  }),
  calendarEvent({
    id: 'restaurants-2026-10-21',
    type: 'RESTAURANTS',
    kind: 'RESTAURANTS_REGISTERED',
    title: '2 new restaurants',
    count: 2,
    start: '2026-10-21',
    end: '2026-10-21',
    tone: 'warning',
  }),
];

const renderPage = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/calendar" element={<CalendarPage />} />
      <Route path="/admin/coupons/new" element={<p>Coupon form</p>} />
    </Routes>,
    { route: '/admin/calendar' },
  );

/** jsdom has no matchMedia: answers every query as a phone (true) or not. */
function screenIsPhone(phone: boolean) {
  window.matchMedia = vi.fn((query: string) => ({
    matches: phone,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

const dayCell = (name: string | RegExp) => screen.findByRole('gridcell', { name });

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(NOW);
  vi.mocked(calendarService.getCalendar).mockResolvedValue(calendarResponse({ events: EVENTS }));
});

afterEach(() => {
  vi.useRealTimers();
  Reflect.deleteProperty(window, 'matchMedia');
});

describe('CalendarPage month view', () => {
  it('asks the 6-week grid and draws the events with "+N more"', async () => {
    renderPage();

    expect(await dayCell('9 October 2026, 5 events')).toBeInTheDocument();
    expect(calendarService.getCalendar).toHaveBeenCalledWith('2026-09-27', '2026-11-07');
    expect(screen.getByRole('heading', { name: 'October 2026' })).toBeInTheDocument();
    expect(screen.getAllByRole('columnheader')).toHaveLength(7);
    expect(screen.getAllByRole('row')).toHaveLength(7);

    // One coupon bar per week row, two day pills, then "+2 more" (the two other campaigns)
    expect(screen.getAllByRole('button', { name: /^Coupon SAVE20: / }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /^Coupon WELCOME: / })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '12 orders' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '9 delivered' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Coupon FLASH: / })).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '2 more events on 9 October 2026' }),
    ).toHaveTextContent('+2 more');
    // A day with one event shows it as a pill
    expect(screen.getByRole('button', { name: '4 orders' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2 new restaurants' })).toBeInTheDocument();
  });

  it('continues a campaign on the next row of the grid', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');
    // 8 to 14 October crosses the Saturday/Sunday edge: two pieces of one campaign
    expect(screen.getAllByRole('button', { name: /^Coupon SAVE20: / })).toHaveLength(2);
  });

  it('marks today and the selected day', async () => {
    renderPage();
    const today = await dayCell('9 October 2026, 5 events');
    expect(today).toHaveAttribute('aria-current', 'date');
    await userEvent.click(await dayCell('20 October 2026, 1 event'));
    expect(screen.getByRole('gridcell', { name: '20 October 2026, 1 event' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('moves between days with the arrow keys', async () => {
    renderPage();
    const today = await dayCell('9 October 2026, 5 events');
    today.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('gridcell', { name: '10 October 2026, 2 events' })).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('gridcell', { name: '17 October 2026, no events' })).toHaveFocus();
    await userEvent.keyboard('{Home}');
    expect(screen.getByRole('gridcell', { name: '11 October 2026, 1 event' })).toHaveFocus();
  });

  it('shows dots and a count instead of pills on a phone', async () => {
    screenIsPhone(true);
    renderPage();
    const cell = await dayCell('9 October 2026, 5 events');
    expect(within(cell).getByText('5')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Coupon SAVE20: / })).not.toBeInTheDocument();
    await userEvent.click(cell);
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });
});

describe('CalendarPage navigation', () => {
  it('steps to the next and previous month and back to today', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');

    await userEvent.click(screen.getByRole('button', { name: 'Next month' }));
    expect(await screen.findByRole('heading', { name: 'November 2026' })).toBeInTheDocument();
    expect(calendarService.getCalendar).toHaveBeenLastCalledWith('2026-11-01', '2026-12-12');

    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }));
    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }));
    expect(await screen.findByRole('heading', { name: 'September 2026' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Today (9)' }));
    expect(await screen.findByRole('heading', { name: 'October 2026' })).toBeInTheDocument();
  });

  it('keeps the previous month on the screen while the next one loads', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');
    vi.mocked(calendarService.getCalendar).mockReturnValue(new Promise(() => undefined));
    await userEvent.click(screen.getByRole('button', { name: 'Next month' }));
    expect(screen.queryByRole('status', { name: 'Loading the calendar' })).not.toBeInTheDocument();
    expect(screen.getByRole('grid')).toBeInTheDocument();
  });

  it('switches between the four views, asking each its own range, and remembers the view', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');
    expect(screen.getByRole('tab', { name: 'Month' })).toHaveAttribute('aria-selected', 'true');

    await userEvent.click(screen.getByRole('tab', { name: 'Week' }));
    expect(await screen.findByRole('heading', { name: '4 - 10 Oct 2026' })).toBeInTheDocument();
    expect(calendarService.getCalendar).toHaveBeenLastCalledWith('2026-10-04', '2026-10-10');
    expect(localStorage.getItem('calendar.view')).toBe('WEEK');
  });

  it('starts in the saved view', async () => {
    localStorage.setItem('calendar.view', 'YEAR');
    renderPage();
    expect(
      await screen.findByRole('button', { name: '9 October 2026, 5 events' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Year' })).toHaveAttribute('aria-selected', 'true');
    expect(calendarService.getCalendar).toHaveBeenCalledWith('2026-01-01', '2026-12-31');
  });

  it('starts in Month when storage is blocked', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    renderPage();
    expect(await dayCell('9 October 2026, 5 events')).toBeInTheDocument();
    vi.restoreAllMocks();
  });

  it('shows the week with the bars on top and the day events under them', async () => {
    localStorage.setItem('calendar.view', 'WEEK');
    renderPage();
    expect(await screen.findByRole('button', { name: '9 October 2026, 5 events' })).toHaveAttribute(
      'aria-current',
      'date',
    );
    expect(screen.getByRole('button', { name: /^Coupon SAVE20: / })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Coupon FLASH: / })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '12 orders' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '9 delivered' })).toBeInTheDocument();
  });

  it('lists the days of the week on a phone', async () => {
    screenIsPhone(true);
    localStorage.setItem('calendar.view', 'WEEK');
    renderPage();
    expect(await screen.findAllByRole('listitem')).toHaveLength(7);
    expect(screen.getAllByText('No events').length).toBeGreaterThan(0);
  });

  it('shows one day as an agenda', async () => {
    localStorage.setItem('calendar.view', 'DATE');
    renderPage();
    expect(await screen.findByRole('heading', { name: '9 October 2026' })).toBeInTheDocument();
    expect(calendarService.getCalendar).toHaveBeenCalledWith('2026-10-09', '2026-10-09');
    expect(await screen.findByRole('heading', { name: /Orders/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Coupons/ })).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
    expect(screen.getByText('12 orders')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Next day' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next day' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next day' }));
    // Nothing on 12 October but the campaign running through it
    expect(await screen.findByText('SAVE20')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next day' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next day' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next day' }));
    expect(await screen.findByText('Nothing happening this day')).toBeInTheDocument();
  });

  it('picks a month and year from the title', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');
    const title = screen.getByRole('button', { name: /^October 2026$/ });
    await userEvent.click(title);

    const picker = screen.getByRole('dialog', { name: 'Choose month and year' });
    expect(within(picker).getByRole('button', { name: 'October 2026' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await userEvent.click(within(picker).getByRole('button', { name: 'Next year' }));
    await userEvent.click(within(picker).getByRole('button', { name: 'March 2027' }));

    expect(await screen.findByRole('heading', { name: 'March 2027' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('closes the picker with Escape', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');
    await userEvent.click(screen.getByRole('button', { name: /^October 2026$/ }));
    expect(screen.getByRole('dialog', { name: 'Choose month and year' })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('CalendarPage year view', () => {
  it('opens a month in the Month view and a day in the Date view', async () => {
    localStorage.setItem('calendar.view', 'YEAR');
    renderPage();
    expect(await screen.findByRole('heading', { name: '2026' })).toBeInTheDocument();
    expect(await screen.findAllByRole('heading', { level: 3 })).toHaveLength(12);

    await userEvent.click(screen.getByRole('button', { name: 'March' }));
    expect(await screen.findByRole('heading', { name: 'March 2026' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Month' })).toHaveAttribute('aria-selected', 'true');

    await userEvent.click(screen.getByRole('tab', { name: 'Year' }));
    await userEvent.click(await screen.findByRole('button', { name: '21 October 2026, 1 event' }));
    expect(await screen.findByRole('heading', { name: '21 October 2026' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Date' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText('2 new restaurants')).toBeInTheDocument();
  });
});

describe('CalendarPage event types', () => {
  it('hides the events of a type switched off, and says so when none is left', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');

    const coupons = screen.getByRole('button', { name: 'Coupons' });
    expect(coupons).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(coupons);
    expect(coupons).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByRole('button', { name: /^Coupon / })).not.toBeInTheDocument();
    expect(screen.getByRole('gridcell', { name: '9 October 2026, 2 events' })).toBeInTheDocument();
    // The chips filter what is loaded: no new request
    expect(calendarService.getCalendar).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole('button', { name: 'Orders' }));
    await userEvent.click(screen.getByRole('button', { name: 'Restaurants' }));
    expect(screen.getByText('No event types selected')).toBeInTheDocument();
    expect(screen.queryByRole('grid')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Restaurants' }));
    expect(await dayCell('21 October 2026, 1 event')).toBeInTheDocument();
  });
});

describe('CalendarPage day panel', () => {
  it('opens on a day and lists its events by type with the links', async () => {
    const { store } = renderPage();
    await userEvent.click(await dayCell('9 October 2026, 5 events'));

    const panel = await screen.findByRole('dialog');
    expect(within(panel).getByText(/9 October 2026/)).toBeInTheDocument();
    expect(within(panel).getByRole('heading', { name: /Orders/ })).toHaveTextContent('21');
    expect(within(panel).getByRole('heading', { name: /Coupons/ })).toHaveTextContent('3');
    expect(within(panel).getByText('12 orders')).toBeInTheDocument();
    expect(within(panel).getByText('9 delivered')).toBeInTheDocument();
    expect(within(panel).getByText('WELCOME')).toBeInTheDocument();
    expect(within(panel).getByText('Inactive')).toBeInTheDocument();

    const couponLinks = within(panel).getAllByRole('link', { name: 'View coupon' });
    expect(couponLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/admin/coupons/7',
      '/admin/coupons/8',
      '/admin/coupons/9',
    ]);
    const orderLink = within(panel).getAllByRole('link', { name: 'View orders' })[0];
    expect(orderLink).toHaveAttribute('href', '/admin/orders');
    await userEvent.click(orderLink);
    // The order list opens on that day
    expect(store.getState().orderList.filters).toEqual({
      fromDate: '2026-10-09',
      toDate: '2026-10-09',
    });
  });

  it('links new restaurants to the newest-first restaurant list', async () => {
    const { store } = renderPage();
    await userEvent.click(await dayCell('21 October 2026, 1 event'));
    const link = within(await screen.findByRole('dialog')).getByRole('link', {
      name: 'View restaurants',
    });
    expect(link).toHaveAttribute('href', '/admin/restaurants');
    await userEvent.click(link);
    expect(store.getState().restaurantList.filters).toEqual({ sort: 'createdAt,desc' });
  });

  it('opens from "+N more" and closes again', async () => {
    renderPage();
    await userEvent.click(
      await screen.findByRole('button', { name: '2 more events on 9 October 2026' }),
    );
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('says when a day is empty', async () => {
    renderPage();
    await userEvent.click(await dayCell('2 October 2026, no events'));
    expect(await screen.findByText('Nothing happening this day')).toBeInTheDocument();
  });
});

describe('CalendarPage data states', () => {
  it('shows a loading placeholder first', () => {
    vi.mocked(calendarService.getCalendar).mockReturnValue(new Promise(() => undefined));
    renderPage();
    expect(screen.getByRole('status', { name: 'Loading the calendar' })).toBeInTheDocument();
    // The controls are there already
    expect(screen.getByRole('button', { name: 'New coupon' })).toBeInTheDocument();
  });

  it('names the event types that could not be loaded', async () => {
    vi.mocked(calendarService.getCalendar).mockResolvedValue(
      calendarResponse({ unavailable: ['COUPONS'], events: [calendarEvent()] }),
    );
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('Coupons could not be loaded');
    // What is there still shows
    expect(screen.getByRole('button', { name: '12 orders' })).toBeInTheDocument();
  });

  it('shows an error with a way to try again', async () => {
    vi.mocked(calendarService.getCalendar).mockRejectedValueOnce(serverError());
    renderPage();
    expect(await screen.findByText('Unable to load the calendar')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await dayCell('9 October 2026, 5 events')).toBeInTheDocument();
    expect(calendarService.getCalendar).toHaveBeenCalledTimes(2);
  });

  it('shows an empty month as a grid without events', async () => {
    vi.mocked(calendarService.getCalendar).mockResolvedValue(calendarResponse());
    renderPage();
    expect(await dayCell('9 October 2026, no events')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('CalendarPage time zone', () => {
  it('takes today from the business time zone, not the device', async () => {
    // 20:00 UTC on 9 October: still the 9th in Los Angeles, already the 10th in Auckland
    vi.setSystemTime(new Date('2026-10-09T20:00:00Z'));
    vi.mocked(calendarService.getCalendar).mockResolvedValue(
      calendarResponse({ timeZone: 'Pacific/Auckland' }),
    );
    const first = renderPage();
    expect(await screen.findByRole('button', { name: 'Today (10)' })).toBeInTheDocument();
    expect(await dayCell('10 October 2026, no events')).toHaveAttribute('aria-current', 'date');
    first.unmount();

    vi.mocked(calendarService.getCalendar).mockResolvedValue(
      calendarResponse({ timeZone: 'America/Los_Angeles' }),
    );
    renderPage();
    expect(await screen.findByRole('button', { name: 'Today (9)' })).toBeInTheDocument();
    expect(await dayCell('9 October 2026, no events')).toHaveAttribute('aria-current', 'date');
  });
});

describe('CalendarPage New coupon', () => {
  it('opens the coupon creation page', async () => {
    renderPage();
    await dayCell('9 October 2026, 5 events');
    await userEvent.click(screen.getByRole('button', { name: 'New coupon' }));
    expect(await screen.findByText('Coupon form')).toBeInTheDocument();
  });
});
