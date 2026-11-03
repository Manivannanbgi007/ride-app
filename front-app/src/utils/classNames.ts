/**
 * Joins Tailwind class names, leaving out the ones that are switched off:
 * cn('rounded-lg', isActive && 'bg-brand-50', className)
 */
export function cn(...classNames: Array<string | false | null | undefined>): string {
  return classNames.filter(Boolean).join(' ');
}
