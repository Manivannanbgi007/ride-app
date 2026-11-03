import { Moon, Sun } from 'lucide-react';

import { HEADER_ITEM, HEADER_TILE } from '@/components/common/HeaderIcon';
import { useTooltip } from '@/components/common/Tooltip';
import { useTheme } from '@/theme/themeContext';
import { cn } from '@/utils/classNames';

/**
 * The light / dark switch of the headers (admin and customer), in the same style as the other header icons. The
 * icon shows what a click leads to (the moon in light mode, the sun in dark mode) and turns over on the way.
 */
export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const dark = theme === 'dark';
  const label = dark ? 'Switch to light mode' : 'Switch to dark mode';
  const Icon = dark ? Sun : Moon;
  const { trigger, tooltip, hide } = useTooltip({ label, icon: Icon });

  return (
    <>
      <button
        type="button"
        aria-label={label}
        aria-pressed={dark}
        {...trigger}
        onClick={() => {
          hide();
          toggleTheme();
        }}
        className={HEADER_ITEM}
      >
        <span className={HEADER_TILE}>
          {/* A new icon each time (the key), which plays the small turn-over */}
          <Icon
            key={theme}
            aria-hidden="true"
            className={cn(
              'size-5 motion-safe:animate-pop group-hover/header:motion-safe:animate-pop',
            )}
          />
        </span>
      </button>
      {tooltip}
    </>
  );
}
