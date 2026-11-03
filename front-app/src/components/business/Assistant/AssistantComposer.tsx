import { AlertCircle, Mic, Send, Square, X } from 'lucide-react';
import { useLayoutEffect, useState, type KeyboardEvent, type RefObject } from 'react';

import { Tooltip } from '@/components/common/Tooltip';
import { useVoiceInput } from '@/hooks/useVoiceInput';
import { ASSISTANT_MESSAGE_MAX_LENGTH } from '@/types/assistant';
import { cn } from '@/utils/classNames';

/** The counter appears from this many characters on. */
const COUNTER_FROM = 800;
const MAX_HEIGHT_PX = 128;

interface AssistantComposerProps {
  onSend: (text: string) => void;
  /** A question is on its way: typing stays possible, sending (and speaking a new one) waits */
  busy: boolean;
  inputRef: RefObject<HTMLTextAreaElement | null>;
  /** The microphone is about to listen: an answer being read aloud must stop first */
  onListeningStart?: () => void;
}

/** Three bars that move while the microphone listens (still for people who asked for less motion). */
function LevelBars() {
  return (
    <span aria-hidden="true" className="inline-flex h-3 items-end gap-0.5">
      {[0, 150, 300].map((delay) => (
        <span
          key={delay}
          style={{ animationDelay: `${delay}ms` }}
          className="inline-block h-full w-0.5 origin-bottom rounded-full bg-danger-500 motion-safe:animate-pulse"
        />
      ))}
    </span>
  );
}

/**
 * Where the question is written or spoken. Typing: the box grows with the text, Enter sends, Shift+Enter starts a
 * new line. Speaking: the microphone listens until the person pauses (or presses Stop), the words appear in the box
 * as they are recognised, and the question is sent like a typed one. Without voice support in the browser the
 * microphone explains why and typing goes on as before.
 */
export function AssistantComposer({
  onSend,
  busy,
  inputRef,
  onListeningStart,
}: AssistantComposerProps) {
  const [draft, setDraft] = useState('');
  const voice = useVoiceInput({
    onTranscript: (spoken) => {
      if (busy) {
        setDraft(spoken); // an answer is still on its way: the spoken question waits in the box
      } else {
        onSend(spoken);
      }
    },
  });
  const recording = voice.state !== 'idle';
  const text = draft.trim();
  const canSend = text.length > 0 && !busy && !recording;

  useLayoutEffect(() => {
    const element = inputRef.current;
    if (element) {
      element.style.height = 'auto';
      element.style.height = `${Math.min(element.scrollHeight, MAX_HEIGHT_PX)}px`;
    }
  }, [draft, voice.interim, inputRef]);

  const submit = () => {
    if (!canSend) {
      return;
    }
    onSend(text);
    setDraft('');
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (recording && event.key === 'Escape') {
      event.preventDefault(); // Esc while recording cancels the recording, not the whole panel
      voice.cancel();
      return;
    }
    // Enter confirming an input method's text (Japanese, Chinese...) is not a send
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  };

  const toggleMicrophone = () => {
    if (recording) {
      voice.stop();
      return;
    }
    onListeningStart?.();
    voice.start();
  };

  const voiceAvailable = voice.support === 'supported';
  const micLabel = recording ? 'Stop recording' : 'Speak your question';
  const status =
    voice.state === 'starting'
      ? 'Starting the microphone...'
      : voice.state === 'listening'
        ? 'Listening... speak now'
        : voice.state === 'processing'
          ? 'Recognising your words...'
          : null;

  return (
    <div className="flex flex-col gap-2 border-t border-line/70 bg-page px-3 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
      {voice.error && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-2xl bg-surface px-3 py-2 text-sm text-ink shadow-card ring-1 ring-danger-500/30"
        >
          <AlertCircle aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-danger-text" />
          <p className="flex-1">{voice.error}</p>
          <button
            type="button"
            aria-label="Dismiss"
            onClick={voice.clearError}
            className="-m-1 flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-soft hover:bg-scrim/5 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>
      )}
      {status && (
        <p role="status" className="flex items-center gap-2 px-2 text-xs font-medium text-ink-soft">
          <span className="relative flex size-2.5">
            <span className="absolute inline-flex size-full rounded-full bg-danger-500 opacity-75 motion-safe:animate-ping" />
            <span className="relative inline-flex size-2.5 rounded-full bg-danger-500" />
          </span>
          {status}
          {voice.state === 'listening' && <LevelBars />}
        </p>
      )}

      <form
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
        className="flex items-end gap-2"
      >
        <div
          className={cn(
            'flex min-w-0 flex-1 items-end gap-1 rounded-3xl bg-surface p-1.5 shadow-card transition-shadow',
            recording
              ? 'ring-2 ring-danger-500'
              : 'ring-1 ring-line focus-within:ring-4 focus-within:ring-brand-500/25',
          )}
        >
          <Tooltip label={voiceAvailable ? micLabel : 'Voice input is not available here'}>
            <button
              type="button"
              aria-label={micLabel}
              aria-pressed={recording}
              aria-disabled={!voiceAvailable || (busy && !recording) || undefined}
              disabled={voice.state === 'processing'}
              onClick={() => {
                if (busy && !recording) {
                  return;
                }
                toggleMicrophone();
              }}
              className={cn(
                'flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full transition-colors',
                'focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none',
                recording
                  ? 'bg-danger-500 text-white motion-safe:animate-pulse'
                  : 'text-brand-text hover:bg-brand-50',
                (!voiceAvailable || (busy && !recording)) && 'opacity-50',
              )}
            >
              {recording ? (
                <Square aria-hidden="true" className="size-3.5 fill-current" />
              ) : (
                <Mic aria-hidden="true" className="size-5" />
              )}
            </button>
          </Tooltip>
          <textarea
            ref={inputRef}
            aria-label="Ask the assistant"
            placeholder={recording ? 'Listening... speak now' : 'Ask a question...'}
            rows={1}
            maxLength={ASSISTANT_MESSAGE_MAX_LENGTH}
            value={recording ? voice.interim : draft}
            readOnly={recording}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={onKeyDown}
            className="max-h-32 min-w-0 flex-1 resize-none overflow-y-auto bg-transparent px-1 py-2 text-sm text-ink placeholder:text-ink-soft focus:outline-none"
          />
          {recording && (
            <button
              type="button"
              aria-label="Cancel recording"
              onClick={voice.cancel}
              className="flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full text-ink-soft hover:bg-scrim/5 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none"
            >
              <X aria-hidden="true" className="size-4" />
            </button>
          )}
        </div>
        <button
          type="submit"
          aria-label="Send"
          disabled={!canSend}
          className="flex size-12 shrink-0 cursor-pointer items-center justify-center rounded-full bg-brand-500 text-on-brand shadow-brand transition not-disabled:hover:bg-brand-400 focus-visible:ring-4 focus-visible:ring-brand-500/30 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none motion-safe:not-disabled:hover:-translate-y-0.5 motion-safe:not-disabled:active:scale-95"
        >
          <Send aria-hidden="true" className="size-5" />
        </button>
      </form>
      {draft.length >= COUNTER_FROM && !recording && (
        <p aria-live="polite" className="px-2 text-xs text-ink-soft">
          {`${draft.length}/${ASSISTANT_MESSAGE_MAX_LENGTH}`}
        </p>
      )}
    </div>
  );
}
