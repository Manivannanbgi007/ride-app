import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { useLeaveWarning } from '@/hooks/useLeaveWarning';

interface UnsavedChangesGuard {
  /** Where the link the owner followed leads, while the page asks whether to leave; null otherwise */
  leaveTo: string | null;
  /** Stay on the page (the changes are kept) */
  stay: () => void;
  /** Leave without saving: go where the link led */
  leave: () => void;
}

/**
 * While `active` (a form with unsaved changes), leaving the page asks first. Reloading or closing the tab gets the
 * browser's own question; a link inside the application (the top menu, a card) is held back and `leaveTo` says
 * where it led, so the page can ask and then `leave()` or `stay()`. A jump to a section of the same page is let
 * through. The application uses a BrowserRouter (React Router's blocker needs a data router), so the links are
 * caught before React Router sees the click; the browser's Back button is not caught.
 */
export function useUnsavedChangesGuard(active: boolean): UnsavedChangesGuard {
  const navigate = useNavigate();
  const { pathname, search } = useLocation();
  const [leaveTo, setLeaveTo] = useState<string | null>(null);
  useLeaveWarning(active);

  useEffect(() => {
    if (!active) {
      return undefined;
    }
    const holdLink = (event: MouseEvent) => {
      if (
        event.defaultPrevented ||
        event.button !== 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey
      ) {
        return;
      }
      const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
      if (
        !(link instanceof HTMLAnchorElement) ||
        (link.target && link.target !== '_self') ||
        link.hasAttribute('download')
      ) {
        return;
      }
      const url = new URL(
        link.getAttribute('href') ?? '',
        `${window.location.origin}${pathname}${search}`,
      );
      const samePage = url.pathname === pathname && url.search === search;
      if (url.origin !== window.location.origin || samePage) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      setLeaveTo(`${url.pathname}${url.search}${url.hash}`);
    };
    // Capture: before the router's own click handler (which follows the link)
    document.addEventListener('click', holdLink, true);
    return () => document.removeEventListener('click', holdLink, true);
  }, [active, pathname, search]);

  return {
    leaveTo,
    stay: () => setLeaveTo(null),
    leave: () => {
      const to = leaveTo;
      setLeaveTo(null);
      if (to) {
        void navigate(to);
      }
    },
  };
}
