import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { FormDialog } from '@/components/common/FormDialog';
import { Select } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import type { Category, MenuItem } from '@/types/catalog';
import {
  REASON_MAX_LENGTH,
  changeCategoryFormSchema,
  showServerErrors,
  type ChangeCategoryForm,
} from '@/utils/validationUtils';

interface ChangeCategoryDialogProps {
  open: boolean;
  item: MenuItem;
  categories: readonly Category[];
  onSubmit: (categoryId: number, reason: string) => Promise<unknown>;
  onClose: () => void;
}

/** Moves a dish to another category (audited). Only active categories other than the current one are offered. */
export function ChangeCategoryDialog({
  open,
  item,
  categories,
  onSubmit,
  onClose,
}: ChangeCategoryDialogProps) {
  const options = categories
    .filter((category) => category.status === 'ACTIVE' && category.id !== item.categoryId)
    .map((category) => ({ value: String(category.id), label: category.name }));
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangeCategoryForm>({
    resolver: zodResolver(changeCategoryFormSchema),
    defaultValues: { categoryId: '', reason: '' },
  });

  const close = () => {
    reset();
    onClose();
  };

  const submit = handleSubmit(async ({ categoryId, reason }) => {
    try {
      await onSubmit(Number(categoryId), reason);
      close();
    } catch (error) {
      showServerErrors(error, setError, ['categoryId', 'reason']);
    }
  });

  return (
    <FormDialog
      open={open}
      title={`Move "${item.name}"`}
      submitLabel="Move item"
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={close}
    >
      <p className="text-sm">
        Now in <span className="font-semibold">{item.categoryName}</span>. Customers find it under
        the new category in menus and search.
      </p>
      <Select
        label="New category"
        placeholder="Choose a category"
        options={options}
        error={errors.categoryId?.message}
        {...register('categoryId')}
      />
      <Textarea
        label="Reason"
        placeholder="Kept in the audit log"
        maxLength={REASON_MAX_LENGTH}
        error={errors.reason?.message}
        {...register('reason')}
      />
    </FormDialog>
  );
}
