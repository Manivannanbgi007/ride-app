import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { LayoutContext } from '@/context/LayoutContext';

/** The admin's choice of a narrow sidebar, kept in this browser (a convenience: nothing breaks without it). */
const COLLAPSED_KEY = 'front-app.sidebar-collapsed';

function loadCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
}

export function LayoutProvider({ children }: { children: ReactNode }) {
  const [isSidebarOpen, setSidebarOpen] = useState(false);
  const [isSidebarCollapsed, setSidebarCollapsed] = useState(loadCollapsed);
  const openSidebar = useCallback(() => setSidebarOpen(true), []);
  const closeSidebar = useCallback(() => setSidebarOpen(false), []);
  const toggleSidebarCollapsed = useCallback(
    () => setSidebarCollapsed((collapsed) => !collapsed),
    [],
  );

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSED_KEY, String(isSidebarCollapsed));
    } catch {
      // Storage blocked (private window): the choice lasts until the page is reloaded
    }
  }, [isSidebarCollapsed]);

  const value = useMemo(
    () => ({
      isSidebarOpen,
      openSidebar,
      closeSidebar,
      isSidebarCollapsed,
      toggleSidebarCollapsed,
    }),
    [isSidebarOpen, openSidebar, closeSidebar, isSidebarCollapsed, toggleSidebarCollapsed],
  );
  return <LayoutContext value={value}>{children}</LayoutContext>;
}
