import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm, useWatch } from 'react-hook-form';

import { CurrentLocationField } from '@/components/business/Address/CurrentLocationField';
import { FormDialog } from '@/components/common/FormDialog';
import { Input } from '@/components/common/Input';
import { RadioCards } from '@/components/common/RadioCards';
import { useSaveAddress } from '@/hooks/api/addressHooks';
import { ADDRESS_TYPES, type Address } from '@/types/shopping';
import { ADDRESS_TYPE_LABELS } from '@/utils/constants';
import { addressFormSchema, showServerErrors, type AddressForm } from '@/utils/validationUtils';

interface AddressFormDialogProps {
  /** Change this address; without it a new one is added */
  address?: Address;
  /** The first address is always the default: the box is hidden then */
  isFirst?: boolean;
  onSaved?: (address: Address) => void;
  onClose: () => void;
}

const toForm = (address?: Address): AddressForm => ({
  type: address?.type ?? 'HOME',
  line1: address?.line1 ?? '',
  line2: address?.line2 ?? '',
  city: address?.city ?? '',
  state: address?.state ?? '',
  postalCode: address?.postalCode ?? '',
  country: address?.country ?? 'India',
  latitude: address?.latitude ?? null,
  longitude: address?.longitude ?? null,
  makeDefault: address?.defaultAddress ?? false,
});

/**
 * Adds or changes a delivery address (user-service checks the same rules and keeps at most 10), with an optional map
 * pin from the device's position. A pin saved earlier is kept when the address is changed.
 */
export function AddressFormDialog({
  address,
  isFirst = false,
  onSaved,
  onClose,
}: AddressFormDialogProps) {
  const save = useSaveAddress();
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<AddressForm>({
    resolver: zodResolver(addressFormSchema),
    defaultValues: toForm(address),
  });

  const latitude = useWatch({ control, name: 'latitude' });
  const longitude = useWatch({ control, name: 'longitude' });

  const submit = handleSubmit(async (form) => {
    try {
      const saved = await save.mutateAsync({
        addressId: address?.id,
        request: { ...form, line2: form.line2 || undefined },
      });
      onSaved?.(saved);
      onClose();
    } catch (error) {
      showServerErrors(error, setError, [
        'line1',
        'line2',
        'city',
        'state',
        'postalCode',
        'country',
      ]);
    }
  });

  return (
    <FormDialog
      open
      title={address ? 'Change address' : 'New address'}
      submitLabel={address ? 'Save address' : 'Add address'}
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={onClose}
      size="lg"
    >
      <Controller
        control={control}
        name="type"
        render={({ field }) => (
          <RadioCards
            legend="Kind of address"
            columns={3}
            options={ADDRESS_TYPES.map((type) => ({
              value: type,
              label: ADDRESS_TYPE_LABELS[type],
            }))}
            value={field.value}
            onChange={field.onChange}
            error={errors.type?.message}
          />
        )}
      />
      <Input
        label="Street and number"
        autoComplete="address-line1"
        maxLength={255}
        error={errors.line1?.message}
        {...register('line1')}
      />
      <Input
        label="Flat, floor, landmark (optional)"
        autoComplete="address-line2"
        maxLength={255}
        error={errors.line2?.message}
        {...register('line2')}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <Input
          label="City"
          autoComplete="address-level2"
          maxLength={100}
          error={errors.city?.message}
          {...register('city')}
        />
        <Input
          label="State"
          autoComplete="address-level1"
          maxLength={100}
          error={errors.state?.message}
          {...register('state')}
        />
        <Input
          label="Postal code"
          autoComplete="postal-code"
          maxLength={10}
          error={errors.postalCode?.message}
          {...register('postalCode')}
        />
        <Input
          label="Country"
          autoComplete="country-name"
          maxLength={100}
          error={errors.country?.message}
          {...register('country')}
        />
      </div>
      <CurrentLocationField
        value={latitude !== null && longitude !== null ? { latitude, longitude } : null}
        onChange={(pin) => {
          setValue('latitude', pin?.latitude ?? null, { shouldDirty: true });
          setValue('longitude', pin?.longitude ?? null, { shouldDirty: true });
        }}
      />
      {!isFirst && !address?.defaultAddress && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="size-4 accent-brand-500" {...register('makeDefault')} />
          Make this my default address
        </label>
      )}
    </FormDialog>
  );
}
