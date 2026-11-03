import { Send, X } from 'lucide-react';
import { useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';

import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Textarea';
import { CHAT_MESSAGE_MAX_LENGTH } from '@/types/chat';

/** The counter appears from this many characters on. */
const COUNTER_FROM = 1800;
const MAX_HEIGHT_PX = 160;

interface ComposerProps {
  onSend: (body: string) => void;
  placeholder?: string;
}

/**
 * Where a message is written: a text box that grows with the text (to a limit), Enter sends, Shift+Enter makes a
 * new line, Cancel clears the draft, Send is off while there is nothing to send. Plain text only.
 */
export function Composer({ onSend, placeholder = 'Write your message here...' }: ComposerProps) {
  const [draft, setDraft] = useState('');
  const field = useRef<HTMLTextAreaElement>(null);
  const body = draft.trim();

  // Grows with the text: shrink first so a deleted line gives its space back
  useLayoutEffect(() => {
    const element = field.current;
    if (element) {
      element.style.height = 'auto';
      element.style.height = `${Math.min(element.scrollHeight, MAX_HEIGHT_PX)}px`;
    }
  }, [draft]);

  const submit = () => {
    if (!body) {
      return;
    }
    onSend(body);
    setDraft('');
    field.current?.focus();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter confirming an input method's text (Japanese, Chinese...) is not a send
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  };

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className="flex flex-col gap-3 border-t border-line px-4 py-4 sm:px-6"
    >
      <Textarea
        ref={field}
        aria-label="Message"
        placeholder={placeholder}
        rows={1}
        maxLength={CHAT_MESSAGE_MAX_LENGTH}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={onKeyDown}
        className="max-h-40 resize-none overflow-y-auto rounded-2xl"
      />
      <div className="flex items-center justify-end gap-3">
        {draft.length >= COUNTER_FROM && (
          <p
            aria-live="polite"
            className="mr-auto text-xs text-ink-soft"
          >{`${draft.length}/${CHAT_MESSAGE_MAX_LENGTH}`}</p>
        )}
        <Button
          variant="outline"
          size="md"
          disabled={draft.length === 0}
          onClick={() => {
            setDraft('');
            field.current?.focus();
          }}
          leftIcon={<X aria-hidden="true" className="size-4" />}
        >
          Cancel
        </Button>
        <Button
          type="submit"
          size="md"
          disabled={!body}
          leftIcon={<Send aria-hidden="true" className="size-4" />}
        >
          Send
        </Button>
      </div>
    </form>
  );
}
