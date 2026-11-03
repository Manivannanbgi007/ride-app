import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Bell } from 'lucide-react';
import { describe, expect, it } from 'vitest';

import { Tooltip } from '@/components/common/Tooltip';
import { HeaderIconLink } from '@/components/common/HeaderIcon';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('tooltips', () => {
  it('show a header icon title on hover and keyboard focus, and go again', async () => {
    renderWithProviders(<HeaderIconLink to="/coupons" label="Coupons" icon={Bell} />);
    const link = screen.getByRole('link', { name: 'Coupons' });

    await userEvent.hover(link);
    expect(screen.getByText('Coupons')).toBeInTheDocument();
    await userEvent.unhover(link);
    await waitFor(() => expect(screen.queryByText('Coupons')).not.toBeInTheDocument());

    await userEvent.tab();
    expect(link).toHaveFocus();
    expect(screen.getByText('Coupons')).toBeInTheDocument();
  });

  it('wraps any button, also a disabled one, and hides on click', async () => {
    renderWithProviders(
      <>
        <Tooltip label="Delete it" icon={Bell}>
          <button type="button">Enabled</button>
        </Tooltip>
        <Tooltip label="Why it is off">
          <button type="button" disabled>
            Disabled
          </button>
        </Tooltip>
      </>,
    );

    await userEvent.hover(screen.getByRole('button', { name: 'Disabled' }));
    expect(screen.getByText('Why it is off')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Enabled' }));
    await waitFor(() => expect(screen.queryByText('Delete it')).not.toBeInTheDocument());
  });
});
