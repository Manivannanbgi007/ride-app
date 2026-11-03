import { createContext, useContext, type RefObject } from 'react';

import type { ActingUser } from '@/types/user';
import type { ActionResolution, ThreadItem } from '@/types/assistant';

export interface AssistantContextValue {
  user: ActingUser;
  open: boolean;
  /** Opens the panel (from the button, or back from minimized) */
  openPanel: () => void;
  /** Closes the panel: the round button comes back (the conversation is kept) */
  closePanel: () => void;
  /** The panel is folded into a small bar at the bottom right; the conversation goes on behind it */
  minimized: boolean;
  minimizePanel: () => void;
  /**
   * The newest answer when the panel was minimized (null: none yet); the bar says "New answer" once a newer one is
   * there
   */
  answerWhenMinimized: string | null;
  /** The panel is a large window instead of the side sheet (remembered for this browser) */
  maximized: boolean;
  toggleMaximized: () => void;
  /** The floating button: focus goes back to it when the panel closes */
  launcherRef: RefObject<HTMLButtonElement | null>;
  items: ThreadItem[];
  /** Waiting for an answer: since when (ms), else null */
  thinkingSince: number | null;
  historyLoading: boolean;
  send: (text: string) => void;
  /** Sends the question of an error entry again (the entry goes away) */
  retry: (errorId: string) => void;
  /** Stops waiting; a late answer is ignored */
  cancel: () => void;
  newChat: () => void;
  resolutions: Record<string, ActionResolution>;
  resolveAction: (actionId: string, resolution: ActionResolution) => void;
  /** After following a link from the assistant: a phone's full-screen panel gives way to the page */
  onNavigated: () => void;
}

export const AssistantContext = createContext<AssistantContextValue | null>(null);

export function useAssistant(): AssistantContextValue {
  const context = useContext(AssistantContext);
  if (!context) {
    throw new Error('useAssistant must be used inside <AssistantProvider>');
  }
  return context;
}
