import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { TimePicker } from '@/components/common/TimePicker';

function Picker({ initial, onPick }: { initial: string; onPick: (value: string) => void }) {
  const [value, setValue] = useState(initial);
  return (
    <TimePicker
      label="Monday opens at"
      value={value}
      onChange={(next) => {
        setValue(next);
        onPick(next);
      }}
    />
  );
}

describe('TimePicker', () => {
  it('shows the time the Indian way and changes the hour and the minute', async () => {
    const onPick = vi.fn();
    render(<Picker initial="09:30" onPick={onPick} />);

    const field = screen.getByRole('button', { name: 'Monday opens at 9:30 am' });
    await userEvent.click(field);
    const panel = within(screen.getByRole('dialog', { name: 'Monday opens at' }));
    await userEvent.click(within(panel.getByRole('group', { name: 'Hour' })).getByText('7 pm'));
    expect(onPick).toHaveBeenLastCalledWith('19:30');
    await userEvent.click(within(panel.getByRole('group', { name: 'Minute' })).getByText(':45'));
    expect(onPick).toHaveBeenLastCalledWith('19:45');

    await userEvent.click(panel.getByRole('button', { name: 'Done' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Monday opens at 7:45 pm' })).toHaveFocus();
  });

  it("offers the time's own minute, and moves with the arrow keys", async () => {
    const onPick = vi.fn();
    render(<Picker initial="23:59" onPick={onPick} />);

    await userEvent.click(screen.getByRole('button', { name: /Monday opens at/ }));
    const minutes = within(screen.getByRole('group', { name: 'Minute' }));
    const ownMinute = minutes.getByRole('button', { name: ':59' });
    expect(ownMinute).toHaveAttribute('aria-pressed', 'true');

    ownMinute.focus();
    await userEvent.keyboard('{ArrowUp}');
    expect(onPick).toHaveBeenLastCalledWith('23:55');
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
