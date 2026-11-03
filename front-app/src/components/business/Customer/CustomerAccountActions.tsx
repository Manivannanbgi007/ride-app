import { HelpCircle, Pencil, type LucideIcon } from 'lucide-react';
import { useState } from 'react';

import { ChangeRoleDialog } from '@/components/business/Customer/ChangeRoleDialog';
import { ReasonDialog } from '@/components/common/ReasonDialog';
import { Tooltip } from '@/components/common/Tooltip';
import { useToast } from '@/context/ToastContext';
import { useChangeUserRole, useChangeUserStatus } from '@/hooks/api/userHooks';
import { useActingUser } from '@/hooks/useActingUser';
import type { AdminUserDetails, UserRole } from '@/types/user';
import { USER_ROLE_LABELS } from '@/utils/constants';
import { cn } from '@/utils/classNames';

function RoundButton({
  label,
  tooltip,
  icon: Icon,
  tone,
  disabled,
  onClick,
}: {
  label: string;
  tooltip: string;
  icon: LucideIcon;
  tone: 'brand' | 'neutral';
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <Tooltip
      label={tooltip}
      icon={Icon}
      className={cn('rounded-full', disabled && 'cursor-not-allowed')}
    >
      <button
        type="button"
        aria-label={label}
        disabled={disabled}
        onClick={onClick}
        className={cn(
          'flex size-12 cursor-pointer items-center justify-center rounded-full transition duration-200',
          'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
          'motion-safe:hover:-translate-y-0.5 motion-safe:active:scale-95',
          tone === 'brand'
            ? 'bg-brand-100 text-brand-text-strong hover:bg-brand-200'
            : 'bg-page text-ink-soft ring-1 ring-line hover:text-ink',
          'disabled:pointer-events-none disabled:opacity-40',
        )}
      >
        <Icon aria-hidden="true" className="size-5" />
      </button>
    </Tooltip>
  );
}

/**
 * Activate / deactivate and change role, as two round buttons with their reason dialogs. An admin cannot do either
 * to their own account (user-service refuses it too), so the buttons are switched off and the tooltip says why.
 */
export function CustomerAccountActions({ user }: { user: AdminUserDetails }) {
  const actingUser = useActingUser();
  const changeStatus = useChangeUserStatus();
  const changeRole = useChangeUserRole();
  const { showToast } = useToast();
  const [dialog, setDialog] = useState<'status' | 'role' | null>(null);
  const isOwnAccount = actingUser?.id === user.id;
  const activate = user.status === 'INACTIVE';
  const statusLabel = activate ? 'Activate' : 'Deactivate';
  const ownAccountHint = 'This is your own account: another admin has to change it';

  const submitStatus = async (reason: string) => {
    await changeStatus.mutateAsync({ userId: user.id, activate, reason });
    showToast(`${user.fullName} was ${activate ? 'activated' : 'deactivated'}.`);
  };
  const submitRole = async (role: UserRole, reason: string) => {
    await changeRole.mutateAsync({ userId: user.id, role, reason });
    showToast(`${user.fullName} is now ${USER_ROLE_LABELS[role].toLowerCase()}.`);
  };

  return (
    <>
      <div className="flex gap-3">
        <RoundButton
          label={statusLabel}
          tooltip={isOwnAccount ? ownAccountHint : statusLabel}
          icon={HelpCircle}
          tone="brand"
          disabled={isOwnAccount}
          onClick={() => setDialog('status')}
        />
        <RoundButton
          label="Change role"
          tooltip={isOwnAccount ? ownAccountHint : 'Change role'}
          icon={Pencil}
          tone="neutral"
          disabled={isOwnAccount}
          onClick={() => setDialog('role')}
        />
      </div>

      <ReasonDialog
        open={dialog === 'status'}
        title={`${statusLabel} ${user.fullName}?`}
        description={
          activate
            ? 'The user can log in and use the platform again.'
            : 'The user can no longer log in or place orders until an admin activates the account again.'
        }
        confirmLabel={statusLabel}
        tone={activate ? 'primary' : 'danger'}
        onSubmit={submitStatus}
        onClose={() => setDialog(null)}
      />
      <ChangeRoleDialog
        open={dialog === 'role'}
        user={user}
        onSubmit={submitRole}
        onClose={() => setDialog(null)}
      />
    </>
  );
}
