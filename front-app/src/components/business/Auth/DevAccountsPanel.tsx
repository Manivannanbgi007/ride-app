import { ArrowRight, FlaskConical } from 'lucide-react';
import { useState } from 'react';

import type { AuthAudience } from '@/components/business/Auth/AuthShell';
import { Spinner } from '@/components/common/Loader';
import type { SignInForm } from '@/utils/validationUtils';

interface DevAccount extends SignInForm {
  name: string;
  /** What the account has to try things with */
  note: string;
}

/**
 * The local platform's test accounts (app-credential.txt at the project's root). They live in the local database
 * only: after the Docker volumes are removed they have to be made again. The admin is the platform's bootstrap admin
 * (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD in the project's .env). Only in the npm run dev bundle
 * (DevAccounts.ts): no build contains this file.
 */
const DEV_ACCOUNTS: Record<AuthAudience, readonly DevAccount[]> = {
  customer: [
    {
      name: 'Manual Customer',
      note: 'Default address: 24 MG Road, Bengaluru',
      email: 'manual.customer@example.com',
      password: 'Customer@123',
    },
  ],
  owner: [
    {
      name: 'Test Owner',
      note: 'QA Test Kitchen, Indiranagar: active, 4 dishes',
      email: 'test.owner@fooddelivery.local',
      password: 'Test@12345',
    },
  ],
  partner: [
    {
      name: 'Bengaluru Rider',
      note: 'Delivers in Bengaluru',
      email: 'rider.bengaluru@example.com',
      password: 'Rider1234',
    },
  ],
  admin: [
    {
      name: 'Platform Admin',
      note: 'The bootstrap admin',
      email: 'admin@fooddelivery.local',
      password: 'ChangeMe123',
    },
  ],
};

function initials(name: string): string {
  return name
    .split(' ')
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join('');
}

interface DevAccountsPanelProps {
  audience: AuthAudience;
  /** Fills the form with the account and signs in */
  onUse: (account: SignInForm) => void;
  /** The form is signing in: no second click */
  isBusy: boolean;
}

/**
 * Under the sign-in form while developing: the area's test accounts, each with its email and password, signed in with
 * one click (the form is filled and sent, so a refused sign-in shows its message as usual).
 */
export default function DevAccountsPanel({ audience, onUse, isBusy }: DevAccountsPanelProps) {
  const [chosen, setChosen] = useState<string | null>(null);

  return (
    <section
      aria-labelledby="dev-accounts-title"
      className="flex flex-col gap-3 rounded-2xl border border-dashed border-warning-500/70 bg-warning-50 p-4"
    >
      <div className="flex items-start gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-warning-100 text-warning-ink">
          <FlaskConical aria-hidden="true" className="size-4.5" />
        </span>
        <div className="flex flex-col gap-0.5">
          <h2
            id="dev-accounts-title"
            className="flex items-center gap-2 text-sm font-bold text-warning-ink"
          >
            Test accounts
            <span className="rounded-full bg-warning-100 px-2 py-0.5 text-[0.6875rem] font-bold tracking-wide uppercase">
              Dev only
            </span>
          </h2>
          <p className="text-xs text-warning-text">
            Click an account to sign in. Never part of a build.
          </p>
        </div>
      </div>

      <ul className="flex flex-col gap-2">
        {DEV_ACCOUNTS[audience].map((account) => (
          <li key={account.email}>
            <button
              type="button"
              disabled={isBusy}
              aria-label={`Sign in as ${account.name} (${account.email})`}
              onClick={() => {
                setChosen(account.email);
                onUse({ email: account.email, password: account.password });
              }}
              className="group flex w-full items-center gap-3 rounded-xl bg-surface p-3 text-left ring-1 ring-line transition not-disabled:hover:shadow-card not-disabled:hover:ring-brand-400 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none disabled:cursor-wait disabled:opacity-70"
            >
              <span
                aria-hidden="true"
                className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-500/10 text-sm font-bold text-brand-text-strong"
              >
                {initials(account.name)}
              </span>
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="text-sm font-semibold">{account.name}</span>
                <span className="truncate text-xs text-ink-soft">{account.note}</span>
                <span className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 font-mono text-xs">
                  <span className="truncate">{account.email}</span>
                  <span className="text-ink-soft">{account.password}</span>
                </span>
              </span>
              {isBusy && chosen === account.email ? (
                <Spinner size="sm" label="Signing in" />
              ) : (
                <ArrowRight
                  aria-hidden="true"
                  className="size-4.5 shrink-0 text-ink-soft transition group-hover:text-brand-text motion-safe:group-hover:translate-x-0.5"
                />
              )}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
