import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import DevAccountsPanel from '@/components/business/Auth/DevAccountsPanel';
import OwnerSignInPage from '@/pages/Owner/OwnerSignInPage';
import SignInPage from '@/pages/Storefront/SignInPage';
import { userService } from '@/services/userService';
import { renderWithProviders } from '@/test/renderWithProviders';

// The pages with the panel, as npm run dev shows them (the tests' settings leave it out: vite.config.ts)
vi.mock('@/components/business/Auth/DevAccounts', async () => ({
  DevAccounts: (await import('@/components/business/Auth/DevAccountsPanel')).default,
}));
vi.mock('@/services/userService', () => ({ userService: { login: vi.fn() } }));

describe('the test accounts under the sign-in forms', () => {
  it("lists the area's own accounts with their email and password", () => {
    render(<DevAccountsPanel audience="owner" onUse={vi.fn()} isBusy={false} />);

    expect(screen.getByRole('heading', { name: /Test accounts/ })).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: 'Sign in as Test Owner (test.owner@fooddelivery.local)',
      }),
    ).toBeInTheDocument();
    expect(screen.getByText('Test@12345')).toBeInTheDocument();
    // Another area's accounts are not offered: that area's sign-in would refuse them
    expect(screen.queryByText('manual.customer@example.com')).not.toBeInTheDocument();
  });

  it('hands over the clicked account, and blocks a second click while signing in', async () => {
    const onUse = vi.fn();
    const { rerender } = render(<DevAccountsPanel audience="admin" onUse={onUse} isBusy={false} />);

    await userEvent.click(screen.getByRole('button', { name: /Sign in as Platform Admin/ }));
    expect(onUse).toHaveBeenCalledWith({
      email: 'admin@fooddelivery.local',
      password: 'ChangeMe123',
    });

    rerender(<DevAccountsPanel audience="admin" onUse={onUse} isBusy />);
    expect(screen.getByRole('button', { name: /Sign in as Platform Admin/ })).toBeDisabled();
    expect(screen.getByRole('status', { name: 'Signing in' })).toBeInTheDocument();
  });

  it('customer sign-in: one click fills the form and signs in, as if it had been typed', async () => {
    // Still waiting for user-service: the page stays busy
    vi.mocked(userService.login).mockReturnValue(new Promise(() => {}));
    renderWithProviders(<SignInPage />, { route: '/customer/sign-in' });

    await userEvent.click(screen.getByRole('button', { name: /Sign in as Manual Customer/ }));

    expect(userService.login).toHaveBeenCalledWith(
      'manual.customer@example.com',
      'Customer@123',
      'CUSTOMER',
    );
    expect(screen.getByLabelText('Email')).toHaveValue('manual.customer@example.com');
    expect(screen.getByRole('button', { name: /Sign in as Manual Customer/ })).toBeDisabled();
  });

  it("owner sign-in: the owner's account signs in to the restaurant area", async () => {
    vi.mocked(userService.login).mockReturnValue(new Promise(() => {}));
    renderWithProviders(<OwnerSignInPage />, { route: '/owner/sign-in' });

    await userEvent.click(screen.getByRole('button', { name: /Sign in as Test Owner/ }));

    expect(userService.login).toHaveBeenCalledWith(
      'test.owner@fooddelivery.local',
      'Test@12345',
      'RESTAURANT_ADMIN',
    );
  });
});
