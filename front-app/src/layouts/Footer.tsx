import { env } from '@/utils/env';

/** The sidebar's last lines (as in the design). */
export function Footer() {
  return (
    <footer className="px-8 pb-8 text-xs text-ink-soft">
      <p className="font-semibold text-ink">{env.appName} Food Delivery Admin</p>
      <p className="mt-1">© {new Date().getFullYear()} All Rights Reserved</p>
    </footer>
  );
}
