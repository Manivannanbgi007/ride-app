import { AlertTriangle, CheckCircle2, Info, X, XCircle, type LucideIcon } from 'lucide-react';
import {
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type FocusEvent,
  type KeyboardEvent,
} from 'react';

import { Tooltip } from '@/components/common/Tooltip';
import { TOAST_EXIT_MS, type Toast, type ToastTone } from '@/context/ToastContext';
import { cn } from '@/utils/classNames';

interface ToneLook {
  /**
   * The card: a two-colour gradient per kind (theme tokens --color-toast-*), white text readable at both ends
   * (WCAG AA), and a soft glow of its deeper colour under it
   */
  card: string;
  icon: LucideIcon;
  /** Read before the message by screen readers (the colour and icon say it to everybody else) */
  label: string;
}

const LOOKS: Record<ToastTone, ToneLook> = {
  success: {
    card: 'from-toast-success-start to-toast-success-end shadow-[0_16px_36px_-14px_var(--color-toast-success-end)]',
    icon: CheckCircle2,
    label: 'Success',
  },
  error: {
    card: 'from-toast-error-start to-toast-error-end shadow-[0_16px_36px_-14px_var(--color-toast-error-end)]',
    icon: XCircle,
    label: 'Error',
  },
  warning: {
    card: 'from-toast-warning-start to-toast-warning-end shadow-[0_16px_36px_-14px_var(--color-toast-warning-end)]',
    icon: AlertTriangle,
    label: 'Warning',
  },
  info: {
    card: 'from-toast-info-start to-toast-info-end shadow-[0_16px_36px_-14px_var(--color-toast-info-end)]',
    icon: Info,
    label: 'Information',
  },
};

/** Space between the page's top bar and the first toast, and from the screen's top edge without a top bar (px) */
const GAP_UNDER_HEADER = 12;
const EDGE = 16;

/** No toast on screen: only the window size matters (nothing is watched while there is nothing to place). */
function onResize(change: () => void): () => void {
  window.addEventListener('resize', change);
  return () => window.removeEventListener('resize', change);
}

/**
 * Toasts on screen: the top bar can also appear or change with the page (a toast shown just before a navigation,
 * like "Welcome back" after signing in, lands on a page with another bar). Checked at most once per frame.
 */
function onPageChange(change: () => void): () => void {
  let frame = 0;
  const check = () => {
    if (frame === 0) {
      frame = window.requestAnimationFrame(() => {
        frame = 0;
        change();
      });
    }
  };
  const observer = new MutationObserver(check);
  observer.observe(document.body, { childList: true, subtree: true });
  window.addEventListener('resize', check);
  return () => {
    observer.disconnect();
    window.removeEventListener('resize', check);
    window.cancelAnimationFrame(frame);
  };
}

/**
 * Where the stack starts: just under the area's sticky top bar (marked data-app-header), so the account menu, the
 * cart and the bell stay usable while a toast is showing; at the top edge on pages without one (sign-in).
 */
function toastTop(): number {
  const header = document.querySelector('[data-app-header]');
  const bottom = header?.getBoundingClientRect().bottom ?? 0;
  return bottom > 0 ? Math.round(bottom) + GAP_UNDER_HEADER : EDGE;
}

interface ToasterProps {
  toasts: readonly Toast[];
  /** Starts a toast's exit animation */
  onDismiss: (id: number) => void;
  /** Takes a toast away once its exit animation is over */
  onRemove: (id: number) => void;
}

/**
 * The toasts, in the top-right corner (the full width of a phone), oldest on top, one under the other: they never
 * overlap. See ToastProvider.
 */
export function Toaster({ toasts, onDismiss, onRemove }: ToasterProps) {
  const top = useSyncExternalStore(
    toasts.length > 0 ? onPageChange : onResize,
    toastTop,
    () => EDGE,
  );
  return (
    <div
      aria-live="polite"
      style={{ top }}
      // When the page's top bar changes under a toast, the stack glides to its new place
      className="pointer-events-none fixed inset-x-4 z-[60] flex flex-col transition-[top] duration-300 ease-out sm:right-6 sm:left-auto sm:w-96"
    >
      {toasts.map((toast) => (
        <ToastCard key={toast.id} toast={toast} onDismiss={onDismiss} onRemove={onRemove} />
      ))}
    </div>
  );
}

interface ToastCardProps {
  toast: Toast;
  onDismiss: (id: number) => void;
  onRemove: (id: number) => void;
}

/**
 * One toast. It hides by itself after its duration; pointing at it or focusing it (keyboard) pauses the countdown,
 * so there is time to read it. The X button (or Escape while it has the focus) dismisses it at once.
 */
function ToastCard({ toast, onDismiss, onRemove }: ToastCardProps) {
  const { id, tone, message, duration, leaving } = toast;
  const look = LOOKS[tone];
  const Icon = look.icon;
  const [paused, setPaused] = useState(false);
  // What is left of the duration: a pause keeps the rest for later
  const remaining = useRef(duration);

  useEffect(() => {
    if (leaving || paused || duration <= 0) {
      return;
    }
    const startedAt = Date.now();
    const timer = window.setTimeout(() => onDismiss(id), remaining.current);
    return () => {
      window.clearTimeout(timer);
      remaining.current -= Date.now() - startedAt;
    };
  }, [id, duration, leaving, paused, onDismiss]);

  // Once the exit animation is over, the toast is taken away (and the ones under it move up)
  useEffect(() => {
    if (!leaving) {
      return;
    }
    const timer = window.setTimeout(() => onRemove(id), TOAST_EXIT_MS);
    return () => window.clearTimeout(timer);
  }, [id, leaving, onRemove]);

  const resumeUnlessFocusStays = (event: FocusEvent<HTMLDivElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      setPaused(false);
    }
  };
  const dismissOnEscape = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'Escape') {
      onDismiss(id);
    }
  };

  return (
    // The row shrinks to nothing while the toast leaves, so the ones under it glide up instead of jumping
    <div
      className={cn(
        'grid transition-[grid-template-rows] duration-200 ease-in-out',
        leaving ? 'grid-rows-[0fr] delay-100' : 'grid-rows-[1fr]',
      )}
    >
      <div className={cn('min-h-0', leaving && '[overflow-y:clip]')}>
        <div className="pb-3">
          <div
            role={tone === 'error' || tone === 'warning' ? 'alert' : 'status'}
            data-tone={tone}
            onMouseEnter={() => setPaused(true)}
            onMouseLeave={() => setPaused(false)}
            onFocus={() => setPaused(true)}
            onBlur={resumeUnlessFocusStays}
            className={cn(
              'pointer-events-auto relative isolate flex items-start gap-3 overflow-hidden rounded-2xl bg-linear-to-br py-3.5 pr-3 pl-3.5 text-sm font-medium text-white ring-1 ring-white/15 ring-inset',
              look.card,
              leaving ? 'toast-leave' : 'toast-enter',
            )}
          >
            {/* A soft light in the corner, behind the content */}
            <span
              aria-hidden="true"
              className="pointer-events-none absolute -top-12 -right-10 -z-10 size-32 rounded-full bg-white/15 blur-2xl"
            />
            <span
              aria-hidden="true"
              className="flex size-8 shrink-0 items-center justify-center rounded-full bg-white/20 ring-1 ring-white/30 backdrop-blur-sm"
            >
              <Icon className="size-4.5" />
            </span>
            <p className="min-w-0 flex-1 self-center leading-snug break-words">
              <span className="sr-only">{look.label}: </span>
              {message}
            </p>
            <Tooltip label="Dismiss" icon={X}>
              <button
                type="button"
                aria-label="Dismiss"
                onClick={() => onDismiss(id)}
                onKeyDown={dismissOnEscape}
                className="-my-0.5 flex size-7 shrink-0 items-center justify-center rounded-full opacity-80 transition hover:bg-white/15 hover:opacity-100 focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-white focus-visible:outline-none"
              >
                <X className="size-4" />
              </button>
            </Tooltip>
            {duration > 0 && (
              // How much time is left; it stops while the countdown is paused
              <span
                aria-hidden="true"
                className="toast-countdown absolute inset-x-0 bottom-0 h-1 bg-white/45"
                style={{
                  animationDuration: `${duration}ms`,
                  animationPlayState: paused || leaving ? 'paused' : 'running',
                }}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
