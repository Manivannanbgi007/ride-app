import { Search } from 'lucide-react';
import { useId, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router';

import { useSuggestions } from '@/hooks/api/storefrontHooks';
import { useDebounce } from '@/hooks/useDebounce';
import type { Suggestions } from '@/types/storefront';
import { cn } from '@/utils/classNames';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';

interface Option {
  key: string;
  label: string;
  detail: string | null;
  group: string;
  /** Where choosing it leads */
  to: string;
}

/** The suggestions as one list (restaurants, then dishes, then categories), as the arrow keys walk them. */
function optionsOf(suggestions: Suggestions | undefined): Option[] {
  if (!suggestions) {
    return [];
  }
  return [
    ...suggestions.restaurants.map((item) => ({
      key: `r${item.id}`,
      label: item.name,
      detail: item.detail,
      group: 'Restaurants',
      to: customerRouteTo.restaurantMenu(item.id),
    })),
    // A dish suggestion does not say which restaurant cooks it: show every restaurant with that dish
    ...suggestions.foods.map((item) => ({
      key: `f${item.id}`,
      label: item.name,
      detail: item.detail,
      group: 'Dishes',
      to: `${CUSTOMER_ROUTES.search}?tab=dishes&q=${encodeURIComponent(item.name)}`,
    })),
    ...suggestions.categories.map((item) => ({
      key: `c${item.id}`,
      label: item.name,
      detail: null,
      group: 'Categories',
      to: `${CUSTOMER_ROUTES.search}?tab=dishes&categoryId=${item.id}`,
    })),
  ];
}

/**
 * "Search for restaurants and dishes": suggestions appear while typing (from the second letter); the arrow keys
 * move through them, Enter opens the chosen one or searches for the text, Escape closes the list.
 */
export function SearchBox({ className }: { className?: string }) {
  const navigate = useNavigate();
  const listId = useId();
  const [text, setText] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const debounced = useDebounce(text, 250);
  const suggestions = useSuggestions(debounced);
  const options = debounced.trim().length >= 2 ? optionsOf(suggestions.data) : [];
  const showList = isOpen && options.length > 0;

  const close = () => {
    setIsOpen(false);
    setActive(-1);
  };
  const go = (to: string) => {
    close();
    setText('');
    void navigate(to);
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const chosen = showList && active >= 0 ? options[active] : undefined;
    if (chosen) {
      go(chosen.to);
    } else if (text.trim()) {
      go(customerRouteTo.search(text.trim()));
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      close();
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (options.length === 0) {
        return;
      }
      event.preventDefault();
      setIsOpen(true);
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActive((current) => (current + step + options.length) % options.length);
    }
  };

  return (
    <form role="search" onSubmit={submit} className={cn('relative', className)}>
      <label htmlFor={`${listId}-input`} className="sr-only">
        Search for restaurants and dishes
      </label>
      <input
        id={`${listId}-input`}
        type="search"
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
        autoComplete="off"
        maxLength={100}
        placeholder="Search for restaurants and dishes"
        value={text}
        onChange={(event) => {
          setText(event.target.value);
          setIsOpen(true);
          setActive(-1);
        }}
        onKeyDown={onKeyDown}
        onFocus={() => setIsOpen(true)}
        // Closing waits a moment, so a click on a suggestion still lands
        onBlur={() => window.setTimeout(close, 150)}
        className="h-11 w-full rounded-xl border border-line bg-page pr-11 pl-4 text-sm placeholder:text-ink-soft focus:border-brand-500 focus:bg-surface focus:ring-2 focus:ring-brand-100 focus:outline-none"
      />
      <button
        type="submit"
        aria-label="Search"
        className="absolute top-1/2 right-2 -translate-y-1/2 rounded-lg p-1.5 text-ink-soft hover:text-brand-text"
      >
        <Search aria-hidden="true" className="size-5" />
      </button>
      <ul
        id={listId}
        role="listbox"
        aria-label="Suggestions"
        hidden={!showList}
        className="absolute inset-x-0 top-full z-30 mt-2 max-h-96 overflow-y-auto rounded-2xl bg-surface py-2 shadow-lg"
      >
        {options.map((option, index) => (
          <li
            key={option.key}
            id={`${listId}-${index}`}
            role="option"
            aria-selected={index === active}
            // mousedown: before the input's blur closes the list
            onMouseDown={(event) => {
              event.preventDefault();
              go(option.to);
            }}
            className={cn(
              'flex cursor-pointer items-baseline justify-between gap-3 px-4 py-2 text-sm',
              index === active ? 'bg-brand-50' : 'hover:bg-page',
            )}
          >
            <span>
              <span className="font-semibold">{option.label}</span>
              {option.detail && <span className="text-ink-soft"> · {option.detail}</span>}
            </span>
            <span className="text-xs text-ink-soft">{option.group}</span>
          </li>
        ))}
      </ul>
    </form>
  );
}
