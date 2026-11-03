import { describe, expect, it } from 'vitest';

import { calendarEvent, couponEvent } from '@/test/fixtures';
import {
  clampToRange,
  dayLabel,
  describeEvent,
  indexEventsByDay,
  intensityLevel,
  layoutWeek,
  monthGridWeeks,
  pillText,
  shiftDate,
  todayInZone,
  viewTitle,
  visibleRange,
  weekDays,
  withMonthYear,
} from '@/utils/calendarUtils';

const WEEK = weekDays('2026-10-09'); // Sunday 4 Oct to Saturday 10 Oct 2026

describe('month and week ranges', () => {
  it('draws 6 Sunday-first weeks around a month', () => {
    const weeks = monthGridWeeks(2026, 9);
    expect(weeks).toHaveLength(6);
    expect(weeks.every((week) => week.length === 7)).toBe(true);
    expect(weeks[0][0]).toBe('2026-09-27');
    expect(weeks[0][4]).toBe('2026-10-01');
    expect(weeks[5][6]).toBe('2026-11-07');
  });

  it('starts on the 1st when it is a Sunday', () => {
    const weeks = monthGridWeeks(2026, 1); // February 2026 starts on a Sunday
    expect(weeks[0][0]).toBe('2026-02-01');
    expect(weeks[5][6]).toBe('2026-03-14');
  });

  it('crosses the year', () => {
    const weeks = monthGridWeeks(2026, 11);
    expect(weeks[0][0]).toBe('2026-11-29');
    expect(weeks[5][6]).toBe('2027-01-09');
  });

  it('gives the week of a date, Sunday to Saturday', () => {
    expect(WEEK[0]).toBe('2026-10-04');
    expect(WEEK[6]).toBe('2026-10-10');
    expect(weekDays('2026-10-04')[0]).toBe('2026-10-04');
    expect(weekDays('2026-10-10')[0]).toBe('2026-10-04');
  });

  it('asks the range each view draws', () => {
    expect(visibleRange('DATE', '2026-10-09')).toEqual({ from: '2026-10-09', to: '2026-10-09' });
    expect(visibleRange('WEEK', '2026-10-09')).toEqual({ from: '2026-10-04', to: '2026-10-10' });
    expect(visibleRange('MONTH', '2026-10-09')).toEqual({ from: '2026-09-27', to: '2026-11-07' });
    expect(visibleRange('YEAR', '2026-10-09')).toEqual({ from: '2026-01-01', to: '2026-12-31' });
  });

  it('always draws 42 days for a month, well within the 400 days the API allows', () => {
    for (let month = 1; month <= 12; month++) {
      const { from, to } = visibleRange('MONTH', `2026-${String(month).padStart(2, '0')}-15`);
      expect(weekDays(from)[0]).toBe(from);
      expect(Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000)).toBe(41);
    }
  });

  it('steps by a day, week, month or year', () => {
    expect(shiftDate('DATE', '2026-10-31', 1)).toBe('2026-11-01');
    expect(shiftDate('WEEK', '2026-10-09', -1)).toBe('2026-10-02');
    expect(shiftDate('MONTH', '2026-01-31', 1)).toBe('2026-02-28');
    expect(shiftDate('MONTH', '2026-01-15', -1)).toBe('2025-12-15');
    expect(shiftDate('YEAR', '2028-02-29', 1)).toBe('2029-02-28');
  });

  it('moves to a month and year keeping the day where it exists', () => {
    expect(withMonthYear('2026-10-31', 2027, 1)).toBe('2027-02-28');
    expect(withMonthYear('2026-10-09', 2025, 4)).toBe('2025-05-09');
  });

  it('titles the views', () => {
    expect(viewTitle('MONTH', '2026-10-09')).toBe('October 2026');
    expect(viewTitle('YEAR', '2026-10-09')).toBe('2026');
    expect(viewTitle('DATE', '2026-10-09')).toBe('9 October 2026');
    expect(viewTitle('WEEK', '2026-10-09')).toBe('4 - 10 Oct 2026');
    expect(viewTitle('WEEK', '2026-10-01')).toMatch(/^27 Sep\w* - 3 Oct 2026$/);
    expect(viewTitle('WEEK', '2026-12-31')).toBe('27 Dec 2026 - 2 Jan 2027');
  });

  it('labels a day for screen readers', () => {
    expect(dayLabel('2026-10-09', 3)).toBe('9 October 2026, 3 events');
    expect(dayLabel('2026-10-09', 1)).toBe('9 October 2026, 1 event');
    expect(dayLabel('2026-10-09', 0)).toBe('9 October 2026, no events');
  });
});

describe('todayInZone', () => {
  it('reads the day in the given zone, not the device', () => {
    const instant = new Date('2026-10-09T19:00:00Z');
    expect(todayInZone('Asia/Kolkata', instant)).toBe('2026-10-10');
    expect(todayInZone('America/Los_Angeles', instant)).toBe('2026-10-09');
    expect(todayInZone('UTC', instant)).toBe('2026-10-09');
  });

  it('changes day exactly at the zone midnight', () => {
    expect(todayInZone('Asia/Kolkata', new Date('2026-10-09T18:29:59Z'))).toBe('2026-10-09');
    expect(todayInZone('Asia/Kolkata', new Date('2026-10-09T18:30:00Z'))).toBe('2026-10-10');
    expect(todayInZone('America/New_York', new Date('2026-10-10T03:59:59Z'))).toBe('2026-10-09');
    expect(todayInZone('America/New_York', new Date('2026-10-10T04:00:00Z'))).toBe('2026-10-10');
  });

  it('crosses month and year boundaries', () => {
    expect(todayInZone('Asia/Kolkata', new Date('2026-10-31T18:30:00Z'))).toBe('2026-11-01');
    expect(todayInZone('Asia/Kolkata', new Date('2026-12-31T18:30:00Z'))).toBe('2027-01-01');
    expect(todayInZone('Pacific/Auckland', new Date('2026-02-28T11:30:00Z'))).toBe('2026-03-01');
  });

  it('falls back to the device day for an unknown or missing zone', () => {
    const instant = new Date(2026, 9, 9, 12, 0, 0);
    expect(todayInZone('Not/AZone', instant)).toBe('2026-10-09');
    expect(todayInZone(undefined, instant)).toBe('2026-10-09');
  });
});

describe('events by day', () => {
  it('clamps an event to a range', () => {
    const event = { start: '2026-09-20', end: '2026-10-20' };
    expect(clampToRange(event, '2026-09-27', '2026-11-07')).toEqual({
      start: '2026-09-27',
      end: '2026-10-20',
    });
    expect(clampToRange(event, '2026-10-21', '2026-11-07')).toBeNull();
    expect(clampToRange(event, '2026-08-01', '2026-09-19')).toBeNull();
  });

  it('lists a campaign on each of its days inside the range only', () => {
    const coupon = couponEvent({ start: '2026-10-08', end: '2026-10-10' });
    const orders = calendarEvent({ start: '2026-10-09', end: '2026-10-09' });
    const byDay = indexEventsByDay([coupon, orders], '2026-10-09', '2026-10-31');
    expect([...byDay.keys()]).toEqual(['2026-10-09', '2026-10-10']);
    expect(byDay.get('2026-10-09')).toEqual([coupon, orders]);
    expect(byDay.get('2026-10-10')).toEqual([coupon]);
    expect(byDay.get('2026-10-08')).toBeUndefined();
  });

  it('grades the tint of a day', () => {
    expect([0, 1, 2, 3, 5, 6, 40].map(intensityLevel)).toEqual([0, 1, 1, 2, 2, 3, 3]);
  });
});

describe('layoutWeek', () => {
  it('cuts a campaign at the week edges and continues it on the next row', () => {
    const coupon = couponEvent({ start: '2026-10-08', end: '2026-10-14' });
    const first = layoutWeek(WEEK, [coupon]);
    expect(first.bars).toEqual([
      {
        event: coupon,
        lane: 0,
        startCol: 4,
        endCol: 6,
        continuesBefore: false,
        continuesAfter: true,
      },
    ]);
    const second = layoutWeek(weekDays('2026-10-11'), [coupon]);
    expect(second.bars[0]).toMatchObject({
      startCol: 0,
      endCol: 3,
      continuesBefore: true,
      continuesAfter: false,
    });
  });

  it('clips a campaign that starts before and ends after the week to the whole row', () => {
    const coupon = couponEvent({ start: '2026-09-01', end: '2026-12-31' });
    expect(layoutWeek(WEEK, [coupon]).bars[0]).toMatchObject({
      startCol: 0,
      endCol: 6,
      continuesBefore: true,
      continuesAfter: true,
    });
  });

  it('puts overlapping campaigns in different lanes and reuses a lane once it is free', () => {
    const a = couponEvent({ id: 'a', title: 'A', start: '2026-10-04', end: '2026-10-06' });
    const b = couponEvent({ id: 'b', title: 'B', start: '2026-10-05', end: '2026-10-08' });
    const c = couponEvent({ id: 'c', title: 'C', start: '2026-10-07', end: '2026-10-09' });
    const lanes = Object.fromEntries(
      layoutWeek(WEEK, [c, b, a]).bars.map((bar) => [bar.event.id, bar.lane]),
    );
    expect(lanes).toEqual({ a: 0, b: 1, c: 0 });
  });

  it('never lets two bars of one lane overlap', () => {
    const events = Array.from({ length: 8 }, (_, index) =>
      couponEvent({
        id: `c${index}`,
        start: `2026-10-0${4 + (index % 4)}`,
        end: `2026-10-0${6 + (index % 3)}`,
      }),
    );
    const { bars } = layoutWeek(WEEK, events);
    for (const a of bars) {
      for (const b of bars) {
        if (a !== b && a.lane === b.lane) {
          expect(a.endCol < b.startCol || b.endCol < a.startCol).toBe(true);
        }
      }
    }
  });

  it('lets the longer campaign keep the top lane when two start together', () => {
    const short = couponEvent({ id: 's', start: '2026-10-05', end: '2026-10-06' });
    const long = couponEvent({ id: 'l', start: '2026-10-05', end: '2026-10-09' });
    const lanes = Object.fromEntries(
      layoutWeek(WEEK, [short, long]).bars.map((bar) => [bar.event.id, bar.lane]),
    );
    expect(lanes).toEqual({ l: 0, s: 1 });
  });

  it('puts the day pills under the bars in priority order', () => {
    const coupon = couponEvent({ start: '2026-10-04', end: '2026-10-05' });
    const events = [
      calendarEvent({ id: 'r', type: 'RESTAURANTS', kind: 'RESTAURANTS_REGISTERED' }),
      calendarEvent({ id: 'c', kind: 'ORDERS_CANCELLED' }),
      calendarEvent({ id: 'd', kind: 'ORDERS_DELIVERED' }),
      calendarEvent({ id: 'p', kind: 'ORDERS_PLACED' }),
      coupon,
    ];
    const layout = layoutWeek(WEEK, events);
    expect(layout.days[5].pills.map((pill) => [pill.event.id, pill.lane])).toEqual([
      ['p', 1],
      ['d', 2],
      ['c', 3],
      ['r', 4],
    ]);
  });

  it('shows one coupon bar and two day pills, and counts every other event as "+N more"', () => {
    const coupons = ['a', 'b', 'c'].map((id) =>
      couponEvent({ id, title: id, start: '2026-10-05', end: '2026-10-06' }),
    );
    const day = [
      calendarEvent({ id: 'p', start: '2026-10-05', end: '2026-10-05' }),
      calendarEvent({ id: 'd', kind: 'ORDERS_DELIVERED', start: '2026-10-05', end: '2026-10-05' }),
      calendarEvent({ id: 'x', kind: 'ORDERS_CANCELLED', start: '2026-10-05', end: '2026-10-05' }),
    ];
    const layout = layoutWeek(WEEK, [...coupons, ...day], { maxBarLanes: 1, maxPills: 2 });
    expect(layout.bars.map((bar) => bar.event.id)).toEqual(['a']);
    // Monday: 3 campaigns + 3 day events = 6; drawn: 1 bar + 2 pills; hidden: 2 coupons + 1 pill
    expect(layout.days[1]).toMatchObject({ total: 6, hiddenCount: 3 });
    expect(layout.days[1].pills.map((pill) => [pill.event.id, pill.lane])).toEqual([
      ['p', 1],
      ['d', 2],
    ]);
    expect(layout.moreRow).toBe(3);
    // Tuesday: 3 campaigns, one drawn
    expect(layout.days[2]).toMatchObject({ total: 3, hiddenCount: 2, pills: [] });
    expect(layout.days[3]).toMatchObject({ total: 0, hiddenCount: 0 });
  });

  it('keeps a single campaign visible with no "+N more"', () => {
    const layout = layoutWeek(WEEK, [couponEvent({ start: '2026-10-05', end: '2026-10-06' })], {
      maxBarLanes: 1,
      maxPills: 2,
    });
    expect(layout.bars).toHaveLength(1);
    expect(layout.days.every((day) => day.hiddenCount === 0)).toBe(true);
  });

  it('shows the day pills of a day crowded with campaigns, right after the bars drawn', () => {
    const coupons = Array.from({ length: 28 }, (_, index) =>
      couponEvent({ id: 'c' + index, start: '2026-10-04', end: '2026-10-10' }),
    );
    const layout = layoutWeek(WEEK, [...coupons, calendarEvent()], { maxBarLanes: 3 });
    expect(layout.bars).toHaveLength(3);
    expect(layout.days[5].pills[0].lane).toBe(3);
    expect(layout.days[5].moreLane).toBe(4);
    expect(layout.days[5].hiddenCount).toBe(25);
  });

  it('ignores events outside the week', () => {
    const layout = layoutWeek(WEEK, [calendarEvent({ start: '2026-10-20', end: '2026-10-20' })]);
    expect(layout.bars).toEqual([]);
    expect(layout.days.every((day) => day.total === 0)).toBe(true);
    expect(layout.laneCount).toBe(0);
  });
});

describe('event text', () => {
  it('says what a pill shows', () => {
    expect(pillText(calendarEvent())).toBe('12 orders');
    expect(pillText(calendarEvent({ kind: 'ORDERS_DELIVERED', title: '37 delivered' }))).toBe(
      '37 delivered',
    );
    expect(pillText(couponEvent())).toBe('SAVE20');
  });

  it('describes a coupon fully', () => {
    expect(describeEvent(couponEvent())).toBe(
      'Coupon SAVE20: 8 Oct 2026 to 14 Oct 2026, Active, used 5 times',
    );
    expect(describeEvent(couponEvent({ start: '2026-10-08', end: '2026-10-08', count: 1 }))).toBe(
      'Coupon SAVE20: 8 Oct 2026, Active, used once',
    );
  });
});
