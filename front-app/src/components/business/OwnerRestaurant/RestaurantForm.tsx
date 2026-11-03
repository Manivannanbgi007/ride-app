import { zodResolver } from '@hookform/resolvers/zod';
import { CircleCheck } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';

import type { RestaurantSectionId } from '@/components/business/OwnerRestaurant/restaurantSections';
import { Alert } from '@/components/common/Alert';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { Input } from '@/components/common/Input';
import { Textarea } from '@/components/common/Textarea';
import { useUnsavedChangesGuard } from '@/hooks/useUnsavedChangesGuard';
import { restaurantFormSchema, type RestaurantFormValues } from '@/utils/ownerRestaurantUtils';

interface RestaurantFormProps {
  defaultValues: RestaurantFormValues;
  submitLabel: string;
  /** The request is on its way: the button shows a spinner */
  isSubmitting?: boolean;
  /** What the server refused, shown above the button */
  serverError?: string;
  /** Rendered between the details and the button (the opening hours) */
  children?: ReactNode;
  /** The hours or anything else outside the form is not valid: the form cannot be sent yet */
  blocked?: boolean;
  /** Something outside the form (the opening hours) was changed and is not saved yet */
  extraDirty?: boolean;
  /**
   * Changing a restaurant that exists: the buttons sit in a bar that stays at the bottom of the screen, with
   * "Discard changes" (which calls this after putting the fields back), and leaving the page with unsaved changes
   * asks first. Without it (registering) there is the one button at the end of the form.
   */
  onDiscard?: () => void;
  onSubmit: (values: RestaurantFormValues) => void | Promise<void>;
}

/** A part of the form, with the id the "On this page" links jump to (clear of the sticky header). */
export function RestaurantSection({
  id,
  children,
}: {
  id: RestaurantSectionId;
  children: ReactNode;
}) {
  return (
    <div id={id} className="scroll-mt-28">
      {children}
    </div>
  );
}

/**
 * The restaurant's details as one form: who they are, how to reach them, where, the legal information and the food
 * licence. Used to register a restaurant and to change it. Every message comes from the same rules the server
 * applies (utils/ownerRestaurantUtils.ts), so a form that passes here is accepted there.
 */
export function RestaurantForm({
  defaultValues,
  submitLabel,
  isSubmitting = false,
  serverError,
  children,
  blocked = false,
  extraDirty = false,
  onDiscard,
  onSubmit,
}: RestaurantFormProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<RestaurantFormValues>({
    resolver: zodResolver(restaurantFormSchema),
    defaultValues,
  });
  const editing = onDiscard !== undefined;
  const unsaved = editing && (isDirty || extraDirty);
  const guard = useUnsavedChangesGuard(unsaved && !isSubmitting);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  const discard = () => {
    reset(defaultValues);
    onDiscard?.();
    setConfirmDiscard(false);
  };

  return (
    <form
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
      noValidate
      className="flex flex-col gap-6"
    >
      <RestaurantSection id="restaurant-about">
        <Card
          title="About the restaurant"
          subtitle="What customers read about you, and how to reach you"
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Restaurant name" error={errors.name?.message} {...register('name')} />
            <Input
              label="Cuisines"
              hint="Separate with commas, up to 5: Biryani, North Indian"
              error={errors.cuisines?.message}
              {...register('cuisines')}
            />
            <div className="sm:col-span-2">
              <Textarea
                label="Description (optional)"
                rows={3}
                maxLength={1000}
                error={errors.description?.message}
                {...register('description')}
              />
            </div>
            <Input
              label="Phone"
              type="tel"
              autoComplete="tel"
              hint="With the country code, e.g. +919876543210"
              error={errors.phone?.message}
              {...register('phone')}
            />
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email')}
            />
          </div>
        </Card>
      </RestaurantSection>

      <RestaurantSection id="restaurant-address">
        <Card title="Address" subtitle="Where riders pick the orders up">
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Street address" error={errors.line1?.message} {...register('line1')} />
            <Input
              label="Address line 2 (optional)"
              error={errors.line2?.message}
              {...register('line2')}
            />
            <Input label="City" error={errors.city?.message} {...register('city')} />
            <Input label="State" error={errors.state?.message} {...register('state')} />
            <Input
              label="Postal code"
              error={errors.postalCode?.message}
              {...register('postalCode')}
            />
            <Input label="Country" error={errors.country?.message} {...register('country')} />
            <Input
              label="Latitude (optional)"
              inputMode="decimal"
              hint="Lets customers find you with “near me”"
              error={errors.latitude?.message}
              {...register('latitude')}
            />
            <Input
              label="Longitude (optional)"
              inputMode="decimal"
              error={errors.longitude?.message}
              {...register('longitude')}
            />
            <Input
              label="Time zone"
              hint="Your opening hours are in this time zone, e.g. Asia/Kolkata"
              error={errors.timeZone?.message}
              {...register('timeZone')}
            />
          </div>
        </Card>
      </RestaurantSection>

      <RestaurantSection id="restaurant-business">
        <Card
          title="Business and licence"
          subtitle="Checked by the platform team before you go live"
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label="Legal business name"
              error={errors.legalBusinessName?.message}
              {...register('legalBusinessName')}
            />
            <Input
              label="Tax registration number (optional)"
              hint="GSTIN or another tax number"
              error={errors.taxRegistrationNumber?.message}
              {...register('taxRegistrationNumber')}
            />
            <Input
              label="Food licence number"
              hint="FSSAI in India"
              error={errors.foodLicenseNumber?.message}
              {...register('foodLicenseNumber')}
            />
            <Input
              label="Food licence document (web address)"
              hint="Where the scanned licence is stored, e.g. https://..."
              error={errors.foodLicenseUrl?.message}
              {...register('foodLicenseUrl')}
            />
          </div>
        </Card>
      </RestaurantSection>

      {children}

      {serverError && <Alert tone="error">{serverError}</Alert>}

      {editing ? (
        // Stays in sight at the bottom of the screen while the owner scrolls through the form
        <div className="sticky bottom-4 z-20">
          <div
            role="region"
            aria-label="Save your changes"
            className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-surface/95 p-4 shadow-lg ring-1 ring-line backdrop-blur"
          >
            <p
              aria-live="polite"
              className="flex items-center gap-2 text-sm font-semibold text-ink"
            >
              {unsaved ? (
                <>
                  <span aria-hidden="true" className="size-2.5 rounded-full bg-warning-500" />
                  You have unsaved changes
                </>
              ) : (
                <>
                  <CircleCheck aria-hidden="true" className="size-4 text-brand-text" />
                  Everything is saved
                </>
              )}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                disabled={!unsaved || isSubmitting}
                onClick={() => setConfirmDiscard(true)}
              >
                Discard changes
              </Button>
              <Button type="submit" isLoading={isSubmitting} disabled={!unsaved || blocked}>
                {submitLabel}
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <div>
          <Button type="submit" isLoading={isSubmitting} disabled={blocked}>
            {submitLabel}
          </Button>
        </div>
      )}

      {editing && (
        <>
          <ConfirmationDialog
            open={confirmDiscard}
            title="Discard your changes?"
            message="The form goes back to what is saved. What you changed since then is lost."
            confirmLabel="Discard changes"
            cancelLabel="Keep editing"
            tone="danger"
            onConfirm={discard}
            onCancel={() => setConfirmDiscard(false)}
          />
          <ConfirmationDialog
            open={guard.leaveTo !== null}
            title="Leave without saving?"
            message="You have changes to your restaurant that are not saved. If you leave now, they are lost."
            confirmLabel="Leave without saving"
            cancelLabel="Keep editing"
            tone="danger"
            onConfirm={guard.leave}
            onCancel={guard.stay}
          />
        </>
      )}
    </form>
  );
}
