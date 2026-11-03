import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';

import { accessTokenFor } from '@/api/sessionRefresh';
import { useActingUser } from '@/hooks/useActingUser';
import { LiveStatusContext } from '@/realtime/liveStatusContext';
import { InvalidationBatcher } from '@/realtime/invalidationBatcher';
import { LiveConnection, type LiveStatus } from '@/realtime/liveConnection';
import { queryKeysFor } from '@/realtime/liveInvalidation';
import { liveUrl } from '@/realtime/liveUrl';
import { useToast, type ToastTone } from '@/context/ToastContext';
import type { LiveEvent } from '@/types/live';
import { ORDER_STATUS_LABELS } from '@/utils/constants';
import { env } from '@/utils/env';
import { areaOfRole } from '@/utils/sessionUtils';
import type { OrderStatus } from '@/types/order';

/**
 * Keeps one live connection open for whoever the application acts as (the person signed in to the open page's area,
 * with that area's access token) and turns what arrives into fresh data on screen, with no page refresh: events say
 * which data changed, and that data is read again through the REST APIs. It reconnects by itself (see
 * LiveConnection) and, when the acting user changes, closes the old connection and opens the new user's.
 *
 * Off when the environment turns the feature off (VITE_FEATURE_LIVE_UPDATES=false): every page then works as
 * before, refreshing by its own timers.
 */
export function LiveUpdatesProvider({ children }: { children: ReactNode }) {
  const user = useActingUser();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [status, setStatus] = useState<LiveStatus>('closed');
  // The latest toast function without restarting the connection when it changes
  const toast = useRef(showToast);
  useEffect(() => {
    toast.current = showToast;
  }, [showToast]);

  const userId = user?.id;
  const userRole = user?.role;
  useEffect(() => {
    if (!env.features.liveUpdates || userId === undefined || userRole === undefined) {
      return;
    }
    const acting = { id: userId, role: userRole };
    const batcher = new InvalidationBatcher(queryClient);
    const connection = new LiveConnection({
      // The access token of the open area (renewed first when it is about to expire); none: try again later
      url: async () => {
        const token = await accessTokenFor(areaOfRole(acting.role));
        return token === null ? null : liveUrl(env.apiBaseUrl, token);
      },
      onStatus: setStatus,
      onEvent: (event) => {
        batcher.add(queryKeysFor(event, acting));
        // Each kind of user is told what concerns them, in their own words; the admins' dashboard just updates
        const show = (message: string, tone?: ToastTone) => toast.current(message, tone);
        switch (acting.role) {
          case 'CUSTOMER':
            announceToCustomer(event, show);
            break;
          case 'RESTAURANT_ADMIN':
            announceToOwner(event, show);
            break;
          case 'DELIVERY_PARTNER':
            announceToPartner(event, show);
            break;
          case 'PLATFORM_ADMIN':
            break;
        }
      },
      onResync: () => batcher.addEverything(),
    });
    connection.start();
    return () => {
      connection.stop();
      batcher.cancel();
      setStatus('closed');
    };
  }, [userId, userRole, queryClient]);

  return <LiveStatusContext.Provider value={status}>{children}</LiveStatusContext.Provider>;
}

/**
 * A delivery partner is told at once about a new offer (it has to be accepted within minutes) and about a delivery
 * of theirs that was cancelled; their page also updates.
 */
function announceToPartner(
  event: LiveEvent,
  show: (message: string, tone?: ToastTone) => void,
): void {
  if (event.type !== 'DELIVERY_STATUS_CHANGED') {
    return;
  }
  const order = event.orderId === undefined ? 'The order' : `Order #${event.orderId}`;
  if (event.status === 'ASSIGNED') {
    show(`New delivery offer (${order.toLowerCase()}): accept it soon.`, 'info');
  } else if (event.status === 'CANCELLED') {
    show(`${order} was cancelled: no need to deliver it.`, 'error');
  }
}

/**
 * A restaurant owner is told only what they did not do themselves: a new order for the kitchen (cash on delivery, or
 * paid online) and an order cancelled by the customer or the platform. Their own steps (accept, reject, preparing,
 * ready) already have their own message where they pressed the button, and the rider's steps just update the page.
 */
function announceToOwner(
  event: LiveEvent,
  show: (message: string, tone?: ToastTone) => void,
): void {
  if (event.type !== 'ORDER_STATUS_CHANGED' || event.orderId === undefined) {
    return;
  }
  if (event.status === 'PLACED') {
    show(`New order #${event.orderId}: accept it or reject it.`, 'info');
  } else if (event.status === 'CANCELLED') {
    show(`Order #${event.orderId} was cancelled.`, 'error');
  }
}

/** A customer is told, in a few words, that their order moved (the page they are on also updates). */
function announceToCustomer(event: LiveEvent, show: (message: string) => void): void {
  if (event.type !== 'ORDER_STATUS_CHANGED' || event.orderId === undefined || !event.status) {
    return;
  }
  const label = ORDER_STATUS_LABELS[event.status as OrderStatus];
  if (label) {
    show(`Order #${event.orderId}: ${label}`);
  }
}
