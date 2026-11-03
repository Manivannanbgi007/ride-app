import { Check, Copy } from 'lucide-react';
import { useEffect, useState } from 'react';

import { useToast } from '@/context/ToastContext';
import { cn } from '@/utils/classNames';

const LOOKS = {
  /** A dashed ticket: the code is the thing to read (offer cards) */
  ticket:
    'border-2 border-dashed border-brand-300 bg-brand-50 text-brand-text-strong hover:border-brand-500',
  /** A filled chip: the code next to a countdown, where it must stand out (limited time offers) */
  solid: 'bg-brand-500 text-on-brand shadow-brand hover:bg-brand-400',
} as const;

/**
 * A coupon code, copied with one click (for typing in at checkout, or sharing). The icon turns into a tick for a
 * moment so the click shows it worked.
 */
export function CopyCode({
  code,
  look = 'ticket',
  className,
}: {
  code: string;
  look?: keyof typeof LOOKS;
  className?: string;
}) {
  const { showToast } = useToast();
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) {
      return;
    }
    const timer = window.setTimeout(() => setCopied(false), 1_800);
    return () => window.clearTimeout(timer);
  }, [copied]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      showToast(`Copied ${code}`);
    } catch {
      showToast(`Copying is blocked in this browser. The code is ${code}.`, 'error');
    }
  };

  const Icon = copied ? Check : Copy;
  return (
    <button
      type="button"
      onClick={() => void copy()}
      aria-label={`Copy code ${code}`}
      className={cn(
        'flex h-10 min-w-0 items-center justify-between gap-2 rounded-xl px-3 font-mono text-sm font-bold tracking-wider transition-colors focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:outline-none',
        LOOKS[look],
        className,
      )}
    >
      <span className="truncate">{code}</span>
      <Icon aria-hidden="true" className="size-4 shrink-0" />
    </button>
  );
}
