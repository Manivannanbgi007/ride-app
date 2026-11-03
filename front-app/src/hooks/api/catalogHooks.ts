import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { catalogService } from '@/services/catalogService';
import type {
  CategoryRequest,
  MenuItem,
  MenuItemCreateRequest,
  MenuItemFilters,
  MenuItemUpdateRequest,
} from '@/types/catalog';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** One page of menu items. While the next page loads, the current one stays on screen. */
export function useMenuItems(filters: MenuItemFilters, page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.menuItems.list(filters, page, size),
    queryFn: () => catalogService.searchMenuItems(filters, page, size),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useMenuItem(itemId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.menuItems.details(itemId),
    queryFn: () => catalogService.getMenuItem(itemId),
  });
}

export function useMenuItemAuditLogs(itemId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.menuItems.auditLogs(itemId),
    queryFn: () => catalogService.getAuditLogs(itemId),
  });
}

/** Every category; they change rarely, so they are kept for 5 minutes. */
export function useCategories() {
  return useQuery({
    queryKey: QUERY_KEYS.categories,
    queryFn: () => catalogService.getCategories(),
    staleTime: 5 * 60_000,
  });
}

/** After a change: show the new item at once, then reload lists and the history. */
function useMenuItemChanged() {
  const queryClient = useQueryClient();
  return async (item: MenuItem) => {
    queryClient.setQueryData(QUERY_KEYS.menuItems.details(item.id), item);
    await queryClient.invalidateQueries({ queryKey: QUERY_KEYS.menuItems.all });
  };
}

export function useChangeMenuItemStatus() {
  const onSuccess = useMenuItemChanged();
  return useMutation({
    mutationFn: ({
      itemId,
      activate,
      reason,
    }: {
      itemId: number;
      activate: boolean;
      reason: string;
    }) => catalogService.changeStatus(itemId, activate, reason),
    onSuccess,
  });
}

export function useChangeMenuItemCategory() {
  const onSuccess = useMenuItemChanged();
  return useMutation({
    mutationFn: ({
      itemId,
      categoryId,
      reason,
    }: {
      itemId: number;
      categoryId: number;
      reason: string;
    }) => catalogService.changeCategory(itemId, categoryId, reason),
    onSuccess,
  });
}

export function useCategoryAuditLogs(categoryId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.categoryAuditLogs(categoryId),
    queryFn: () => catalogService.getCategoryAuditLogs(categoryId),
  });
}

/**
 * After a category change: reload the categories, their histories, and the menu items (they show the category's
 * name).
 */
function useCategoriesChanged() {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.categories }),
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.menuItems.all }),
    ]);
  };
}

/** Creates a category (categoryId undefined) or changes one. */
export function useSaveCategory() {
  const onSuccess = useCategoriesChanged();
  return useMutation({
    mutationFn: ({ categoryId, category }: { categoryId?: number; category: CategoryRequest }) =>
      categoryId === undefined
        ? catalogService.createCategory(category)
        : catalogService.updateCategory(categoryId, category),
    onSuccess,
  });
}

export function useChangeCategoryStatus() {
  const onSuccess = useCategoriesChanged();
  return useMutation({
    mutationFn: ({
      categoryId,
      activate,
      reason,
    }: {
      categoryId: number;
      activate: boolean;
      reason: string;
    }) => catalogService.changeCategoryStatus(categoryId, activate, reason),
    onSuccess,
  });
}

export function useRemoveCategory() {
  const onSuccess = useCategoriesChanged();
  return useMutation({
    mutationFn: ({ categoryId, reason }: { categoryId: number; reason: string }) =>
      catalogService.removeCategory(categoryId, reason),
    onSuccess,
  });
}

/**
 * Moves the categories into a new menu order. The new order is shown at once; if the server refuses, the list is
 * reloaded from it.
 */
export function useReorderCategories() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (categoryIds: number[]) => catalogService.reorderCategories(categoryIds),
    onSuccess: (categories) => queryClient.setQueryData(QUERY_KEYS.categories, categories),
    onError: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.categories }),
  });
}

export function useRemoveMenuItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, reason }: { itemId: number; reason: string }) =>
      catalogService.removeMenuItem(itemId, reason),
    onSuccess: async (_, { itemId }) => {
      queryClient.removeQueries({ queryKey: QUERY_KEYS.menuItems.details(itemId) });
      await queryClient.invalidateQueries({ queryKey: QUERY_KEYS.menuItems.all });
    },
  });
}

/** The categories with the most dishes and the share of each that can be ordered now ("Menu Comparison"). */
export function useCategoryStats(limit: number) {
  return useQuery({
    queryKey: QUERY_KEYS.menuItems.categoryStats(limit),
    queryFn: () => catalogService.getCategoryStats(limit),
  });
}

/** After a create, edit or copy: the lists and the comparison (all under the menu-items key) and category counts. */
function useMenuItemsChanged() {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.menuItems.all }),
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.categories }),
    ]);
  };
}

export function useCreateMenuItem() {
  const onSuccess = useMenuItemsChanged();
  return useMutation({
    mutationFn: (item: MenuItemCreateRequest) => catalogService.createMenuItem(item),
    onSuccess,
  });
}

export function useUpdateMenuItem() {
  const onSuccess = useMenuItemsChanged();
  return useMutation({
    mutationFn: ({ itemId, item }: { itemId: number; item: MenuItemUpdateRequest }) =>
      catalogService.updateMenuItem(itemId, item),
    onSuccess,
  });
}

export function useDuplicateMenuItem() {
  const onSuccess = useMenuItemsChanged();
  return useMutation({
    mutationFn: (itemId: number) => catalogService.duplicateMenuItem(itemId),
    onSuccess,
  });
}
