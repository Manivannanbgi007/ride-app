import {
  CakeSlice,
  Coffee,
  CookingPot,
  Pizza,
  Salad,
  Sandwich,
  Soup,
  UtensilsCrossed,
  type LucideIcon,
} from 'lucide-react';

/** An icon per category, for one without a picture or whose picture cannot be loaded. */
const ICONS: Record<string, LucideIcon> = {
  Pizza,
  Burgers: Sandwich,
  Biryani: CookingPot,
  Chinese: Soup,
  'South Indian': Salad,
  'North Indian': CookingPot,
  Desserts: CakeSlice,
  Beverages: Coffee,
};

/** The icon of a food category; an unknown category gets a plate. */
export function CategoryIcon({ name, className }: { name: string; className?: string }) {
  const Icon = ICONS[name] ?? UtensilsCrossed;
  return <Icon aria-hidden="true" className={className} />;
}
