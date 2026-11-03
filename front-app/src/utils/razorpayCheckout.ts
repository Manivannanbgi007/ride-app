import type { RazorpayCheckoutSession, RazorpayVerifyRequest } from '@/types/payment';

/**
 * Razorpay Checkout (Standard Checkout, https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/):
 * Razorpay's own payment window. Card numbers, UPI PINs and bank logins are typed into Razorpay's window, never into
 * this app. Checkout only needs the public key id and the Razorpay order that payment-service created; the key
 * secret stays on the server.
 */
export const RAZORPAY_CHECKOUT_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

/** What Checkout's handler receives once a payment went through (not proof by itself: the server verifies it). */
interface RazorpaySuccessResponse {
  razorpay_payment_id: string;
  razorpay_order_id?: string;
  razorpay_signature: string;
}

/** What the 'payment.failed' event carries (Checkout stays open: the customer may try another way). */
interface RazorpayFailureResponse {
  error: { code?: string; description?: string; reason?: string };
}

/** The Checkout options this app uses. */
interface RazorpayCheckoutOptions {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  prefill?: { name?: string; email?: string; method?: string };
  notes?: Record<string, string>;
  handler: (response: RazorpaySuccessResponse) => void;
  modal?: { ondismiss?: () => void; confirm_close?: boolean };
}

interface RazorpayInstance {
  open: () => void;
  on: (event: 'payment.failed', listener: (response: RazorpayFailureResponse) => void) => void;
}

export type RazorpayConstructor = new (options: RazorpayCheckoutOptions) => RazorpayInstance;

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

/** checkout.js could not be loaded (offline, or blocked by an ad or script blocker). */
export class RazorpayLoadError extends Error {
  constructor() {
    super('Razorpay Checkout could not be loaded');
    this.name = 'RazorpayLoadError';
  }
}

let loading: Promise<RazorpayConstructor> | null = null;

/** Loads checkout.js once, when the customer is about to pay (not on every page of the app). */
export function loadRazorpayCheckout(): Promise<RazorpayConstructor> {
  if (window.Razorpay) {
    return Promise.resolve(window.Razorpay);
  }
  loading ??= new Promise<RazorpayConstructor>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = RAZORPAY_CHECKOUT_SCRIPT;
    script.async = true;
    const fail = () => {
      loading = null; // the next try loads it again
      script.remove();
      reject(new RazorpayLoadError());
    };
    script.onload = () => (window.Razorpay ? resolve(window.Razorpay) : fail());
    script.onerror = fail;
    document.body.appendChild(script);
  });
  return loading;
}

/** How a Checkout visit ended. */
export type RazorpayCheckoutOutcome =
  /** Checkout reports a payment: still to be verified by payment-service */
  | { kind: 'paid'; result: RazorpayVerifyRequest }
  /** The customer closed the window without paying */
  | { kind: 'cancelled' }
  /** The customer closed the window after an attempt failed (declined card, wrong UPI PIN, ...) */
  | { kind: 'failed'; reason: string };

/** Opens Checkout for a session from payment-service and waits until the customer pays or closes it. */
export function openRazorpayCheckout(
  Razorpay: RazorpayConstructor,
  session: RazorpayCheckoutSession,
  customer: { name?: string; email?: string },
): Promise<RazorpayCheckoutOutcome> {
  return new Promise((resolve) => {
    let lastFailure: string | null = null;
    const checkout = new Razorpay({
      key: session.keyId,
      amount: session.amount,
      currency: session.currency,
      name: session.merchantName,
      description: session.description,
      order_id: session.razorpayOrderId,
      prefill: { name: customer.name, email: customer.email, method: session.preferredMethod },
      notes: { orderId: String(session.orderId) },
      handler: (response) =>
        resolve({
          kind: 'paid',
          result: {
            razorpayOrderId: response.razorpay_order_id ?? session.razorpayOrderId,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          },
        }),
      modal: {
        // Asks "Are you sure?" before closing, so a payment in progress is not dropped by accident
        confirm_close: true,
        ondismiss: () =>
          resolve(
            lastFailure === null ? { kind: 'cancelled' } : { kind: 'failed', reason: lastFailure },
          ),
      },
    });
    checkout.on('payment.failed', (response) => {
      lastFailure = response.error.description ?? 'The payment failed.';
    });
    checkout.open();
  });
}
