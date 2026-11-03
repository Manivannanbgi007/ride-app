import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { toApiError } from '@/api/apiErrorHandler';
import {
  isStaleVersion,
  priceFormSchema,
  staleMessageOf,
  type PriceForm,
} from '@/components/business/OwnerMenu/ownerMenuUtils';
import { FormDialog } from '@/components/common/FormDialog';
import { Input } from '@/components/common/Input';
import type { MenuItem } from '@/types/catalog';
import { DEFAULT_CURRENCY } from '@/utils/constants';
import { formatCurrency } from '@/utils/formatUtils';
import { showServerErrors } from '@/utils/validationUtils';

interface PriceDialogProps {
  item: MenuItem;
  /** Saves the new price; `version` is the one the owner saw (the server answers 409 if the dish changed since) */
  onSave: (itemId: number, price: number, version: number) => Promise<unknown>;
  /** The save was refused because the dish changed since it was opened: the page reloads the menu */
  onStale: () => void;
  onClose: () => void;
}

/** "Edit price": a small dialog for the most frequent change of a menu, so it needs no full form. */
export function PriceDialog({ item, onSave, onStale, onClose }: PriceDialogProps) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PriceForm>({
    resolver: zodResolver(priceFormSchema),
    defaultValues: { price: item.price.toFixed(2) },
  });

  const submit = handleSubmit(async (values) => {
    try {
      await onSave(item.id, Number(values.price), item.version);
      onClose();
    } catch (error) {
      showServerErrors(error, setError, ['price']);
      if (isStaleVersion(error)) {
        setError('root.server', { message: staleMessageOf(toApiError(error)) });
        onStale();
      }
    }
  });

  return (
    <FormDialog
      open
      title={`Price of "${item.name}"`}
      submitLabel="Save price"
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={onClose}
      size="sm"
    >
      <Input
        label="Price (INR)"
        inputMode="decimal"
        placeholder="249.00"
        autoFocus
        hint={`Now ${formatCurrency(item.price, DEFAULT_CURRENCY)}`}
        error={errors.price?.message}
        {...register('price')}
      />
    </FormDialog>
  );
}
