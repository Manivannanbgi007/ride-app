import { Plus, UtensilsCrossed } from 'lucide-react';
import { useState } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import {
  categoryOptionsOf,
  dishCountText,
  filterMenuItems,
  filtersFromParams,
  hasMenuFilters,
  MENU_QUERY_KEYS,
  menuSortOf,
  menuStateCounts,
  MenuStatusChips,
  OWNER_MENU_MESSAGES,
  OWNER_MENU_PAGE_SIZE,
  OwnerMenuFilterBar,
  OwnerMenuItemDialog,
  OwnerMenuList,
  OwnerMenuLoader,
  paginate,
  PriceDialog,
  sortMenuItems,
} from '@/components/business/OwnerMenu';
import { DishReviewsDialog } from '@/components/business/Rating';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { useToast } from '@/context/ToastContext';
import {
  useCreateMenuItem,
  useOwnerMenu,
  useSetAvailability,
  useSetMenuItemVisible,
  useUpdateMenuItem,
  useUpdatePrice,
} from '@/hooks/api/ownerHooks';
import { useDishRatings } from '@/hooks/api/ratingHooks';
import { useStoreCategories } from '@/hooks/api/storefrontHooks';
import { pageFromParam, pageToParam, useQueryParams } from '@/hooks/useQueryParams';
import { useOwnerRestaurant } from '@/layouts/owner/ownerRestaurantContext';
import type { MenuItem } from '@/types/catalog';

/** The open dialog, if any: adding a dish, editing one, or changing only its price. */
type Dialog = null | { kind: 'add' } | { kind: 'edit' | 'price'; item: MenuItem };

/**
 * "Menu": the owner's dishes as cards, with the quick controls of each (sold out, price, hide) and the full edit
 * form. The status chips count the dishes and filter them. The API sends every dish at once, so searching,
 * filtering, sorting and paging are done here, and all of them live in the address.
 */
export default function OwnerMenuPage() {
  const { restaurantId, restaurant } = useOwnerRestaurant();
  const { showToast } = useToast();
  const menu = useOwnerMenu(restaurantId);
  const categories = useStoreCategories();
  const createItem = useCreateMenuItem(restaurantId);
  const updateItem = useUpdateMenuItem(restaurantId);
  const updatePrice = useUpdatePrice(restaurantId);
  const setAvailability = useSetAvailability(restaurantId);
  const setVisible = useSetMenuItemVisible(restaurantId);

  const [params, setParams] = useQueryParams(MENU_QUERY_KEYS);
  const filters = filtersFromParams(params);
  const sort = menuSortOf(params.sort);
  const filtered = hasMenuFilters(filters);

  const [dialog, setDialog] = useState<Dialog>(null);
  const [hiding, setHiding] = useState<MenuItem | null>(null);
  // Dishes with a quick action running: their buttons wait until the menu has been reloaded
  const [busyIds, setBusyIds] = useState<readonly number[]>([]);

  const dishes = menu.data;
  const matching = dishes ? sortMenuItems(filterMenuItems(dishes, filters), sort) : [];
  const stateCounts = menuStateCounts(dishes ?? [], filters);
  const categoryOptions = categoryOptionsOf(dishes ?? []);
  const view = paginate(matching, pageFromParam(params.page));
  // The customers' stars of the dishes on this page (one request), and the dish whose reviews are open
  const dishRatings = useDishRatings(view.rows.map((dish) => dish.id));
  const [reviewsOf, setReviewsOf] = useState<MenuItem | null>(null);

  const clearFilters = () =>
    setParams({ search: undefined, category: undefined, type: undefined, status: undefined });

  // Someone (or another tab) changed a dish meanwhile: show what is there now
  const reloadMenu = () => void menu.refetch();

  const showFailure = (error: unknown) => {
    const apiError = toApiError(error);
    showToast(apiError.userMessage, 'error');
    if (apiError.status === 409) {
      reloadMenu();
    }
  };

  /** Runs a quick action on one dish: its buttons wait, a toast says how it went. Returns whether it worked. */
  const runOnDish = async (item: MenuItem, action: () => Promise<unknown>, success: string) => {
    setBusyIds((ids) => [...ids, item.id]);
    try {
      await action();
      showToast(success);
      return true;
    } catch (error) {
      showFailure(error);
      return false;
    } finally {
      setBusyIds((ids) => ids.filter((id) => id !== item.id));
    }
  };

  const toggleStock = (item: MenuItem) =>
    runOnDish(
      item,
      () => setAvailability.mutateAsync({ itemId: item.id, available: !item.available }),
      item.available ? `"${item.name}" is marked sold out.` : `"${item.name}" is back in stock.`,
    );

  const showOnMenu = (item: MenuItem) =>
    runOnDish(
      item,
      () => setVisible.mutateAsync({ itemId: item.id, visible: true }),
      `"${item.name}" is on the menu again.`,
    );

  const confirmHide = async () => {
    if (!hiding) {
      return;
    }
    const item = hiding;
    await runOnDish(
      item,
      () => setVisible.mutateAsync({ itemId: item.id, visible: false }),
      `"${item.name}" is hidden from the menu.`,
    );
    setHiding(null);
  };

  const toggleVisible = (item: MenuItem) => {
    if (item.status === 'ACTIVE') {
      // Hiding takes a dish away from customers: ask first
      setHiding(item);
    } else {
      void showOnMenu(item);
    }
  };

  const count = dishes ? dishCountText(matching.length, dishes.length) : null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Menu"
        subtitle={`What ${restaurant.name} serves`}
        actions={
          <Button
            size="lg"
            onClick={() => setDialog({ kind: 'add' })}
            leftIcon={<Plus aria-hidden="true" className="size-4" />}
          >
            Add dish
          </Button>
        }
      />

      {dishes && dishes.length > 0 && (
        <Card>
          <div className="flex flex-col gap-5">
            <MenuStatusChips
              counts={stateCounts}
              value={filters.state}
              onChange={(state) => setParams({ status: state })}
            />
            <div className="border-t border-line pt-5">
              <OwnerMenuFilterBar
                filters={filters}
                sort={sort}
                categoryOptions={categoryOptions}
                onChange={setParams}
                onClear={clearFilters}
              />
            </div>
          </div>
        </Card>
      )}

      <AsyncContent
        isLoading={menu.isPending}
        // A failed reload keeps showing the dishes already loaded
        error={dishes ? undefined : menu.error}
        isEmpty={dishes?.length === 0}
        errorTitle={OWNER_MENU_MESSAGES.LOAD_FAILED}
        onRetry={reloadMenu}
        isRetrying={menu.isFetching}
        loading={<OwnerMenuLoader />}
        empty={
          <Card>
            <EmptyState
              icon={UtensilsCrossed}
              title="Your menu is empty — add your first dish"
              message="Customers see the dishes you add here once your restaurant is live."
              action={
                <Button onClick={() => setDialog({ kind: 'add' })}>Add your first dish</Button>
              }
            />
          </Card>
        }
      >
        <div className="flex flex-col gap-4">
          <p aria-live="polite" className="text-sm font-medium text-ink-soft">
            {count}
          </p>
          {matching.length === 0 ? (
            <Card>
              <EmptyState
                title="No dish matches these filters"
                action={filtered && <ClearFiltersButton size="sm" onClick={clearFilters} />}
              />
            </Card>
          ) : (
            <>
              <OwnerMenuList
                items={view.rows}
                busyIds={busyIds}
                onToggleStock={(item) => void toggleStock(item)}
                onEditPrice={(item) => setDialog({ kind: 'price', item })}
                onEdit={(item) => setDialog({ kind: 'edit', item })}
                onToggleVisible={toggleVisible}
                ratings={dishRatings}
                onShowReviews={setReviewsOf}
              />
              <Pagination
                page={view.page}
                size={OWNER_MENU_PAGE_SIZE}
                totalPages={view.totalPages}
                totalElements={matching.length}
                onPageChange={(page) => setParams({ page: pageToParam(page) })}
                summary={`Showing ${view.rows.length} of ${matching.length} dishes`}
              />
            </>
          )}
        </div>
      </AsyncContent>

      {(dialog?.kind === 'add' || dialog?.kind === 'edit') && (
        <OwnerMenuItemDialog
          item={dialog.kind === 'edit' ? dialog.item : undefined}
          categories={categories.data ?? []}
          categoriesStatus={categories.status}
          onCreate={async (request) => {
            const created = await createItem.mutateAsync(request);
            showToast(`"${created.name}" was added to your menu.`);
          }}
          onUpdate={async (itemId, request) => {
            const updated = await updateItem.mutateAsync({ itemId, item: request });
            showToast(`"${updated.name}" was updated.`);
          }}
          onStale={reloadMenu}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'price' && (
        <PriceDialog
          item={dialog.item}
          onSave={async (itemId, price, version) => {
            const updated = await updatePrice.mutateAsync({ itemId, price, version });
            showToast(`The price of "${updated.name}" was changed.`);
          }}
          onStale={reloadMenu}
          onClose={() => setDialog(null)}
        />
      )}
      <DishReviewsDialog
        dish={reviewsOf}
        onClose={() => setReviewsOf(null)}
        emptyMessage="Customers rate a dish when they review a delivered order with it."
      />
      <ConfirmationDialog
        open={hiding !== null}
        title={`Hide "${hiding?.name ?? ''}" from the menu?`}
        message="Customers will not see this dish until you show it again. Orders already placed are not affected."
        confirmLabel="Hide dish"
        isLoading={hiding !== null && busyIds.includes(hiding.id)}
        onConfirm={() => void confirmHide()}
        onCancel={() => setHiding(null)}
      />
    </div>
  );
}
