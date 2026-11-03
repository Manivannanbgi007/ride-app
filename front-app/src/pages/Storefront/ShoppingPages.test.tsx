import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useSearchParams } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { RequireCustomer } from '@/layouts/customer/RequireCustomer';
import AddressesPage from '@/pages/Storefront/AddressesPage';
import CartPage from '@/pages/Storefront/CartPage';
import CheckoutPage from '@/pages/Storefront/CheckoutPage';
import OffersPage from '@/pages/Storefront/OffersPage';
import PaymentPage from '@/pages/Storefront/PaymentPage';
import RestaurantMenuPage from '@/pages/Storefront/RestaurantMenuPage';
import SignInPage from '@/pages/Storefront/SignInPage';
import { catalogService } from '@/services/catalogService';
import { couponService } from '@/services/couponService';
import { orderService } from '@/services/orderService';
import { paymentService } from '@/services/paymentService';
import { restaurantService } from '@/services/restaurantService';
import { userService } from '@/services/userService';
import { makeStore } from '@/store/store';
import { address, cart, emptyCart, orderDetails, pageOf, payment, priya } from '@/test/fixtures';
import { chooseOption } from '@/test/chooseOption';
import {
  geolocationError,
  installFakeGeolocation,
  positionAt,
  removeFakeGeolocation,
} from '@/test/fakeGeolocation';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/orderService', () => ({
  orderService: {
    getCart: vi.fn(),
    setCartQuantity: vi.fn(),
    removeFromCart: vi.fn(),
    clearCart: vi.fn(),
    applyCoupon: vi.fn(),
    removeCoupon: vi.fn(),
    placeOrder: vi.fn(),
    getMyOrder: vi.fn(),
  },
}));
vi.mock('@/services/userService', () => ({
  userService: {
    getAddresses: vi.fn(),
    addAddress: vi.fn(),
    updateAddress: vi.fn(),
    makeDefaultAddress: vi.fn(),
    deleteAddress: vi.fn(),
  },
}));
vi.mock('@/services/paymentService', () => ({
  paymentService: {
    payOrder: vi.fn(),
    getRazorpayConfig: vi.fn(),
    startRazorpayCheckout: vi.fn(),
    verifyRazorpayPayment: vi.fn(),
  },
}));
vi.mock('@/services/couponService', () => ({ couponService: { getOffers: vi.fn() } }));
vi.mock('@/services/restaurantService', () => ({
  restaurantService: { getStoreRestaurant: vi.fn(), browseRestaurants: vi.fn() },
}));
vi.mock('@/services/catalogService', () => ({ catalogService: { getRestaurantMenu: vi.fn() } }));

const apiError = (status: number, kind: ApiError['kind'], userMessage: string) =>
  new ApiError({ kind, status, userMessage, technicalMessage: userMessage });

const signedIn = () => makeStore({ session: { adminUser: null, customer: priya } });

/** Renders a page at an address, with markers for where it may lead. */
function renderAt(route: string, path: string, page: React.ReactElement, store = signedIn()) {
  return renderWithProviders(
    <Routes>
      <Route path={path} element={page} />
      <Route path="/customer/cart" element={<p>Cart page</p>} />
      <Route path="/customer/orders/:orderId" element={<p>Order page</p>} />
      <Route path="/customer/orders/:orderId/pay" element={<p>Payment page</p>} />
      <Route path="/customer/sign-in" element={<p>Sign-in page</p>} />
    </Routes>,
    { route, store },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(orderService.getCart).mockResolvedValue(cart());
  vi.mocked(userService.getAddresses).mockResolvedValue([address()]);
  vi.mocked(restaurantService.getStoreRestaurant).mockResolvedValue({
    id: 3,
    name: 'Spice Garden',
    description: null,
    cuisines: ['Biryani'],
    phone: '+919876500012',
    address: {
      line1: '12 MG Road',
      line2: null,
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
      country: 'India',
      latitude: null,
      longitude: null,
    },
    timeZone: 'Asia/Kolkata',
    operatingHours: [],
    openNow: true,
  });
});

describe('Add to cart on the menu', () => {
  beforeEach(() => {
    vi.mocked(catalogService.getRestaurantMenu).mockResolvedValue({
      restaurantId: 3,
      restaurantName: 'Spice Garden',
      sections: [
        {
          categoryId: 3,
          categoryName: 'Biryani',
          items: [
            {
              id: 12,
              name: 'Veg Biryani',
              description: null,
              price: 220,
              foodType: 'VEG',
              imageUrl: null,
              available: true,
            },
          ],
        },
      ],
    });
  });

  it('adds a dish, then changes how many', async () => {
    vi.mocked(orderService.getCart).mockResolvedValue(emptyCart);
    const withOne = cart({
      items: [
        {
          menuItemId: 12,
          name: 'Veg Biryani',
          foodType: 'VEG',
          unitPrice: 220,
          quantity: 1,
          lineTotal: 220,
          orderable: true,
        },
      ],
    });
    vi.mocked(orderService.setCartQuantity)
      .mockResolvedValueOnce(withOne)
      .mockResolvedValueOnce(
        cart({ items: [{ ...withOne.items[0], quantity: 2, lineTotal: 440 }] }),
      );
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
    );

    await userEvent.click(await screen.findByRole('button', { name: 'Add Veg Biryani' }));
    expect(orderService.setCartQuantity).toHaveBeenCalledWith(12, 1);
    expect(await screen.findByText('Veg Biryani is in your cart.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add one more Veg Biryani' }));
    expect(orderService.setCartQuantity).toHaveBeenLastCalledWith(12, 2);
    expect(await screen.findByRole('link', { name: /2 dishes/ })).toHaveAttribute(
      'href',
      '/customer/cart',
    );
  });

  it("offers a new cart when the cart holds another restaurant's dishes", async () => {
    vi.mocked(orderService.getCart).mockResolvedValue(cart({ restaurantId: 9 }));
    vi.mocked(orderService.clearCart).mockResolvedValue(undefined);
    vi.mocked(orderService.setCartQuantity).mockResolvedValue(cart());
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
    );

    await userEvent.click(await screen.findByRole('button', { name: 'Add Veg Biryani' }));
    const dialog = screen.getByRole('dialog', { name: 'Start a new cart?' });
    expect(orderService.setCartQuantity).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Start a new cart' }));
    await waitFor(() => expect(orderService.setCartQuantity).toHaveBeenCalledWith(12, 1));
    expect(orderService.clearCart).toHaveBeenCalled();
  });

  it('asks visitors to sign in first', async () => {
    renderAt(
      '/customer/restaurants/3',
      '/customer/restaurants/:restaurantId',
      <RestaurantMenuPage />,
      makeStore(),
    );

    await userEvent.click(await screen.findByRole('link', { name: 'Sign in to add Veg Biryani' }));
    expect(await screen.findByText('Sign-in page')).toBeInTheDocument();
    expect(orderService.getCart).not.toHaveBeenCalled();
  });
});

describe('CartPage', () => {
  it('shows the dishes and the price, and applies a coupon', async () => {
    vi.mocked(orderService.applyCoupon)
      .mockRejectedValueOnce(
        apiError(
          422,
          'validation',
          'Add items worth 100.00 more to use this coupon (minimum order 698.00)',
        ),
      )
      .mockResolvedValueOnce(
        cart({
          coupon: {
            code: 'SAVE50',
            description: null,
            applied: true,
            discountAmount: 50,
            reason: null,
          },
        }),
      );
    renderAt('/customer/cart', '/customer/cart', <CartPage />);

    expect(await screen.findByText('Chicken Biryani')).toBeInTheDocument();
    expect(screen.getByText('₹627.90')).toBeInTheDocument();
    expect(await screen.findByText('Spice Garden')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Checkout' })).toBeEnabled();

    await userEvent.type(screen.getByLabelText('Coupon code'), 'save50');
    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText(/Add items worth 100.00 more/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText('SAVE50 applied: you save ₹50.00.')).toBeInTheDocument();
    expect(orderService.applyCoupon).toHaveBeenLastCalledWith('save50');
  });

  it('keeps checkout closed while the cart has a problem, and hints at free delivery', async () => {
    vi.mocked(orderService.getCart).mockResolvedValue(
      cart({
        orderable: false,
        problems: ["'Chicken Biryani' is not available right now"],
        price: {
          itemsTotal: 299,
          discountAmount: 0,
          deliveryFee: 40,
          taxAmount: 14.95,
          totalAmount: 353.95,
          currency: 'INR',
        },
      }),
    );
    renderAt('/customer/cart', '/customer/cart', <CartPage />);

    expect(
      await screen.findByText("'Chicken Biryani' is not available right now"),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Checkout' })).toBeDisabled();
    expect(screen.getByText('Add ₹200.00 more for free delivery.')).toBeInTheDocument();
  });
});

describe('CheckoutPage', () => {
  it('places the order to the default address and goes on to the payment', async () => {
    vi.mocked(orderService.placeOrder).mockResolvedValue(
      orderDetails({ id: 50, status: 'PAYMENT_PENDING', paymentStatus: 'PENDING' }),
    );
    renderAt('/customer/checkout', '/customer/checkout', <CheckoutPage />);

    expect(await screen.findByRole('radio', { name: /Home \(default\)/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /UPI/ })).toBeChecked();
    await userEvent.type(screen.getByLabelText('Note for the restaurant'), 'Less spicy');
    await userEvent.click(screen.getByRole('button', { name: /Place order/ }));

    expect(await screen.findByText('Payment page')).toBeInTheDocument();
    expect(orderService.placeOrder).toHaveBeenCalledWith(
      { addressId: 21, paymentMethod: 'UPI', notes: 'Less spicy' },
      expect.stringMatching(/^[A-Za-z0-9_-]{8,64}$/),
    );
  });

  it('retries with the same key after a timeout, and with a new one after a refusal', async () => {
    vi.mocked(orderService.placeOrder)
      .mockRejectedValueOnce(
        apiError(504, 'gatewayTimeout', 'The order service did not answer in time.'),
      )
      .mockRejectedValueOnce(apiError(422, 'validation', "'Spice Garden' is closed right now"))
      .mockResolvedValueOnce(
        orderDetails({ id: 51, status: 'PLACED', paymentMethod: 'CASH_ON_DELIVERY' }),
      );
    renderAt('/customer/checkout', '/customer/checkout', <CheckoutPage />);

    await userEvent.click(await screen.findByRole('radio', { name: /Cash on delivery/ }));
    const place = screen.getByRole('button', { name: /Place order/ });
    await userEvent.click(place);
    expect(
      await screen.findByText('The order service did not answer in time.'),
    ).toBeInTheDocument();
    await userEvent.click(place);
    expect(await screen.findByText(/is closed right now/)).toBeInTheDocument();
    await userEvent.click(place);
    expect(await screen.findByText('Order page')).toBeInTheDocument();

    const keys = vi.mocked(orderService.placeOrder).mock.calls.map((call) => call[1]);
    expect(keys[1]).toBe(keys[0]);
    expect(keys[2]).not.toBe(keys[1]);
  });

  it('warns when the restaurant is closed right now', async () => {
    vi.mocked(restaurantService.getStoreRestaurant).mockResolvedValue({
      ...(await restaurantService.getStoreRestaurant(3)),
      openNow: false,
    });
    renderAt('/customer/checkout', '/customer/checkout', <CheckoutPage />);

    expect(await screen.findByText('Spice Garden is closed right now')).toBeInTheDocument();
  });
  it('adds a first address on the way', async () => {
    vi.mocked(userService.getAddresses)
      .mockResolvedValueOnce([])
      .mockResolvedValue([address({ id: 22 })]);
    vi.mocked(userService.addAddress).mockResolvedValue(address({ id: 22 }));
    renderAt('/customer/checkout', '/customer/checkout', <CheckoutPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Add your address' }));
    const dialog = screen.getByRole('dialog', { name: 'New address' });
    await userEvent.type(within(dialog).getByLabelText('Street and number'), '4 Church Street');
    await userEvent.type(within(dialog).getByLabelText('City'), 'Bengaluru');
    await userEvent.type(within(dialog).getByLabelText('State'), 'Karnataka');
    await userEvent.type(within(dialog).getByLabelText('Postal code'), '560001');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add address' }));

    expect(userService.addAddress).toHaveBeenCalledWith({
      type: 'HOME',
      line1: '4 Church Street',
      line2: undefined,
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
      country: 'India',
      latitude: null,
      longitude: null,
      makeDefault: false,
    });
    expect(await screen.findByRole('radio', { name: /Home \(default\)/ })).toBeChecked();
    expect(orderService.placeOrder).not.toHaveBeenCalled();
  });
});

describe('PaymentPage', () => {
  beforeEach(() => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      orderDetails({
        id: 50,
        status: 'PAYMENT_PENDING',
        paymentMethod: 'CARD',
        paymentStatus: 'PENDING',
      }),
    );
    // The default gateway: payment-service's simulated one
    vi.mocked(paymentService.getRazorpayConfig).mockResolvedValue({
      enabled: false,
      keyId: null,
      testMode: false,
    });
  });

  it('checks the card, pays, and waits for the order to be confirmed', async () => {
    vi.mocked(paymentService.payOrder).mockResolvedValue(payment({ orderId: 50, method: 'CARD' }));
    renderAt('/customer/orders/50/pay', '/customer/orders/:orderId/pay', <PaymentPage />);

    await userEvent.type(await screen.findByLabelText('Card number'), '4111 1111 1111 1112');
    await chooseOption(screen.getByLabelText('Expiry month'), '12');
    await chooseOption(screen.getByLabelText('Expiry year'), String(new Date().getFullYear() + 1));
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹562.90' }));
    expect(await screen.findByText('This card number is not valid.')).toBeInTheDocument();
    expect(paymentService.payOrder).not.toHaveBeenCalled();

    await userEvent.clear(screen.getByLabelText('Card number'));
    await userEvent.type(screen.getByLabelText('Card number'), '4111 1111 1111 1111');
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      orderDetails({ id: 50, status: 'PLACED' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹562.90' }));

    expect(paymentService.payOrder).toHaveBeenCalledWith(
      {
        orderId: 50,
        paymentMethod: 'CARD',
        card: {
          number: '4111111111111111',
          expiryMonth: 12,
          expiryYear: new Date().getFullYear() + 1,
        },
      },
      expect.any(String),
    );
    expect(await screen.findByText('Payment received')).toBeInTheDocument();
    expect(await screen.findByText('Order page', {}, { timeout: 3000 })).toBeInTheDocument();
  });

  it('shows why a payment was declined and tries again with a new key', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      orderDetails({
        id: 50,
        status: 'PAYMENT_PENDING',
        paymentMethod: 'UPI',
        paymentStatus: 'PENDING',
      }),
    );
    vi.mocked(paymentService.payOrder).mockResolvedValue(
      payment({ status: 'FAILED', failureReason: 'UPI request declined by the customer' }),
    );
    renderAt('/customer/orders/50/pay', '/customer/orders/:orderId/pay', <PaymentPage />);

    await userEvent.type(await screen.findByLabelText('UPI id'), 'fail@upi');
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹562.90' }));
    expect(await screen.findByText(/UPI request declined by the customer/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹562.90' }));
    await waitFor(() => expect(paymentService.payOrder).toHaveBeenCalledTimes(2));
    const keys = vi.mocked(paymentService.payOrder).mock.calls.map((call) => call[1]);
    expect(keys[1]).not.toBe(keys[0]);
  });

  it('says so when the order no longer waits for a payment', async () => {
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      orderDetails({
        id: 50,
        status: 'CANCELLED',
        cancellationReason: 'Payment not completed within 15 minutes',
      }),
    );
    renderAt('/customer/orders/50/pay', '/customer/orders/:orderId/pay', <PaymentPage />);

    expect(await screen.findByText('This order was cancelled')).toBeInTheDocument();
    expect(screen.getByText(/not completed within 15 minutes/)).toBeInTheDocument();
  });
});

/** A stand-in for Razorpay's checkout.js: each test decides what the customer does in the window. */
interface FakeCheckout {
  options: {
    key: string;
    amount: number;
    order_id: string;
    prefill?: { name?: string; email?: string; method?: string };
    handler: (response: Record<string, string>) => void;
    modal?: { ondismiss?: () => void };
  };
  failed?: (response: { error: { description?: string } }) => void;
}

describe('PaymentPage with Razorpay Checkout', () => {
  let customerDoes: (checkout: FakeCheckout) => void;
  const opened: FakeCheckout[] = [];

  beforeEach(() => {
    opened.length = 0;
    vi.mocked(orderService.getMyOrder).mockResolvedValue(
      orderDetails({
        id: 50,
        status: 'PAYMENT_PENDING',
        paymentMethod: 'UPI',
        paymentStatus: 'PENDING',
      }),
    );
    vi.mocked(paymentService.getRazorpayConfig).mockResolvedValue({
      enabled: true,
      keyId: 'rzp_test_Key123',
      testMode: true,
    });
    vi.mocked(paymentService.startRazorpayCheckout).mockResolvedValue({
      paymentId: 77,
      orderId: 50,
      razorpayOrderId: 'order_Q1',
      keyId: 'rzp_test_Key123',
      amount: 56290,
      currency: 'INR',
      merchantName: 'Front-App',
      description: 'Order #50',
      preferredMethod: 'upi',
    });
    window.Razorpay = class {
      private readonly checkout: FakeCheckout;
      constructor(options: FakeCheckout['options']) {
        this.checkout = { options };
        opened.push(this.checkout);
      }
      on(_event: string, listener: NonNullable<FakeCheckout['failed']>) {
        this.checkout.failed = listener;
      }
      open() {
        customerDoes(this.checkout);
      }
    } as unknown as NonNullable<typeof window.Razorpay>;
  });

  afterEach(() => {
    delete window.Razorpay;
  });

  it('opens Checkout with the server-made order and waits until the server verified the payment', async () => {
    customerDoes = ({ options }) =>
      options.handler({
        razorpay_payment_id: 'pay_P1',
        razorpay_order_id: 'order_Q1',
        razorpay_signature: 'abc123',
      });
    // Once verified, order-service confirms the order (the page then sees it PLACED)
    vi.mocked(paymentService.verifyRazorpayPayment).mockImplementation(() => {
      vi.mocked(orderService.getMyOrder).mockResolvedValue(
        orderDetails({ id: 50, status: 'PLACED' }),
      );
      return Promise.resolve(
        payment({
          orderId: 50,
          status: 'SUCCESS',
          provider: 'RAZORPAY',
          providerOrderId: 'order_Q1',
        }),
      );
    });
    renderAt('/customer/orders/50/pay', '/customer/orders/:orderId/pay', <PaymentPage />);

    expect(await screen.findByText('Razorpay Test Mode')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹562.90' }));

    expect(paymentService.startRazorpayCheckout).toHaveBeenCalledWith(50);
    expect(opened[0].options).toMatchObject({
      key: 'rzp_test_Key123',
      order_id: 'order_Q1',
      amount: 56290,
      prefill: { name: 'Priya Sharma', email: 'priya@example.com', method: 'upi' },
    });
    expect(paymentService.verifyRazorpayPayment).toHaveBeenCalledWith({
      razorpayOrderId: 'order_Q1',
      razorpayPaymentId: 'pay_P1',
      razorpaySignature: 'abc123',
    });
    expect(await screen.findByText('Payment received')).toBeInTheDocument();
    expect(await screen.findByText('Order page', {}, { timeout: 3000 })).toBeInTheDocument();
  });

  it('says so when the customer closes Checkout, and shows why an attempt failed', async () => {
    customerDoes = ({ options }) => options.modal?.ondismiss?.();
    renderAt('/customer/orders/50/pay', '/customer/orders/:orderId/pay', <PaymentPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Pay ₹562.90' }));
    expect(await screen.findByText('Payment cancelled')).toBeInTheDocument();

    customerDoes = ({ options, failed }) => {
      failed?.({ error: { description: 'Your payment was declined by the bank.' } });
      options.modal?.ondismiss?.();
    };
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹562.90' }));
    expect(await screen.findByText(/Your payment was declined by the bank\./)).toBeInTheDocument();
    expect(screen.queryByText('Payment cancelled')).not.toBeInTheDocument();
    expect(paymentService.verifyRazorpayPayment).not.toHaveBeenCalled();
  });

  it('never treats a payment as paid when the server cannot verify it', async () => {
    customerDoes = ({ options }) =>
      options.handler({
        razorpay_payment_id: 'pay_P1',
        razorpay_order_id: 'order_Q1',
        razorpay_signature: 'forged',
      });
    vi.mocked(paymentService.verifyRazorpayPayment).mockRejectedValue(
      apiError(422, 'validation', 'The payment could not be verified.'),
    );
    renderAt('/customer/orders/50/pay', '/customer/orders/:orderId/pay', <PaymentPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Pay ₹562.90' }));

    expect(await screen.findByText('The payment could not be verified')).toBeInTheDocument();
    expect(screen.queryByText('Payment received')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pay ₹562.90' })).toBeEnabled();
  });

  it('waits for the webhook when Razorpay has not confirmed the payment yet', async () => {
    customerDoes = ({ options }) =>
      options.handler({
        razorpay_payment_id: 'pay_P1',
        razorpay_order_id: 'order_Q1',
        razorpay_signature: 'abc123',
      });
    vi.mocked(paymentService.verifyRazorpayPayment).mockResolvedValue(
      payment({ orderId: 50, status: 'PROCESSING', provider: 'RAZORPAY' }),
    );
    renderAt('/customer/orders/50/pay', '/customer/orders/:orderId/pay', <PaymentPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Pay ₹562.90' }));

    expect(await screen.findByText('Confirming your payment')).toBeInTheDocument();
    expect(screen.queryByText('Payment received')).not.toBeInTheDocument();
  });
});

describe('AddressesPage', () => {
  it('makes another address the default, and deletes one', async () => {
    vi.mocked(userService.getAddresses).mockResolvedValue([
      address(),
      address({ id: 22, type: 'WORK', line1: '1 Tech Park', defaultAddress: false }),
    ]);
    vi.mocked(userService.makeDefaultAddress).mockResolvedValue(address({ id: 22 }));
    vi.mocked(userService.deleteAddress).mockResolvedValue(undefined);
    renderAt('/customer/account/addresses', '/customer/account/addresses', <AddressesPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Make default' }));
    expect(userService.makeDefaultAddress).toHaveBeenCalledWith(22);
    expect(await screen.findByText('Work is now your default address.')).toBeInTheDocument();

    await userEvent.click(screen.getAllByRole('button', { name: 'Delete' })[0]);
    const dialog = screen.getByRole('dialog', { name: 'Delete this address?' });
    expect(
      within(dialog).getByText(/your newest other address becomes the default/),
    ).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));
    expect(userService.deleteAddress).toHaveBeenCalledWith(21);
  });
});

describe('the address form: map pin from the current location', () => {
  afterEach(() => {
    removeFakeGeolocation();
  });

  const openAddresses = async () => {
    renderAt('/customer/account/addresses', '/customer/account/addresses', <AddressesPage />);
    return screen.findByText('12 MG Road');
  };

  it('pins a new address to where the customer stands', async () => {
    const phone = installFakeGeolocation();
    phone.geolocation.getCurrentPosition.mockImplementation((success) =>
      success(positionAt({ lat: 12.97159871, lng: 77.59457123, accuracy: 15 })),
    );
    vi.mocked(userService.addAddress).mockResolvedValue(address({ id: 22 }));
    await openAddresses();

    await userEvent.click(screen.getByRole('button', { name: 'Add address' }));
    const dialog = screen.getByRole('dialog', { name: 'New address' });
    await userEvent.type(within(dialog).getByLabelText('Street and number'), '4 Church Street');
    await userEvent.type(within(dialog).getByLabelText('City'), 'Bengaluru');
    await userEvent.type(within(dialog).getByLabelText('State'), 'Karnataka');
    await userEvent.type(within(dialog).getByLabelText('Postal code'), '560001');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Use my current location' }));
    expect(within(dialog).getByText(/Pin set at 12.97160, 77.59457/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add address' }));

    expect(userService.addAddress).toHaveBeenCalledWith(
      expect.objectContaining({
        line1: '4 Church Street',
        latitude: 12.971599,
        longitude: 77.594571,
      }),
    );
  }, 20_000);

  it('keeps the pin of an address that is changed, and removes it on request', async () => {
    installFakeGeolocation();
    vi.mocked(userService.getAddresses).mockResolvedValue([
      address({ latitude: 12.9716, longitude: 77.5946 }),
    ]);
    vi.mocked(userService.updateAddress).mockResolvedValue(address());
    await openAddresses();

    await userEvent.click(screen.getByRole('button', { name: 'Change' }));
    let dialog = screen.getByRole('dialog', { name: 'Change address' });
    expect(within(dialog).getByText(/Pin set at 12.97160, 77.59460/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save address' }));
    expect(userService.updateAddress).toHaveBeenLastCalledWith(
      21,
      expect.objectContaining({ latitude: 12.9716, longitude: 77.5946 }),
    );

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Change' }));
    dialog = screen.getByRole('dialog', { name: 'Change address' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove pin' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save address' }));
    expect(userService.updateAddress).toHaveBeenLastCalledWith(
      21,
      expect.objectContaining({ latitude: null, longitude: null }),
    );
  });

  it('explains a blocked location: the address can still be saved without a pin', async () => {
    const phone = installFakeGeolocation();
    phone.geolocation.getCurrentPosition.mockImplementation((_success, failure) =>
      failure?.(geolocationError(1)),
    );
    await openAddresses();

    await userEvent.click(screen.getByRole('button', { name: 'Add address' }));
    const dialog = screen.getByRole('dialog', { name: 'New address' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Use my current location' }));

    expect(within(dialog).getByRole('alert')).toHaveTextContent(
      /Location access is blocked for this site/,
    );
    expect(within(dialog).queryByText(/Pin set at/)).not.toBeInTheDocument();
  });
});

describe('OffersPage', () => {
  const offer = {
    code: 'SAVE50',
    description: 'Fifty off',
    summary: '50.00 off',
    minOrderAmount: 199,
    restaurantId: null,
    validUntil: '2026-10-31',
  };

  beforeEach(() => {
    vi.mocked(restaurantService.browseRestaurants).mockResolvedValue(pageOf([]));
  });

  it("lists the offers with the cart restaurant's own, and applies one to the cart", async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([offer]);
    vi.mocked(orderService.applyCoupon).mockResolvedValue(
      cart({
        coupon: {
          code: 'SAVE50',
          description: null,
          applied: true,
          discountAmount: 50,
          reason: null,
        },
      }),
    );
    renderAt('/customer/offers', '/customer/offers', <OffersPage />);

    // Featured, and counted down among the limited time offers
    expect(await screen.findAllByText('50.00 off')).toHaveLength(2);
    await waitFor(() => expect(couponService.getOffers).toHaveBeenLastCalledWith(3));
    await userEvent.click(screen.getAllByRole('button', { name: 'Apply to cart' })[0]);

    expect(orderService.applyCoupon).toHaveBeenCalledWith('SAVE50');
    expect(await screen.findByText('Cart page')).toBeInTheDocument();
  });

  it('asks visitors to sign in to use an offer', async () => {
    vi.mocked(couponService.getOffers).mockResolvedValue([offer]);
    renderAt('/customer/offers', '/customer/offers', <OffersPage />, makeStore());

    expect(await screen.findAllByRole('link', { name: 'Sign in to use' })).not.toHaveLength(0);
    expect(couponService.getOffers).toHaveBeenCalledWith(null);
  });
});

describe('SignInPage for a signed-in customer', () => {
  it('goes straight on to where the customer was going', async () => {
    renderAt('/customer/sign-in?returnTo=%2Fcustomer%2Fcart', '/customer/sign-in', <SignInPage />);

    expect(await screen.findByText('Cart page')).toBeInTheDocument();
  });
});

describe('RequireCustomer', () => {
  it('sends a visitor to sign in, and back afterwards', async () => {
    renderWithProviders(
      <Routes>
        <Route element={<RequireCustomer />}>
          <Route path="/customer/cart" element={<p>Cart page</p>} />
        </Route>
        <Route path="/customer/sign-in" element={<SignInTarget />} />
      </Routes>,
      { route: '/customer/cart', store: makeStore() },
    );

    expect(await screen.findByText('Sign in, then back to /customer/cart')).toBeInTheDocument();
  });
});

/** Where the guard sent the visitor (MemoryRouter keeps the address in the router, not window.location). */
function SignInTarget() {
  const [searchParams] = useSearchParams();
  return <p>{`Sign in, then back to ${searchParams.get('returnTo') ?? ''}`}</p>;
}
