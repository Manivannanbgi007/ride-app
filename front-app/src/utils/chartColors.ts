import type { OrderStatus } from '@/types/order';

/**
 * Charts need colour values, not Tailwind classes: these are the theme's colours from index.css.
 * Change both places together. The ones that differ between light and dark mode (text, lines, track, berry) are the
 * CSS variables themselves: the browser takes the value of the theme that is on, so a chart changes with the theme
 * without being drawn again.
 */
export const CHART_COLORS = {
  brand: '#39c576',
  /** The dashboard's complementary colours (index.css: berry, sunset, ruby, lime) */
  berry: 'var(--color-chart-berry)',
  sunset: '#f26a1b',
  ruby: '#c92a22',
  lime: '#8bc34a',
  danger: '#f05252',
  info: '#2d9cdb',
  warning: '#ffc327',
  /** The order-status gradients' ends (index.css --color-st-*: the same in both themes) */
  violet: '#6d28d9',
  indigo: '#4f46e5',
  rose: '#e11d48',
  blue: '#1d4ed8',
  deepGreen: '#1d864c',
  ink: 'var(--color-ink)',
  inkSoft: 'var(--color-ink-soft)',
  line: 'var(--color-line)',
  /** The empty part of a donut */
  track: 'var(--color-chart-track)',
} as const;

export const ORDER_STATUS_COLORS: Record<OrderStatus, string> = {
  PAYMENT_PENDING: CHART_COLORS.warning,
  PLACED: CHART_COLORS.info,
  ACCEPTED: CHART_COLORS.info,
  PREPARING: CHART_COLORS.warning,
  READY_FOR_PICKUP: CHART_COLORS.warning,
  OUT_FOR_DELIVERY: CHART_COLORS.info,
  DELIVERED: CHART_COLORS.brand,
  CANCELLED: CHART_COLORS.danger,
  REJECTED: CHART_COLORS.danger,
};
