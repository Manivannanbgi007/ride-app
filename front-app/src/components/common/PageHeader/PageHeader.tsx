import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  /** At the right: filters or buttons that belong to the whole page */
  actions?: ReactNode;
}

/** The page's title line ("Dashboard" / "Hi, ... Welcome back"). */
export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-[1.75rem] leading-tight font-extrabold tracking-tight sm:text-3xl">
          {title}
        </h1>
        {subtitle && <p className="mt-1.5 text-sm text-ink-soft">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}
