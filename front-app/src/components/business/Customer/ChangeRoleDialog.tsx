import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { FormDialog } from '@/components/common/FormDialog';
import { Select } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import { USER_ROLES, type AdminUserDetails, type UserRole } from '@/types/user';
import { USER_ROLE_LABELS } from '@/utils/constants';
import {
  REASON_MAX_LENGTH,
  changeRoleFormSchema,
  showServerErrors,
  type ChangeRoleForm,
} from '@/utils/validationUtils';

interface ChangeRoleDialogProps {
  open: boolean;
  user: AdminUserDetails;
  onSubmit: (role: UserRole, reason: string) => Promise<unknown>;
  onClose: () => void;
}

/** Gives a user another role (audited). The roles other than the current one are offered. */
export function ChangeRoleDialog({ open, user, onSubmit, onClose }: ChangeRoleDialogProps) {
  const roleOptions = USER_ROLES.filter((role) => role !== user.role).map((role) => ({
    value: role,
    label: USER_ROLE_LABELS[role],
  }));
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangeRoleForm>({
    resolver: zodResolver(changeRoleFormSchema),
    defaultValues: { reason: '' },
  });

  const close = () => {
    reset();
    onClose();
  };

  const submit = handleSubmit(async ({ role, reason }) => {
    try {
      await onSubmit(role, reason);
      close();
    } catch (error) {
      showServerErrors(error, setError, ['role', 'reason']);
    }
  });

  return (
    <FormDialog
      open={open}
      title={`Change the role of ${user.fullName}`}
      submitLabel="Change role"
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={close}
    >
      <p className="text-sm">
        Now: <span className="font-semibold">{USER_ROLE_LABELS[user.role]}</span>. The new role
        decides what this user can do on the platform.
      </p>
      <Select
        label="New role"
        placeholder="Choose a role"
        options={roleOptions}
        error={errors.role?.message}
        {...register('role')}
      />
      <Textarea
        label="Reason"
        placeholder="Kept in the audit log"
        maxLength={REASON_MAX_LENGTH}
        error={errors.reason?.message}
        {...register('reason')}
      />
    </FormDialog>
  );
}
