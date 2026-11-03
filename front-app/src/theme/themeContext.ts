import { createContext, useContext } from 'react';

import type { Theme } from '@/theme/themeStorage';

export interface ThemeState {
  theme: Theme;
  /** Switches to the other theme and remembers the choice */
  toggleTheme: () => void;
  setTheme: (theme: Theme) => void;
}

/** Without a provider (a test of one component) the theme is light and switching does nothing. */
export const ThemeContext = createContext<ThemeState>({
  theme: 'light',
  toggleTheme: () => undefined,
  setTheme: () => undefined,
});

/** The current theme and how to change it: `const { theme, toggleTheme } = useTheme();` */
export function useTheme(): ThemeState {
  return useContext(ThemeContext);
}
