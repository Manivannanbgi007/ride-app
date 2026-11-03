import { screen } from '@testing-library/react';
import { Bell } from 'lucide-react';
import { describe, expect, it } from 'vitest';

import { HeaderIconLink } from '@/components/common/HeaderIcon';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('HeaderIconLink', () => {
  it('is a named link with its number on the icon', () => {
    renderWithProviders(
      <HeaderIconLink to="/notifications" label="Notifications (4 unread)" icon={Bell} count={4} />,
    );

    const link = screen.getByRole('link', { name: 'Notifications (4 unread)' });
    expect(link).toHaveAttribute('href', '/notifications');
    expect(link).toHaveTextContent('4');
  });

  it('shows 99+ for large numbers and no badge at 0', () => {
    const { unmount } = renderWithProviders(
      <HeaderIconLink to="/notifications" label="Notifications" icon={Bell} count={120} />,
    );
    expect(screen.getByRole('link')).toHaveTextContent('99+');
    unmount();

    renderWithProviders(
      <HeaderIconLink to="/notifications" label="Notifications" icon={Bell} count={0} />,
    );
    expect(screen.getByRole('link', { name: 'Notifications' })).toHaveTextContent('');
  });
});
