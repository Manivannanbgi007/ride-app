import { ImageIcon, ImageOff } from 'lucide-react';
import { useState } from 'react';
import { Controller, useWatch, type UseFormReturn } from 'react-hook-form';

import { Input } from '@/components/common/Input';
import { RadioCards } from '@/components/common/RadioCards';
import { Select, type SelectOption } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import type { FoodType, MenuItem } from '@/types/catalog';
import { FOOD_TYPE_LABELS } from '@/utils/constants';
import {
  FOOD_DESCRIPTION_MAX_LENGTH,
  FOOD_IMAGE_URL_MAX_LENGTH,
  FOOD_NAME_MAX_LENGTH,
  FOOD_TEXT_MAX_LENGTH,
  type FoodForm,
} from '@/utils/validationUtils';

/**
 * The fields of the menu item form, in parts, so that the dialog can show them in one column and the "Add New Food
 * Item" page in cards. Each part gets the form of useFoodForm().
 */

const FOOD_TYPE_OPTIONS = (Object.keys(FOOD_TYPE_LABELS) as FoodType[]).map((value) => ({
  value,
  label: FOOD_TYPE_LABELS[value],
}));

interface FoodBasicsFieldsProps {
  form: UseFormReturn<FoodForm>;
  /** The item being edited (its restaurant is shown, it cannot be changed) */
  item?: MenuItem;
  /** The restaurants to choose from when creating */
  restaurants: readonly SelectOption[];
  /** The restaurant's name when editing */
  restaurantName?: string | null;
  categoryOptions: readonly SelectOption[];
  /** The restaurant field's empty choice (e.g. while the restaurants load) */
  restaurantPlaceholder?: string;
}

/** Restaurant, category, name and price, two by two on a wide enough screen. */
export function FoodBasicsFields({
  form,
  item,
  restaurants,
  restaurantName,
  categoryOptions,
  restaurantPlaceholder = 'Choose a restaurant',
}: FoodBasicsFieldsProps) {
  const {
    register,
    formState: { errors },
  } = form;
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {item ? (
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">Restaurant</span>
          <p className="flex h-11 items-center text-sm">
            {restaurantName ?? `Restaurant #${item.restaurantId}`}
          </p>
        </div>
      ) : (
        <Select
          label="Restaurant"
          placeholder={restaurantPlaceholder}
          options={restaurants}
          error={errors.restaurantId?.message}
          {...register('restaurantId')}
        />
      )}
      <Select
        label="Category"
        placeholder="Choose a category"
        options={categoryOptions}
        error={errors.categoryId?.message}
        {...register('categoryId')}
      />
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
    </div>
  );
}

/** Description, ingredients and nutrition info, each with its count of characters. */
export function FoodTextFields({ form }: { form: UseFormReturn<FoodForm> }) {
  const {
    register,
    control,
    formState: { errors },
  } = form;
  const description = useWatch({ control, name: 'description' });
  const ingredients = useWatch({ control, name: 'ingredients' });
  const nutritionInfo = useWatch({ control, name: 'nutritionInfo' });
  return (
    <>
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
    </>
  );
}

/** Veg, non-veg or egg, as three cards. */
export function FoodTypeField({ form }: { form: UseFormReturn<FoodForm> }) {
  const {
    control,
    formState: { errors },
  } = form;
  return (
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
  );
}

interface FoodImageFieldProps {
  form: UseFormReturn<FoodForm>;
  /** inline: a small round preview beside the field (the dialog); panel: a large picture under it (the page) */
  preview?: 'inline' | 'panel';
}

/** The picture's web address, with a preview once it is a valid address that loads. */
export function FoodImageField({ form, preview = 'inline' }: FoodImageFieldProps) {
  const {
    register,
    control,
    formState: { errors },
  } = form;
  const imageUrl = useWatch({ control, name: 'imageUrl' }).trim();
  const [brokenImage, setBrokenImage] = useState<string | null>(null);
  const isBroken = imageUrl !== '' && brokenImage === imageUrl;
  const showImage = imageUrl !== '' && !errors.imageUrl && !isBroken;

  const field = (
    <Input
      label="Image URL (optional)"
      type="url"
      placeholder="https://..."
      maxLength={FOOD_IMAGE_URL_MAX_LENGTH}
      error={errors.imageUrl?.message}
      {...register('imageUrl')}
    />
  );

  if (preview === 'inline') {
    return (
      <div className="flex items-start gap-4">
        <div className="min-w-0 flex-1">{field}</div>
        {showImage && (
          <img
            src={imageUrl}
            alt="Preview of the dish"
            onError={() => setBrokenImage(imageUrl)}
            className="mt-6 size-12 shrink-0 rounded-full object-cover"
          />
        )}
      </div>
    );
  }

  // The field beside a picture of limited size; one above the other in a narrow column (phone, the page's side column)
  return (
    <div className="grid items-start gap-4 sm:grid-cols-[minmax(0,1fr)_16rem] xl:grid-cols-1">
      {field}
      <div className="flex aspect-4/3 items-center justify-center overflow-hidden rounded-2xl bg-page ring-1 ring-line">
        {showImage ? (
          <img
            src={imageUrl}
            alt="Preview of the dish"
            onError={() => setBrokenImage(imageUrl)}
            className="size-full object-cover"
          />
        ) : (
          <p className="flex flex-col items-center gap-2 px-6 text-center text-sm text-ink-soft">
            {isBroken ? (
              <ImageOff aria-hidden="true" className="size-8" />
            ) : (
              <ImageIcon aria-hidden="true" className="size-8" />
            )}
            {isBroken
              ? 'This picture could not be loaded: check the address.'
              : 'The picture appears here once you enter its address.'}
          </p>
        )}
      </div>
    </div>
  );
}
