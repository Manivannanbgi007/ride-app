import { Link } from 'react-router';

import { buttonStyles } from '@/components/common/Button/buttonStyles';
import { useAssistant } from '@/context/AssistantContext';
import type { AssistantLink } from '@/types/assistant';
import { safeAppPath } from '@/utils/assistantUtils';
import { cn } from '@/utils/classNames';

interface AssistantLinkButtonProps {
  link: AssistantLink;
  variant?: 'outline' | 'primary';
  className?: string;
}

/**
 * A link the assistant gave, as a button that moves inside the application (the router, no page load). An
 * address that is not an in-app path is not drawn at all.
 */
export function AssistantLinkButton({
  link,
  variant = 'outline',
  className,
}: AssistantLinkButtonProps) {
  const { onNavigated } = useAssistant();
  const path = safeAppPath(link.path);
  if (!path) {
    return null;
  }
  return (
    <Link
      to={path}
      onClick={onNavigated}
      className={buttonStyles(variant, 'sm', cn('cursor-pointer', className))}
    >
      {link.label}
    </Link>
  );
}
