import { Check, ChevronDown } from 'lucide-react';
import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ComponentProps,
  type CSSProperties,
  type KeyboardEvent,
} from 'react';
import { createPortal } from 'react-dom';

import { cn } from '@/utils/classNames';

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps extends Omit<ComponentProps<'select'>, 'children'> {
  label?: string;
  options: readonly SelectOption[];
  /** A first, empty choice ("All statuses"); leave out when a value is always chosen */
  placeholder?: string;
  /** Shown under the field in red; also marks the field invalid for screen readers */
  error?: string;
  hint?: string;
  /** field: a bordered box (forms, filters); inline: just the chosen text with an arrow (inside a tile) */
  variant?: 'field' | 'inline';
}

/** The space kept between the list and its field, and between the list and the window's edge (px). */
const GAP = 6;
const EDGE = 8;
/** The two looks of the field (the open and error borders are added where it is drawn). */
const FIELD = cn(
  'h-11 rounded-xl border bg-surface px-4 shadow-xs transition duration-200',
  'hover:border-brand-300 hover:shadow-card',
  'focus-visible:border-brand-500 focus-visible:ring-4 focus-visible:ring-brand-500/15',
  'disabled:cursor-not-allowed disabled:bg-page disabled:text-ink-soft disabled:shadow-none',
);
const INLINE_FIELD =
  'cursor-pointer rounded-md text-xs text-ink focus-visible:ring-2 focus-visible:ring-brand-100';

/** The tallest the list grows (px, as max-h-64); it scrolls beyond that. */
const LIST_MAX_HEIGHT = 256;

interface Placement {
  left: number;
  top: number;
  minWidth: number;
  /** Opens upward: `top` is then the field's top edge and the list sits above it */
  up: boolean;
}

/**
 * A dropdown that looks like the rest of the application: a field with an arrow that opens a rounded list over the
 * page (so no card edge cuts it) with the chosen item ticked.
 *
 * It keeps the API of a plain select (value, onChange(event), name, ref, register(...)): a real <select> stays in
 * the page, out of sight, as the form control, and choosing an item sets it and fires its change event. The
 * visible field is the combobox for people: it opens with the mouse, Enter, Space or the arrow keys, moves with the
 * arrows, Home, End and typing a letter, and closes with Escape or Tab.
 */
export function Select({
  label,
  options,
  placeholder,
  error,
  hint,
  id,
  className,
  variant = 'field',
  ref,
  'aria-label': ariaLabel,
  ...props
}: SelectProps) {
  const generatedId = useId();
  const baseId = id ?? generatedId;
  const labelId = `${baseId}-label`;
  const listId = `${baseId}-list`;
  const messageId = `${baseId}-message`;
  const message = error ?? hint;

  const nativeRef = useRef<HTMLSelectElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const typed = useRef({ text: '', timer: 0 });

  const choices: readonly SelectOption[] =
    placeholder === undefined ? options : [{ value: '', label: placeholder }, ...options];

  // The chosen value lives in the real <select> (controlled or not); this copy is what the field shows
  const [value, setValue] = useState('');
  // After every render: a form library can change the real select without telling React
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useLayoutEffect(() => {
    setValue(nativeRef.current?.value ?? '');
  });
  const selectedIndex = choices.findIndex((choice) => choice.value === value);
  const selected = choices[selectedIndex];

  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [placement, setPlacement] = useState<Placement | null>(null);

  const setRefs = useCallback(
    (element: HTMLSelectElement | null) => {
      nativeRef.current = element;
      if (typeof ref === 'function') {
        ref(element);
      } else if (ref) {
        ref.current = element;
      }
    },
    [ref],
  );

  const close = useCallback(() => setOpen(false), []);

  const openList = () => {
    if (props.disabled) {
      return;
    }
    setActive(Math.max(selectedIndex, 0));
    setOpen(true);
  };

  const choose = (index: number) => {
    const select = nativeRef.current;
    const choice = choices[index];
    if (select && choice) {
      select.value = choice.value;
      select.dispatchEvent(new Event('change', { bubbles: true }));
      setValue(select.value);
    }
    setOpen(false);
    triggerRef.current?.focus();
  };

  // Place the list under the field (above it when there is no room), then keep it there or let it go
  useLayoutEffect(() => {
    const trigger = triggerRef.current;
    if (!open || !trigger) {
      return;
    }
    const box = trigger.getBoundingClientRect();
    const wanted = Math.min(LIST_MAX_HEIGHT, choices.length * 40 + 12);
    const up = window.innerHeight - box.bottom < wanted + GAP + EDGE && box.top > wanted;
    setPlacement({
      left: Math.max(EDGE, Math.min(box.left, window.innerWidth - EDGE - box.width)),
      top: up ? box.top - GAP : box.bottom + GAP,
      minWidth: box.width,
      up,
    });
  }, [open, choices.length]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const closeOutside = (event: Event) => {
      const target = event.target as Node;
      if (!listRef.current?.contains(target) && !triggerRef.current?.contains(target)) {
        close();
      }
    };
    document.addEventListener('pointerdown', closeOutside);
    window.addEventListener('scroll', closeOutside, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      window.removeEventListener('scroll', closeOutside, true);
      window.removeEventListener('resize', close);
    };
  }, [open, close]);

  // The item under the arrow keys stays in view
  useEffect(() => {
    if (open && placement) {
      listRef.current?.children[active]?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [open, placement, active]);

  useEffect(() => () => window.clearTimeout(typed.current.timer), []);

  const move = (to: number) => setActive(Math.max(0, Math.min(choices.length - 1, to)));

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const { key } = event;
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(key)) {
        event.preventDefault();
        openList();
      }
      return;
    }
    switch (key) {
      case 'ArrowDown':
        move(active + 1);
        break;
      case 'ArrowUp':
        move(active - 1);
        break;
      case 'Home':
        move(0);
        break;
      case 'End':
        move(choices.length - 1);
        break;
      case 'Enter':
      case ' ':
        choose(active);
        break;
      case 'Escape':
        // Only the list closes (a dialog around the field stays open)
        event.stopPropagation();
        close();
        break;
      case 'Tab':
        close();
        return;
      default: {
        if (key.length !== 1) {
          return;
        }
        // Typing jumps to the next item that starts with what was typed
        window.clearTimeout(typed.current.timer);
        typed.current.text += key.toLowerCase();
        typed.current.timer = window.setTimeout(() => (typed.current.text = ''), 600);
        const from = typed.current.text.length === 1 ? active + 1 : active;
        const order = [...choices.keys()].map((index) => (index + from) % choices.length);
        const found = order.find((index) =>
          choices[index]?.label.toLowerCase().startsWith(typed.current.text),
        );
        if (found !== undefined) {
          setActive(found);
        }
      }
    }
    event.preventDefault();
  };

  const inline = variant === 'inline';

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <span
          id={labelId}
          onClick={() => triggerRef.current?.focus()}
          className={inline ? 'block text-sm font-semibold' : 'text-sm font-medium'}
        >
          {label}
        </span>
      )}
      <button
        ref={triggerRef}
        type="button"
        role="combobox"
        id={baseId}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-activedescendant={open ? `${baseId}-option-${active}` : undefined}
        aria-label={ariaLabel}
        aria-labelledby={ariaLabel ? undefined : label ? labelId : undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        disabled={props.disabled}
        onClick={() => (open ? close() : openList())}
        onKeyDown={onKeyDown}
        onBlur={() => {
          // The form library listens for the real select's blur (the field was touched)
          nativeRef.current?.dispatchEvent(new Event('focusout', { bubbles: true }));
        }}
        className={cn(
          'group/select flex items-center justify-between gap-2 text-left text-sm',
          'focus-visible:outline-none',
          inline ? INLINE_FIELD : FIELD,
          !inline && open && 'border-brand-500 ring-4 ring-brand-500/15',
          !inline && (error ? 'border-danger-500' : !open && 'border-line'),
          className,
        )}
      >
        <span
          className={cn('truncate', value === '' && placeholder !== undefined && 'text-ink-soft')}
        >
          {selected?.label ?? ''}
        </span>
        <ChevronDown
          aria-hidden="true"
          className={cn(
            'shrink-0 text-ink-soft transition-transform duration-200 group-hover/select:text-brand-text',
            inline ? 'size-3.5' : 'size-4',
            open && 'rotate-180 text-brand-text',
          )}
        />
      </button>

      {/* The real form control: out of sight and out of the tab order, kept in step by the field above */}
      <select ref={setRefs} aria-hidden="true" tabIndex={-1} className="sr-only" {...props}>
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      {open &&
        placement &&
        createPortal(
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            aria-label={ariaLabel ?? label}
            style={
              {
                left: placement.left,
                top: placement.top,
                minWidth: placement.minWidth,
                maxHeight: LIST_MAX_HEIGHT,
                '--dropdown-from': placement.up ? '6px' : '-6px',
              } as CSSProperties
            }
            className={cn(
              'fixed z-50 w-max max-w-[calc(100vw-1rem)] overflow-y-auto rounded-2xl bg-surface p-1.5',
              'shadow-[0_18px_40px_-12px_rgb(16_40_28/0.28)] ring-1 ring-line',
              placement.up ? 'origin-bottom -translate-y-full' : 'origin-top',
              '[scrollbar-width:thin] [scrollbar-color:var(--color-brand-200)_transparent]',
              'motion-safe:animate-dropdown-in',
            )}
          >
            {choices.map((choice, index) => {
              const isSelected = index === selectedIndex;
              return (
                <li
                  key={choice.value}
                  id={`${baseId}-option-${index}`}
                  role="option"
                  aria-selected={isSelected}
                  data-value={choice.value}
                  // Focus stays on the field: pressing an item must not take it away
                  onMouseDown={(event) => event.preventDefault()}
                  onMouseMove={() => setActive(index)}
                  onClick={() => choose(index)}
                  className={cn(
                    'flex cursor-pointer items-center justify-between gap-6 rounded-xl px-3 py-2 text-sm transition-colors',
                    index === active ? 'bg-brand-50 text-brand-text-strong' : 'text-ink',
                    isSelected && 'font-semibold text-brand-text-strong',
                    choice.value === '' && !isSelected && 'text-ink-soft',
                  )}
                >
                  <span className="whitespace-nowrap">{choice.label}</span>
                  {isSelected && (
                    <Check aria-hidden="true" className="size-4 shrink-0 text-brand-text" />
                  )}
                </li>
              );
            })}
          </ul>,
          document.body,
        )}

      {message && (
        <p id={messageId} className={cn('text-xs', error ? 'text-danger-500' : 'text-ink-soft')}>
          {message}
        </p>
      )}
    </div>
  );
}
