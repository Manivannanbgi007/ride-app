import { X } from 'lucide-react';
import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

import { Tooltip } from '@/components/common/Tooltip';
import { cn } from '@/utils/classNames';

/** The header band: the brand's green, or red for actions that cannot be undone */
const HEADER_TONES = {
  primary: 'border-line from-brand-50 via-surface to-surface text-ink',
  danger: 'border-danger-500/20 from-danger-50 via-danger-50 to-surface text-danger-text',
};

const WIDTHS = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-3xl',
};

export interface ModalProps {
  open: boolean;
  /** Called on the close button, the Escape key and a click beside the dialog */
  onClose: () => void;
  title: string;
  children: ReactNode;
  /** Buttons at the bottom (right-aligned) */
  footer?: ReactNode;
  size?: keyof typeof WIDTHS;
  /** danger: a red header for actions that cannot be undone (cancel an order, delete) */
  tone?: keyof typeof HEADER_TONES;
}

/** A dialog above the page. Open and close it with local state: const [open, setOpen] = useState(false). */
export function Modal({
  open,
  onClose,
  title,
  children,
  footer,
  size = 'md',
  tone = 'primary',
}: ModalProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  // The latest onClose, without re-running the effect below when the caller passes a new function each render
  // (re-running it would move the focus away from the field being typed in)
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCloseRef.current();
      }
    };
    document.addEventListener('keydown', closeOnEscape);
    // The page behind does not scroll while the dialog is open
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    return () => {
      document.removeEventListener('keydown', closeOnEscape);
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div aria-hidden="true" className="absolute inset-0 bg-scrim/40" onClick={onClose} />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={cn(
          'relative w-full rounded-2xl bg-surface shadow-card focus:outline-none',
          WIDTHS[size],
        )}
      >
        <div
          className={cn(
            'flex items-center justify-between gap-4 rounded-t-2xl border-b bg-linear-to-r px-6 py-4',
            HEADER_TONES[tone],
          )}
        >
          <h2 id={titleId} className="text-lg font-bold">
            {title}
          </h2>
          <Tooltip label="Close" icon={X}>
            <button
              type="button"
              aria-label="Close"
              onClick={onClose}
              className="rounded-lg p-1 text-current opacity-70 transition hover:bg-surface/70 hover:opacity-100"
            >
              <X className="size-5" />
            </button>
          </Tooltip>
        </div>
        <div className="px-6 py-5">{children}</div>
        {footer && (
          <div className="flex justify-end gap-3 border-t border-line px-6 py-4">{footer}</div>
        )}
      </div>
    </div>,
    document.body,
  );
}
