import type { Theme } from '@/theme/themeStorage';

/** How long colours glide from one theme to the other (milliseconds); the same time as in index.css. */
const TRANSITION_MS = 350;

let transitionTimer: number | undefined;

/**
 * Puts the theme on the page: <html data-theme="dark">, which the CSS tokens in index.css follow. With `animate`,
 * <html> also carries data-theme-changing for a third of a second, which index.css uses to let colours glide
 * instead of jumping (and which is gone again afterwards, so the page's own transitions are not touched).
 */
export function applyTheme(theme: Theme, animate: boolean): void {
  const root = document.documentElement;
  if (root.getAttribute('data-theme') === theme) {
    return;
  }
  if (animate) {
    root.setAttribute('data-theme-changing', '');
    window.clearTimeout(transitionTimer);
    transitionTimer = window.setTimeout(
      () => root.removeAttribute('data-theme-changing'),
      TRANSITION_MS,
    );
  }
  root.setAttribute('data-theme', theme);
}
