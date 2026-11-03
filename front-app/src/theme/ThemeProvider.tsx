import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { applyTheme } from '@/theme/applyTheme';
import { ThemeContext } from '@/theme/themeContext';
import {
  isTheme,
  loadTheme,
  saveTheme,
  systemTheme,
  THEME_STORAGE_KEY,
  type Theme,
} from '@/theme/themeStorage';

/**
 * The application's theme (light or dark), in one place:
 *  - first visit: the system's preference; afterwards: the user's last choice (kept in this browser);
 *  - the choice is put on <html data-theme> (the CSS tokens do the rest: no component knows about themes);
 *  - while the user has not chosen, a change of the system's preference is followed;
 *  - another open tab that switches theme switches this one too.
 * The state is a single string, so switching re-renders only what reads it (the toggle button).
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => loadTheme() ?? systemTheme());

  const setTheme = useCallback((next: Theme) => {
    setThemeState(next);
    saveTheme(next);
  }, []);

  const toggleTheme = useCallback(() => {
    setThemeState((current) => {
      const next = current === 'dark' ? 'light' : 'dark';
      saveTheme(next);
      return next;
    });
  }, []);

  // The page follows the state; the first one (page load) is not animated
  useEffect(() => {
    applyTheme(theme, document.documentElement.hasAttribute('data-theme'));
  }, [theme]);

  // Until the user chooses, the system decides
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      return;
    }
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const follow = () => {
      if (loadTheme() === null) {
        setThemeState(query.matches ? 'dark' : 'light');
      }
    };
    query.addEventListener('change', follow);
    return () => query.removeEventListener('change', follow);
  }, []);

  // A choice made in another tab
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY && isTheme(event.newValue)) {
        setThemeState(event.newValue);
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const value = useMemo(() => ({ theme, toggleTheme, setTheme }), [theme, toggleTheme, setTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
