import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { QuickFilters } from '@/components/common/QuickFilters';

const ITEMS = [
  { value: 'ALL', label: 'All', count: 1200 },
  { value: 'DELIVERED', label: 'Delivered', count: 3 },
  { value: 'CANCELLED', label: 'Cancelled' },
] as const;

describe('QuickFilters', () => {
  it('marks the chosen chip as pressed and shows the counts it knows', () => {
    render(
      <QuickFilters label="Order status" items={ITEMS} value="DELIVERED" onChange={vi.fn()} />,
    );

    expect(screen.getByRole('group', { name: 'Order status' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Delivered/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: /All/ })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: 'All 1,200' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelled' })).toBeInTheDocument();
  });

  it('hands over the chip clicked', async () => {
    const onChange = vi.fn();
    render(<QuickFilters label="Order status" items={ITEMS} value="ALL" onChange={onChange} />);

    await userEvent.click(screen.getByRole('button', { name: 'Cancelled' }));

    expect(onChange).toHaveBeenCalledWith('CANCELLED');
  });
});
