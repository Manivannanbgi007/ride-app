import { ArrowDown, ArrowUp, History, Pencil, Power, Trash2 } from 'lucide-react';

import { CategoryThumbnail } from '@/components/business/Category/CategoryThumbnail';
import { CategoryStatusBadge } from '@/components/business/StatusBadges';
import { IconButton } from '@/components/common/IconButton';
import { Table, type TableColumn } from '@/components/common/Table';
import type { Category } from '@/types/catalog';
import { formatDateTime } from '@/utils/dateUtils';
import { formatNumber } from '@/utils/formatUtils';

export type CategoryAction = 'edit' | 'status' | 'remove' | 'history';

interface CategoryTableProps {
  categories: readonly Category[];
  /** Up / down buttons (off while a filter hides some categories, or while a move is saved) */
  canReorder: boolean;
  onMove: (category: Category, direction: -1 | 1) => void;
  onAction: (category: Category, action: CategoryAction) => void;
}

/** The categories in menu order, with their dishes, status and actions. */
export function CategoryTable({ categories, canReorder, onMove, onAction }: CategoryTableProps) {
  const columns: readonly TableColumn<Category>[] = [
    {
      header: 'Order',
      render: (category) => {
        const index = categories.indexOf(category);
        return (
          <div className="flex items-center gap-1">
            <span className="w-6 text-center font-semibold">{category.displayOrder}</span>
            <IconButton
              label={`Move ${category.name} up`}
              icon={ArrowUp}
              disabled={!canReorder || index === 0}
              onClick={() => onMove(category, -1)}
            />
            <IconButton
              label={`Move ${category.name} down`}
              icon={ArrowDown}
              disabled={!canReorder || index === categories.length - 1}
              onClick={() => onMove(category, 1)}
            />
          </div>
        );
      },
    },
    {
      header: 'Category',
      render: (category) => (
        <div className="flex items-center gap-3">
          <CategoryThumbnail category={category} />
          <div>
            <p className="font-semibold">{category.name}</p>
            {category.description && (
              <p className="text-xs text-ink-soft">{category.description}</p>
            )}
          </div>
        </div>
      ),
    },
    {
      header: 'Dishes',
      align: 'right',
      render: (category) => formatNumber(category.menuItemCount),
    },
    { header: 'Status', render: (category) => <CategoryStatusBadge status={category.status} /> },
    { header: 'Changed', render: (category) => formatDateTime(category.updatedAt) },
    {
      header: 'Actions',
      align: 'right',
      render: (category) => (
        <div className="flex justify-end gap-1.5">
          <IconButton
            label={`Edit ${category.name}`}
            icon={Pencil}
            onClick={() => onAction(category, 'edit')}
          />
          <IconButton
            label={`${category.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} ${category.name}`}
            icon={Power}
            onClick={() => onAction(category, 'status')}
          />
          <IconButton
            label={
              category.menuItemCount > 0
                ? `${category.name} is used by dishes and cannot be deleted`
                : `Delete ${category.name}`
            }
            icon={Trash2}
            tone="danger"
            disabled={category.menuItemCount > 0}
            onClick={() => onAction(category, 'remove')}
          />
          <IconButton
            label={`History of ${category.name}`}
            icon={History}
            onClick={() => onAction(category, 'history')}
          />
        </div>
      ),
    },
  ];

  return (
    <Table
      caption="Categories"
      columns={columns}
      rows={categories}
      rowKey={(category) => category.id}
    />
  );
}
