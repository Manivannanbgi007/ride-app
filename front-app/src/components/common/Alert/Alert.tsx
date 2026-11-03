import { AlertTriangle, CheckCircle2, Info, XCircle, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { cn } from '@/utils/classNames';

const TONES: Record<AlertTone, { classes: string; icon: LucideIcon }> = {
  info: { classes: 'bg-info-50 text-info-500', icon: Info },
  success: { classes: 'bg-brand-50 text-brand-text-strong', icon: CheckCircle2 },
  warning: { classes: 'bg-warning-50 text-warning-text', icon: AlertTriangle },
  error: { classes: 'bg-danger-50 text-danger-text', icon: XCircle },
};

type AlertTone = 'info' | 'success' | 'warning' | 'error';

interface AlertProps {
  tone?: AlertTone;
  title?: string;
  children: ReactNode;
  className?: string;
}

/** A message inside the page (not a pop-up): "Some figures could not be loaded", "Saved". */
export function Alert({ tone = 'info', title, children, className }: AlertProps) {
  const { classes, icon: Icon } = TONES[tone];
  return (
    <div
      role={tone === 'error' || tone === 'warning' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-xl px-4 py-3 text-sm', classes, className)}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <div className="text-ink">{children}</div>
      </div>
    </div>
  );
}
