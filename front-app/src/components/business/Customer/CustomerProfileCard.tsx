import { CalendarDays, Mail, Phone, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { CustomerAccountActions } from '@/components/business/Customer/CustomerAccountActions';
import { ReviewerAvatar } from '@/components/business/Review/ReviewerAvatar';
import { UserStatusBadge } from '@/components/business/StatusBadges';
import { useUserAddresses } from '@/hooks/api/userHooks';
import type { AdminUserDetails } from '@/types/user';
import { USER_ROLE_LABELS } from '@/utils/constants';
import { cn } from '@/utils/classNames';
import { formatInstantDate } from '@/utils/dateUtils';
import { addressLines } from '@/utils/shoppingUtils';
import { useActingUser } from '@/hooks/useActingUser';

function ChipRow({
  icon: Icon,
  chip,
  children,
}: {
  icon: LucideIcon;
  chip: string;
  children: ReactNode;
}) {
  return (
    <li className="flex min-w-0 items-center gap-3">
      <span className={cn('flex size-9 shrink-0 items-center justify-center rounded-xl', chip)}>
        <Icon aria-hidden="true" className="size-4" />
      </span>
      <span className="min-w-0 text-sm font-medium break-words">{children}</span>
    </li>
  );
}

const LINK = 'cursor-pointer hover:text-brand-text-strong hover:underline';

/**
 * The default address as small muted text. It is an extra: while it loads or when its call fails nothing is shown,
 * and the page is never blocked.
 */
function DefaultAddress({ userId }: { userId: number }) {
  const addresses = useUserAddresses(userId);
  if (!addresses.data) {
    return null;
  }
  const first = addresses.data[0];
  return (
    <p className="mt-2 text-xs text-ink-soft">
      {first ? addressLines(first).join(', ') : 'No address saved'}
    </p>
  );
}

/** The customer: avatar, name, role, status, default address, how to reach them, and the account actions. */
export function CustomerProfileCard({ user }: { user: AdminUserDetails }) {
  const actingUser = useActingUser();
  return (
    <section
      aria-label="Customer profile"
      className="flex flex-col gap-6 rounded-3xl bg-surface p-6 shadow-card sm:flex-row sm:items-start"
    >
      <ReviewerAvatar
        name={user.fullName}
        size="none"
        className="size-28 rounded-3xl text-4xl sm:size-36 sm:text-5xl"
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 className="text-2xl font-bold tracking-tight break-words sm:text-3xl">
              {user.fullName}
            </h2>
            <div className="mt-1 flex flex-wrap items-center gap-3">
              <p className="text-lg font-medium text-brand-text">{USER_ROLE_LABELS[user.role]}</p>
              <UserStatusBadge status={user.status} />
            </div>
          </div>
          <div className="flex shrink-0">
            <CustomerAccountActions user={user} />
          </div>
        </div>
        <DefaultAddress userId={user.id} />
        {actingUser?.id === user.id && (
          <p className="mt-2 text-xs text-ink-soft">
            This is your own account: another admin has to change it.
          </p>
        )}
        <ul className="mt-5 flex flex-wrap gap-x-8 gap-y-3">
          <ChipRow icon={Mail} chip="bg-info-50 text-info-500">
            <a href={`mailto:${user.email}`} className={LINK}>
              {user.email}
            </a>
          </ChipRow>
          <ChipRow icon={Phone} chip="bg-brand-100 text-brand-text">
            {user.phone ? (
              <a href={`tel:${user.phone}`} className={LINK}>
                {user.phone}
              </a>
            ) : (
              <span className="font-normal text-ink-soft">No phone</span>
            )}
          </ChipRow>
          <ChipRow icon={CalendarDays} chip="bg-danger-50 text-danger-500">
            Member since {formatInstantDate(user.createdAt)} · Last login{' '}
            {user.lastLoginAt ? formatInstantDate(user.lastLoginAt) : 'Never'}
          </ChipRow>
        </ul>
      </div>
    </section>
  );
}
