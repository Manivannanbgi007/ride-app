import { useId } from 'react';

import { cn } from '@/utils/classNames';

/*
 * The delivery partner's illustrations: small scenes drawn in SVG with the application's own colours, so they follow
 * the theme and need no image files. They are decoration only (aria-hidden): the text next to them says everything.
 * Movement (clouds, road, rider, pins, stars) is CSS and switches off for people who asked for less motion.
 */

export type SceneMood = 'day' | 'busy' | 'night' | 'alert';

interface SceneProps {
  className?: string;
}

/** Rows of windows on a building: [x, y] of each lit or dark window. */
function windows(
  x: number,
  y: number,
  columns: number,
  rows: number,
  step = 14,
): [number, number][] {
  const cells: [number, number][] = [];
  for (let row = 0; row < rows; row += 1) {
    for (let column = 0; column < columns; column += 1) {
      cells.push([x + column * step, y + row * step]);
    }
  }
  return cells;
}

/** [x, width, height, lit windows?] of the buildings, left to right. */
const FAR_BUILDINGS: [number, number, number][] = [
  [0, 54, 74],
  [60, 40, 98],
  [106, 62, 62],
  [176, 44, 88],
  [226, 58, 70],
  [292, 38, 104],
  [336, 56, 66],
  [398, 46, 90],
  [450, 60, 60],
  [516, 42, 96],
  [564, 58, 72],
  [628, 44, 86],
  [678, 62, 64],
  [746, 54, 92],
];

const NEAR_BUILDINGS: [number, number, number, number, number][] = [
  // x, width, height, window columns, window rows
  [14, 64, 54, 3, 2],
  [120, 52, 70, 2, 3],
  [262, 70, 48, 4, 2],
  [596, 58, 66, 3, 3],
  [700, 70, 52, 4, 2],
];

/** The scooter, with its rider unless parked. Drawn with its ground line at y = 0 and its rear wheel at x = 0. */
function Scooter({ parked, lit }: { parked: boolean; lit: boolean }) {
  return (
    <g>
      {/* Shadow on the road */}
      <ellipse cx="30" cy="2" rx="48" ry="4" className="fill-black/25" />
      {/* Wind lines behind a moving scooter */}
      {!parked && (
        <g className="stroke-white/70" strokeWidth="3" strokeLinecap="round">
          <line x1="-18" y1="-26" x2="-46" y2="-26" className="motion-safe:animate-scene-wind" />
          <line
            x1="-14"
            y1="-40"
            x2="-52"
            y2="-40"
            className="motion-safe:animate-scene-wind motion-safe:[animation-delay:250ms]"
          />
          <line
            x1="-18"
            y1="-12"
            x2="-38"
            y2="-12"
            className="motion-safe:animate-scene-wind motion-safe:[animation-delay:500ms]"
          />
        </g>
      )}
      <g className={cn(!parked && 'motion-safe:animate-scene-bob')}>
        {/* Delivery box on the back */}
        <g>
          <rect x="-12" y="-86" width="34" height="32" rx="6" className="fill-sunset-500" />
          <rect x="-12" y="-86" width="34" height="9" rx="4" className="fill-sunset-600" />
          <rect x="-1" y="-72" width="12" height="3" rx="1.5" className="fill-white/80" />
          <rect x="3" y="-66" width="5" height="3" rx="1.5" className="fill-white/60" />
        </g>
        {/* Body */}
        <path
          d="M-8 -28 C-8 -46 12 -52 30 -48 L44 -45 L46 -28 Z"
          className="fill-white"
          strokeLinejoin="round"
        />
        <rect x="2" y="-52" width="32" height="8" rx="4" className="fill-brand-900" />
        {/* Floorboard and front shield */}
        <rect x="40" y="-28" width="22" height="7" rx="3.5" className="fill-white" />
        <path
          d="M54 -24 C54 -44 56 -58 60 -66 L69 -64 C67 -52 68 -38 70 -24 Z"
          className="fill-white"
        />
        {/* Handlebar and headlight */}
        <line
          x1="58"
          y1="-66"
          x2="54"
          y2="-80"
          className="stroke-brand-900"
          strokeWidth="4"
          strokeLinecap="round"
        />
        <line
          x1="46"
          y1="-80"
          x2="62"
          y2="-80"
          className="stroke-brand-900"
          strokeWidth="4"
          strokeLinecap="round"
        />
        <circle cx="72" cy="-58" r="5" className={lit ? 'fill-[#ffe49a]' : 'fill-white/70'} />
        {lit && <circle cx="72" cy="-58" r="10" className="fill-[#ffe49a]/30" />}
        {/* The rider */}
        {!parked && (
          <g>
            <path
              d="M14 -50 C12 -66 20 -80 34 -86 L44 -84 C40 -72 38 -62 34 -50 Z"
              className="fill-lime-500"
            />
            <path
              d="M18 -50 L40 -44 L46 -26 L36 -24 L32 -38 L14 -42 Z"
              className="fill-brand-900"
            />
            <path
              d="M40 -82 C46 -76 52 -70 56 -66"
              className="stroke-lime-500"
              strokeWidth="7"
              strokeLinecap="round"
              fill="none"
            />
            <circle cx="56" cy="-66" r="4" className="fill-white" />
            <circle cx="41" cy="-96" r="11" className="fill-white" />
            <path d="M43 -100 C52 -100 54 -94 52 -90 L43 -90 Z" className="fill-brand-900/80" />
            <circle
              cx="41"
              cy="-96"
              r="11"
              className="fill-none stroke-brand-600/40"
              strokeWidth="1.5"
            />
          </g>
        )}
      </g>
      {/* Wheels */}
      <g>
        <circle cx="4" cy="-13" r="13" className="fill-brand-900" />
        <circle cx="4" cy="-13" r="6" className="fill-white/85" />
        <circle cx="4" cy="-13" r="2" className="fill-brand-900" />
        <circle cx="62" cy="-13" r="13" className="fill-brand-900" />
        <circle cx="62" cy="-13" r="6" className="fill-white/85" />
        <circle cx="62" cy="-13" r="2" className="fill-brand-900" />
      </g>
    </g>
  );
}

/** A map pin. Its tip is at (0, 0). */
function Pin({ className, size = 1 }: { className?: string; size?: number }) {
  return (
    <g transform={`scale(${size})`}>
      <path
        d="M0 0 C-10 -14 -16 -20 -16 -30 A16 16 0 1 1 16 -30 C16 -20 10 -14 0 0 Z"
        className={className}
      />
      <circle cx="0" cy="-30" r="6.5" className="fill-white" />
    </g>
  );
}

const STARS: [number, number, number][] = [
  [70, 22, 0],
  [150, 44, 600],
  [248, 18, 1200],
  [330, 40, 300],
  [468, 24, 900],
  [610, 16, 1500],
  [690, 46, 200],
  [752, 20, 1100],
];

/**
 * The hero's picture: a city skyline with a delivery rider on the road (day), the same ride with a destination pin
 * (busy), or a night street with the scooter parked and a moon (night) or a warning sign (alert). Drawn in white on
 * the hero's own colour, it fills the width of its box and is cropped at the sides on a narrow screen.
 */
export function CityScene({ mood, className }: SceneProps & { mood: SceneMood }) {
  const night = mood === 'night' || mood === 'alert';
  const moving = mood === 'day' || mood === 'busy';
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 800 170"
      preserveAspectRatio="xMidYMax slice"
      className={cn('pointer-events-none', className)}
    >
      {/* Sky: sun or moon, clouds or stars */}
      {night ? (
        <g>
          <circle cx="612" cy="52" r="48" className="fill-white/5" />
          <path d="M630 30 A26 26 0 1 0 630 74 A20 20 0 1 1 630 30 Z" className="fill-[#ffe49a]" />
          {STARS.map(([x, y, delay]) => (
            <circle
              key={x}
              cx={x}
              cy={y}
              r="2"
              className="fill-white motion-safe:animate-scene-twinkle"
              style={{ animationDelay: `${delay}ms` }}
            />
          ))}
        </g>
      ) : (
        <g>
          <circle cx="664" cy="48" r="42" className="fill-white/10" />
          <circle cx="664" cy="48" r="26" className="fill-[#ffe49a]/90" />
          <g className="fill-white/30 motion-safe:animate-scene-drift">
            <ellipse cx="120" cy="40" rx="38" ry="11" />
            <ellipse cx="146" cy="32" rx="26" ry="11" />
            <ellipse cx="96" cy="34" rx="22" ry="9" />
          </g>
          <g className="fill-white/20 [animation-direction:alternate-reverse] motion-safe:animate-scene-drift">
            <ellipse cx="430" cy="26" rx="34" ry="9" />
            <ellipse cx="452" cy="19" rx="22" ry="9" />
          </g>
        </g>
      )}

      {/* Far skyline */}
      <g className="fill-white/10">
        {FAR_BUILDINGS.map(([x, width, height]) => (
          <rect key={x} x={x} y={134 - height} width={width} height={height} rx="3" />
        ))}
      </g>
      {/* Near skyline with windows */}
      {NEAR_BUILDINGS.map(([x, width, height, columns, rows]) => (
        <g key={x}>
          <rect
            x={x}
            y={140 - height}
            width={width}
            height={height}
            rx="4"
            className="fill-white/15"
          />
          {windows(x + 10, 140 - height + 10, columns, rows).map(([wx, wy], index) => (
            <rect
              key={`${wx}-${wy}`}
              x={wx}
              y={wy}
              width="7"
              height="8"
              rx="1.5"
              className={night && index % 3 !== 1 ? 'fill-[#ffe49a]/90' : 'fill-white/25'}
            />
          ))}
        </g>
      ))}

      {/* Trees */}
      <g className="fill-lime-500/45">
        <circle cx="96" cy="124" r="13" />
        <circle cx="110" cy="128" r="10" />
        <circle cx="560" cy="122" r="14" />
        <circle cx="576" cy="127" r="10" />
        <circle cx="772" cy="124" r="12" />
      </g>

      {/* Road */}
      <rect x="0" y="136" width="800" height="40" className="fill-black/25" />
      <rect x="0" y="136" width="800" height="3" className="fill-white/25" />
      <line
        x1="0"
        y1="158"
        x2="800"
        y2="158"
        className="stroke-white/70 motion-safe:animate-scene-road"
        strokeWidth="3"
        strokeDasharray="26 18"
        strokeLinecap="round"
      />

      {/* Street lamp (night) */}
      {night && (
        <g>
          <rect x="312" y="74" width="4" height="64" rx="2" className="fill-white/60" />
          <path
            d="M314 76 H340"
            className="stroke-white/60"
            strokeWidth="4"
            strokeLinecap="round"
          />
          <circle cx="340" cy="82" r="30" className="fill-[#ffe49a]/15" />
          <circle cx="340" cy="80" r="6" className="fill-[#ffe49a]" />
        </g>
      )}

      {/* Route and destination pin */}
      {(mood === 'day' || mood === 'busy') && (
        <g>
          <path
            d="M512 120 C560 108 600 96 640 88"
            fill="none"
            className="stroke-white/70"
            strokeWidth="3"
            strokeDasharray="2 9"
            strokeLinecap="round"
          />
          <g transform="translate(656 88)">
            <ellipse cx="0" cy="2" rx="12" ry="4" className="fill-black/25" />
            <g className="motion-safe:animate-scene-float">
              <Pin className="fill-sunset-500" size={1.05} />
            </g>
          </g>
        </g>
      )}

      {/* Warning sign (alert) */}
      {mood === 'alert' && (
        <g transform="translate(560 138)">
          <path d="M0 -62 L30 -8 H-30 Z" className="fill-[#ffe49a]" strokeLinejoin="round" />
          <rect x="-3" y="-44" width="6" height="22" rx="3" className="fill-[#1c2c20]" />
          <circle cx="0" cy="-14" r="3.6" className="fill-[#1c2c20]" />
          <rect x="-3" y="-8" width="6" height="8" className="fill-white/60" />
        </g>
      )}

      {/* The scooter */}
      <g transform={moving ? 'translate(404 150)' : 'translate(404 150)'}>
        <Scooter parked={!moving} lit={night} />
      </g>

      {/* Sleeping scooter */}
      {mood === 'night' && (
        <g className="fill-white font-extrabold" fontSize="20">
          <text x="480" y="84" className="motion-safe:animate-scene-zzz">
            z
          </text>
          <text
            x="498"
            y="68"
            fontSize="26"
            className="motion-safe:animate-scene-zzz motion-safe:[animation-delay:900ms]"
          >
            z
          </text>
          <text
            x="520"
            y="48"
            fontSize="32"
            className="motion-safe:animate-scene-zzz motion-safe:[animation-delay:1800ms]"
          >
            Z
          </text>
        </g>
      )}
    </svg>
  );
}

/**
 * "Waiting for a delivery": a street map of the partner's city with a radar sweeping from their position and
 * restaurants appearing as pins. A banner at the top of the waiting panel.
 */
export function RadarMap({ className }: SceneProps) {
  const id = useId();
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 400 200"
      preserveAspectRatio="xMidYMid slice"
      className={cn('pointer-events-none', className)}
    >
      <defs>
        <linearGradient
          id={`${id}-sweep`}
          gradientUnits="userSpaceOnUse"
          x1="229"
          y1="19"
          x2="286"
          y2="100"
          className="text-brand-500"
        >
          <stop offset="0" stopColor="currentColor" stopOpacity="0" />
          <stop offset="1" stopColor="currentColor" stopOpacity="0.5" />
        </linearGradient>
      </defs>
      <rect width="400" height="200" className="fill-brand-50" />
      {/* City blocks */}
      <g className="fill-surface">
        <rect x="10" y="12" width="84" height="52" rx="8" />
        <rect x="108" y="12" width="70" height="52" rx="8" />
        <rect x="236" y="12" width="76" height="52" rx="8" />
        <rect x="326" y="12" width="68" height="52" rx="8" />
        <rect x="10" y="78" width="84" height="44" rx="8" />
        <rect x="108" y="78" width="70" height="44" rx="8" />
        <rect x="236" y="78" width="76" height="44" rx="8" />
        <rect x="326" y="78" width="68" height="44" rx="8" />
        <rect x="10" y="136" width="84" height="54" rx="8" />
        <rect x="108" y="136" width="70" height="54" rx="8" />
        <rect x="236" y="136" width="76" height="54" rx="8" />
        <rect x="326" y="136" width="68" height="54" rx="8" />
      </g>
      {/* A park and a river */}
      <rect x="108" y="78" width="70" height="44" rx="8" className="fill-brand-200/70" />
      <circle cx="128" cy="98" r="7" className="fill-brand-400/70" />
      <circle cx="148" cy="106" r="9" className="fill-brand-400/70" />
      <circle cx="162" cy="92" r="6" className="fill-brand-400/70" />
      <path
        d="M-10 126 C60 116 120 140 200 128 S340 108 410 120"
        fill="none"
        className="stroke-info-100"
        strokeWidth="9"
        strokeLinecap="round"
      />

      {/* Radar around the partner (the centre of the map) */}
      <g className="text-brand-500">
        <circle
          cx="200"
          cy="100"
          r="86"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.2"
          strokeWidth="1.5"
        />
        <circle
          cx="200"
          cy="100"
          r="58"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.3"
          strokeWidth="1.5"
        />
        <circle cx="200" cy="100" r="30" fill="currentColor" fillOpacity="0.12" />
        <g style={{ transformOrigin: '200px 100px' }} className="motion-safe:animate-scene-sweep">
          <path d="M200 100 L286 100 A86 86 0 0 0 229.4 19.2 Z" fill={`url(#${id}-sweep)`} />
        </g>
      </g>

      {/* Restaurants with food ready */}
      <g transform="translate(112 66)">
        <g className="motion-safe:animate-scene-float">
          <Pin className="fill-sunset-500" size={0.7} />
        </g>
      </g>
      <g transform="translate(290 70)">
        <g className="motion-safe:animate-scene-float motion-safe:[animation-delay:800ms]">
          <Pin className="fill-brand-600" size={0.6} />
        </g>
      </g>
      <g transform="translate(262 162)">
        <g className="motion-safe:animate-scene-float motion-safe:[animation-delay:1500ms]">
          <Pin className="fill-sunset-500" size={0.55} />
        </g>
      </g>
    </svg>
  );
}

/** "No delivery right now": a quiet night street with a parked scooter. A banner at the top of the offline panel. */
export function NightStreet({ className }: SceneProps) {
  return (
    <div
      className={cn('overflow-hidden bg-linear-to-b from-strip to-[#1c2c20] text-white', className)}
    >
      <CityScene mood="night" className="absolute inset-x-0 bottom-0 h-full w-full" />
    </div>
  );
}

/** "One step before your first delivery": a pin, the road and a clipboard with the checklist. */
export function SetupScene({ className }: SceneProps) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 400 180"
      preserveAspectRatio="xMidYMid meet"
      className={cn('pointer-events-none', className)}
    >
      <rect width="400" height="180" className="fill-warning-100" />
      <circle cx="330" cy="40" r="70" className="fill-white/30" />
      <circle cx="60" cy="150" r="60" className="fill-white/25" />
      {/* Clipboard */}
      <g transform="translate(92 22) rotate(-6 60 70)">
        <rect width="120" height="144" rx="14" className="fill-surface" />
        <rect x="38" y="-8" width="44" height="20" rx="8" className="fill-brand-600" />
        <circle cx="60" cy="2" r="4" className="fill-white" />
        {[34, 62, 90, 118].map((y, index) => (
          <g key={y}>
            <circle
              cx="24"
              cy={y}
              r="9"
              className={index === 0 ? 'fill-brand-500' : 'fill-page stroke-line'}
              strokeWidth="2"
            />
            {index === 0 && (
              <path
                d="M19.5 34 L23 37.5 L29 30.5"
                fill="none"
                className="stroke-white"
                strokeWidth="3"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            )}
            <rect
              x="42"
              y={y - 5}
              width={index === 0 ? 56 : 62}
              height="10"
              rx="5"
              className={index === 0 ? 'fill-brand-200' : 'fill-line'}
            />
          </g>
        ))}
      </g>
      {/* Pin with its shadow */}
      <ellipse cx="290" cy="150" rx="26" ry="6" className="fill-black/15" />
      <g transform="translate(290 146)">
        <g className="motion-safe:animate-scene-float">
          <Pin className="fill-sunset-500" size={2} />
        </g>
      </g>
      {/* A small scooter below the clipboard */}
      <g transform="translate(206 168) scale(0.6)">
        <Scooter parked lit={false} />
      </g>
      <line x1="0" y1="168" x2="400" y2="168" className="stroke-warning-ink/20" strokeWidth="3" />
    </svg>
  );
}

/** "No deliveries while suspended": a shield with a pause mark. */
export function ShieldScene({ className }: SceneProps) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 400 180"
      preserveAspectRatio="xMidYMid meet"
      className={cn('pointer-events-none', className)}
    >
      <rect width="400" height="180" className="fill-danger-50" />
      <circle cx="200" cy="90" r="76" className="fill-white/50" />
      <circle cx="200" cy="90" r="54" className="fill-white/60" />
      <g className="motion-safe:animate-scene-float">
        <path
          d="M200 22 L252 42 V86 C252 118 230 142 200 156 C170 142 148 118 148 86 V42 Z"
          className="fill-danger-500"
        />
        <path d="M200 22 L252 42 V86 C252 118 230 142 200 156 Z" className="fill-ruby-600" />
        <rect x="183" y="64" width="12" height="44" rx="6" className="fill-white" />
        <rect x="205" y="64" width="12" height="44" rx="6" className="fill-white" />
      </g>
      <g className="fill-danger-500/30">
        <circle cx="86" cy="50" r="6" />
        <circle cx="318" cy="132" r="8" />
        <circle cx="330" cy="46" r="4" />
        <circle cx="68" cy="128" r="4" />
      </g>
    </svg>
  );
}
