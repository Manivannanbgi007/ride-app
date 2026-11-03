import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import CouponDetailsPage from '@/pages/Coupon/CouponDetailsPage';
import CouponFormPage from '@/pages/Coupon/CouponFormPage';
import { couponService } from '@/services/couponService';
import { coupon, couponDetails, pageOf } from '@/test/fixtures';
import { pickDate } from '@/test/pickDate';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/couponService', () => ({
  couponService: {
    searchCoupons: vi.fn(),
    getCoupon: vi.fn(),
    createCoupon: vi.fn(),
    updateCoupon: vi.fn(),
    changeStatus: vi.fn(),
    getRedemptions: vi.fn(),
    getAuditLogs: vi.fn(),
  },
}));

const renderAt = (route: string) =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/coupons/new" element={<CouponFormPage />} />
      <Route path="/admin/coupons/:couponId/edit" element={<CouponFormPage />} />
      <Route path="/admin/coupons/:couponId" element={<CouponDetailsPage />} />
    </Routes>,
    { route },
  );

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(couponService.getCoupon).mockResolvedValue(couponDetails());
  vi.mocked(couponService.getAuditLogs).mockResolvedValue(pageOf([]));
  vi.mocked(couponService.getRedemptions).mockResolvedValue(pageOf([]));
});

describe('New coupon', () => {
  it('checks the rules in the browser before sending', async () => {
    renderAt('/admin/coupons/new');

    await userEvent.type(screen.getByLabelText('Code'), 'BIG');
    await userEvent.type(screen.getByLabelText('Percent off'), '150');
    await pickDate('First day', '2026-10-10');
    // A last day before the first cannot be picked
    await userEvent.click(screen.getByLabelText('Last day'));
    const calendar = screen.getByRole('dialog', { name: 'Choose: Last day' });
    expect(
      within(calendar).getByRole('button', { name: 'Thursday, 1 October 2026' }),
    ).toBeDisabled();
    await userEvent.keyboard('{Escape}');
    await userEvent.click(screen.getByRole('button', { name: 'Create coupon' }));

    expect(await screen.findByText('A percentage cannot be more than 100.')).toBeInTheDocument();
    expect(couponService.createCoupon).not.toHaveBeenCalled();
  });

  it('creates the coupon with numbers and nulls, then opens it', async () => {
    vi.mocked(couponService.createCoupon).mockResolvedValue(coupon({ id: 5 }));
    renderAt('/admin/coupons/new');

    await userEvent.type(screen.getByLabelText('Code'), 'welcome100');
    await chooseOption(screen.getByLabelText('Type'), 'Fixed amount');
    await userEvent.type(screen.getByLabelText('Amount off (₹)'), '100');
    await userEvent.type(screen.getByLabelText('Minimum items total (₹)'), '499');
    await pickDate('First day', '2026-10-01');
    await pickDate('Last day', '2026-10-31');
    await userEvent.type(screen.getByLabelText('Uses per customer'), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Create coupon' }));

    expect(couponService.createCoupon).toHaveBeenCalledWith({
      code: 'welcome100',
      description: null,
      discountType: 'FIXED',
      discountValue: 100,
      maxDiscount: null,
      minOrderAmount: 499,
      restaurantId: null,
      validFrom: '2026-10-01',
      validUntil: '2026-10-31',
      usageLimit: null,
      perCustomerLimit: 1,
    });
    expect(
      await screen.findByRole('heading', { name: 'WELCOME100', level: 1 }),
    ).toBeInTheDocument();
  });
});

describe('Change a coupon', () => {
  it('sends the version it read, and explains a change made by someone else meanwhile', async () => {
    vi.mocked(couponService.updateCoupon).mockRejectedValue(
      new ApiError({
        kind: 'conflict',
        status: 409,
        userMessage: 'The coupon was changed by someone else (version 5). Reload it and try again.',
        technicalMessage: 'PUT failed with 409',
      }),
    );
    renderAt('/admin/coupons/5/edit');

    expect(await screen.findByLabelText('Code')).toHaveAttribute('readonly');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(couponService.updateCoupon).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ version: 4, discountValue: 100, usageLimit: 1000 }),
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('changed by someone else');
  });
});

describe('CouponDetailsPage', () => {
  it('shows the terms and usage, and pauses the coupon with a reason', async () => {
    vi.mocked(couponService.changeStatus).mockResolvedValue(
      coupon({ status: 'INACTIVE', availability: 'INACTIVE' }),
    );
    renderAt('/admin/coupons/5');

    expect(await screen.findByText('100.00 off')).toBeInTheDocument();
    expect(screen.getByText('₹1,100.00')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Pause' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Campaign over');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Pause' }));

    expect(couponService.changeStatus).toHaveBeenCalledWith(5, false, 'Campaign over');
    expect(await screen.findByText('WELCOME100 is paused.')).toBeInTheDocument();
  });

  it('filters the uses by status', async () => {
    renderAt('/admin/coupons/5');

    await chooseOption(await screen.findByLabelText('Status of the uses'), 'Redeemed (delivered)');

    expect(couponService.getRedemptions).toHaveBeenLastCalledWith(5, 'REDEEMED', 0, 10);
  });
});
