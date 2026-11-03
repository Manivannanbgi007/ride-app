import { useRef, type KeyboardEvent, type ReactNode } from 'react';

import { cn } from '@/utils/classNames';

export interface TabItem<T extends string = string> {
  id: T;
  label: string;
  /** A number next to the label (e.g. unread notifications); hidden when 0 or missing */
  count?: number;
}

interface TabsProps<T extends string> {
  /** Read out by screen readers: what the tabs switch between */
  label: string;
  tabs: readonly TabItem<T>[];
  activeId: T;
  onChange: (id: T) => void;
}

/**
 * Tabs above a page's sections. The arrow keys move between tabs (as screen-reader users expect).
 * Put each section in a <TabPanel id=...> with the same id.
 */
export function Tabs<T extends string>({ label, tabs, activeId, onChange }: TabsProps<T>) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  const moveWithArrows = (event: KeyboardEvent, index: number) => {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (step === 0) {
      return;
    }
    event.preventDefault();
    const next = (index + step + tabs.length) % tabs.length;
    onChange(tabs[next].id);
    buttons.current[next]?.focus();
  };

  return (
    <div
      role="tablist"
      aria-label={label}
      className="flex gap-1 overflow-x-auto border-b border-line"
    >
      {tabs.map((tab, index) => {
        const isActive = tab.id === activeId;
        return (
          <button
            key={tab.id}
            ref={(element) => {
              buttons.current[index] = element;
            }}
            type="button"
            role="tab"
            id={`tab-${tab.id}`}
            aria-selected={isActive}
            aria-label={tab.count ? `${tab.label} (${tab.count})` : undefined}
            aria-controls={`panel-${tab.id}`}
            tabIndex={isActive ? 0 : -1}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => moveWithArrows(event, index)}
            className={cn(
              '-mb-px flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold whitespace-nowrap transition-colors',
              isActive
                ? 'border-brand-500 text-brand-text'
                : 'border-transparent text-ink-soft hover:text-ink',
            )}
          >
            {tab.label}
            {tab.count !== undefined && tab.count > 0 && (
              <span className="rounded-full bg-brand-500 px-2 py-0.5 text-xs text-on-brand">
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

/** The section of one tab (see Tabs). */
export function TabPanel({
  id,
  className,
  children,
}: {
  id: string;
  /** Replaces the default spacing between the parts (gap-6) */
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      role="tabpanel"
      id={`panel-${id}`}
      aria-labelledby={`tab-${id}`}
      className={className ?? 'flex flex-col gap-6'}
    >
      {children}
    </div>
  );
}
