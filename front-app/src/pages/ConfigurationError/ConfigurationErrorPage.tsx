import { Settings } from 'lucide-react';

/** Shown instead of the application when its environment file is incomplete (see utils/env.ts). */
export function ConfigurationErrorPage({ problem }: { problem: string }) {
  return (
    <div
      role="alert"
      className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center"
    >
      <span className="flex size-16 items-center justify-center rounded-full bg-danger-50 text-danger-500">
        <Settings aria-hidden="true" className="size-8" />
      </span>
      <h1 className="text-2xl font-bold">The application is not configured</h1>
      <p className="max-w-lg text-sm text-ink-soft">{problem}</p>
    </div>
  );
}
