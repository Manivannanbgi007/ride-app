import { apiClient } from '@/api/apiClient';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  Category,
  CategoryRequest,
  CategoryStat,
  MenuItem,
  MenuItemCreateRequest,
  MenuItemFilters,
  MenuItemUpdateRequest,
} from '@/types/catalog';
import type { RestaurantMenu, StoreCategory } from '@/types/storefront';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** catalog-service APIs: menu items ("foods") and categories (admin), the public menu and categories. */
export const catalogService = {
  /** Public: the active categories, in menu order. */
  getStoreCategories: () => apiClient.get<StoreCategory[]>(API_ENDPOINTS.storefront.categories),

  /** Public: a restaurant's menu by category (hidden dishes left out, sold-out ones marked). */
  getRestaurantMenu: (restaurantId: number) =>
    apiClient.get<RestaurantMenu>(API_ENDPOINTS.storefront.menu(restaurantId)),

  /** Menu items of every restaurant matching the filters, one page (page 0 = the first). */
  searchMenuItems: (filters: MenuItemFilters, page: number, size: number) =>
    apiClient.get<PageResponse<MenuItem>>(API_ENDPOINTS.admin.menuItems.list, {
      params: { ...filters, page, size },
    }),

  getMenuItem: (itemId: number) =>
    apiClient.get<MenuItem>(API_ENDPOINTS.admin.menuItems.details(itemId)),

  /** A new menu item for a restaurant. 409 when the restaurant already has a dish of that name. */
  createMenuItem: (item: MenuItemCreateRequest) =>
    apiClient.post<MenuItem>(API_ENDPOINTS.admin.menuItems.create, item),

  /** Edit a menu item (all fields are replaced). 409 when `version` is stale or the name is taken. */
  updateMenuItem: (itemId: number, item: MenuItemUpdateRequest) =>
    apiClient.put<MenuItem>(API_ENDPOINTS.admin.menuItems.update(itemId), item),

  /** A copy named "<name> (copy)", hidden (DISABLED). */
  duplicateMenuItem: (itemId: number) =>
    apiClient.post<MenuItem>(API_ENDPOINTS.admin.menuItems.duplicate(itemId)),

  /** The categories with the most dishes, and how many of their dishes can be ordered now. */
  getCategoryStats: (limit: number) =>
    apiClient.get<CategoryStat[]>(API_ENDPOINTS.admin.menuItems.categoryStats, {
      params: { limit },
    }),

  /**
   * Activate: list the item again (also releases a BLOCKED item). Block: hide it; the owner cannot show it again.
   * Both audited.
   */
  changeStatus: (itemId: number, activate: boolean, reason: string) =>
    apiClient.put<MenuItem>(
      activate
        ? API_ENDPOINTS.admin.menuItems.activate(itemId)
        : API_ENDPOINTS.admin.menuItems.block(itemId),
      { reason },
    ),

  changeCategory: (itemId: number, categoryId: number, reason: string) =>
    apiClient.put<MenuItem>(API_ENDPOINTS.admin.menuItems.category(itemId), { categoryId, reason }),

  /** Remove an invalid item (only one that is not listed). Past orders keep their own copy of it. */
  removeMenuItem: (itemId: number, reason: string) =>
    apiClient.delete(API_ENDPOINTS.admin.menuItems.remove(itemId), { params: { reason } }),

  getAuditLogs: (itemId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(API_ENDPOINTS.admin.menuItems.auditLogs(itemId), {
      params: { size: 50 },
    }),

  /** Every category (active and inactive), in menu order, with how many dishes use it. */
  getCategories: async (): Promise<Category[]> => {
    const page = await apiClient.get<PageResponse<Category>>(API_ENDPOINTS.admin.categories.list, {
      params: { size: 100, sort: 'displayOrder' },
    });
    return page.content;
  },

  /** A new category: ACTIVE, at the end of the menu order. 409 when the name exists (any letter case). */
  createCategory: (category: CategoryRequest) =>
    apiClient.post<Category>(API_ENDPOINTS.admin.categories.create, category),

  /** Rename or describe a category (all three fields are replaced). */
  updateCategory: (categoryId: number, category: CategoryRequest) =>
    apiClient.put<Category>(API_ENDPOINTS.admin.categories.update(categoryId), category),

  /** Inactive: hidden from customers and cannot be given to dishes; dishes keep it. Audited. */
  changeCategoryStatus: (categoryId: number, activate: boolean, reason: string) =>
    apiClient.put<Category>(
      activate
        ? API_ENDPOINTS.admin.categories.activate(categoryId)
        : API_ENDPOINTS.admin.categories.deactivate(categoryId),
      { reason },
    ),

  /** Only a category no dish uses (409 otherwise). */
  removeCategory: (categoryId: number, reason: string) =>
    apiClient.delete(API_ENDPOINTS.admin.categories.remove(categoryId), { params: { reason } }),

  /** The new menu order: every category id (also inactive ones) exactly once. */
  reorderCategories: (categoryIds: number[]) =>
    apiClient.put<Category[]>(API_ENDPOINTS.admin.categories.order, { categoryIds }),

  getCategoryAuditLogs: (categoryId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(
      API_ENDPOINTS.admin.categories.auditLogs(categoryId),
      {
        params: { size: 50 },
      },
    ),
};
