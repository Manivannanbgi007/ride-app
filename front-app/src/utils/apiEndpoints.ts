/**
 * Every REST API the application calls, in one place (paths behind the API Gateway; the gateway's address is
 * VITE_API_BASE_URL). Services use these constants; components never contain a URL.
 */
export const API_ENDPOINTS = {
  // user-service: register, and the sign-in sessions (an access token, and a refresh cookie per area)
  auth: {
    register: '/api/v1/auth/register',
    login: '/api/v1/auth/login',
    refresh: '/api/v1/auth/refresh',
    logout: '/api/v1/auth/logout',
  },
  users: {
    me: '/api/v1/users/me',
    password: '/api/v1/users/me/password',
    addresses: '/api/v1/users/me/addresses',
    address: (addressId: number) => `/api/v1/users/me/addresses/${addressId}`,
    defaultAddress: (addressId: number) => `/api/v1/users/me/addresses/${addressId}/default`,
  },
  // ai-mcp-client: the AI assistant (chat, history, order proposals)
  assistant: {
    chat: '/api/v1/assistant/chat',
    conversation: '/api/v1/assistant/conversation',
    confirm: (actionId: string) =>
      `/api/v1/assistant/actions/${encodeURIComponent(actionId)}/confirm`,
    cancel: (actionId: string) =>
      `/api/v1/assistant/actions/${encodeURIComponent(actionId)}/cancel`,
    suggestions: '/api/v1/assistant/suggestions',
    status: '/api/v1/assistant/status',
  },
  // order-service: the signed-in customer's cart and orders
  cart: {
    cart: '/api/v1/cart',
    item: (menuItemId: number) => `/api/v1/cart/items/${menuItemId}`,
    coupon: '/api/v1/cart/coupon',
  },
  orders: {
    list: '/api/v1/orders',
    details: (orderId: number) => `/api/v1/orders/${orderId}`,
    cancel: (orderId: number) => `/api/v1/orders/${orderId}/cancel`,
    // The signed-in customer's dashboard (always their own orders: the customer is the access token's)
    summary: '/api/v1/orders/summary',
    activity: '/api/v1/orders/activity',
    favouriteDishes: '/api/v1/orders/favourite-dishes',
  },
  // The restaurant owner's own restaurants (restaurant-service), their orders (order-service) and menu (catalog-service)
  // The signed-in delivery partner: profile (delivery-partner-service), going online and the deliveries
  // (delivery-service); the API Gateway sends each path to its service
  partner: {
    me: '/api/v1/delivery/partners/me',
    availability: '/api/v1/delivery/partners/me/availability',
    currentDelivery: '/api/v1/delivery/deliveries/mine/current',
    deliveries: '/api/v1/delivery/deliveries/mine',
    delivery: (deliveryId: number) => `/api/v1/delivery/deliveries/mine/${deliveryId}`,
    /** action: accept, decline (optional reason), pickup, deliver (cashCollected for cash on delivery) */
    deliveryAction: (deliveryId: number, action: string) =>
      `/api/v1/delivery/deliveries/mine/${deliveryId}/${action}`,
    /** PUT the partner's GPS position while they are on the way (204) */
    deliveryLocation: (deliveryId: number) =>
      `/api/v1/delivery/deliveries/mine/${deliveryId}/location`,
  },
  owner: {
    restaurants: '/api/v1/restaurants/mine',
    restaurant: (restaurantId: number) => `/api/v1/restaurants/mine/${restaurantId}`,
    operatingHours: (restaurantId: number) =>
      `/api/v1/restaurants/mine/${restaurantId}/operating-hours`,
    resubmit: (restaurantId: number) => `/api/v1/restaurants/mine/${restaurantId}/resubmit`,
    orders: (restaurantId: number) => `/api/v1/restaurants/mine/${restaurantId}/orders`,
    order: (restaurantId: number, orderId: number) =>
      `/api/v1/restaurants/mine/${restaurantId}/orders/${orderId}`,
    /** action: accept, reject (with a reason), preparing, ready */
    orderAction: (restaurantId: number, orderId: number, action: string) =>
      `/api/v1/restaurants/mine/${restaurantId}/orders/${orderId}/${action}`,
    salesSummary: (restaurantId: number) =>
      `/api/v1/restaurants/mine/${restaurantId}/orders/sales-summary`,
    topItems: (restaurantId: number) => `/api/v1/restaurants/mine/${restaurantId}/orders/top-items`,
    menuItems: (restaurantId: number) => `/api/v1/restaurants/mine/${restaurantId}/menu-items`,
    menuItem: (restaurantId: number, itemId: number) =>
      `/api/v1/restaurants/mine/${restaurantId}/menu-items/${itemId}`,
    /** action: price, availability, enable, disable */
    menuItemAction: (restaurantId: number, itemId: number, action: string) =>
      `/api/v1/restaurants/mine/${restaurantId}/menu-items/${itemId}/${action}`,
  },
  // delivery-service: following the delivery of one's order
  delivery: {
    tracking: (orderId: number) => `/api/v1/delivery/orders/${orderId}`,
    /** The partner's live GPS position while they are on the way, and the two stops (see liveTrackingService) */
    liveTracking: (orderId: number) => `/api/v1/delivery/orders/${orderId}/live`,
  },
  // payment-service: paying an order
  payments: {
    pay: '/api/v1/payments',
    // Razorpay Checkout (when payment-service runs with PAYMENT_PROVIDER=RAZORPAY)
    razorpay: {
      config: '/api/v1/payments/razorpay/config',
      orders: '/api/v1/payments/razorpay/orders',
      verify: '/api/v1/payments/razorpay/verify',
    },
  },
  // Public: what every visitor sees (restaurant-, catalog- and search-service)
  storefront: {
    restaurants: '/api/v1/restaurants',
    restaurant: (restaurantId: number) => `/api/v1/restaurants/${restaurantId}`,
    menu: (restaurantId: number) => `/api/v1/restaurants/${restaurantId}/menu`,
    categories: '/api/v1/categories',
    // promotion-service
    offers: '/api/v1/coupons',
  },
  search: {
    restaurants: '/api/v1/search/restaurants',
    foods: '/api/v1/search/foods',
    suggest: '/api/v1/search/suggest',
  },
  // review-service: a customer's review of an order
  reviews: {
    create: '/api/v1/reviews',
    ofOrder: (orderId: number) => `/api/v1/reviews/orders/${orderId}`,
    review: (reviewId: number) => `/api/v1/reviews/${reviewId}`,
  },
  // review-service: the public ratings (average and number of reviews) of restaurants and dishes
  ratings: {
    restaurants: '/api/v1/ratings/restaurants',
    menuItems: '/api/v1/ratings/menu-items',
    restaurant: (restaurantId: number) => `/api/v1/ratings/restaurants/${restaurantId}`,
    menuItem: (menuItemId: number) => `/api/v1/ratings/menu-items/${menuItemId}`,
    restaurantReviews: (restaurantId: number) =>
      `/api/v1/ratings/restaurants/${restaurantId}/reviews`,
    menuItemReviews: (menuItemId: number) => `/api/v1/ratings/menu-items/${menuItemId}/reviews`,
  },
  // notification-service: the acting user's inbox
  notifications: {
    list: '/api/v1/notifications',
    unreadCount: '/api/v1/notifications/unread-count',
    markRead: (notificationId: number) => `/api/v1/notifications/${notificationId}/read`,
    markAllRead: '/api/v1/notifications/read-all',
  },
  // chat-service: the participant's own conversation with Support
  chat: {
    conversation: '/api/v1/chat/conversation',
    messages: '/api/v1/chat/messages',
    read: '/api/v1/chat/read',
  },
  admin: {
    dashboard: '/api/v1/admin/dashboard',
    orderActivity: '/api/v1/admin/dashboard/order-activity',
    calendar: '/api/v1/admin/calendar',
    reports: {
      orders: '/api/v1/admin/reports/orders',
      revenue: '/api/v1/admin/reports/revenue',
      restaurants: '/api/v1/admin/reports/restaurants',
      customers: '/api/v1/admin/reports/customers',
      deliveries: '/api/v1/admin/reports/deliveries',
    },
    auditLogs: {
      list: '/api/v1/admin/audit-logs',
      actions: '/api/v1/admin/audit-logs/actions',
      details: (auditId: number) => `/api/v1/admin/audit-logs/${auditId}`,
      backfill: '/api/v1/admin/audit-logs/backfill',
    },
    // order-service
    orders: {
      list: '/api/v1/admin/orders',
      details: (orderId: number) => `/api/v1/admin/orders/${orderId}`,
      cancel: (orderId: number) => `/api/v1/admin/orders/${orderId}/cancel`,
      auditLogs: (orderId: number) => `/api/v1/admin/orders/${orderId}/audit-logs`,
      customerSummary: (customerId: number) =>
        `/api/v1/admin/orders/customers/${customerId}/summary`,
      dishRevenue: (menuItemId: number) =>
        `/api/v1/admin/orders/stats/menu-items/${menuItemId}/revenue`,
      customerTopFoods: (customerId: number) =>
        `/api/v1/admin/orders/stats/customers/${customerId}/top-foods`,
    },
    // user-service
    users: {
      list: '/api/v1/admin/users',
      details: (userId: number) => `/api/v1/admin/users/${userId}`,
      activate: (userId: number) => `/api/v1/admin/users/${userId}/activate`,
      deactivate: (userId: number) => `/api/v1/admin/users/${userId}/deactivate`,
      role: (userId: number) => `/api/v1/admin/users/${userId}/role`,
      auditLogs: (userId: number) => `/api/v1/admin/users/${userId}/audit-logs`,
      addresses: (userId: number) => `/api/v1/admin/users/${userId}/addresses`,
    },
    // catalog-service
    menuItems: {
      list: '/api/v1/admin/menu-items',
      create: '/api/v1/admin/menu-items',
      update: (itemId: number) => `/api/v1/admin/menu-items/${itemId}`,
      duplicate: (itemId: number) => `/api/v1/admin/menu-items/${itemId}/duplicate`,
      categoryStats: '/api/v1/admin/menu-items/category-stats',
      details: (itemId: number) => `/api/v1/admin/menu-items/${itemId}`,
      activate: (itemId: number) => `/api/v1/admin/menu-items/${itemId}/activate`,
      block: (itemId: number) => `/api/v1/admin/menu-items/${itemId}/deactivate`,
      category: (itemId: number) => `/api/v1/admin/menu-items/${itemId}/category`,
      remove: (itemId: number) => `/api/v1/admin/menu-items/${itemId}`,
      auditLogs: (itemId: number) => `/api/v1/admin/menu-items/${itemId}/audit-logs`,
    },
    categories: {
      list: '/api/v1/admin/categories',
      create: '/api/v1/admin/categories',
      update: (categoryId: number) => `/api/v1/admin/categories/${categoryId}`,
      activate: (categoryId: number) => `/api/v1/admin/categories/${categoryId}/activate`,
      deactivate: (categoryId: number) => `/api/v1/admin/categories/${categoryId}/deactivate`,
      remove: (categoryId: number) => `/api/v1/admin/categories/${categoryId}`,
      order: '/api/v1/admin/categories/order',
      auditLogs: (categoryId: number) => `/api/v1/admin/categories/${categoryId}/audit-logs`,
    },
    // restaurant-service
    restaurants: {
      list: '/api/v1/admin/restaurants',
      details: (restaurantId: number) => `/api/v1/admin/restaurants/${restaurantId}`,
      /** approve, reject, activate, deactivate, suspend */
      action: (restaurantId: number, action: string) =>
        `/api/v1/admin/restaurants/${restaurantId}/${action}`,
      auditLogs: (restaurantId: number) => `/api/v1/admin/restaurants/${restaurantId}/audit-logs`,
    },
    // promotion-service
    coupons: {
      list: '/api/v1/admin/coupons',
      create: '/api/v1/admin/coupons',
      details: (couponId: number) => `/api/v1/admin/coupons/${couponId}`,
      update: (couponId: number) => `/api/v1/admin/coupons/${couponId}`,
      activate: (couponId: number) => `/api/v1/admin/coupons/${couponId}/activate`,
      deactivate: (couponId: number) => `/api/v1/admin/coupons/${couponId}/deactivate`,
      redemptions: (couponId: number) => `/api/v1/admin/coupons/${couponId}/redemptions`,
      auditLogs: (couponId: number) => `/api/v1/admin/coupons/${couponId}/audit-logs`,
    },
    // review-service
    reviews: {
      list: '/api/v1/admin/reviews',
      summary: '/api/v1/admin/reviews/summary',
      featuredDishes: '/api/v1/admin/reviews/featured-dishes',
      customerLikedDishes: (customerId: number) =>
        `/api/v1/admin/reviews/customers/${customerId}/liked-dishes`,
    },
    // payment-service
    payments: {
      list: '/api/v1/admin/payments',
      summary: '/api/v1/admin/payments/summary',
      details: (paymentId: number) => `/api/v1/admin/payments/${paymentId}`,
      refund: (paymentId: number) => `/api/v1/admin/payments/${paymentId}/refunds`,
      auditLogs: (paymentId: number) => `/api/v1/admin/payments/${paymentId}/audit-logs`,
    },
    refunds: {
      list: '/api/v1/admin/refunds',
      details: (refundId: number) => `/api/v1/admin/refunds/${refundId}`,
    },
    // delivery-service
    deliveries: {
      list: '/api/v1/admin/deliveries',
      delayed: '/api/v1/admin/deliveries/delayed',
      summary: '/api/v1/admin/deliveries/summary',
      details: (deliveryId: number) => `/api/v1/admin/deliveries/${deliveryId}`,
      reassign: (deliveryId: number) => `/api/v1/admin/deliveries/${deliveryId}/reassign`,
      auditLogs: (deliveryId: number) => `/api/v1/admin/deliveries/${deliveryId}/audit-logs`,
    },
    // delivery-partner-service (list, details, activate, deactivate, audit logs) and delivery-service (availability,
    // deliveries): the API Gateway sends each path to its service
    partners: {
      list: '/api/v1/admin/delivery-partners',
      availability: '/api/v1/admin/delivery-partners/availability',
      details: (userId: number) => `/api/v1/admin/delivery-partners/${userId}`,
      deliveries: (userId: number) => `/api/v1/admin/delivery-partners/${userId}/deliveries`,
      activate: (userId: number) => `/api/v1/admin/delivery-partners/${userId}/activate`,
      deactivate: (userId: number) => `/api/v1/admin/delivery-partners/${userId}/deactivate`,
      auditLogs: (userId: number) => `/api/v1/admin/delivery-partners/${userId}/audit-logs`,
    },
    // chat-service: the staff's shared inbox
    chat: {
      conversations: '/api/v1/admin/chat/conversations',
      conversation: (id: number) => `/api/v1/admin/chat/conversations/${id}`,
      messages: (id: number) => `/api/v1/admin/chat/conversations/${id}/messages`,
      read: (id: number) => `/api/v1/admin/chat/conversations/${id}/read`,
      pin: (id: number) => `/api/v1/admin/chat/conversations/${id}/pin`,
      mute: (id: number) => `/api/v1/admin/chat/conversations/${id}/mute`,
      unreadCount: '/api/v1/admin/chat/unread-count',
    },
    // notification-service
    notifications: {
      list: '/api/v1/admin/notifications',
      summary: '/api/v1/admin/notifications/summary',
      details: (notificationId: number) => `/api/v1/admin/notifications/${notificationId}`,
      resend: (notificationId: number) => `/api/v1/admin/notifications/${notificationId}/resend`,
      auditLogs: (notificationId: number) =>
        `/api/v1/admin/notifications/${notificationId}/audit-logs`,
    },
  },
} as const;

/**
 * Sent with changes that must never happen twice (a refund): retrying with the same key returns the first result
 * instead of refunding again.
 */
export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';

/** The gateway gives every request an id (and sends it back); quote it when reporting a problem. */
export const REQUEST_ID_HEADER = 'x-request-id';
