/**
 * "Live: updates by itself", with a softly pulsing dot (motion-reduce: still): the page asks the server again every
 * few seconds, so nobody needs to reload it.
 */
export function LiveBadge({ label = 'Live: updates by itself' }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-full bg-st-delivered-soft px-3 py-1.5 text-xs font-semibold text-st-delivered-text ring-1 ring-st-delivered-from/20">
      <span aria-hidden="true" className="relative flex size-2">
        <span className="absolute inline-flex size-full rounded-full bg-st-delivered-from opacity-75 motion-safe:animate-ping" />
        <span className="relative inline-flex size-2 rounded-full bg-st-delivered-from" />
      </span>
      {label}
    </span>
  );
}
