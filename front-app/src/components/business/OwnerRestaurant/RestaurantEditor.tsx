import { useState } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import { OperatingHoursEditor } from '@/components/business/OwnerRestaurant/OperatingHoursEditor';
import {
  RestaurantForm,
  RestaurantSection,
} from '@/components/business/OwnerRestaurant/RestaurantForm';
import { Card } from '@/components/common/Card';
import type { OwnerRestaurantRequest } from '@/types/owner';
import {
  hoursOf,
  hoursProblem,
  requestOf,
  type HoursRow,
  type RestaurantFormValues,
} from '@/utils/ownerRestaurantUtils';

interface RestaurantEditorProps {
  initialValues: RestaurantFormValues;
  initialHours: HoursRow[];
  /** Documents the restaurant already has besides the food licence: they are sent back unchanged */
  otherDocuments?: readonly { type: string; url: string }[];
  submitLabel: string;
  /**
   * Changing a restaurant that exists (not registering one): the save bar with "Discard changes", and a question
   * before leaving the page with unsaved changes.
   */
  editing?: boolean;
  /** Sends the request; a refusal (it throws) is shown above the button */
  onSave: (request: OwnerRestaurantRequest) => Promise<void>;
}

const sameRows = (a: readonly HoursRow[], b: readonly HoursRow[]) =>
  a.length === b.length &&
  a.every(
    (row, index) =>
      row.day === b[index].day &&
      row.open === b[index].open &&
      row.opensAt === b[index].opensAt &&
      row.closesAt === b[index].closesAt,
  );

/**
 * The restaurant form and its opening hours as one screen with one button: registering a restaurant and changing
 * one are the same thing to fill in. The hours are checked here (the form cannot be sent with hours that do not
 * add up); everything else by the form's own rules.
 */
export function RestaurantEditor({
  initialValues,
  initialHours,
  otherDocuments,
  submitLabel,
  editing = false,
  onSave,
}: RestaurantEditorProps) {
  const [hours, setHours] = useState(initialHours);
  const [hoursError, setHoursError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string>();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const submit = async (values: RestaurantFormValues) => {
    setServerError(undefined);
    const problem = hoursProblem(hours);
    setHoursError(problem);
    if (problem) {
      return;
    }
    setIsSubmitting(true);
    try {
      await onSave(requestOf(values, hoursOf(hours), otherDocuments));
    } catch (error) {
      setServerError(toApiError(error).userMessage);
    } finally {
      setIsSubmitting(false);
    }
  };

  const discard = () => {
    setHours(initialHours);
    setHoursError(null);
    setServerError(undefined);
  };

  return (
    <RestaurantForm
      defaultValues={initialValues}
      submitLabel={submitLabel}
      isSubmitting={isSubmitting}
      serverError={serverError}
      extraDirty={!sameRows(hours, initialHours)}
      onDiscard={editing ? discard : undefined}
      onSubmit={submit}
    >
      <RestaurantSection id="restaurant-hours">
        <Card title="Opening hours" subtitle="Customers can order only while you are open">
          <OperatingHoursEditor
            rows={hours}
            onChange={(rows) => {
              setHours(rows);
              setHoursError(null);
            }}
            error={hoursError}
          />
        </Card>
      </RestaurantSection>
    </RestaurantForm>
  );
}
