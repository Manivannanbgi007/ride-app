interface FoodTextSectionProps {
  title: string;
  /** The text as the restaurant wrote it; null or blank: not added */
  text: string | null | undefined;
}

/** "Ingredients" / "Nutrition Info": a heading and the free text under it (line breaks kept). */
export function FoodTextSection({ title, text }: FoodTextSectionProps) {
  const hasText = Boolean(text?.trim());
  return (
    <section aria-label={title} className="border-t border-dashed border-line px-6 py-6">
      <h3 className="text-lg font-semibold">{title}</h3>
      {hasText ? (
        <p className="mt-3 text-sm leading-7 break-words whitespace-pre-wrap text-ink-soft">
          {text}
        </p>
      ) : (
        <p className="mt-3 text-sm text-ink-soft italic">Not added yet. Use Edit Menu to add it.</p>
      )}
    </section>
  );
}
