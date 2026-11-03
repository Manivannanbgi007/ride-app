import { combineReducers, configureStore } from '@reduxjs/toolkit';

import auditLogListReducer from '@/store/slices/auditLogListSlice';
import couponListReducer from '@/store/slices/couponListSlice';
import customerListReducer from '@/store/slices/customerListSlice';
import deliveryListReducer from '@/store/slices/deliveryListSlice';
import foodListReducer from '@/store/slices/foodListSlice';
import notificationListReducer from '@/store/slices/notificationListSlice';
import orderListReducer from '@/store/slices/orderListSlice';
import partnerListReducer from '@/store/slices/partnerListSlice';
import paymentListReducer from '@/store/slices/paymentListSlice';
import refundListReducer from '@/store/slices/refundListSlice';
import reportPeriodReducer from '@/store/slices/reportPeriodSlice';
import restaurantListReducer from '@/store/slices/restaurantListSlice';
import sessionReducer from '@/store/slices/sessionSlice';

/**
 * Redux holds only state that several pages share and that must survive navigation. Data from the APIs lives
 * in React Query (hooks/api); a component's own state (an open dialog, a typed value) stays in the component.
 */
const rootReducer = combineReducers({
  session: sessionReducer,
  reportPeriod: reportPeriodReducer,
  orderList: orderListReducer,
  customerList: customerListReducer,
  foodList: foodListReducer,
  restaurantList: restaurantListReducer,
  couponList: couponListReducer,
  paymentList: paymentListReducer,
  refundList: refundListReducer,
  deliveryList: deliveryListReducer,
  partnerList: partnerListReducer,
  auditLogList: auditLogListReducer,
  notificationList: notificationListReducer,
});

export type RootState = ReturnType<typeof rootReducer>;

/** A new store; tests create their own, so they never share state. */
export function makeStore(preloadedState?: Partial<RootState>) {
  return configureStore({ reducer: rootReducer, preloadedState });
}

export type AppStore = ReturnType<typeof makeStore>;
export type AppDispatch = AppStore['dispatch'];

/** The application's store. */
export const store = makeStore();
