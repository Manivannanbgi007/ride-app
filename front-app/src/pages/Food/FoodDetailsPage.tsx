import { Ban, FolderInput, RotateCcw, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { AuditHistory } from '@/components/business/AuditHistory';
import {
  ChangeCategoryDialog,
  DishRevenueCard,
  DishReviews,
  FoodDetailCard,
  FoodFormDialog,
} from '@/components/business/Food';
import { AsyncContent } from '@/components/common/AsyncContent';
import { BackLink } from '@/components/common/BackLink';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { useToast } from '@/context/ToastContext';
import {
  useCategories,
  useChangeMenuItemCategory,
  useChangeMenuItemStatus,
  useCreateMenuItem,
  useMenuItem,
  useMenuItemAuditLogs,
  useRemoveMenuItem,
  useUpdateMenuItem,
} from '@/hooks/api/catalogHooks';
import { useRestaurant, useRestaurants } from '@/hooks/api/restaurantHooks';
import { useIdParam } from '@/hooks/useIdParam';
import { NotFoundPage } from '@/pages/NotFound/NotFoundPage';
import type { MenuItem } from '@/types/catalog';
import { ROUTES } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** "Food Detail": one menu item, its revenue and reviews, its change history, and the admin actions on it. */
export default function FoodDetailsPage() {
  const itemId = useIdParam('itemId');
  return itemId === null ? <NotFoundPage /> : <FoodDetails itemId={itemId} />;
}

function FoodDetails({ itemId }: { itemId: number }) {
  const item = useMenuItem(itemId);
  const restaurant = useRestaurant(item.data?.restaurantId);
  const auditLogs = useMenuItemAuditLogs(itemId);
  const [form, setForm] = useState<'new' | 'edit' | null>(null);
  const restaurantName = restaurant.data?.restaurant.name ?? null;

  return (
    <div className="flex flex-col gap-6">
      <BackLink to={ROUTES.foods}>Back to foods</BackLink>
      <AsyncContent
        isLoading={item.isPending}
        error={item.error}
        errorTitle={ERROR_MESSAGES.FOOD_LOAD_FAILED}
        onRetry={() => void item.refetch()}
        isRetrying={item.isFetching}
      >
        {item.data && (
          <>
            <PageHeader
              title={item.data.name}
              subtitle={`Menu item #${item.data.id} in ${item.data.categoryName} · Last change ${formatDateTime(item.data.updatedAt)}`}
              actions={<FoodActions item={item.data} />}
            />
            <div className="grid items-start gap-6 xl:grid-cols-2">
              <FoodDetailCard
                item={item.data}
                restaurantName={restaurantName}
                onAdd={() => setForm('new')}
                onEdit={() => setForm('edit')}
              />
              <DishRevenueCard menuItemId={itemId} />
            </div>
            <DishReviews menuItemId={itemId} />
            <Card title="Change history" subtitle="Admin actions on this item">
              <AsyncContent
                isLoading={auditLogs.isPending}
                error={auditLogs.error}
                errorTitle={ERROR_MESSAGES.AUDIT_LOAD_FAILED}
                onRetry={() => void auditLogs.refetch()}
              >
                {auditLogs.data && <AuditHistory entries={auditLogs.data.content} />}
              </AsyncContent>
            </Card>
            {form !== null && (
              <DishFormDialog
                item={item.data}
                mode={form}
                restaurantName={restaurantName}
                onClose={() => setForm(null)}
              />
            )}
          </>
        )}
      </AsyncContent>
    </div>
  );
}

/**
 * "Edit Menu" (this dish, with its ingredients and nutrition) or "Add Menu" (a new dish for the same restaurant and
 * category; nothing else is copied). The restaurants are only loaded once the dialog is opened.
 */
function DishFormDialog({
  item,
  mode,
  restaurantName,
  onClose,
}: {
  item: MenuItem;
  mode: 'new' | 'edit';
  restaurantName: string | null;
  onClose: () => void;
}) {
  const { showToast } = useToast();
  const categories = useCategories();
  const restaurants = useRestaurants({ sort: 'name,asc' }, 0, 100);
  const createItem = useCreateMenuItem();
  const updateItem = useUpdateMenuItem();

  // This dish's restaurant is always on offer (even beyond the first 100, or while the list loads)
  const options = (restaurants.data?.content ?? []).map((r) => ({
    value: String(r.id),
    label: r.name,
  }));
  if (!options.some((option) => option.value === String(item.restaurantId))) {
    options.unshift({
      value: String(item.restaurantId),
      label: restaurantName ?? `Restaurant #${item.restaurantId}`,
    });
  }

  return (
    <FoodFormDialog
      open
      item={mode === 'edit' ? item : undefined}
      preset={{ restaurantId: item.restaurantId, categoryId: item.categoryId }}
      restaurants={options}
      restaurantName={restaurantName}
      categories={categories.data ?? []}
      onCreate={async (request) => {
        const created = await createItem.mutateAsync(request);
        showToast(`"${created.name}" was added.`);
      }}
      onUpdate={async (itemId, request) => {
        const updated = await updateItem.mutateAsync({ itemId, item: request });
        showToast(`"${updated.name}" was updated.`);
      }}
      onClose={onClose}
    />
  );
}

type FoodDialog = 'activate' | 'block' | 'category' | 'remove';

/**
 * The admin's actions, offered by the item's listing (catalog-service checks them again):
 * block a listed or hidden item; release a blocked one or list a hidden one again; move it to another category;
 * remove an item that is not listed.
 */
function FoodActions({ item }: { item: MenuItem }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const categories = useCategories();
  const changeStatus = useChangeMenuItemStatus();
  const changeCategory = useChangeMenuItemCategory();
  const removeItem = useRemoveMenuItem();
  const [dialog, setDialog] = useState<FoodDialog | null>(null);
  const close = () => setDialog(null);

  const activateLabel = item.status === 'BLOCKED' ? 'Release' : 'List again';

  return (
    <div className="flex flex-wrap gap-3">
      <Button
        variant="outline"
        onClick={() => setDialog('category')}
        leftIcon={<FolderInput aria-hidden="true" className="size-4" />}
      >
        Change category
      </Button>
      {item.status !== 'ACTIVE' && (
        <Button
          variant="outline"
          onClick={() => setDialog('activate')}
          leftIcon={<RotateCcw aria-hidden="true" className="size-4" />}
        >
          {activateLabel}
        </Button>
      )}
      {item.status !== 'BLOCKED' && (
        <Button
          variant="danger"
          onClick={() => setDialog('block')}
          leftIcon={<Ban aria-hidden="true" className="size-4" />}
        >
          Block
        </Button>
      )}
      {item.status !== 'ACTIVE' && (
        <Button
          variant="ghost"
          onClick={() => setDialog('remove')}
          leftIcon={<Trash2 aria-hidden="true" className="size-4" />}
        >
          Remove
        </Button>
      )}

      <ReasonDialog
        open={dialog === 'activate'}
        title={`${activateLabel} "${item.name}"?`}
        description="The dish is listed on the restaurant's menu again, and customers can order it."
        confirmLabel={activateLabel}
        onSubmit={async (reason) => {
          await changeStatus.mutateAsync({ itemId: item.id, activate: true, reason });
          showToast(`"${item.name}" is listed again.`);
        }}
        onClose={close}
      />
      <ReasonDialog
        open={dialog === 'block'}
        title={`Block "${item.name}"?`}
        description="The dish disappears from the menu and from search. The restaurant cannot list it again; only an admin can release it."
        confirmLabel="Block"
        tone="danger"
        onSubmit={async (reason) => {
          await changeStatus.mutateAsync({ itemId: item.id, activate: false, reason });
          showToast(`"${item.name}" is blocked.`);
        }}
        onClose={close}
      />
      <ReasonDialog
        open={dialog === 'remove'}
        title={`Remove "${item.name}"?`}
        description="The item is deleted from the restaurant's menu. Past orders keep their own copy of it. This cannot be undone."
        confirmLabel="Remove"
        tone="danger"
        onSubmit={async (reason) => {
          await removeItem.mutateAsync({ itemId: item.id, reason });
          showToast(`"${item.name}" was removed.`);
          void navigate(ROUTES.foods);
        }}
        onClose={close}
      />
      <ChangeCategoryDialog
        open={dialog === 'category'}
        item={item}
        categories={categories.data ?? []}
        onSubmit={async (categoryId, reason) => {
          const moved = await changeCategory.mutateAsync({ itemId: item.id, categoryId, reason });
          showToast(`"${item.name}" moved to ${moved.categoryName}.`);
        }}
        onClose={close}
      />
    </div>
  );
}
