import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import PaymentDetailsPage from '@/pages/Payment/PaymentDetailsPage';
import PaymentListPage from '@/pages/Payment/PaymentListPage';
import { orderService } from '@/services/orderService';
import { paymentService } from '@/services/paymentService';
import { orderDetails, pageOf, payment, refund } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/paymentService', () => ({
  paymentService: {
    searchPayments: vi.fn(),
    getSummary: vi.fn(),
    getPayment: vi.fn(),
    refundPayment: vi.fn(),
    getAuditLogs: vi.fn(),
    searchRefunds: vi.fn(),
    getRefund: vi.fn(),
  },
}));
vi.mock('@/services/orderService', () => ({
  orderService: { getOrder: vi.fn() },
}));

const renderPayment = () =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/payments/:paymentId" element={<PaymentDetailsPage />} />
    </Routes>,
    { route: '/admin/payments/77' },
  );

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(paymentService.getAuditLogs).mockResolvedValue(pageOf([]));
  vi.mocked(paymentService.getPayment).mockResolvedValue({
    payment: payment({ refundedAmount: 100 }),
    refunds: [refund()],
    events: [],
  });
  vi.mocked(orderService.getOrder).mockResolvedValue(orderDetails({ status: 'DELIVERED' }));
});

describe('PaymentListPage', () => {
  it("shows the period's money and the payments, and filters them", async () => {
    vi.mocked(paymentService.getSummary).mockResolvedValue({
      fromDate: '2026-10-08',
      toDate: '2026-10-08',
      byStatus: { SUCCESS: { count: 40, amount: 18000 }, FAILED: { count: 3, amount: 900 } },
      byMethod: {},
      collectedAmount: 18000,
      refundedAmount: 450,
      currency: 'INR',
    });
    vi.mocked(paymentService.searchPayments).mockResolvedValue(pageOf([payment()]));
    renderWithProviders(<PaymentListPage />);

    expect(await screen.findByText('₹18,000.00')).toBeInTheDocument();
    expect(screen.getByText('40')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: '#77' })).toBeInTheDocument();

    await chooseOption(screen.getByLabelText('Status'), 'Failed');
    expect(paymentService.searchPayments).toHaveBeenLastCalledWith({ status: 'FAILED' }, 0, 20);
  });
});

describe('PaymentDetailsPage refunds', () => {
  it('refunds part of a payment once, with a reason and an idempotency key', async () => {
    vi.mocked(paymentService.refundPayment).mockResolvedValue(refund({ id: 10, amount: 50 }));
    renderPayment();

    await userEvent.click(await screen.findByRole('button', { name: 'Refund' }));
    const dialog = screen.getByRole('dialog', { name: 'Refund payment #77' });
    // 562.90 paid, 100.00 refunded already: at most 462.90
    await userEvent.type(within(dialog).getByLabelText('Amount (₹)'), '500');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Late delivery');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Refund' }));
    expect(await within(dialog).findByText(/Between 0.01 and 462.90/)).toBeInTheDocument();
    expect(paymentService.refundPayment).not.toHaveBeenCalled();

    await userEvent.clear(within(dialog).getByLabelText('Amount (₹)'));
    await userEvent.type(within(dialog).getByLabelText('Amount (₹)'), '50');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Refund' }));

    expect(paymentService.refundPayment).toHaveBeenCalledWith(
      77,
      50,
      'Late delivery',
      expect.stringMatching(/^[A-Za-z0-9_-]{8,64}$/),
    );
    expect(await screen.findByText('₹50.00 refunded to the customer.')).toBeInTheDocument();
  });

  it('says so when the payment provider refused the refund', async () => {
    vi.mocked(paymentService.refundPayment).mockResolvedValue(
      refund({ status: 'FAILED', failureReason: 'Provider could not refund to this account' }),
    );
    renderPayment();

    await userEvent.click(await screen.findByRole('button', { name: 'Refund' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Cancelled');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Refund' }));

    expect(paymentService.refundPayment).toHaveBeenCalledWith(
      77,
      null,
      'Cancelled',
      expect.any(String),
    );
    expect(
      await screen.findByText(/Provider could not refund to this account/),
    ).toBeInTheDocument();
  });

  it('explains why an order still in progress cannot be refunded yet', async () => {
    vi.mocked(orderService.getOrder).mockResolvedValue(orderDetails({ status: 'PREPARING' }));
    renderPayment();

    expect(await screen.findByText(/is preparing: it must be cancelled/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Refund' })).toBeDisabled();
  });

  it('offers no refund for a failed payment', async () => {
    vi.mocked(paymentService.getPayment).mockResolvedValue({
      payment: payment({ status: 'FAILED', failureReason: 'Card declined' }),
      refunds: [],
      events: [],
    });
    renderPayment();

    expect(await screen.findByText('Card declined')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Refund' })).not.toBeInTheDocument();
  });
});
