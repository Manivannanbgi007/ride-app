import { zodResolver } from '@hookform/resolvers/zod';
import type { ReactNode } from 'react';
import { useForm } from 'react-hook-form';

import { FormDialog } from '@/components/common/FormDialog';
import { Textarea } from '@/components/common/Textarea';
import {
  REASON_MAX_LENGTH,
  reasonFormSchemaOf,
  showServerErrors,
  type ReasonForm,
} from '@/utils/validationUtils';

interface ReasonDialogProps {
  open: boolean;
  title: string;
  /** What will happen ("The customer is told and refunded if they paid online.") */
  description: ReactNode;
  confirmLabel: string;
  /** danger: for actions that cannot be undone */
  tone?: 'primary' | 'danger';
  reasonLabel?: string;
  /** The longest reason the service accepts (default 500) */
  maxLength?: number;
  /** Shown in the empty box (default: "Kept in the audit log", for admins) */
  placeholder?: string;
  /** Shown when no reason is given (default: the admin wording about the audit log) */
  requiredMessage?: string;
  /** Does the change; throw (reject) to keep the dialog open with the error shown */
  onSubmit: (reason: string) => Promise<unknown>;
  onClose: () => void;
}

/**
 * Asks for the reason of an audited admin action and does it. The reason is checked in the browser first
 * (required, at most 500 characters); a refusal from the server (409, 422, network error) is shown in the dialog,
 * which stays open so the admin can read it and try again.
 */
export function ReasonDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = 'primary',
  reasonLabel = 'Reason',
  maxLength = REASON_MAX_LENGTH,
  placeholder = 'Kept in the audit log',
  requiredMessage,
  onSubmit,
  onClose,
}: ReasonDialogProps) {
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ReasonForm>({
    resolver: zodResolver(reasonFormSchemaOf(maxLength, requiredMessage)),
    defaultValues: { reason: '' },
  });

  const close = () => {
    reset();
    onClose();
  };

  const submit = handleSubmit(async ({ reason }) => {
    try {
      await onSubmit(reason);
      close();
    } catch (error) {
      showServerErrors(error, setError, ['reason']);
    }
  });

  return (
    <FormDialog
      open={open}
      title={title}
      submitLabel={confirmLabel}
      tone={tone}
      isSubmitting={isSubmitting}
      serverError={errors.root?.server?.message}
      onSubmit={submit}
      onClose={close}
    >
      <div className="text-sm">{description}</div>
      <Textarea
        label={reasonLabel}
        placeholder={placeholder}
        maxLength={maxLength}
        error={errors.reason?.message}
        {...register('reason')}
      />
    </FormDialog>
  );
}
