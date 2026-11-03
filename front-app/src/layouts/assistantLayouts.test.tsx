import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { CustomerLayout } from '@/layouts/customer/CustomerLayout';
import { MainLayout } from '@/layouts/MainLayout';
import { PartnerLayout } from '@/layouts/partner/PartnerLayout';
import { makeStore } from '@/store/store';
import { renderWithProviders } from '@/test/renderWithProviders';

// Only the assistant is under test: the headers and the sidebar have their own tests
vi.mock('@/layouts/Header', () => ({ Header: () => null }));
vi.mock('@/layouts/Sidebar', () => ({ Sidebar: () => null }));
vi.mock('@/layouts/customer/CustomerHeader', () => ({ CustomerHeader: () => null }));
vi.mock('@/layouts/partner/PartnerStatusChip', () => ({ PartnerStatusChip: () => null }));
vi.mock('@/layouts/partner/PartnerAccountMenu', () => ({ PartnerAccountMenu: () => null }));
vi.mock('@/services/assistantService', () => ({
  assistantService: {},
  ASSISTANT_CHAT_TIMEOUT_MS: 180_000,
}));

const launcher = () => screen.queryByRole('button', { name: 'Ask the assistant' });

const customer = { id: 7, role: 'CUSTOMER' as const, fullName: 'Priya', email: 'p@example.com' };

function renderAdmin(adminUser: { id: number; role: 'PLATFORM_ADMIN' } | null) {
  return renderWithProviders(
    <Routes>
      <Route path="/admin" element={<MainLayout />}>
        <Route path="orders" element={<p>Orders page</p>} />
      </Route>
    </Routes>,
    { route: '/admin/orders', store: makeStore({ session: { adminUser, customer: null } }) },
  );
}

function renderStorefront(signedIn: boolean) {
  return renderWithProviders(
    <Routes>
      <Route element={<CustomerLayout />}>
        <Route path="/restaurants" element={<p>Restaurants page</p>} />
      </Route>
    </Routes>,
    {
      route: '/restaurants',
      store: makeStore({ session: { adminUser: null, customer: signedIn ? customer : null } }),
    },
  );
}

describe('the assistant button in the layouts', () => {
  it('is in the admin layout when the admin area has an acting user', () => {
    renderAdmin({ id: 1, role: 'PLATFORM_ADMIN' });
    expect(screen.getByText('Orders page')).toBeInTheDocument();
    expect(launcher()).toBeInTheDocument();
  });

  it('is not in the admin layout without an acting user', () => {
    renderAdmin(null);
    expect(screen.getByText('Orders page')).toBeInTheDocument();
    expect(launcher()).not.toBeInTheDocument();
  });

  it('is in the storefront layout for a signed-in customer only', () => {
    const signedIn = renderStorefront(true);
    expect(launcher()).toBeInTheDocument();
    signedIn.unmount();

    renderStorefront(false);
    expect(screen.getByText('Restaurants page')).toBeInTheDocument();
    expect(launcher()).not.toBeInTheDocument();
  });

  it('is in the partner layout for a signed-in delivery partner, above the bottom tab bar on a phone', () => {
    const partner = {
      id: 8,
      role: 'DELIVERY_PARTNER' as const,
      fullName: 'Ravi',
      email: 'r@example.com',
    };
    const signedIn = renderWithProviders(
      <Routes>
        <Route path="/partner" element={<PartnerLayout>{<p>Today page</p>}</PartnerLayout>} />
      </Routes>,
      {
        route: '/partner',
        store: makeStore({ session: { adminUser: null, customer: null, partner } }),
      },
    );
    expect(screen.getByText('Today page')).toBeInTheDocument();
    // The wrapper of the round button sits higher than the tab bar below the lg breakpoint
    expect(launcher()?.parentElement).toHaveClass(
      'bottom-[calc(5.5rem+env(safe-area-inset-bottom))]',
    );
    signedIn.unmount();

    renderWithProviders(
      <Routes>
        <Route path="/partner" element={<PartnerLayout>{<p>Today page</p>}</PartnerLayout>} />
      </Routes>,
      { route: '/partner', store: makeStore({ session: { adminUser: null, customer } }) },
    );
    // A customer signed in elsewhere is not the partner area's user
    expect(launcher()).not.toBeInTheDocument();
  });
});
