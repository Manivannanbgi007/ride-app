import type { SVGProps } from 'react';

/**
 * The assistant's own mark: a speech bubble holding a serving cloche, someone to ask about food. Drawn like the
 * lucide icons around it (24 x 24, the current text colour, 2 px rounded strokes), so it sits well beside them.
 */
export function AssistantIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      {/* The bubble, its tail at the bottom left */}
      <path d="M7 3h10a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4h-6l-4.5 3.6V17H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4z" />
      {/* The cloche: its dome, plate and handle */}
      <path d="M7.5 13.5a4.5 4.5 0 0 1 9 0" />
      <path d="M6.5 13.5h11" />
      <circle cx="12" cy="7.4" r="1.1" fill="currentColor" stroke="none" />
    </svg>
  );
}
