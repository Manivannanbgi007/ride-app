import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { DateRangePicker, type DateRangePickerProps } from '@/components/common/DateRangePicker';
import { presetRange, type DatePresetId, type DateRange } from '@/utils/dateRangeUtils';

/** A picker that keeps what is chosen, and reports every change. */
function Picker({
  initial,
  initialPreset,
  onPick,
  ...props
}: Partial<DateRangePickerProps> & {
  initial?: DateRange;
  initialPreset?: DatePresetId;
  onPick?: (range: DateRange | undefined, preset?: DatePresetId) => void;
}) {
  const [value, setValue] = useState(initial);
  const [preset, setPreset] = useState(initialPreset);
  return (
    <DateRangePicker
      label="Overview dates"
      {...props}
      value={value}
      preset={preset}
      onChange={(range, id) => {
        setValue(range);
        setPreset(id);
        onPick?.(range, id);
      }}
    />
  );
}

const OCTOBER_WEEK = { fromDate: '2026-10-03', toDate: '2026-10-09' };
const trigger = () => screen.getByRole('button', { name: /^Overview dates/ });
const panel = () => within(screen.getByRole('dialog', { name: 'Choose: Overview dates' }));
const day = (name: string) => panel().getByRole('button', { name });

describe('DateRangePicker', () => {
  it('shows the chosen days, and a quick range applies at once', async () => {
    const onPick = vi.fn();
    render(<Picker initial={presetRange('7')} initialPreset="7" onPick={onPick} />);

    expect(trigger()).toHaveTextContent('Last 7 days');
    await userEvent.click(trigger());
    expect(panel().getByRole('button', { name: 'Last 7 days' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await userEvent.click(panel().getByRole('button', { name: 'Last 30 days' }));

    expect(onPick).toHaveBeenCalledWith(presetRange('30'), '30');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger()).toHaveTextContent('Last 30 days');
    expect(trigger()).toHaveFocus();
  });

  it('picks a custom range: the first day, the last day, then Apply', async () => {
    const onPick = vi.fn();
    render(<Picker initial={OCTOBER_WEEK} max="2026-10-20" onPick={onPick} />);

    expect(trigger()).toHaveTextContent('Custom · 7 days');
    expect(trigger()).toHaveTextContent('3 – 9 Oct 2026');
    await userEvent.click(trigger());
    await userEvent.click(day('Monday, 12 October 2026'));
    expect(panel().getByText('Now pick the last day.')).toBeInTheDocument();
    expect(panel().getByRole('button', { name: 'Apply' })).toBeDisabled();

    await userEvent.click(day('Thursday, 15 October 2026'));
    expect(panel().getByText('12 – 15 Oct 2026')).toBeInTheDocument();
    await userEvent.click(panel().getByRole('button', { name: 'Apply' }));

    expect(onPick).toHaveBeenCalledWith(
      { fromDate: '2026-10-12', toDate: '2026-10-15' },
      undefined,
    );
    expect(trigger()).toHaveTextContent('12 – 15 Oct 2026');
  });

  it('starts again when the second day is before the first', async () => {
    render(<Picker initial={OCTOBER_WEEK} max="2026-10-20" />);

    await userEvent.click(trigger());
    await userEvent.click(day('Monday, 12 October 2026'));
    await userEvent.click(day('Monday, 5 October 2026'));

    expect(panel().getByText('Now pick the last day.')).toBeInTheDocument();
  });

  it('picks a single day', async () => {
    const onPick = vi.fn();
    render(<Picker initial={OCTOBER_WEEK} max="2026-10-20" onPick={onPick} />);

    await userEvent.click(trigger());
    await userEvent.click(panel().getByRole('tab', { name: 'Single day' }));
    await userEvent.click(day('Friday, 16 October 2026'));
    await userEvent.click(panel().getByRole('button', { name: 'Apply' }));

    expect(onPick).toHaveBeenCalledWith(
      { fromDate: '2026-10-16', toDate: '2026-10-16' },
      undefined,
    );
    expect(trigger()).toHaveTextContent('Single day');
    expect(trigger()).toHaveTextContent('Fri, 16 Oct 2026');
  });

  it('strikes out the days after the last allowed one, and refuses a range that is too long', async () => {
    render(<Picker initial={OCTOBER_WEEK} max="2026-10-20" maxDays={5} />);

    await userEvent.click(trigger());
    expect(day('Wednesday, 21 October 2026')).toBeDisabled();
    await userEvent.click(day('Monday, 12 October 2026'));
    await userEvent.click(day('Saturday, 17 October 2026'));

    expect(panel().getByRole('alert')).toHaveTextContent('Choose at most 5 days.');
    expect(panel().getByRole('button', { name: 'Apply' })).toBeDisabled();
  });

  it('keeps the days when the panel is cancelled or closed with Escape', async () => {
    const onPick = vi.fn();
    render(<Picker initial={OCTOBER_WEEK} max="2026-10-20" onPick={onPick} />);

    await userEvent.click(trigger());
    await userEvent.click(day('Monday, 12 October 2026'));
    await userEvent.click(panel().getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await userEvent.click(trigger());
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger()).toHaveFocus();
    expect(onPick).not.toHaveBeenCalled();
    expect(trigger()).toHaveTextContent('3 – 9 Oct 2026');
  });

  it('offers "Any day" when the days may be removed', async () => {
    const onPick = vi.fn();
    render(<Picker initial={OCTOBER_WEEK} emptyLabel="Any day" onPick={onPick} />);

    await userEvent.click(screen.getByRole('button', { name: 'Clear overview dates' }));
    expect(onPick).toHaveBeenLastCalledWith(undefined, undefined);
    expect(trigger()).toHaveTextContent('Any day');

    await userEvent.click(trigger());
    expect(panel().getByRole('button', { name: 'Any day' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });
});
