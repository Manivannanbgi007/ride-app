import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';

import { toApiError } from '@/api/apiErrorHandler';
import {
  menuFormDefaults,
  OWNER_MENU_MESSAGES,
  ownerMenuFormSchema,
  staleMessageOf,
  toMenuItemRequest,
  isStaleVersion,
  type OwnerMenuForm,
} from '@/components/business/OwnerMenu/ownerMenuUtils';
import { FormDialog } from '@/components/common/FormDialog';
import { Input } from '@/components/common/Input';
import { RadioCards } from '@/components/common/RadioCards';
import { Select } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import type { FoodType, MenuItem } from '@/types/catalog';
import type { OwnerMenuItemRequest, OwnerMenuItemUpdate } from '@/types/owner';
import type { StoreCategory } from '@/types/storefront';
import { FOOD_TYPE_LABELS } from '@/utils/constants';
import {
  FOOD_DESCRIPTION_MAX_LENGTH,
  FOOD_IMAGE_URL_MAX_LENGTH,
  FOOD_NAME_MAX_LENGTH,
  FOOD_TEXT_MAX_LENGTH,
  showServerErrors,
} from '@/utils/validationUtils';

const FOOD_TYPE_OPTIONS = (Object.keys(FOOD_TYPE_LABELS) as FoodType[]).map((value) => ({
  value,
  label: FOOD_TYPE_LABELS[value],
}));

/** The fields the server can name in a 400 (so its message appears next to the right box). */
const FIELDS = [
  'categoryId',
  'name',
  'description',
  'price',
  'foodType',
  'imageUrl',
  'ingredients',
  'nutritionInfo',
] as const;

interface OwnerMenuItemDialogProps {
  /** The dish being edited; leave out to add a new one */
  item?: MenuItem;
  /** The platform's categories: the owner picks one (empty while they load) */
  categories: readonly StoreCategory[];
  categoriesStatus: 'pending' | 'error' | 'success';
  onCreate: (request: OwnerMenuItemRequest) => Promise<unknown>;
  onUpdate: (itemId: number, request: OwnerMenuItemUpdate) => Promise<unknown>;
  /** The save was refused because the dish changed since it was opened: the page reloads the menu */
  onStale: () => void;
  onClose: () => void;
}

/**
 * "Add dish" / "Edit dish": one form for both. The rules are the admin food form's (validationUtils), the
 * restaurant is the owner's own. Server refusals stay inside the dialog; it closes only when the save worked.
 */
export function OwnerMenuItemDialog({
  item,
  categories,
  categoriesStatus,
  onCreate,
  onUpdate,
  onStale,
  onClose,
}: OwnerMenuItemDialogProps) {
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<OwnerMenuForm>({
    resolver: zodResolver(ownerMenuFormSchema),
    defaultValues: menuFormDefaults(item),
  });
  const description = useWatch({ control, name: 'description' });
  const ingredients = useWatch({ control, name: 'ingredients' });
  const nutritionInfo = useWatch({ control, name: 'nutritionInfo' });
  const imageUrl = useWatch({ control, name: 'imageUrl' }).trim();
  const [brokenImage, setBrokenImage] = useState<string | null>(null);

  // The platform's categories, plus the one the dish has now (it may have been switched off since)
  const categoryOptions = categories.map((category) => ({
    value: String(category.id),
    label: category.name,
  }));
  if (item && !categoryOptions.some((option) => option.value === String(item.categoryId))) {
    categoryOptions.push({ value: String(item.categoryId), label: item.categoryName });
  }

  const submit = handleSubmit(async (values) => {
    const request = toMenuItemRequest(values);
    try {
      if (item) {
        // The version is the one the owner saw: the server refuses (409) if the dish changed since
        await onUpdate(item.id, { ...request, version: item.version });
      } else {
        await onCreate(request);
      }
      onClose();
    } catch (error) {
      showServerErrors(error, setError, FIELDS);
      if (item && isStaleVersion(error)) {
        setError('root.server', { message: staleMessageOf(toApiError(error)) });
        onStale();
      }
    }
  });

  return (
    <FormDialog
      open
      title={item ? `Edit "${item.name}"` : 'Add dish'}
      submitLabel={item ? 'Save changes' : 'Add dish'}
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={onClose}
      size="lg"
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Input
          label="Name"
          maxLength={FOOD_NAME_MAX_LENGTH}
          error={errors.name?.message}
          {...register('name')}
        />
        <Input
          label="Price (INR)"
          inputMode="decimal"
          placeholder="249.00"
          error={errors.price?.message}
          {...register('price')}
        />
        <Select
          label="Category"
          placeholder="Choose a category"
          options={categoryOptions}
          error={errors.categoryId?.message}
          hint={
            categoriesStatus === 'pending'
              ? 'Loading the categories...'
              : categoriesStatus === 'error'
                ? OWNER_MENU_MESSAGES.CATEGORIES_FAILED
                : undefined
          }
          {...register('categoryId')}
        />
      </div>
      <Controller
        control={control}
        name="foodType"
        render={({ field }) => (
          <RadioCards
            legend="Food type"
            columns={3}
            options={FOOD_TYPE_OPTIONS}
            value={field.value ?? ''}
            onChange={field.onChange}
            error={errors.foodType?.message}
          />
        )}
      />
      <Textarea
        label="Description"
        rows={3}
        maxLength={FOOD_DESCRIPTION_MAX_LENGTH}
        error={errors.description?.message}
        hint={`${description.length}/${FOOD_DESCRIPTION_MAX_LENGTH}`}
        {...register('description')}
      />
      <Textarea
        label="Ingredients (optional)"
        rows={3}
        maxLength={FOOD_TEXT_MAX_LENGTH}
        error={errors.ingredients?.message}
        hint={`${ingredients.length}/${FOOD_TEXT_MAX_LENGTH}`}
        {...register('ingredients')}
      />
      <Textarea
        label="Nutrition info (optional)"
        rows={3}
        maxLength={FOOD_TEXT_MAX_LENGTH}
        error={errors.nutritionInfo?.message}
        hint={`${nutritionInfo.length}/${FOOD_TEXT_MAX_LENGTH}`}
        {...register('nutritionInfo')}
      />
      <div className="flex items-start gap-4">
        <div className="min-w-0 flex-1">
          <Input
            label="Image URL (optional)"
            type="url"
            placeholder="https://..."
            maxLength={FOOD_IMAGE_URL_MAX_LENGTH}
            error={errors.imageUrl?.message}
            {...register('imageUrl')}
          />
        </div>
        {/* A preview, so a wrong address is noticed before saving; hidden when it cannot be loaded */}
        {imageUrl !== '' && !errors.imageUrl && brokenImage !== imageUrl && (
          <img
            src={imageUrl}
            alt="Preview of the dish"
            onError={() => setBrokenImage(imageUrl)}
            className="mt-6 size-12 shrink-0 rounded-full object-cover"
          />
        )}
      </div>
    </FormDialog>
  );
}
