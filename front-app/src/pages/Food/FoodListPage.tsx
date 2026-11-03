import { UserPlus } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import {
  FoodFilterBar,
  FoodFormDialog,
  FoodGrid,
  FoodGridLoader,
  FoodTable,
  FoodViewToggle,
  MenuComparison,
} from '@/components/business/Food';
import { buttonStyles } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { SearchInput } from '@/components/common/SearchInput';
import { useToast } from '@/context/ToastContext';
import {
  useCategories,
  useDuplicateMenuItem,
  useMenuItems,
  useRemoveMenuItem,
  useUpdateMenuItem,
} from '@/hooks/api/catalogHooks';
import { useRestaurants } from '@/hooks/api/restaurantHooks';
import { useFoodViewMode } from '@/hooks/useFoodViewMode';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  foodFiltersChanged,
  foodFiltersCleared,
  foodPageChanged,
  selectFoodFilters,
  selectFoodPage,
} from '@/store/slices/foodListSlice';
import type { MenuItem } from '@/types/catalog';
import { FOOD_PAGE_SIZE, ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** The edit form: closed, or editing this item (a new item has its own page, ROUTES.foodNew). */
type FormState = null | MenuItem;

/** "Foods": every menu item of every restaurant, as cards or a table, with a comparison of the categories. */
export default function FoodListPage() {
  const dispatch = useAppDispatch();
  const { showToast } = useToast();
  const filters = useAppSelector(selectFoodFilters);
  const page = useAppSelector(selectFoodPage);
  const [view, setView] = useFoodViewMode();
  const items = useMenuItems(filters, page, FOOD_PAGE_SIZE);
  const categories = useCategories();
  // Names for the cards; the first 100 restaurants (others show their number)
  const restaurants = useRestaurants({ sort: 'name,asc' }, 0, 100);
  const restaurantList = restaurants.data?.content;
  const restaurantNames = useMemo(
    () => new Map((restaurantList ?? []).map((restaurant) => [restaurant.id, restaurant.name])),
    [restaurantList],
  );
  const restaurantOptions = useMemo(
    () => (restaurantList ?? []).map((r) => ({ value: String(r.id), label: r.name })),
    [restaurantList],
  );

  const updateItem = useUpdateMenuItem();
  const duplicateItem = useDuplicateMenuItem();
  const removeItem = useRemoveMenuItem();
  const [form, setForm] = useState<FormState>(null);
  const [deleting, setDeleting] = useState<MenuItem | null>(null);
  const [duplicatingId, setDuplicatingId] = useState<number | null>(null);

  const clearFilters = () => dispatch(foodFiltersCleared());

  const duplicate = async (item: MenuItem) => {
    setDuplicatingId(item.id);
    try {
      const copy = await duplicateItem.mutateAsync(item.id);
      showToast(`Copy created: "${copy.name}" (hidden until you list it)`);
    } catch (error) {
      showToast(toApiError(error).userMessage, 'error');
    } finally {
      setDuplicatingId(null);
    }
  };

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Foods"
        subtitle="Every dish of every restaurant: find, edit and compare them"
        actions={
          <div className="flex w-full flex-wrap items-center gap-3 lg:w-auto">
            <div className="min-w-48 flex-1 lg:w-72 lg:flex-none">
              <SearchInput
                aria-label="Search menu items by name"
                placeholder="Search by name"
                maxLength={120}
                value={filters.search ?? ''}
                onSearch={(text) => dispatch(foodFiltersChanged({ search: text }))}
              />
            </div>
            <FoodViewToggle value={view} onChange={setView} />
            {/* A page of its own: the whole form fits on any screen (it scrolls under the header) */}
            <Link to={ROUTES.foodNew} className={buttonStyles('primary', 'md')}>
              <UserPlus aria-hidden="true" className="size-4" />
              New Menu
            </Link>
          </div>
        }
      />

      <Card>
        <FoodFilterBar
          filters={filters}
          categories={categories.data ?? []}
          onChange={(changes) => dispatch(foodFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>

      <PagedResults
        query={items}
        errorTitle={ERROR_MESSAGES.FOODS_LOAD_FAILED}
        emptyTitle="No menu items yet"
        filteredEmptyTitle="No menu items match these filters"
        // The sort order is always set; any other key is a filter the admin chose
        hasFilters={Object.keys(filters).some((key) => key !== 'sort')}
        onClearFilters={clearFilters}
        onPageChange={(newPage) => dispatch(foodPageChanged(newPage))}
        columns={7}
        loading={view === 'grid' ? <FoodGridLoader count={FOOD_PAGE_SIZE} /> : undefined}
        summary={(shown, total) => `Showing ${shown} from ${total} menu items`}
      >
        {(rows, isUpdating) =>
          view === 'grid' ? (
            <FoodGrid
              items={rows}
              restaurantNames={restaurantNames}
              isUpdating={isUpdating}
              duplicatingId={duplicatingId}
              onEdit={setForm}
              onDelete={setDeleting}
              onDuplicate={(item) => void duplicate(item)}
            />
          ) : (
            <Card>
              <FoodTable items={rows} isUpdating={isUpdating} />
            </Card>
          )
        }
      </PagedResults>

      <MenuComparison />

      {form !== null && (
        <FoodFormDialog
          open
          item={form}
          restaurants={restaurantOptions}
          restaurantName={form.restaurantName ?? restaurantNames.get(form.restaurantId)}
          categories={categories.data ?? []}
          onUpdate={async (itemId, request) => {
            const updated = await updateItem.mutateAsync({ itemId, item: request });
            showToast(`"${updated.name}" was updated.`);
          }}
          onClose={() => setForm(null)}
        />
      )}
      {deleting && (
        <ReasonDialog
          open
          title={`Delete "${deleting.name}"?`}
          description="The item is deleted from the restaurant's menu. Past orders keep their own copy of it. This cannot be undone."
          confirmLabel="Delete"
          tone="danger"
          onSubmit={async (reason) => {
            await removeItem.mutateAsync({ itemId: deleting.id, reason });
            showToast(`"${deleting.name}" was deleted.`);
          }}
          onClose={() => setDeleting(null)}
        />
      )}
    </div>
  );
}
