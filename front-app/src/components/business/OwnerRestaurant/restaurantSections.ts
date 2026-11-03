/** The parts of the restaurant form, in their order: the ids the "On this page" links jump to. */
export const RESTAURANT_SECTIONS = [
  { id: 'restaurant-about', label: 'About the restaurant' },
  { id: 'restaurant-address', label: 'Address' },
  { id: 'restaurant-business', label: 'Business and licence' },
  { id: 'restaurant-hours', label: 'Opening hours' },
] as const;

export type RestaurantSectionId = (typeof RESTAURANT_SECTIONS)[number]['id'];
