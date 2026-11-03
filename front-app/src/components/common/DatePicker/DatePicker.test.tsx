import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { DatePicker, type DatePickerProps } from '@/components/common/DatePicker';
import { DateRangeFields } from '@/components/common/DateRangeFields';
import { addMonths, clampToLimits, isWithinLimits, monthGrid } from '@/utils/calendarUtils';
import { toIsoDate } from '@/utils/dateUtils';

/** A date picker that keeps its value, and reports every change. */
function Picker(
  props: Partial<DatePickerProps> & { initial?: string; onPick?: (value: string) => void },
) {
  const { initial = '', onPick, ...rest } = props;
  const [value, setValue] = useState(initial);
  return (
    <DatePicker
      label="Delivery day"
      {...rest}
      value={value}
      onChange={(next) => {
        setValue(next);
        onPick?.(next);
      }}
    />
  );
}

const calendar = () => screen.getByRole('dialog', { name: 'Choose: Delivery day' });

describe('DatePicker', () => {
  it('shows the date the Indian way and picks a day with the mouse', async () => {
    const onPick = vi.fn();
    render(<Picker initial="2026-10-08" onPick={onPick} />);

    const field = screen.getByLabelText('Delivery day');
    expect(field).toHaveTextContent('8 Oct 2026');
    await userEvent.click(field);
    await userEvent.click(
      within(calendar()).getByRole('button', { name: 'Friday, 16 October 2026' }),
    );

    expect(onPick).toHaveBeenCalledWith('2026-10-16');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(field).toHaveTextContent('16 Oct 2026');
    expect(field).toHaveFocus();
  });

  it('works with the keyboard: arrows, Page Down, Enter, and Escape', async () => {
    const onPick = vi.fn();
    render(<Picker initial="2026-10-08" onPick={onPick} />);

    await userEvent.click(screen.getByLabelText('Delivery day'));
    // The chosen day takes the focus
    expect(
      within(calendar()).getByRole('button', { name: 'Thursday, 8 October 2026' }),
    ).toHaveFocus();

    await userEvent.keyboard('{ArrowRight}{ArrowDown}');
    expect(
      within(calendar()).getByRole('button', { name: 'Friday, 16 October 2026' }),
    ).toHaveFocus();
    await userEvent.keyboard('{PageDown}');
    expect(within(calendar()).getByLabelText('Month')).toHaveValue('10');
    await userEvent.keyboard('{Enter}');
    expect(onPick).toHaveBeenCalledWith('2026-11-16');

    await userEvent.click(screen.getByLabelText('Delivery day'));
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Delivery day')).toHaveFocus();
    expect(onPick).toHaveBeenCalledTimes(1);
  });

  it('strikes out the days outside the limits', async () => {
    render(<Picker initial="2026-10-08" min="2026-10-05" max="2026-10-20" />);

    await userEvent.click(screen.getByLabelText('Delivery day'));
    const days = within(calendar());
    expect(days.getByRole('button', { name: 'Sunday, 4 October 2026' })).toBeDisabled();
    expect(days.getByRole('button', { name: 'Monday, 5 October 2026' })).toBeEnabled();
    expect(days.getByRole('button', { name: 'Wednesday, 21 October 2026' })).toBeDisabled();
  });

  it('clears the date with the cross, unless the field needs one', async () => {
    const onPick = vi.fn();
    const { unmount } = render(<Picker initial="2026-10-08" onPick={onPick} />);

    await userEvent.click(screen.getByRole('button', { name: 'Clear Delivery day' }));
    expect(onPick).toHaveBeenCalledWith('');
    expect(screen.getByLabelText('Delivery day')).toHaveTextContent('Any day');
    unmount();

    render(<Picker initial="2026-10-08" clearable={false} />);
    expect(screen.queryByRole('button', { name: 'Clear Delivery day' })).not.toBeInTheDocument();
  });
});

describe('DatePicker: where the calendar opens', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** A sticky top menu, like the owner layout's, with the field this far below the top of the window. */
  function renderUnderStickyHeader(fieldTop: number, headerBottom: number) {
    vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(700);
    render(
      <>
        <header style={{ position: 'sticky', top: 0 }}>Top menu</header>
        <Picker />
      </>,
    );
    vi.spyOn(screen.getByText('Top menu'), 'getBoundingClientRect').mockReturnValue(
      new DOMRect(0, 0, 1200, headerBottom),
    );
    vi.spyOn(screen.getByLabelText('Delivery day'), 'getBoundingClientRect').mockReturnValue(
      new DOMRect(0, fieldTop, 300, 44),
    );
  }

  it('opens below, not behind the sticky top menu, when the room above is covered by it', async () => {
    // 444 px above the field, but the menu covers 110 of them: the calendar would not fit above it
    renderUnderStickyHeader(444, 110);

    await userEvent.click(screen.getByLabelText('Delivery day'));

    expect(calendar()).toHaveClass('top-full');
    expect(calendar()).not.toHaveClass('bottom-full');
  });

  it('opens above when it fits between the top menu and the field', async () => {
    renderUnderStickyHeader(600, 64);

    await userEvent.click(screen.getByLabelText('Delivery day'));

    expect(calendar()).toHaveClass('bottom-full');
  });
});

describe('DateRangeFields', () => {
  it('does not let "until" be before "from"', async () => {
    render(<DateRangeFields fromDate="2026-10-10" toDate="2026-10-15" onChange={vi.fn()} />);

    await userEvent.click(screen.getByLabelText('Until'));
    const until = screen.getByRole('dialog', { name: 'Choose: Until' });
    expect(within(until).getByRole('button', { name: 'Friday, 9 October 2026' })).toBeDisabled();
    expect(within(until).getByRole('button', { name: 'Saturday, 10 October 2026' })).toBeEnabled();
  });
});

describe('calendar arithmetic', () => {
  it('keeps the day of the month, or the last day of a shorter month', () => {
    expect(toIsoDate(addMonths(new Date(2026, 0, 31), 1))).toBe('2026-02-28');
    expect(toIsoDate(addMonths(new Date(2026, 9, 8), -12))).toBe('2025-10-08');
  });

  it('shows six weeks from the Monday before the 1st', () => {
    const grid = monthGrid(2026, 9);
    expect(grid).toHaveLength(42);
    expect(toIsoDate(grid[0])).toBe('2026-09-28');
    expect(grid[0].getDay()).toBe(1);
  });

  it('keeps days inside the limits', () => {
    expect(isWithinLimits(new Date(2026, 9, 8), '2026-10-01', '2026-10-31')).toBe(true);
    expect(isWithinLimits(new Date(2026, 10, 1), '2026-10-01', '2026-10-31')).toBe(false);
    expect(toIsoDate(clampToLimits(new Date(2026, 8, 1), '2026-10-01'))).toBe('2026-10-01');
  });
});
