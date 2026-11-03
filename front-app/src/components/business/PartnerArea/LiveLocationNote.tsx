import { LoaderCircle, MapPinOff, Navigation, RotateCw } from 'lucide-react';

import { Button } from '@/components/common/Button';
import { useLiveLocationSharing, type LiveLocationState } from '@/context/LiveLocationContext';
import { useNow } from '@/hooks/useNow';
import { cn } from '@/utils/classNames';

type Tone = 'live' | 'wait' | 'warn';

const NOTES: Record<
  Exclude<LiveLocationState, 'off'>,
  { tone: Tone; title: string; text: string; canRetry?: boolean }
> = {
  starting: {
    tone: 'wait',
    title: 'Finding your location',
    text: 'Allow location access when your browser asks: the customer then sees you on their map.',
  },
  sharing: {
    tone: 'live',
    title: 'Sharing your live location with the customer',
    text: 'Only while you are on the way: it stops by itself once the food is delivered.',
  },
  retrying: {
    tone: 'warn',
    title: 'Your location could not be sent',
    text: 'Check your mobile data. It is sent again with your next position.',
  },
  denied: {
    tone: 'warn',
    title: 'Location access is blocked',
    text: 'The customer cannot see you on the map. Allow location for this site in your browser settings, then try again.',
    canRetry: true,
  },
  unavailable: {
    tone: 'warn',
    title: 'Your phone cannot find your position',
    text: 'It keeps trying. A clear view of the sky usually helps the GPS.',
    canRetry: true,
  },
  unsupported: {
    tone: 'warn',
    title: 'This browser cannot share your location',
    text: 'The customer still sees each step you take: picked up, delivered.',
  },
  insecure: {
    tone: 'warn',
    title: 'Location needs a secure (https) connection',
    text: 'Browsers share a position only with secure pages. The customer still sees each step you take.',
  },
};

const TONE_STYLES: Record<Tone, string> = {
  live: 'bg-brand-50 text-brand-text-strong',
  wait: 'bg-page text-ink',
  warn: 'bg-warning-50 text-warning-text ring-1 ring-warning-500/40',
};

/** 'just now', '12 s ago', '3 min ago' */
function secondsAgo(seconds: number): string {
  if (seconds < 5) {
    return 'just now';
  }
  return seconds < 60 ? `${seconds} s ago` : `${Math.round(seconds / 60)} min ago`;
}

/**
 * Under a delivery on its way: whether the partner's live position reaches the customer, and what to do when it
 * does not. Nothing for another delivery or while nothing is shared.
 */
export function LiveLocationNote({ deliveryId }: { deliveryId: number }) {
  const sharing = useLiveLocationSharing();
  const now = useNow(5_000);
  if (sharing.state === 'off' || sharing.deliveryId !== deliveryId) {
    return null;
  }
  const { tone, title, text, canRetry } = NOTES[sharing.state];
  const Icon = tone === 'live' ? Navigation : tone === 'wait' ? LoaderCircle : MapPinOff;
  const sentAgo =
    sharing.state === 'sharing' && sharing.lastSentAt !== null
      ? Math.max(0, Math.round((now - sharing.lastSentAt) / 1000))
      : null;

  return (
    <div
      role="status"
      className={cn('flex items-start gap-3 rounded-2xl px-4 py-3', TONE_STYLES[tone])}
    >
      <span aria-hidden="true" className="relative mt-0.5 flex size-4 shrink-0">
        {tone === 'live' && (
          <span className="absolute inset-0 rounded-full bg-brand-500/40 motion-safe:animate-ping" />
        )}
        <Icon className={cn('relative size-4', tone === 'wait' && 'motion-safe:animate-spin')} />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="text-sm font-bold">{title}</p>
        <p className="text-sm">
          {sentAgo !== null && `Sent ${secondsAgo(sentAgo)}. `}
          {text}
        </p>
        {canRetry && (
          <Button
            variant="outline"
            size="sm"
            className="mt-1 self-start"
            leftIcon={<RotateCw aria-hidden="true" className="size-4" />}
            onClick={sharing.retry}
          >
            Try again
          </Button>
        )}
      </div>
    </div>
  );
}
