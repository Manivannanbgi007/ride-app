import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';

import { Toaster } from '@/components/common/Toast/Toaster';
import {
  DEFAULT_TOAST_DURATIONS,
  MAX_VISIBLE_TOASTS,
  ToastContext,
  type Toast,
  type ToastOptions,
  type ToastTone,
} from '@/context/ToastContext';

interface ToastProviderProps {
  children: ReactNode;
  /** Default time per tone (milliseconds; 0 = until dismissed). Pass a constant: it is read when a toast shows. */
  durations?: Partial<Record<ToastTone, number>>;
  maxVisible?: number;
}

/** Holds the toasts on screen and draws them (Toaster). Wraps the whole application in main.tsx. */
export function ToastProvider({
  children,
  durations,
  maxVisible = MAX_VISIBLE_TOASTS,
}: ToastProviderProps) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  /** Starts the exit animation; the Toaster removes the toast when it is over. */
  const dismiss = useCallback((id: number) => {
    setToasts((current) =>
      current.map((toast) => (toast.id === id ? { ...toast, leaving: true } : toast)),
    );
  }, []);

  const remove = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback(
    (message: string, tone: ToastTone = 'success', options: ToastOptions = {}) => {
      const id = nextId.current++;
      const duration = Math.max(
        0,
        options.duration ?? durations?.[tone] ?? DEFAULT_TOAST_DURATIONS[tone],
      );
      setToasts((current) => {
        const next = [...current, { id, tone, message, duration, leaving: false }];
        // Over the limit: the oldest ones still on screen leave (with their exit animation)
        const tooMany = next.filter((toast) => !toast.leaving).length - maxVisible;
        if (tooMany <= 0) {
          return next;
        }
        const leaving = new Set(
          next
            .filter((toast) => !toast.leaving)
            .slice(0, tooMany)
            .map((toast) => toast.id),
        );
        return next.map((toast) => (leaving.has(toast.id) ? { ...toast, leaving: true } : toast));
      });
    },
    [durations, maxVisible],
  );

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext value={value}>
      {children}
      <Toaster toasts={toasts} onDismiss={dismiss} onRemove={remove} />
    </ToastContext>
  );
}
