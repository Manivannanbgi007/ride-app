import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { toApiError } from '@/api/apiErrorHandler';
import type {
  Category,
  MenuItem,
  MenuItemCreateRequest,
  MenuItemUpdateRequest,
} from '@/types/catalog';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { foodFormSchema, showServerErrors, type FoodForm } from '@/utils/validationUtils';

const FIELDS = [
  'restaurantId',
  'categoryId',
  'name',
  'description',
  'price',
  'foodType',
  'imageUrl',
  'ingredients',
  'nutritionInfo',
] as const;

/** What both a new item and a changed one send (empty optional texts become null). */
function bodyOf(values: FoodForm) {
  return {
    name: values.name,
    description: values.description === '' ? null : values.description,
    price: Number(values.price),
    categoryId: Number(values.categoryId),
    foodType: values.foodType,
    imageUrl: values.imageUrl === '' ? null : values.imageUrl,
    ingredients: values.ingredients === '' ? null : values.ingredients,
    nutritionInfo: values.nutritionInfo === '' ? null : values.nutritionInfo,
  };
}

/** The form as POST /menu-items wants it. */
export function foodFormToCreateRequest(values: FoodForm): MenuItemCreateRequest {
  return { ...bodyOf(values), restaurantId: Number(values.restaurantId) };
}

/** The form as PUT /menu-items/{id} wants it (with the version the form started from). */
export function foodFormToUpdateRequest(values: FoodForm, version: number): MenuItemUpdateRequest {
  return { ...bodyOf(values), version };
}

interface UseFoodFormOptions {
  /** The item being edited; leave out to create one */
  item?: MenuItem;
  /** When creating: the restaurant and category to start with */
  preset?: { restaurantId: number; categoryId: number };
  categories: readonly Category[];
  /** Sends the checked form; a refusal it throws is shown on the form */
  onSubmit: (values: FoodForm) => Promise<unknown>;
}

/**
 * The menu item form, shared by the "New Menu" / "Edit" dialog and the "Add New Food Item" page: its starting values,
 * its rules, the categories to choose from, and what a refusal of the server shows (on the field it is about, and
 * above the fields).
 */
export function useFoodForm({ item, preset, categories, onSubmit }: UseFoodFormOptions) {
  const form = useForm<FoodForm>({
    resolver: zodResolver(foodFormSchema),
    defaultValues: {
      restaurantId: String((item ?? preset)?.restaurantId ?? ''),
      categoryId: String((item ?? preset)?.categoryId ?? ''),
      name: item?.name ?? '',
      description: item?.description ?? '',
      price: item ? item.price.toFixed(2) : '',
      foodType: item?.foodType,
      imageUrl: item?.imageUrl ?? '',
      ingredients: item?.ingredients ?? '',
      nutritionInfo: item?.nutritionInfo ?? '',
    },
  });
  const { setError } = form;

  // Active categories, plus the one the item has now (it may have been deactivated since)
  const categoryOptions = categories
    .filter(
      (category) => category.status === 'ACTIVE' || category.id === (item ?? preset)?.categoryId,
    )
    .map((category) => ({ value: String(category.id), label: category.name }));

  const submit = form.handleSubmit(async (values) => {
    try {
      await onSubmit(values);
    } catch (error) {
      showServerErrors(error, setError, FIELDS);
      const apiError = toApiError(error);
      // A 409 on an edit is a stale version unless the server says the name is taken
      if (
        item &&
        apiError.status === 409 &&
        !/already|exists|duplicate|taken/i.test(apiError.userMessage)
      ) {
        setError('root.server', { message: ERROR_MESSAGES.FOOD_VERSION_CONFLICT });
      }
    }
  });

  return { form, categoryOptions, submit };
}
