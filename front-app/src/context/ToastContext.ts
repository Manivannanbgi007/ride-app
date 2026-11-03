import { createContext, useContext } from 'react';

/** success: green, error: red, warning: amber, info: blue. */
export type ToastTone = 'success' | 'error' | 'warning' | 'info';

/**
 * How long each kind of toast stays by default (milliseconds). Errors and warnings stay longer: they usually say
 * what to do next. A toast can set its own time (showToast(..., { duration })), and an application can change
 * these (<ToastProvider durations={...}>).
 */
export const DEFAULT_TOAST_DURATIONS: Readonly<Record<ToastTone, number>> = {
  success: 4_000,
  info: 5_000,
  warning: 6_000,
  error: 7_000,
};

/** At most this many toasts at once: a new one makes the oldest leave, so the corner never fills the screen. */
export const MAX_VISIBLE_TOASTS = 4;

/** The exit animation (index.css .toast-leave) and the stack closing the gap, before a toast is removed. */
export const TOAST_EXIT_MS = 350;

export interface ToastOptions {
  /**
   * How long the toast stays (milliseconds) before it hides by itself; 0 = until it is dismissed. Without it, the
   * tone's own time (ToastProvider `durations`).
   */
  duration?: number;
}

export interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
  /** Milliseconds on screen; 0 = until dismissed */
  duration: number;
  /** Playing its exit animation; removed right after */
  leaving: boolean;
}

export interface ToastContextValue {
  /**
   * A short message in the top-right corner that hides by itself (hovering or focusing it pauses the countdown) and
   * can be dismissed: showToast('Order cancelled'), showToast('Could not save', 'error'),
   * showToast('Payment pending', 'warning', { duration: 10_000 }).
   */
  showToast: (message: string, tone?: ToastTone, options?: ToastOptions) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

/** UI state (React Context): any component can show a toast without passing functions down. */
export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside <ToastProvider>');
  }
  return context;
}
