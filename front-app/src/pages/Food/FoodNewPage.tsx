import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  FoodBasicsFields,
  FoodImageField,
  FoodTextFields,
  FoodTypeField,
  foodFormToCreateRequest,
  useFoodForm,
} from '@/components/business/Food';
import { Alert } from '@/components/common/Alert';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useCategories, useCreateMenuItem } from '@/hooks/api/catalogHooks';
import { useRestaurants } from '@/hooks/api/restaurantHooks';
import { useLeaveWarning } from '@/hooks/useLeaveWarning';
import { ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/**
 * "Add New Food Item" (/admin/foods/new): a new dish on a restaurant's menu, on a page of its own so that every field
 * can be reached on any screen (the page scrolls under the header; Cancel and Create stay at the bottom of the
 * screen). The fields, rules and server messages are the "New Menu" dialog's (useFoodForm). Leaving with unsaved
 * changes asks first.
 */
export default function FoodNewPage() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const categories = useCategories();
  // The same choice as the Foods page: the first 100 restaurants, by name
  const restaurants = useRestaurants({ sort: 'name,asc' }, 0, 100);
  const createItem = useCreateMenuItem();
  const [confirmLeave, setConfirmLeave] = useState(false);

  const restaurantList = restaurants.data?.content;
  const restaurantOptions = useMemo(
    () => (restaurantList ?? []).map((r) => ({ value: String(r.id), label: r.name })),
    [restaurantList],
  );

  const { form, categoryOptions, submit } = useFoodForm({
    categories: categories.data ?? [],
    onSubmit: async (values) => {
      const created = await createItem.mutateAsync(foodFormToCreateRequest(values));
      showToast(`"${created.name}" was added.`);
      void navigate(ROUTES.foods);
    },
  });
  const {
    formState: { errors, isDirty: hasChanges, isSubmitting },
  } = form;
  useLeaveWarning(hasChanges);

  const backToFoods = () => void navigate(ROUTES.foods);
  /** Cancel and "Back to foods": straight back, or first a question when something was typed */
  const leave = () => (hasChanges ? setConfirmLeave(true) : backToFoods());

  const loadFailed = restaurants.isError || categories.isError;
  const isReloading = restaurants.isFetching || categories.isFetching;

  return (
    <div className="flex flex-col gap-6">
      <BackLink
        to={ROUTES.foods}
        onClick={(event) => {
          if (hasChanges) {
            event.preventDefault();
            setConfirmLeave(true);
          }
        }}
      >
        Back to foods
      </BackLink>
      <PageHeader
        title="Add New Food Item"
        subtitle="A new dish on a restaurant's menu. Fields marked optional can stay empty."
      />

      <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-6">
        {loadFailed && (
          <Alert
            tone="error"
            title={
              restaurants.isError
                ? ERROR_MESSAGES.RESTAURANTS_LOAD_FAILED
                : ERROR_MESSAGES.CATEGORIES_LOAD_FAILED
            }
          >
            <div className="flex flex-wrap items-center gap-3">
              <span>Without them the item cannot be created.</span>
              <Button
                size="sm"
                variant="outline"
                isLoading={isReloading}
                onClick={() => {
                  void restaurants.refetch();
                  void categories.refetch();
                }}
              >
                Try again
              </Button>
            </div>
          </Alert>
        )}
        {errors.root?.server && <Alert tone="error">{errors.root.server.message}</Alert>}

        <div className="grid items-start gap-6 xl:grid-cols-3">
          <div className="flex min-w-0 flex-col gap-6 xl:col-span-2">
            <Card
              title="The dish"
              subtitle="The restaurant that sells it, its category, name and price"
            >
              <div className="flex flex-col gap-5">
                <FoodBasicsFields
                  form={form}
                  restaurants={restaurantOptions}
                  categoryOptions={categoryOptions}
                  restaurantPlaceholder={
                    restaurants.isPending ? 'Loading restaurants…' : 'Choose a restaurant'
                  }
                />
                <FoodTypeField form={form} />
              </div>
            </Card>
            <Card title="Description and details" subtitle="What customers read on the menu">
              <div className="flex flex-col gap-4">
                <FoodTextFields form={form} />
              </div>
            </Card>
          </div>
          {/* Beside the fields on a wide screen, and kept in view below the header while they scroll */}
          <div className="min-w-0 xl:sticky xl:top-28">
            <Card title="Photo" subtitle="A web address of the dish's picture">
              <FoodImageField form={form} preview="panel" />
            </Card>
          </div>
        </div>

        {/*
         * Cancel and Create stay at the bottom of the screen while the form scrolls. The space on the right keeps
         * them clear of the assistant's round button (fixed at the bottom right of the admin area).
         */}
        <div className="sticky bottom-4 z-10 flex items-center gap-3 rounded-2xl bg-surface/90 py-3 pr-16 pl-4 shadow-card ring-1 ring-line backdrop-blur-xl sm:pr-12 lg:pr-10">
          <p className="hidden text-sm text-ink-soft sm:block">
            {hasChanges ? 'Unsaved changes' : 'Nothing entered yet'}
          </p>
          <div className="ml-auto flex items-center gap-3">
            <Button variant="ghost" onClick={leave} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" isLoading={isSubmitting}>
              Create menu item
            </Button>
          </div>
        </div>
      </form>

      <ConfirmationDialog
        open={confirmLeave}
        title="Discard this new item?"
        message="What you entered is not saved yet. Leave the page and lose it?"
        confirmLabel="Discard"
        cancelLabel="Keep editing"
        tone="danger"
        onConfirm={backToFoods}
        onCancel={() => setConfirmLeave(false)}
      />
    </div>
  );
}
