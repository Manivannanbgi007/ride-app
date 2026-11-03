import { createContext, useContext } from 'react';

export interface LayoutContextValue {
  /** Small screens: the navigation slides in over the page */
  isSidebarOpen: boolean;
  openSidebar: () => void;
  closeSidebar: () => void;
  /** Wide screens: the sidebar is narrowed to its icons (remembered in this browser) */
  isSidebarCollapsed: boolean;
  toggleSidebarCollapsed: () => void;
}

export const LayoutContext = createContext<LayoutContextValue | null>(null);

/**
 * UI state (React Context) shared by the header (menu button) and the sidebar (closes after a click), without
 * passing it through the layout.
 */
export function useLayout(): LayoutContextValue {
  const context = useContext(LayoutContext);
  if (!context) {
    throw new Error('useLayout must be used inside <LayoutProvider>');
  }
  return context;
}
