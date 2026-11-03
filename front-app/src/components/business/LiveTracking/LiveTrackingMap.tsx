import 'leaflet/dist/leaflet.css';

import L from 'leaflet';
import { Bike, FlaskConical, House, LocateFixed, MapPinned, Store, WifiOff } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Circle, MapContainer, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet';

import { MapHtmlMarker } from '@/components/business/LiveTracking/MapHtmlMarker';
import { LIVE_TRACKING_REFRESH_MS } from '@/hooks/api/myOrderHooks';
import { useAnimatedPoint } from '@/hooks/useAnimatedPoint';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import type { GeoPoint, LiveTracking } from '@/types/liveTracking';
import { cn } from '@/utils/classNames';
import { env } from '@/utils/env';
import { framingPoints, isSimulated, mapPoints } from '@/utils/liveTrackingUtils';

/** The theme's greens (index.css: brand-600, brand-500): a map line needs a colour value, not a class */
const ROUTE_COLOR = '#1d864c';
const NEXT_LEG_COLOR = '#39c576';
/** Room around the route when the map frames it (px): the markers' labels stay inside */
const FIT_PADDING = 72;
/** Closer than this the tiles add nothing (and OpenStreetMap's standard tiles stop at 19) */
const MAX_ZOOM = 18;
/** A GPS position less exact than this (metres) is drawn with its uncertainty around it */
const SHOW_ACCURACY_FROM_METERS = 30;

/**
 * The live map of a delivery on OpenStreetMap tiles (Leaflet, no key): the restaurant and the customer's home (when
 * their addresses have map coordinates) and the partner's real GPS position from their phone, gliding between
 * updates. Nothing is invented: no pin without coordinates, and no route line for a real ride (there is no routing
 * service); only the demo simulation draws one, labelled as simulated. The map follows the partner until the customer
 * moves it themselves; "Re-center" brings it back. Render it only when mapPoints(tracking) is not empty.
 */
export function LiveTrackingMap({ tracking }: { tracking: LiveTracking }) {
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
  const [following, setFollowing] = useState(true);
  // Bumped by "Re-center": the camera frames the route again even if nothing else changed
  const [recenterCount, setRecenterCount] = useState(0);
  const [tilesFailing, setTilesFailing] = useState(false);
  const rider = useAnimatedPoint(
    tracking.rider?.location,
    LIVE_TRACKING_REFRESH_MS,
    !reducedMotion,
  );

  const toRestaurant = tracking.phase === 'TO_RESTAURANT';
  const delivered = tracking.phase === 'DELIVERED';
  const start = mapPoints(tracking)[0];
  if (!start) {
    return null;
  }
  const pathToRestaurant = tracking.pathToRestaurant ?? [];
  const pathToCustomer = tracking.pathToCustomer ?? [];
  const accuracy = tracking.rider?.accuracyMeters ?? null;
  const firstName = tracking.partner.name.split(' ')[0];

  return (
    // `isolate`: Leaflet's own layers (z-index 400 to 1000) stay inside the map, under the page's sticky header
    <div className="tracking-map relative isolate size-full">
      <MapContainer
        center={start}
        zoom={14}
        maxZoom={MAX_ZOOM}
        // The mouse wheel scrolls the page; the + / - buttons, a double click or two fingers zoom the map
        scrollWheelZoom={false}
        className="size-full"
      >
        <TileLayer
          url={env.map.tileUrl}
          attribution={env.map.attribution}
          maxZoom={MAX_ZOOM}
          eventHandlers={{
            tileerror: () => setTilesFailing(true),
            tileload: () => setTilesFailing(false),
          }}
        />

        {/* Simulation only: the next part of the trip, dashed, while the partner still rides to the restaurant */}
        {toRestaurant && pathToCustomer.length > 1 && (
          <Polyline
            positions={pathToCustomer}
            pathOptions={{
              color: NEXT_LEG_COLOR,
              weight: 4,
              opacity: 0.9,
              dashArray: '2 10',
              lineCap: 'round',
            }}
          />
        )}
        <RouteLine path={toRestaurant ? pathToRestaurant : pathToCustomer} />

        {tracking.restaurant.location && (
          <MapHtmlMarker position={tracking.restaurant.location} title={tracking.restaurant.name}>
            <PlaceMarker icon={<Store aria-hidden="true" className="size-4" />}>
              {tracking.restaurant.name}
            </PlaceMarker>
          </MapHtmlMarker>
        )}
        {tracking.dropoff.location && (
          <MapHtmlMarker position={tracking.dropoff.location} title="Your delivery address">
            <PlaceMarker icon={<House aria-hidden="true" className="size-4" />} highlight>
              You
            </PlaceMarker>
          </MapHtmlMarker>
        )}
        {/* How exact the phone's GPS is: a soft circle around the partner when it is not very exact */}
        {rider && !delivered && accuracy !== null && accuracy >= SHOW_ACCURACY_FROM_METERS && (
          <Circle
            center={rider}
            radius={accuracy}
            pathOptions={{ color: ROUTE_COLOR, weight: 1, opacity: 0.4, fillOpacity: 0.08 }}
          />
        )}
        {rider && !delivered && (
          <MapHtmlMarker
            position={rider}
            title={`${tracking.partner.name}, your delivery partner`}
            anchor="center"
            zIndexOffset={1000}
          >
            <RiderMarker headingDeg={tracking.rider?.headingDeg ?? null} pulse={!reducedMotion} />
          </MapHtmlMarker>
        )}

        <CameraFollower
          tracking={tracking}
          following={following}
          recenterCount={recenterCount}
          animate={!reducedMotion}
        />
        <DragWatcher onDrag={() => setFollowing(false)} />
      </MapContainer>

      {isSimulated(tracking) && (
        <MapBadge className="top-3 right-3">
          <FlaskConical aria-hidden="true" className="size-3.5" />
          Simulated ride
        </MapBadge>
      )}
      {!tracking.rider && !delivered && (
        <MapBadge className="top-3 right-3">
          <MapPinned aria-hidden="true" className="size-3.5" />
          Waiting for {firstName}’s location
        </MapBadge>
      )}
      {tilesFailing && (
        <MapBadge className="top-3 left-1/2 -translate-x-1/2" tone="warning">
          <WifiOff aria-hidden="true" className="size-3.5" />
          Map tiles are not loading
        </MapBadge>
      )}

      {!following && (
        <button
          type="button"
          onClick={() => {
            setFollowing(true);
            setRecenterCount((count) => count + 1);
          }}
          className="absolute bottom-4 left-4 z-[1100] flex h-10 items-center gap-2 rounded-full bg-surface px-4 text-sm font-semibold text-ink shadow-lg ring-1 ring-line hover:text-brand-text focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
        >
          <LocateFixed aria-hidden="true" className="size-4" />
          Re-center
        </button>
      )}
    </div>
  );
}

/** The road ahead: a green line on a white edge, so it stands out on any street colour. */
function RouteLine({ path }: { path: GeoPoint[] }) {
  if (path.length < 2) {
    return null;
  }
  return (
    <>
      <Polyline positions={path} pathOptions={{ color: '#ffffff', weight: 9, opacity: 0.9 }} />
      <Polyline positions={path} pathOptions={{ color: ROUTE_COLOR, weight: 5, opacity: 1 }} />
    </>
  );
}

/** Stops following the partner once the customer moves the map themselves. */
function DragWatcher({ onDrag }: { onDrag: () => void }) {
  useMapEvents({ dragstart: onDrag });
  return null;
}

/**
 * Keeps the partner in view while the customer has not moved the map: frames the partner and where they ride to
 * whenever the trip enters a new part (and on "Re-center"), and pans along when the partner leaves the view.
 */
function CameraFollower({
  tracking,
  following,
  recenterCount,
  animate,
}: {
  tracking: LiveTracking;
  following: boolean;
  recenterCount: number;
  animate: boolean;
}) {
  const map = useMap();
  const framed = useRef<string | null>(null);
  const { phase } = tracking;
  const rider = tracking.rider?.location;

  useEffect(() => {
    // A map without a size yet (hidden, or not laid out) cannot frame anything
    if (!following || map.getSize().x === 0) {
      return;
    }
    // Framed again when the trip enters a new part, on "Re-center", and when the first position arrives
    const frameKey = `${phase}-${recenterCount}-${rider ? 'rider' : 'places'}`;
    if (framed.current !== frameKey) {
      framed.current = frameKey;
      map.fitBounds(L.latLngBounds(framingPoints(tracking)), {
        padding: [FIT_PADDING, FIT_PADDING],
        maxZoom: 16,
        animate,
      });
      return;
    }
    if (rider && phase !== 'DELIVERED' && !map.getBounds().contains(rider)) {
      map.panTo(rider, { animate });
    }
  }, [map, following, recenterCount, phase, rider, tracking, animate]);

  return null;
}

/** A small label on the map (above Leaflet's own layers). */
function MapBadge({
  className,
  tone = 'neutral',
  children,
}: {
  className: string;
  tone?: 'neutral' | 'warning';
  children: ReactNode;
}) {
  return (
    <p
      className={cn(
        'pointer-events-none absolute z-[1100] flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-bold shadow-lg ring-1',
        tone === 'warning'
          ? 'bg-warning-100 text-warning-ink ring-warning-500/40'
          : 'bg-surface/95 text-ink ring-line',
        className,
      )}
    >
      {children}
    </p>
  );
}

/**
 * The partner: a green disc with the vehicle, an arrow pointing the way they ride (only when the phone knows the
 * direction: it does not while standing still), a soft pulse while live.
 */
function RiderMarker({ headingDeg, pulse }: { headingDeg: number | null; pulse: boolean }) {
  return (
    <span className="relative flex size-12 items-center justify-center">
      {pulse && (
        <span
          aria-hidden="true"
          className="absolute inset-0 animate-ping rounded-full bg-brand-500/30"
        />
      )}
      {/* The arrow turns around the disc, so it always points the way of travel */}
      {headingDeg !== null && (
        <span
          aria-hidden="true"
          className="absolute inset-0 transition-transform duration-700"
          style={{ transform: `rotate(${headingDeg}deg)` }}
        >
          <span className="absolute -top-1.5 left-1/2 size-0 -translate-x-1/2 border-x-[7px] border-b-[9px] border-x-transparent border-b-brand-700" />
        </span>
      )}
      <span className="relative flex size-10 items-center justify-center rounded-full bg-brand-500 text-on-brand shadow-lg ring-[3px] ring-white">
        <Bike aria-hidden="true" className="size-5" />
      </span>
    </span>
  );
}

/** A labelled pin: the restaurant, and the customer's home (in the brand colour: it is where the food goes). */
function PlaceMarker({
  icon,
  highlight = false,
  children,
}: {
  icon: ReactNode;
  highlight?: boolean;
  children: ReactNode;
}) {
  return (
    <span className="flex flex-col items-center">
      <span
        className={cn(
          'flex items-center gap-1.5 rounded-full py-1.5 pr-3 pl-2 text-xs font-bold whitespace-nowrap shadow-lg',
          highlight ? 'bg-brand-500 text-on-brand' : 'bg-surface text-ink ring-1 ring-line',
        )}
      >
        <span
          className={cn(
            'flex size-6 items-center justify-center rounded-full',
            highlight ? 'bg-white/20' : 'bg-brand-50 text-brand-text-strong',
          )}
        >
          {icon}
        </span>
        {children}
      </span>
      <span
        aria-hidden="true"
        className={cn(
          'size-0 border-x-[6px] border-t-[7px] border-x-transparent',
          highlight ? 'border-t-brand-700' : 'border-t-surface',
        )}
      />
    </span>
  );
}
