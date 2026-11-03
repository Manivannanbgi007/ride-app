import { Plus } from 'lucide-react';
import { useState } from 'react';

import {
  CategoryFormDialog,
  CategoryHistoryDialog,
  CategoryTable,
  type CategoryAction,
} from '@/components/business/Category';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { Input } from '@/components/common/Input';
import { TableLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { Select } from '@/components/common/Select';
import { useToast } from '@/context/ToastContext';
import {
  useCategories,
  useChangeCategoryStatus,
  useRemoveCategory,
  useReorderCategories,
  useSaveCategory,
} from '@/hooks/api/catalogHooks';
import { CATEGORY_STATUSES, type Category, type CategoryStatus } from '@/types/catalog';
import { filterCategories, moveCategory } from '@/utils/categoryUtils';
import { CATEGORY_STATUS_LABELS } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { oneOf } from '@/utils/validationUtils';

const STATUS_OPTIONS = CATEGORY_STATUSES.map((status) => ({
  value: status,
  label: CATEGORY_STATUS_LABELS[status],
}));

/** What the page has open: a dialog for one category, or the "new category" form. */
type OpenDialog = { action: CategoryAction; category: Category } | { action: 'create' } | null;

/**
 * "Categories": the food categories in menu order. Every category is shown (there are few), so the search and
 * status filter work in the browser; the order can be changed while nothing is filtered out.
 */
export default function CategoriesPage() {
  const { showToast } = useToast();
  const categories = useCategories();
  const saveCategory = useSaveCategory();
  const changeStatus = useChangeCategoryStatus();
  const removeCategory = useRemoveCategory();
  const reorder = useReorderCategories();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<CategoryStatus | undefined>();
  const [dialog, setDialog] = useState<OpenDialog>(null);

  const all = categories.data ?? [];
  const visible = filterCategories(all, search, status);
  const isFiltered = visible.length !== all.length;
  const close = () => setDialog(null);

  const move = (category: Category, direction: -1 | 1) => {
    const order = moveCategory(
      all.map((each) => each.id),
      category.id,
      direction,
    );
    reorder.mutate(order, {
      onError: () => showToast('The new order could not be saved. Please try again.', 'error'),
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Categories"
        subtitle="The food categories customers browse, in menu order"
        actions={
          <Button
            onClick={() => setDialog({ action: 'create' })}
            leftIcon={<Plus aria-hidden="true" className="size-4" />}
          >
            New category
          </Button>
        }
      />

      <Card>
        <div className="mb-5 grid gap-4 sm:grid-cols-[2fr_1fr]">
          <Input
            label="Search"
            type="search"
            placeholder="Category name"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <Select
            label="Status"
            placeholder="All statuses"
            options={STATUS_OPTIONS}
            value={status ?? ''}
            onChange={(event) => setStatus(oneOf(event.target.value, CATEGORY_STATUSES))}
          />
        </div>
        {isFiltered && (
          <p className="mb-3 text-xs text-ink-soft">
            Clear the search and status to change the order.
          </p>
        )}
        <AsyncContent
          isLoading={categories.isPending}
          error={categories.error}
          isEmpty={visible.length === 0}
          errorTitle={ERROR_MESSAGES.CATEGORIES_LOAD_FAILED}
          onRetry={() => void categories.refetch()}
          loading={<TableLoader columns={6} />}
          empty={<EmptyState title={isFiltered ? 'No category matches' : 'No categories yet'} />}
        >
          <CategoryTable
            categories={visible}
            canReorder={!isFiltered && !reorder.isPending}
            onMove={move}
            onAction={(category, action) => setDialog({ action, category })}
          />
        </AsyncContent>
      </Card>

      {dialog?.action === 'create' && (
        <CategoryFormDialog
          onSubmit={async (category) => {
            const created = await saveCategory.mutateAsync({ category });
            showToast(`Category "${created.name}" created.`);
          }}
          onClose={close}
        />
      )}
      {dialog?.action === 'edit' && (
        <CategoryFormDialog
          category={dialog.category}
          onSubmit={async (category) => {
            const saved = await saveCategory.mutateAsync({
              categoryId: dialog.category.id,
              category,
            });
            showToast(`Category "${saved.name}" saved.`);
          }}
          onClose={close}
        />
      )}
      {dialog?.action === 'status' && (
        <ReasonDialog
          open
          title={`${dialog.category.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} "${dialog.category.name}"?`}
          description={
            dialog.category.status === 'ACTIVE'
              ? 'Customers no longer see the category and it cannot be given to dishes; its dishes keep it.'
              : 'Customers see the category again and it can be given to dishes.'
          }
          confirmLabel={dialog.category.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          tone={dialog.category.status === 'ACTIVE' ? 'danger' : 'primary'}
          onSubmit={async (reason) => {
            const changed = await changeStatus.mutateAsync({
              categoryId: dialog.category.id,
              activate: dialog.category.status !== 'ACTIVE',
              reason,
            });
            showToast(
              `"${changed.name}" is ${CATEGORY_STATUS_LABELS[changed.status].toLowerCase()}.`,
            );
          }}
          onClose={close}
        />
      )}
      {dialog?.action === 'remove' && (
        <ReasonDialog
          open
          title={`Delete "${dialog.category.name}"?`}
          description="No dish uses it. The category is deleted for good; its history stays in the audit log."
          confirmLabel="Delete"
          tone="danger"
          onSubmit={async (reason) => {
            await removeCategory.mutateAsync({ categoryId: dialog.category.id, reason });
            showToast(`"${dialog.category.name}" was deleted.`);
          }}
          onClose={close}
        />
      )}
      {dialog?.action === 'history' && (
        <CategoryHistoryDialog category={dialog.category} onClose={close} />
      )}
    </div>
  );
}
