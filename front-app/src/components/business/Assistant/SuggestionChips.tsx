import { ArrowUpRight, MessageSquareText } from 'lucide-react';

interface SuggestionChipsProps {
  suggestions: string[];
  onPick: (question: string) => void;
}

/** The questions offered for the user's role, as cards: one click sends it. */
export function SuggestionChips({ suggestions, onPick }: SuggestionChipsProps) {
  if (suggestions.length === 0) {
    return null;
  }
  return (
    <ul aria-label="Suggested questions" className="flex w-full flex-col gap-2">
      {suggestions.map((question) => (
        <li key={question}>
          <button
            type="button"
            onClick={() => onPick(question)}
            className="group flex w-full cursor-pointer items-center gap-3 rounded-2xl bg-surface px-4 py-3 text-left text-sm text-ink shadow-card ring-1 ring-transparent transition hover:ring-brand-500/60 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none motion-safe:hover:-translate-y-0.5"
          >
            <span
              aria-hidden="true"
              className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text"
            >
              <MessageSquareText className="size-4" />
            </span>
            <span className="flex-1">{question}</span>
            <ArrowUpRight
              aria-hidden="true"
              className="size-4 shrink-0 text-ink-soft transition-colors group-hover:text-brand-text"
            />
          </button>
        </li>
      ))}
    </ul>
  );
}
