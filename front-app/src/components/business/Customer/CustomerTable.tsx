import { Link } from 'react-router';

import { RoleBadge, UserStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { AdminUserSummary } from '@/types/user';
import { NOT_AVAILABLE, routeTo } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { initialsOf } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<AdminUserSummary>[] = [
  {
    header: 'Name',
    render: (user) => (
      <Link to={routeTo.customerDetails(user.id)} className="group flex items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-bold text-brand-text">
          {initialsOf(user.fullName)}
        </span>
        <span>
          <span className="block font-semibold group-hover:text-brand-text group-hover:underline">
            {user.fullName}
          </span>
          <span className="block text-xs text-ink-soft">{user.email}</span>
        </span>
      </Link>
    ),
  },
  { header: 'Phone', render: (user) => user.phone ?? NOT_AVAILABLE },
  { header: 'Role', render: (user) => <RoleBadge role={user.role} /> },
  { header: 'Status', render: (user) => <UserStatusBadge status={user.status} /> },
  { header: 'Joined', render: (user) => formatDateTime(user.createdAt) },
  {
    header: 'Last login',
    render: (user) => (user.lastLoginAt ? formatDateTime(user.lastLoginAt) : 'Never'),
  },
];

interface CustomerTableProps {
  users: readonly AdminUserSummary[];
  isUpdating?: boolean;
}

/** Users as a table; the name opens the user. */
export function CustomerTable({ users, isUpdating }: CustomerTableProps) {
  return (
    <Table
      caption="Customers and users"
      columns={COLUMNS}
      rows={users}
      rowKey={(user) => user.id}
      isUpdating={isUpdating}
    />
  );
}
