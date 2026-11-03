import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ThemeToggle } from '@/components/common/ThemeToggle';
import { ThemeProvider } from '@/theme/ThemeProvider';
import { THEME_STORAGE_KEY } from '@/theme/themeStorage';

/** A stand-in for the browser's "prefers-color-scheme: dark" setting, which the test can change. */
function stubSystemTheme(initial: 'light' | 'dark') {
  const listeners = new Set<() => void>();
  const query = {
    matches: initial === 'dark',
    addEventListener: (_: string, listener: () => void) => listeners.add(listener),
    removeEventListener: (_: string, listener: () => void) => listeners.delete(listener),
  };
  window.matchMedia = vi.fn(() => query) as unknown as typeof window.matchMedia;
  return {
    change(theme: 'light' | 'dark') {
      query.matches = theme === 'dark';
      listeners.forEach((listener) => listener());
    },
  };
}

const renderToggle = () =>
  render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  );

const pageTheme = () => document.documentElement.getAttribute('data-theme');

describe('theme', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-theme-changing');
  });

  afterEach(() => {
    Reflect.deleteProperty(window, 'matchMedia');
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('starts with the system preference when the user has not chosen', () => {
    stubSystemTheme('dark');
    renderToggle();

    expect(pageTheme()).toBe('dark');
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('is light when there is no preference and the browser cannot tell', () => {
    renderToggle();

    expect(pageTheme()).toBe('light');
    expect(screen.getByRole('button', { name: 'Switch to dark mode' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('uses the choice the user made before, over the system preference', () => {
    stubSystemTheme('dark');
    localStorage.setItem(THEME_STORAGE_KEY, 'light');

    renderToggle();

    expect(pageTheme()).toBe('light');
  });

  it('ignores a damaged saved value', () => {
    stubSystemTheme('light');
    localStorage.setItem(THEME_STORAGE_KEY, 'purple');

    renderToggle();

    expect(pageTheme()).toBe('light');
  });

  it('switches on click, puts it on the page and remembers it for the next visit', async () => {
    stubSystemTheme('light');
    const { unmount } = renderToggle();

    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }));

    expect(pageTheme()).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument();

    // The next visit
    unmount();
    document.documentElement.removeAttribute('data-theme');
    renderToggle();
    expect(pageTheme()).toBe('dark');

    await userEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }));
    expect(pageTheme()).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('lets colours glide for a moment after a switch, and not on the first paint', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    stubSystemTheme('light');
    renderToggle();
    expect(document.documentElement).not.toHaveAttribute('data-theme-changing');

    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }));
    expect(document.documentElement).toHaveAttribute('data-theme-changing');

    act(() => {
      vi.advanceTimersByTime(400);
    });
    expect(document.documentElement).not.toHaveAttribute('data-theme-changing');
  });

  it('follows the system while the user has not chosen, and stops once they have', async () => {
    const system = stubSystemTheme('light');
    renderToggle();

    act(() => system.change('dark'));
    expect(pageTheme()).toBe('dark');

    await userEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }));
    act(() => system.change('dark'));
    expect(pageTheme()).toBe('light'); // the user's choice wins
  });

  it('follows a choice made in another tab', () => {
    stubSystemTheme('light');
    renderToggle();

    act(() => {
      window.dispatchEvent(
        new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: 'dark' }),
      );
    });

    expect(pageTheme()).toBe('dark');
  });

  it('still works when the browser will not store anything', async () => {
    stubSystemTheme('light');
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    renderToggle();

    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }));

    expect(pageTheme()).toBe('dark');
  });
});
