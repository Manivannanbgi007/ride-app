import { afterEach, describe, expect, it } from 'vitest';

import {
  loadRazorpayCheckout,
  RAZORPAY_CHECKOUT_SCRIPT,
  RazorpayLoadError,
  type RazorpayConstructor,
} from '@/utils/razorpayCheckout';

const checkoutScripts = () =>
  Array.from(document.querySelectorAll<HTMLScriptElement>('script')).filter(
    (script) => script.src === RAZORPAY_CHECKOUT_SCRIPT,
  );

describe('loadRazorpayCheckout', () => {
  afterEach(() => {
    delete window.Razorpay;
    checkoutScripts().forEach((script) => script.remove());
  });

  it('uses checkout.js when it is loaded already', async () => {
    const Razorpay = class {} as unknown as RazorpayConstructor;
    window.Razorpay = Razorpay;

    await expect(loadRazorpayCheckout()).resolves.toBe(Razorpay);
    expect(checkoutScripts()).toHaveLength(0);
  });

  it('adds the script once, says clearly when it is blocked, and tries again next time', async () => {
    const first = loadRazorpayCheckout();
    const second = loadRazorpayCheckout();
    expect(checkoutScripts()).toHaveLength(1);

    checkoutScripts()[0].dispatchEvent(new Event('error'));
    await expect(first).rejects.toBeInstanceOf(RazorpayLoadError);
    await expect(second).rejects.toBeInstanceOf(RazorpayLoadError);
    expect(checkoutScripts()).toHaveLength(0);

    const retry = loadRazorpayCheckout();
    const Razorpay = class {} as unknown as RazorpayConstructor;
    window.Razorpay = Razorpay;
    checkoutScripts()[0].dispatchEvent(new Event('load'));
    await expect(retry).resolves.toBe(Razorpay);
  });
});
