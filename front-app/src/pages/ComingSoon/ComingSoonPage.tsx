import { Link, useSearchParams } from 'react-router';

import { Badge } from '@/components/common/Badge';
import { PageHeader } from '@/components/common/PageHeader';
import type { PageEntry } from '@/layouts/navigation';
import { ROUTES } from '@/utils/constants';

/** A page of the design that is built in a later phase: says what it will do and when. */
export function ComingSoonPage({ page }: { page: PageEntry }) {
  const [searchParams] = useSearchParams();
  const searchedFor = searchParams.get('q');
  const Icon = page.icon;

  return (
    <div className="flex flex-col gap-8">
      <PageHeader title={page.label} />
      <div className="flex flex-col items-center gap-4 rounded-2xl bg-surface px-6 py-16 text-center shadow-card">
        <span className="flex size-16 items-center justify-center rounded-full bg-brand-50 text-brand-500">
          <Icon aria-hidden="true" className="size-8" />
        </span>
        <Badge tone="info">Coming in {page.comingIn}</Badge>
        <p className="max-w-md text-sm">{page.description}</p>
        {searchedFor && <p className="text-sm text-ink-soft">You searched for "{searchedFor}".</p>}
        <Link
          to={ROUTES.dashboard}
          className="text-sm font-semibold text-brand-text hover:underline"
        >
          Back to the dashboard
        </Link>
      </div>
    </div>
  );
}
