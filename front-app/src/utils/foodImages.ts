/**
 * Pictures that ship with the application (public/images). A dish or restaurant without a picture of its own
 * borrows the picture of its category, or of its cuisine. Sources and licences: public/images/CREDITS.md.
 */
const CATEGORY_IMAGES: Record<string, string> = {
  pizza: '/images/categories/pizza.jpg',
  burgers: '/images/categories/burgers.jpg',
  biryani: '/images/categories/biryani.jpg',
  chinese: '/images/categories/chinese.jpg',
  'south indian': '/images/categories/south-indian.jpg',
  'north indian': '/images/categories/north-indian.jpg',
  desserts: '/images/categories/desserts.jpg',
  beverages: '/images/categories/beverages.jpg',
};

/** Words in a cuisine name that point to a category, most specific first ("South Indian" before "Indian"). */
const CUISINE_KEYWORDS: readonly (readonly [string, string])[] = [
  ['south indian', 'south indian'],
  ['dosa', 'south indian'],
  ['idli', 'south indian'],
  ['chettinad', 'south indian'],
  ['kerala', 'south indian'],
  ['andhra', 'south indian'],
  ['biryani', 'biryani'],
  ['hyderabadi', 'biryani'],
  ['mughlai', 'biryani'],
  ['pulao', 'biryani'],
  ['north indian', 'north indian'],
  ['punjabi', 'north indian'],
  ['tandoor', 'north indian'],
  ['kebab', 'north indian'],
  ['thali', 'north indian'],
  ['curry', 'north indian'],
  ['chinese', 'chinese'],
  ['thai', 'chinese'],
  ['asian', 'chinese'],
  ['noodle', 'chinese'],
  ['pizza', 'pizza'],
  ['italian', 'pizza'],
  ['burger', 'burgers'],
  ['fast food', 'burgers'],
  ['american', 'burgers'],
  ['sandwich', 'burgers'],
  ['dessert', 'desserts'],
  ['sweet', 'desserts'],
  ['bakery', 'desserts'],
  ['ice cream', 'desserts'],
  ['beverage', 'beverages'],
  ['juice', 'beverages'],
  ['coffee', 'beverages'],
  ['cafe', 'beverages'],
];

/** The picture of a food category by its name ("South Indian"), or null for a category without one. */
export function categoryImageOf(categoryName: string | null | undefined): string | null {
  return CATEGORY_IMAGES[categoryName?.trim().toLowerCase() ?? ''] ?? null;
}

/**
 * A picture for a restaurant, from its cuisines (restaurants have no photos of their own): the first cuisine
 * that names a known category. Null when none does.
 */
export function cuisineImageOf(cuisines: readonly string[] | undefined): string | null {
  for (const cuisine of cuisines ?? []) {
    const name = cuisine.trim().toLowerCase();
    const match = CUISINE_KEYWORDS.find(([keyword]) => name.includes(keyword));
    if (match) {
      return CATEGORY_IMAGES[match[1]] ?? null;
    }
  }
  return null;
}
