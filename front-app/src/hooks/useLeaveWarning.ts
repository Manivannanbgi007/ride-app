import { useEffect } from 'react';

/**
 * While `active` (a form with unsaved changes), reloading or closing the browser tab asks first: the browser shows
 * its own "Leave site?" question. Links inside the application are guarded by the page itself.
 */
export function useLeaveWarning(active: boolean): void {
  useEffect(() => {
    if (!active) {
      return undefined;
    }
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [active]);
}
