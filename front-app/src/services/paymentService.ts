import { apiClient } from '@/api/apiClient';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  Payment,
  PaymentDetails,
  PaymentFilters,
  PaymentSummary,
  RazorpayCheckoutSession,
  RazorpayConfig,
  RazorpayVerifyRequest,
  Refund,
  RefundFilters,
} from '@/types/payment';
import type { PayOrderRequest } from '@/types/shopping';
import { API_ENDPOINTS, IDEMPOTENCY_KEY_HEADER } from '@/utils/apiEndpoints';

/** payment-service APIs: the admin's payments and refunds, and a customer paying an order. */
export const paymentService = {
  /**
   * Pays an order waiting for payment. A declined payment is not an error: it comes back with status FAILED and
   * the reason. Retrying after a timeout uses the same key (never charged twice); a new attempt after a decline
   * needs a new key.
   */
  payOrder: (request: PayOrderRequest, idempotencyKey: string) =>
    apiClient.post<Payment>(API_ENDPOINTS.payments.pay, request, {
      headers: { [IDEMPOTENCY_KEY_HEADER]: idempotencyKey },
    }),

  /** Whether online payments go through Razorpay Checkout, and its public key id. */
  getRazorpayConfig: () => apiClient.get<RazorpayConfig>(API_ENDPOINTS.payments.razorpay.config),

  /**
   * Starts paying an order with Razorpay: payment-service creates the Razorpay order for the order's total (the
   * browser never sends an amount). Asking again for the same order returns the same Razorpay order.
   */
  startRazorpayCheckout: (orderId: number) =>
    apiClient.post<RazorpayCheckoutSession>(API_ENDPOINTS.payments.razorpay.orders, { orderId }),

  /**
   * Hands Checkout's result to payment-service, which checks the signature and asks Razorpay itself. Resolves with
   * the payment: SUCCESS (paid) or PROCESSING (not confirmed yet); a wrong signature is rejected (422).
   */
  verifyRazorpayPayment: (request: RazorpayVerifyRequest) =>
    apiClient.post<Payment>(API_ENDPOINTS.payments.razorpay.verify, request),

  searchPayments: (filters: PaymentFilters, page: number, size: number) =>
    apiClient.get<PageResponse<Payment>>(API_ENDPOINTS.admin.payments.list, {
      params: { ...filters, page, size, sort: 'createdAt,desc' },
    }),

  /** Totals per status and method for a period (both dates empty: today). */
  getSummary: (fromDate?: string, toDate?: string) =>
    apiClient.get<PaymentSummary>(API_ENDPOINTS.admin.payments.summary, {
      params: { fromDate, toDate },
    }),

  /** The payment, its refunds and the events it published. */
  getPayment: (paymentId: number) =>
    apiClient.get<PaymentDetails>(API_ENDPOINTS.admin.payments.details(paymentId)),

  /**
   * Refunds a successful payment (its order must be cancelled, rejected or delivered). amount null = everything
   * not refunded yet. The idempotency key makes a retry return the first refund instead of refunding again.
   * The refund is done at once: check its status, a provider failure comes back as a FAILED refund (not an error).
   */
  refundPayment: (
    paymentId: number,
    amount: number | null,
    reason: string,
    idempotencyKey: string,
  ) =>
    apiClient.post<Refund>(
      API_ENDPOINTS.admin.payments.refund(paymentId),
      { amount, reason },
      { headers: { [IDEMPOTENCY_KEY_HEADER]: idempotencyKey } },
    ),

  getAuditLogs: (paymentId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(API_ENDPOINTS.admin.payments.auditLogs(paymentId), {
      params: { size: 50 },
    }),

  searchRefunds: (filters: RefundFilters, page: number, size: number) =>
    apiClient.get<PageResponse<Refund>>(API_ENDPOINTS.admin.refunds.list, {
      params: { ...filters, page, size, sort: 'requestedAt,desc' },
    }),

  getRefund: (refundId: number) =>
    apiClient.get<Refund>(API_ENDPOINTS.admin.refunds.details(refundId)),
};
