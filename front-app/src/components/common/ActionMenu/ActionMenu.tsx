import { Ellipsis, type LucideIcon } from 'lucide-react';
import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router';

import { Tooltip } from '@/components/common/Tooltip';
import { cn } from '@/utils/classNames';

export interface ActionMenuItem {
  key: string;
  label: string;
  icon: LucideIcon;
  /** danger: for what cannot be undone (red) */
  tone?: 'default' | 'danger';
  /** Opens another page: the item is a link */
  to?: string;
  /** Does something (usually opens a dialog that asks to confirm): the item is a button */
  onSelect?: () => void;
}

interface ActionMenuProps {
  /** Read out for the "..." button: "Actions for order #303" */
  label: string;
  /** A small heading in the menu ("Order #303") */
  title?: string;
  items: readonly ActionMenuItem[];
  /** The highlighted thing that opens the menu as well (a status badge); the "..." button is next to it */
  children?: ReactNode;
  /** Whether the "..." button is shown (default); without it `children` is the only way in */
  dots?: boolean;
}

const MENU_WIDTH = 224;
const ITEM_HEIGHT = 46;
const GAP = 6;
const EDGE = 8;

interface Placement {
  left: number;
  top: number;
  up: boolean;
}

/**
 * A small pop-up menu of actions, in the style of the rest of the application: rounded, with a soft shadow, an icon
 * for each action, a highlight under the mouse or keyboard, and red for what cannot be undone. It opens from a
 * highlighted trigger (a status badge) and/or a "..." button, and is drawn over the page so no table or card can
 * cut it; it opens upward when there is no room below.
 *
 * Keyboard: Enter, Space or the down arrow opens it; the arrows, Home and End move; Enter chooses; Escape closes and
 * returns to the button; Tab leaves. Screen readers get a menu with menu items (role="menu").
 * It only shows and chooses: what an action does, and whether it is allowed, is up to the caller (who passes only
 * the items that are).
 */
export function ActionMenu({ label, title, items, children, dots = true }: ActionMenuProps) {
  const menuId = useId();
  const anchorRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  /** The button that opened the menu: focus goes back to it */
  const openedBy = useRef<HTMLElement | null>(null);
  const [open, setOpen] = useState(false);
  const [placement, setPlacement] = useState<Placement | null>(null);

  const close = useCallback((returnFocus: boolean) => {
    setOpen(false);
    setPlacement(null);
    if (returnFocus) {
      openedBy.current?.focus();
    }
  }, []);

  const toggle = (trigger: HTMLElement) => {
    if (open) {
      close(false);
      return;
    }
    openedBy.current = trigger;
    setOpen(true);
  };

  // Place the menu under the trigger (above it when there is no room), then focus its first item
  useLayoutEffect(() => {
    const anchor = anchorRef.current;
    if (!open || !anchor) {
      return;
    }
    const box = anchor.getBoundingClientRect();
    const height = items.length * ITEM_HEIGHT + (title ? 34 : 0) + 14;
    const up =
      window.innerHeight - box.bottom < height + GAP + EDGE && box.top > height + GAP + EDGE;
    setPlacement({
      left: Math.max(EDGE, Math.min(box.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - EDGE)),
      top: up ? box.top - GAP : box.bottom + GAP,
      up,
    });
  }, [open, items.length, title]);

  useEffect(() => {
    if (open && placement) {
      menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    }
    // Only when the menu has just been placed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, placement !== null]);

  // A click elsewhere, a scroll or a resize closes it
  useEffect(() => {
    if (!open) {
      return;
    }
    const closeOutside = (event: Event) => {
      const target = event.target as Node;
      if (!menuRef.current?.contains(target) && !anchorRef.current?.contains(target)) {
        close(false);
      }
    };
    const closeOnResize = () => close(false);
    document.addEventListener('pointerdown', closeOutside);
    window.addEventListener('scroll', closeOutside, true);
    window.addEventListener('resize', closeOnResize);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      window.removeEventListener('scroll', closeOutside, true);
      window.removeEventListener('resize', closeOnResize);
    };
  }, [open, close]);

  const onTriggerKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (!open && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) {
      event.preventDefault();
      openedBy.current = event.currentTarget;
      setOpen(true);
    }
  };

  const onMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const entries = [...event.currentTarget.querySelectorAll<HTMLElement>('[role="menuitem"]')];
    const index = entries.indexOf(document.activeElement as HTMLElement);
    const focusAt = (to: number) => entries[(to + entries.length) % entries.length]?.focus();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        focusAt(index + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        focusAt(index <= 0 ? -1 : index - 1);
        break;
      case 'Home':
        event.preventDefault();
        focusAt(0);
        break;
      case 'End':
        event.preventDefault();
        focusAt(-1);
        break;
      case 'Escape':
        // Only the menu closes (a dialog around it stays open)
        event.preventDefault();
        event.stopPropagation();
        close(true);
        break;
      case 'Tab':
        close(false);
        break;
    }
  };

  const itemClass = (tone: ActionMenuItem['tone']) =>
    cn(
      'group/item flex w-full items-center gap-3 rounded-xl px-2.5 py-2 text-left text-sm font-medium outline-none',
      'transition-colors duration-150 focus-visible:ring-2 focus-visible:ring-brand-500/40',
      tone === 'danger'
        ? 'text-danger-text hover:bg-danger-50 focus-visible:bg-danger-50'
        : 'text-ink hover:bg-brand-50 hover:text-brand-text-strong focus-visible:bg-brand-50 focus-visible:text-brand-text-strong',
    );
  const bubbleClass = (tone: ActionMenuItem['tone']) =>
    cn(
      'flex size-8 shrink-0 items-center justify-center rounded-full transition-transform duration-150 group-hover/item:scale-110',
      tone === 'danger' ? 'bg-danger-50 text-danger-text' : 'bg-brand-50 text-brand-text',
    );

  return (
    <div ref={anchorRef} className="inline-flex items-center gap-2">
      {children && (
        <button
          type="button"
          aria-haspopup="menu"
          aria-expanded={open}
          aria-controls={open ? menuId : undefined}
          onClick={(event) => toggle(event.currentTarget)}
          onKeyDown={onTriggerKeyDown}
          className={cn(
            'group/trigger inline-flex items-center rounded-full transition duration-150 outline-none',
            'hover:brightness-95 focus-visible:ring-2 focus-visible:ring-brand-500/50 active:scale-95',
            open && 'ring-2 ring-brand-500/40',
          )}
        >
          {children}
        </button>
      )}
      {dots && (
        <Tooltip label="Actions" icon={Ellipsis} placement="top">
          <button
            type="button"
            aria-label={label}
            aria-haspopup="menu"
            aria-expanded={open}
            aria-controls={open ? menuId : undefined}
            onClick={(event) => toggle(event.currentTarget)}
            onKeyDown={onTriggerKeyDown}
            className={cn(
              'flex size-8 items-center justify-center rounded-full text-ink-soft transition duration-150 outline-none',
              'hover:bg-brand-50 hover:text-brand-text focus-visible:ring-2 focus-visible:ring-brand-500/50',
              open && 'bg-brand-50 text-brand-text',
            )}
          >
            <Ellipsis aria-hidden="true" className="size-5" />
          </button>
        </Tooltip>
      )}

      {open &&
        placement &&
        createPortal(
          <div
            ref={menuRef}
            id={menuId}
            role="menu"
            aria-label={label}
            onKeyDown={onMenuKeyDown}
            style={
              {
                left: placement.left,
                top: placement.top,
                width: MENU_WIDTH,
                '--dropdown-from': placement.up ? '6px' : '-6px',
              } as CSSProperties
            }
            className={cn(
              'fixed z-50 rounded-2xl bg-surface p-1.5 ring-1 ring-line',
              'shadow-[0_18px_40px_-12px_rgb(16_40_28/0.35)]',
              placement.up ? 'origin-bottom -translate-y-full' : 'origin-top',
              'motion-safe:animate-dropdown-in',
            )}
          >
            {title && (
              <p className="px-2.5 pt-1.5 pb-1 text-xs font-semibold tracking-wide text-ink-soft uppercase">
                {title}
              </p>
            )}
            {items.map((item) => {
              const Icon = item.icon;
              const content = (
                <>
                  <span aria-hidden="true" className={bubbleClass(item.tone)}>
                    <Icon className="size-4" />
                  </span>
                  {item.label}
                </>
              );
              return item.to ? (
                <Link
                  key={item.key}
                  to={item.to}
                  role="menuitem"
                  tabIndex={-1}
                  onClick={() => close(false)}
                  className={itemClass(item.tone)}
                >
                  {content}
                </Link>
              ) : (
                <button
                  key={item.key}
                  type="button"
                  role="menuitem"
                  tabIndex={-1}
                  onClick={() => {
                    close(false);
                    item.onSelect?.();
                  }}
                  className={itemClass(item.tone)}
                >
                  {content}
                </button>
              );
            })}
          </div>,
          document.body,
        )}
    </div>
  );
}
