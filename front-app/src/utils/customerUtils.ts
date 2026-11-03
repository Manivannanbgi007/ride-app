import { CHART_COLORS } from '@/utils/chartColors';

/** The colours of the "most liked" bars and legend, in turn: blue, red, green, yellow (the dashboard's chart colours). */
export const LIKED_DISH_COLORS = [
  CHART_COLORS.info,
  CHART_COLORS.danger,
  CHART_COLORS.brand,
  CHART_COLORS.warning,
] as const;

export function likedDishColor(index: number): string {
  return LIKED_DISH_COLORS[index % LIKED_DISH_COLORS.length];
}

/** A dish's share of all the customer's ratings in whole percent (0 when there are none). */
export function ratingShare(ratingCount: number, totalRatings: number): number {
  return totalRatings > 0 ? Math.round((ratingCount / totalRatings) * 100) : 0;
}

/** A short rating for text: 4.5 -> '4.5', 4 -> '4.0'. */
export function formatRating(value: number): string {
  return value.toFixed(1);
}
