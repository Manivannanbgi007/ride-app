import { useCallback, useEffect, useRef, useState } from 'react';

import {
  recognitionConstructor,
  speechLanguage,
  unsupportedVoiceMessage,
  voiceInputErrorMessage,
  voiceInputSupport,
  type SpeechRecognitionLike,
  type VoiceSupport,
} from '@/utils/speech';

/**
 * idle; starting (asking the browser, which may ask for the microphone); listening; processing (stopped, the last
 * words are being recognised).
 */
export type VoiceInputState = 'idle' | 'starting' | 'listening' | 'processing';

interface UseVoiceInputOptions {
  /** The recognised question, once the person has finished speaking (never empty) */
  onTranscript: (text: string) => void;
}

/** One recording. Its events only count while it is the current one (a late event of an old one is ignored). */
interface Session {
  recognition: SpeechRecognitionLike;
  heard: string;
  final: string;
  failed: boolean;
}

/**
 * One spoken question, with the browser's SpeechRecognition: start() listens until the person pauses (or stop() is
 * pressed), the words appear while they are spoken (interim), and the final text goes to onTranscript. cancel()
 * throws the recording away. Every failure becomes a message the person can act on (microphone blocked, none
 * found, no speech, speech service unreachable, browser without voice input).
 */
export function useVoiceInput({ onTranscript }: UseVoiceInputOptions) {
  const [support] = useState<VoiceSupport>(voiceInputSupport);
  const [state, setState] = useState<VoiceInputState>('idle');
  const [interim, setInterim] = useState('');
  const [error, setError] = useState<string | null>(null);
  const sessionRef = useRef<Session | null>(null);
  const onTranscriptRef = useRef(onTranscript);

  useEffect(() => {
    onTranscriptRef.current = onTranscript;
  }, [onTranscript]);

  // Closing the panel (or leaving the page) while listening: the microphone is released, nothing is sent
  useEffect(
    () => () => {
      const session = sessionRef.current;
      sessionRef.current = null;
      session?.recognition.abort();
    },
    [],
  );

  const start = useCallback(() => {
    if (support !== 'supported') {
      setError(unsupportedVoiceMessage(support));
      return;
    }
    const Recognition = recognitionConstructor();
    if (!Recognition || sessionRef.current) {
      return;
    }
    const recognition = new Recognition();
    recognition.lang = speechLanguage();
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;
    const session: Session = { recognition, heard: '', final: '', failed: false };
    const current = () => sessionRef.current === session;

    recognition.onstart = () => {
      if (current()) {
        setState('listening');
      }
    };
    recognition.onresult = (event) => {
      if (!current()) {
        return;
      }
      let finalText = '';
      let interimText = '';
      for (const result of Array.from(event.results)) {
        if (result.isFinal) {
          finalText += result[0].transcript;
        } else {
          interimText += result[0].transcript;
        }
      }
      session.final = finalText.trim();
      session.heard = `${finalText}${interimText}`.trim();
      setInterim(session.heard);
    };
    recognition.onerror = (event) => {
      if (!current() || event.error === 'aborted') {
        return;
      }
      session.failed = true;
      setError(voiceInputErrorMessage(event.error));
    };
    recognition.onend = () => {
      if (!current()) {
        return; // cancelled: already reset
      }
      sessionRef.current = null;
      setState('idle');
      setInterim('');
      if (session.failed) {
        return;
      }
      // A browser that ends on "Stop" before it marked the last words final: use what it heard
      const text = session.final || session.heard;
      if (text) {
        onTranscriptRef.current(text);
      } else {
        setError(voiceInputErrorMessage('no-speech'));
      }
    };

    sessionRef.current = session;
    setError(null);
    setInterim('');
    setState('starting');
    try {
      recognition.start();
    } catch {
      sessionRef.current = null;
      setState('idle');
      setError(voiceInputErrorMessage('unknown'));
    }
  }, [support]);

  /** The person is done: the last words are recognised, then sent. */
  const stop = useCallback(() => {
    const session = sessionRef.current;
    if (session) {
      setState('processing');
      session.recognition.stop();
    }
  }, []);

  /** The recording is thrown away: nothing is sent. */
  const cancel = useCallback(() => {
    const session = sessionRef.current;
    if (session) {
      sessionRef.current = null;
      session.recognition.abort();
      setState('idle');
      setInterim('');
    }
  }, []);

  const clearError = useCallback(() => setError(null), []);

  return { support, state, interim, error, start, stop, cancel, clearError };
}
