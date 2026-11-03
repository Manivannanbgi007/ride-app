import { createListSlice } from '@/store/slices/createListSlice';
import type { AuditLogFilters } from '@/types/audit';
import { AUDIT_LOG_SORT_OPTIONS } from '@/utils/constants';

/** The audit log's filters, order and page, kept while navigating. */
const initialFilters: AuditLogFilters = { sort: AUDIT_LOG_SORT_OPTIONS[0].value };
const auditLogListSlice = createListSlice('auditLogList', initialFilters);

export const {
  filtersChanged: auditLogFiltersChanged,
  filtersReplaced: auditLogFiltersReplaced,
  filtersCleared: auditLogFiltersCleared,
  pageChanged: auditLogPageChanged,
} = auditLogListSlice.actions;
export const { selectFilters: selectAuditLogFilters, selectPage: selectAuditLogPage } =
  auditLogListSlice.selectors;
export default auditLogListSlice.reducer;
