import { ChevronLeft } from 'lucide-react';
import type { MouseEvent } from 'react';
import { Link } from 'react-router';

interface BackLinkProps {
  to: string;
  children: string;
  /** Before going back, e.g. to ask about unsaved changes (event.preventDefault() stays on the page) */
  onClick?: (event: MouseEvent<HTMLAnchorElement>) => void;
}

/** "< Back to orders" above a details page. */
export function BackLink({ to, children, onClick }: BackLinkProps) {
  return (
    <Link
      to={to}
      onClick={onClick}
      className="inline-flex items-center gap-1 text-sm font-semibold text-brand-text hover:underline"
    >
      <ChevronLeft aria-hidden="true" className="size-4" />
      {children}
    </Link>
  );
}
