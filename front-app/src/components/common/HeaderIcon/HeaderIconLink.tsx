import type { LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import {
  HEADER_ITEM,
  HEADER_TILE,
  ICON_MOTIONS,
  type IconMotion,
} from '@/components/common/HeaderIcon/headerIconStyles';
import { useTooltip } from '@/components/common/Tooltip';
import { cn } from '@/utils/classNames';

interface HeaderIconLinkProps {
  to: string;
  /** The accessible name, with the number when there is one ("Notifications (4 unread)") */
  label: string;
  icon: LucideIcon;
  /** A number on the icon (hidden when 0 or unknown) */
  count?: number;
  /** How the icon moves on hover: the bell rings, a gift pops, a bag nudges */
  motion?: IconMotion;
  /** A soft pulse around the number (new notifications) */
  pulse?: boolean;
}

/** An icon in the header (notifications, coupons, the cart): a white tile that turns green on hover. */
export function HeaderIconLink({
  to,
  label,
  icon: Icon,
  count,
  motion = 'pop',
  pulse = false,
}: HeaderIconLinkProps) {
  const hasCount = count !== undefined && count > 0;
  const { trigger, tooltip, hide } = useTooltip({ label, icon: Icon });
  return (
    <>
      <Link to={to} aria-label={label} className={HEADER_ITEM} {...trigger} onClick={hide}>
        <span className={HEADER_TILE}>
          <Icon aria-hidden="true" className={cn('size-5', ICON_MOTIONS[motion])} />
        </span>
        {hasCount && (
          <span aria-hidden="true" className="absolute -top-1.5 -right-1.5 z-10 flex">
            {pulse && (
              <span className="absolute inset-0 animate-ping rounded-full bg-danger-500/50 motion-reduce:hidden" />
            )}
            <span className="relative flex h-5 min-w-5 items-center justify-center rounded-full bg-danger-500 px-1 text-[10px] font-bold text-white ring-2 ring-surface">
              {count > 99 ? '99+' : count}
            </span>
          </span>
        )}
      </Link>
      {tooltip}
    </>
  );
}
