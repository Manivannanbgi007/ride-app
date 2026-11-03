import type { ReactNode } from 'react';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';

export interface ConfirmationDialogProps {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** danger: for actions that cannot be undone (cancel an order, deactivate a user) */
  tone?: 'primary' | 'danger';
  /** While the confirmed action runs: the confirm button shows a spinner */
  isLoading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** "Are you sure?" before an important action. */
export function ConfirmationDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'primary',
  isLoading = false,
  onConfirm,
  onCancel,
}: ConfirmationDialogProps) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={title}
      size="sm"
      tone={tone}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={isLoading}>
            {cancelLabel}
          </Button>
          <Button variant={tone} onClick={onConfirm} isLoading={isLoading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="text-sm text-ink">{message}</div>
    </Modal>
  );
}
