import {
  FoodBasicsFields,
  FoodImageField,
  FoodTextFields,
  FoodTypeField,
} from '@/components/business/Food/FoodFormFields';
import {
  foodFormToCreateRequest,
  foodFormToUpdateRequest,
  useFoodForm,
} from '@/components/business/Food/useFoodForm';
import { FormDialog } from '@/components/common/FormDialog';
import type { SelectOption } from '@/components/common/Select';
import type {
  Category,
  MenuItem,
  MenuItemCreateRequest,
  MenuItemUpdateRequest,
} from '@/types/catalog';

interface FoodFormDialogProps {
  open: boolean;
  /** The item being edited; leave out to create one */
  item?: MenuItem;
  /** The restaurants to choose from when creating (empty while they load) */
  restaurants: readonly SelectOption[];
  /** The restaurant's name when editing (it cannot be changed) */
  restaurantName?: string | null;
  categories: readonly Category[];
  /** When creating: the restaurant and category to start with (it must be one of `restaurants`) */
  preset?: { restaurantId: number; categoryId: number };
  /** Needed to create an item (without `item`) */
  onCreate?: (request: MenuItemCreateRequest) => Promise<unknown>;
  onUpdate: (itemId: number, request: MenuItemUpdateRequest) => Promise<unknown>;
  onClose: () => void;
}

/**
 * "New Menu" / "Edit": one form for creating and editing a menu item. Server refusals stay in the dialog. The
 * Foods page creates on a page of its own (FoodNewPage), with the same fields and rules (useFoodForm).
 */
export function FoodFormDialog({
  open,
  item,
  restaurants,
  restaurantName,
  categories,
  preset,
  onCreate,
  onUpdate,
  onClose,
}: FoodFormDialogProps) {
  const { form, categoryOptions, submit } = useFoodForm({
    item,
    preset,
    categories,
    onSubmit: async (values) => {
      if (item) {
        await onUpdate(item.id, foodFormToUpdateRequest(values, item.version));
      } else if (onCreate) {
        await onCreate(foodFormToCreateRequest(values));
      } else {
        throw new Error('FoodFormDialog needs onCreate to create a menu item');
      }
      onClose();
    },
  });
  const {
    formState: { errors, isSubmitting },
  } = form;

  return (
    <FormDialog
      open={open}
      title={item ? `Edit "${item.name}"` : 'New Menu'}
      submitLabel={item ? 'Save changes' : 'Create menu item'}
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={onClose}
      size="lg"
    >
      <FoodBasicsFields
        form={form}
        item={item}
        restaurants={restaurants}
        restaurantName={restaurantName}
        categoryOptions={categoryOptions}
      />
      <FoodTextFields form={form} />
      <FoodTypeField form={form} />
      <FoodImageField form={form} />
    </FormDialog>
  );
}
