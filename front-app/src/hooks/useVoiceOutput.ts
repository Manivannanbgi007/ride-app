import { useCallback, useEffect, useRef, useState } from 'react';

import { ERROR_MESSAGES } from '@/utils/errorMessages';
import {
  pickVoice,
  speakableText,
  speechChunks,
  speechLanguage,
  voiceOutputSupported,
} from '@/utils/speech';

/**
 * Reading answers aloud with the browser's speechSynthesis: one answer at a time (starting another stops the
 * first), in short pieces, with Stop. speakingId says which answer is being read, for its button.
 */
export function useVoiceOutput() {
  const [supported] = useState(voiceOutputSupported);
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Each speak() gets a number: the events of an earlier, stopped reading are ignored
  const readingRef = useRef(0);

  const stop = useCallback(() => {
    readingRef.current++;
    if (supported) {
      window.speechSynthesis.cancel();
    }
    setSpeakingId(null);
  }, [supported]);

  /** Reads the answer (its Markdown is turned into plain sentences). */
  const speak = useCallback(
    (id: string, markdown: string) => {
      if (!supported) {
        return;
      }
      const chunks = speechChunks(speakableText(markdown));
      if (chunks.length === 0) {
        return;
      }
      const reading = ++readingRef.current;
      const synth = window.speechSynthesis;
      synth.cancel();
      const language = speechLanguage();
      const voice = pickVoice(language);
      chunks.forEach((chunk, index) => {
        const utterance = new SpeechSynthesisUtterance(chunk);
        utterance.lang = language;
        if (voice) {
          utterance.voice = voice;
        }
        if (index === chunks.length - 1) {
          utterance.onend = () => {
            if (readingRef.current === reading) {
              setSpeakingId(null);
            }
          };
        }
        utterance.onerror = (event) => {
          if (readingRef.current !== reading) {
            return;
          }
          readingRef.current++;
          synth.cancel();
          setSpeakingId(null);
          // Stopped on purpose (Stop, another answer, closing) is not a failure
          if (event.error !== 'interrupted' && event.error !== 'canceled') {
            setError(ERROR_MESSAGES.VOICE_OUTPUT_FAILED);
          }
        };
        synth.speak(utterance);
      });
      setError(null);
      setSpeakingId(id);
    },
    [supported],
  );

  const toggle = useCallback(
    (id: string, markdown: string) => {
      if (speakingId === id) {
        stop();
      } else {
        speak(id, markdown);
      }
    },
    [speak, speakingId, stop],
  );

  const clearError = useCallback(() => setError(null), []);

  // Closing the panel stops the voice
  useEffect(() => {
    const synth = supported ? window.speechSynthesis : null;
    const readings = readingRef;
    return () => {
      readings.current++;
      synth?.cancel();
    };
  }, [supported]);

  return { supported, speakingId, error, speak, stop, toggle, clearError };
}

export type VoiceOutput = ReturnType<typeof useVoiceOutput>;
