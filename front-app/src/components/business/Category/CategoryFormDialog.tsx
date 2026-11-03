import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { FormDialog } from '@/components/common/FormDialog';
import { Input } from '@/components/common/Input';
import { Textarea } from '@/components/common/Textarea';
import type { Category, CategoryRequest } from '@/types/catalog';
import { categoryFormSchema, showServerErrors, type CategoryForm } from '@/utils/validationUtils';

interface CategoryFormDialogProps {
  /** The category to change; none: a new category */
  category?: Category;
  onSubmit: (request: CategoryRequest) => Promise<unknown>;
  onClose: () => void;
}

/**
 * Creates a category (added at the end of the menu order, active) or renames one. Names are unique in any letter
 * case: a taken name is refused by catalog-service and the message shown here.
 */
export function CategoryFormDialog({ category, onSubmit, onClose }: CategoryFormDialogProps) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CategoryForm>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: {
      name: category?.name ?? '',
      description: category?.description ?? '',
      imageUrl: category?.imageUrl ?? '',
    },
  });

  const submit = handleSubmit(async (form) => {
    try {
      await onSubmit({
        name: form.name,
        description: form.description === '' ? null : form.description,
        imageUrl: form.imageUrl === '' ? null : form.imageUrl,
      });
      onClose();
    } catch (error) {
      showServerErrors(error, setError, ['name', 'description', 'imageUrl']);
    }
  });

  return (
    <FormDialog
      open
      title={category ? `Change "${category.name}"` : 'New category'}
      submitLabel={category ? 'Save' : 'Create category'}
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={onClose}
    >
      <Input label="Name" maxLength={60} error={errors.name?.message} {...register('name')} />
      <Textarea
        label="Description"
        rows={3}
        maxLength={255}
        hint="Optional"
        error={errors.description?.message}
        {...register('description')}
      />
      <Input
        label="Image address"
        type="url"
        placeholder="https://"
        hint="Optional: a picture for menus"
        error={errors.imageUrl?.message}
        {...register('imageUrl')}
      />
    </FormDialog>
  );
}
