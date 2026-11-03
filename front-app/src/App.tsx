import { useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import { NavigationProgress } from '@/components/common/Loader';
import { LiveUpdatesProvider } from '@/realtime/LiveUpdatesProvider';
import { HEADER_PAGES, NAVIGATION } from '@/layouts/navigation';
import { CustomerLayout } from '@/layouts/customer/CustomerLayout';
import { LegacyCustomerRedirect } from '@/layouts/customer/LegacyCustomerRedirect';
import { RequireCustomer } from '@/layouts/customer/RequireCustomer';
import { RequireAdmin } from '@/layouts/RequireAdmin';
import { OwnerRestaurantRoutes } from '@/layouts/owner/OwnerRestaurantRoutes';
import { RequireOwner } from '@/layouts/owner/RequireOwner';
import { RequirePartner } from '@/layouts/partner/RequirePartner';
import { MainLayout } from '@/layouts/MainLayout';
import { ComingSoonPage } from '@/pages/ComingSoon/ComingSoonPage';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import {
  CUSTOMER_ROUTES,
  OWNER_BASE,
  OWNER_ROUTES,
  PARTNER_BASE,
  PARTNER_ROUTES,
  ROUTES,
} from '@/utils/constants';
import { lazyPage, measureAppStart } from '@/utils/pageLoadTracker';

// Large pages are loaded when they are opened (their code, e.g. the chart library, is not in the first download);
// lazyPage also tells the loading bar and times the download. Small pages are loaded with the application.
// The customer area
const HomePage = lazyPage(() => import('@/pages/Storefront/HomePage'));
const StoreCategoriesPage = lazyPage(() => import('@/pages/Storefront/StoreCategoriesPage'));
const RestaurantsPage = lazyPage(() => import('@/pages/Storefront/RestaurantsPage'));
const RestaurantMenuPage = lazyPage(() => import('@/pages/Storefront/RestaurantMenuPage'));
const StoreSearchPage = lazyPage(() => import('@/pages/Storefront/StoreSearchPage'));
const SignInPage = lazyPage(() => import('@/pages/Storefront/SignInPage'));
const RegisterPage = lazyPage(() => import('@/pages/Storefront/RegisterPage'));
const OffersPage = lazyPage(() => import('@/pages/Storefront/OffersPage'));
const CartPage = lazyPage(() => import('@/pages/Storefront/CartPage'));
const CheckoutPage = lazyPage(() => import('@/pages/Storefront/CheckoutPage'));
const PaymentPage = lazyPage(() => import('@/pages/Storefront/PaymentPage'));
const OrderPage = lazyPage(() => import('@/pages/Storefront/OrderPage'));
const LiveTrackingPage = lazyPage(() => import('@/pages/Storefront/LiveTrackingPage'));
const AddressesPage = lazyPage(() => import('@/pages/Storefront/AddressesPage'));
const MyOrdersPage = lazyPage(() => import('@/pages/Storefront/MyOrdersPage'));
const CustomerDashboardPage = lazyPage(() => import('@/pages/Storefront/CustomerDashboardPage'));
const ProfilePage = lazyPage(() => import('@/pages/Storefront/ProfilePage'));
const SupportPage = lazyPage(() => import('@/pages/Storefront/SupportPage'));
const CustomerNotificationsPage = lazyPage(
  () => import('@/pages/Storefront/CustomerNotificationsPage'),
);

// The restaurant owner's area
const OwnerSignInPage = lazyPage(() => import('@/pages/Owner/OwnerSignInPage'));
const OwnerRegisterPage = lazyPage(() => import('@/pages/Owner/OwnerRegisterPage'));
const OwnerDashboardPage = lazyPage(() => import('@/pages/Owner/OwnerDashboardPage'));
const OwnerOrdersPage = lazyPage(() => import('@/pages/Owner/OwnerOrdersPage'));
const OwnerOrderDetailsPage = lazyPage(() => import('@/pages/Owner/OwnerOrderDetailsPage'));
const OwnerMenuPage = lazyPage(() => import('@/pages/Owner/OwnerMenuPage'));
const OwnerReviewsPage = lazyPage(() => import('@/pages/Owner/OwnerReviewsPage'));
const OwnerRestaurantPage = lazyPage(() => import('@/pages/Owner/OwnerRestaurantPage'));
const OwnerRegisterRestaurantPage = lazyPage(
  () => import('@/pages/Owner/OwnerRegisterRestaurantPage'),
);

// The delivery partner's area
const PartnerSignInPage = lazyPage(() => import('@/pages/Partner/PartnerSignInPage'));
const PartnerRegisterPage = lazyPage(() => import('@/pages/Partner/PartnerRegisterPage'));
const PartnerHomePage = lazyPage(() => import('@/pages/Partner/PartnerHomePage'));
const PartnerDeliveriesPage = lazyPage(() => import('@/pages/Partner/PartnerDeliveriesPage'));
const PartnerDeliveryDetailsPage = lazyPage(
  () => import('@/pages/Partner/PartnerDeliveryDetailsPage'),
);
const PartnerProfilePage = lazyPage(() => import('@/pages/Partner/PartnerProfilePage'));

// The admin area
const AdminSignInPage = lazyPage(() => import('@/pages/Admin/AdminSignInPage'));
const DashboardPage = lazyPage(() => import('@/pages/Dashboard/DashboardPage'));
const OrderListPage = lazyPage(() => import('@/pages/Order/OrderListPage'));
const OrderDetailsPage = lazyPage(() => import('@/pages/Order/OrderDetailsPage'));
const CustomerListPage = lazyPage(() => import('@/pages/Customer/CustomerListPage'));
const CustomerDetailsPage = lazyPage(() => import('@/pages/Customer/CustomerDetailsPage'));
const FoodListPage = lazyPage(() => import('@/pages/Food/FoodListPage'));
const FoodDetailsPage = lazyPage(() => import('@/pages/Food/FoodDetailsPage'));
const FoodNewPage = lazyPage(() => import('@/pages/Food/FoodNewPage'));
const SearchPage = lazyPage(() => import('@/pages/Search/SearchPage'));
const RestaurantListPage = lazyPage(() => import('@/pages/Restaurant/RestaurantListPage'));
const RestaurantDetailsPage = lazyPage(() => import('@/pages/Restaurant/RestaurantDetailsPage'));
const CategoriesPage = lazyPage(() => import('@/pages/Category/CategoriesPage'));
const CouponListPage = lazyPage(() => import('@/pages/Coupon/CouponListPage'));
const CouponFormPage = lazyPage(() => import('@/pages/Coupon/CouponFormPage'));
const CouponDetailsPage = lazyPage(() => import('@/pages/Coupon/CouponDetailsPage'));
const PaymentListPage = lazyPage(() => import('@/pages/Payment/PaymentListPage'));
const PaymentDetailsPage = lazyPage(() => import('@/pages/Payment/PaymentDetailsPage'));
const RefundListPage = lazyPage(() => import('@/pages/Payment/RefundListPage'));
const RefundDetailsPage = lazyPage(() => import('@/pages/Payment/RefundDetailsPage'));
const DeliveriesPage = lazyPage(() => import('@/pages/Delivery/DeliveriesPage'));
const DeliveryDetailsPage = lazyPage(() => import('@/pages/Delivery/DeliveryDetailsPage'));
const PartnerDetailsPage = lazyPage(() => import('@/pages/Delivery/PartnerDetailsPage'));
const ReviewsPage = lazyPage(() => import('@/pages/Review/ReviewsPage'));
const CalendarPage = lazyPage(() => import('@/pages/Calendar/CalendarPage'));
const ChatPage = lazyPage(() => import('@/pages/Chat/ChatPage'));
const AnalyticsPage = lazyPage(() => import('@/pages/Analytics/AnalyticsPage'));
const AuditLogListPage = lazyPage(() => import('@/pages/AuditLog/AuditLogListPage'));
const AuditRecordPage = lazyPage(() => import('@/pages/AuditLog/AuditRecordPage'));
const NotificationsPage = lazyPage(() => import('@/pages/Notification/NotificationsPage'));
const NotificationDetailsPage = lazyPage(
  () => import('@/pages/Notification/NotificationDetailsPage'),
);

/** Pages of the design that later phases build: they show "coming soon" until then. */
const PLANNED_PAGES = [...NAVIGATION, ...HEADER_PAGES].filter((page) => page.comingIn);

/**
 * Every address of the application: the admin area under /admin, the customer area everywhere else. The area
 * also decides who the application acts as (utils/sessionUtils.ts).
 */
export function App() {
  // The first screen is drawn: "Front-App Â· app started" in DevTools > Performance
  useEffect(measureAppStart, []);

  return (
    <LiveUpdatesProvider>
      {/* The thin bar at the top while the next page's code downloads */}
      <NavigationProgress />
      <Routes>
        {/* The site's root leads to the customer area, as /admin leads to the dashboard */}
        <Route path="/" element={<Navigate to={CUSTOMER_ROUTES.home} replace />} />

        <Route path={CUSTOMER_ROUTES.home} element={<CustomerLayout />}>
          <Route index element={<HomePage />} />
          <Route path={CUSTOMER_ROUTES.categories} element={<StoreCategoriesPage />} />
          <Route path={CUSTOMER_ROUTES.restaurants} element={<RestaurantsPage />} />
          <Route path={CUSTOMER_ROUTES.restaurantMenu} element={<RestaurantMenuPage />} />
          <Route path={CUSTOMER_ROUTES.search} element={<StoreSearchPage />} />
          <Route path={CUSTOMER_ROUTES.signIn} element={<SignInPage />} />
          <Route path={CUSTOMER_ROUTES.register} element={<RegisterPage />} />
          <Route path={CUSTOMER_ROUTES.offers} element={<OffersPage />} />
          {/* A signed-in customer's pages: visitors are asked to sign in first */}
          <Route element={<RequireCustomer />}>
            <Route path={CUSTOMER_ROUTES.cart} element={<CartPage />} />
            <Route path={CUSTOMER_ROUTES.checkout} element={<CheckoutPage />} />
            <Route path={CUSTOMER_ROUTES.orderDetails} element={<OrderPage />} />
            <Route path={CUSTOMER_ROUTES.orderPayment} element={<PaymentPage />} />
            <Route path={CUSTOMER_ROUTES.orderLiveTracking} element={<LiveTrackingPage />} />
            <Route path={CUSTOMER_ROUTES.addresses} element={<AddressesPage />} />
            <Route path={CUSTOMER_ROUTES.myOrders} element={<MyOrdersPage />} />
            <Route path={CUSTOMER_ROUTES.dashboard} element={<CustomerDashboardPage />} />
            <Route path={CUSTOMER_ROUTES.profile} element={<ProfilePage />} />
            <Route path={CUSTOMER_ROUTES.notifications} element={<CustomerNotificationsPage />} />
            <Route path={CUSTOMER_ROUTES.support} element={<SupportPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        {/* The restaurant owner's area: the sign-in stands alone, the other pages are inside the owner's frame */}
        <Route path={OWNER_ROUTES.signIn} element={<OwnerSignInPage />} />
        <Route path={OWNER_ROUTES.register} element={<OwnerRegisterPage />} />
        <Route element={<RequireOwner />}>
          <Route path={OWNER_ROUTES.newRestaurant} element={<OwnerRegisterRestaurantPage />} />
          {/* Pages that work on one of the owner's restaurants: shown once they have one */}
          <Route element={<OwnerRestaurantRoutes />}>
            <Route path={OWNER_ROUTES.home} element={<OwnerDashboardPage />} />
            <Route path={OWNER_ROUTES.orders} element={<OwnerOrdersPage />} />
            <Route path={OWNER_ROUTES.orderDetails} element={<OwnerOrderDetailsPage />} />
            <Route path={OWNER_ROUTES.menu} element={<OwnerMenuPage />} />
            <Route path={OWNER_ROUTES.reviews} element={<OwnerReviewsPage />} />
            <Route path={OWNER_ROUTES.restaurant} element={<OwnerRestaurantPage />} />
          </Route>
          <Route path={`${OWNER_BASE}/*`} element={<NotFoundPage />} />
        </Route>

        {/* The delivery partner's area: sign-in and sign-up stand alone, the other pages are inside the partner's frame */}
        <Route path={PARTNER_ROUTES.signIn} element={<PartnerSignInPage />} />
        <Route path={PARTNER_ROUTES.register} element={<PartnerRegisterPage />} />
        <Route element={<RequirePartner />}>
          <Route path={PARTNER_ROUTES.home} element={<PartnerHomePage />} />
          <Route path={PARTNER_ROUTES.deliveries} element={<PartnerDeliveriesPage />} />
          <Route path={PARTNER_ROUTES.deliveryDetails} element={<PartnerDeliveryDetailsPage />} />
          <Route path={PARTNER_ROUTES.profile} element={<PartnerProfilePage />} />
          <Route path={`${PARTNER_BASE}/*`} element={<NotFoundPage />} />
        </Route>

        {/* Old customer addresses (from before /customer) go on to the new ones; the rest is "not found" */}
        <Route element={<CustomerLayout />}>
          <Route path="*" element={<LegacyCustomerRedirect />} />
        </Route>

        {/* The admin area: the sign-in stands alone, every other page needs a signed-in administrator */}
        <Route path={ROUTES.signIn} element={<AdminSignInPage />} />
        <Route element={<RequireAdmin />}>
          <Route path={ROUTES.admin} element={<MainLayout />}>
            <Route index element={<Navigate to={ROUTES.dashboard} replace />} />
            <Route path={ROUTES.dashboard} element={<DashboardPage />} />
            <Route path={ROUTES.orders} element={<OrderListPage />} />
            <Route path={ROUTES.orderDetails} element={<OrderDetailsPage />} />
            <Route path={ROUTES.customers} element={<CustomerListPage />} />
            <Route path={ROUTES.customerDetails} element={<CustomerDetailsPage />} />
            <Route path={ROUTES.foods} element={<FoodListPage />} />
            <Route path={ROUTES.foodNew} element={<FoodNewPage />} />
            <Route path={ROUTES.foodDetails} element={<FoodDetailsPage />} />
            <Route path={ROUTES.search} element={<SearchPage />} />
            <Route path={ROUTES.restaurants} element={<RestaurantListPage />} />
            <Route path={ROUTES.restaurantDetails} element={<RestaurantDetailsPage />} />
            <Route path={ROUTES.categories} element={<CategoriesPage />} />
            <Route path={ROUTES.coupons} element={<CouponListPage />} />
            <Route path={ROUTES.couponNew} element={<CouponFormPage />} />
            <Route path={ROUTES.couponDetails} element={<CouponDetailsPage />} />
            <Route path={ROUTES.couponEdit} element={<CouponFormPage />} />
            <Route path={ROUTES.payments} element={<PaymentListPage />} />
            <Route path={ROUTES.paymentDetails} element={<PaymentDetailsPage />} />
            <Route path={ROUTES.refunds} element={<RefundListPage />} />
            <Route path={ROUTES.refundDetails} element={<RefundDetailsPage />} />
            <Route path={ROUTES.deliveries} element={<DeliveriesPage />} />
            <Route path={ROUTES.deliveryDetails} element={<DeliveryDetailsPage />} />
            <Route path={ROUTES.partnerDetails} element={<PartnerDetailsPage />} />
            <Route path={ROUTES.reviews} element={<ReviewsPage />} />
            <Route path={ROUTES.calendar} element={<CalendarPage />} />
            <Route path={ROUTES.chatConversation} element={<ChatPage />} />
            <Route path={ROUTES.analytics} element={<AnalyticsPage />} />
            <Route path={ROUTES.auditLogs} element={<AuditLogListPage />} />
            <Route path={ROUTES.auditLogDetails} element={<AuditRecordPage />} />
            <Route path={ROUTES.notifications} element={<NotificationsPage />} />
            <Route path={ROUTES.notificationDetails} element={<NotificationDetailsPage />} />
            {PLANNED_PAGES.map((page) => (
              <Route key={page.path} path={page.path} element={<ComingSoonPage page={page} />} />
            ))}
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </LiveUpdatesProvider>
  );
}
