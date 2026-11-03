import { Search } from 'lucide-react';
import { useEffect, useState } from 'react';

import { Input, type InputProps } from '@/components/common/Input';
import { useDebounce } from '@/hooks/useDebounce';

interface SearchInputProps extends Omit<InputProps, 'value' | 'onChange' | 'error'> {
  /** The search in use (e.g. from Redux); the box follows it when it changes elsewhere ("Clear filters") */
  value: string;
  /** Called once the user stops typing (not for every key press) */
  onSearch: (text: string) => void;
  /** Returns a message when the text cannot be searched for ("Numbers only"); onSearch is then not called */
  validate?: (text: string) => string | undefined;
  delayMs?: number;
}

/** A search or filter box that asks the server only when the user pauses typing. */
export function SearchInput({
  value,
  onSearch,
  validate,
  delayMs = 400,
  ...props
}: SearchInputProps) {
  const [text, setText] = useState(value);
  const [previousValue, setPreviousValue] = useState(value);
  // The search was changed elsewhere (filters cleared): show it in the box (React's "adjust state on change")
  if (value !== previousValue) {
    setPreviousValue(value);
    setText(value);
  }

  const debouncedText = useDebounce(text, delayMs);
  const error = validate?.(text);

  useEffect(() => {
    // Only once typing has stopped, the text is valid, and it differs from the search in use
    if (debouncedText === text && !validate?.(debouncedText) && debouncedText.trim() !== value) {
      onSearch(debouncedText.trim());
    }
  }, [debouncedText, text, value, validate, onSearch]);

  return (
    <Input
      type="search"
      value={text}
      onChange={(event) => setText(event.target.value)}
      error={error}
      leftIcon={<Search aria-hidden="true" className="size-4" />}
      {...props}
    />
  );
}
