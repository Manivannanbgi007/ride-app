import { parseMarkdown } from '@/components/business/Assistant/markdown';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/**
 * Voice in the assistant, with the browser's own Web Speech API (no extra library, nothing to install):
 * - speech to text: SpeechRecognition (Chrome, Edge, Safari; prefixed "webkit" in most). Firefox has none. The
 *   browser does the recognition (Chrome and Edge send the audio to their speech service); only the recognised TEXT
 *   is sent to our assistant, through the same chat API as a typed question.
 * - text to speech: speechSynthesis (every current browser), with the voices of the device.
 * Both need a secure page (https, or localhost while developing).
 */

/** The part of SpeechRecognition the assistant uses (TypeScript's DOM types have its events but not the class). */
export interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onstart: (() => void) | null;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

type RecognitionConstructor = new () => SpeechRecognitionLike;

/** supported, or why not: no API in this browser, or a page that is not secure (http on another host). */
export type VoiceSupport = 'supported' | 'unsupported' | 'insecure';

export function recognitionConstructor(): RecognitionConstructor | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const speechWindow = window as unknown as {
    SpeechRecognition?: RecognitionConstructor;
    webkitSpeechRecognition?: RecognitionConstructor;
  };
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null;
}

export function voiceInputSupport(): VoiceSupport {
  if (typeof window === 'undefined' || !recognitionConstructor()) {
    return 'unsupported';
  }
  return window.isSecureContext === false ? 'insecure' : 'supported';
}

export function voiceOutputSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'speechSynthesis' in window &&
    typeof window.SpeechSynthesisUtterance === 'function'
  );
}

/** Why voice input cannot be used here, for the person (with the text fallback). */
export function unsupportedVoiceMessage(support: VoiceSupport): string {
  return support === 'insecure'
    ? ERROR_MESSAGES.VOICE_INSECURE
    : ERROR_MESSAGES.VOICE_INPUT_UNSUPPORTED;
}

/** A SpeechRecognition error code -> what the person can do about it. */
export function voiceInputErrorMessage(code: string): string {
  switch (code) {
    case 'not-allowed':
    case 'service-not-allowed':
      return ERROR_MESSAGES.VOICE_PERMISSION_DENIED;
    case 'audio-capture':
      return ERROR_MESSAGES.VOICE_NO_MICROPHONE;
    case 'network':
      return ERROR_MESSAGES.VOICE_SERVICE_UNAVAILABLE;
    case 'no-speech':
      return ERROR_MESSAGES.VOICE_NO_SPEECH;
    case 'language-not-supported':
      return ERROR_MESSAGES.VOICE_LANGUAGE_UNSUPPORTED;
    default:
      return ERROR_MESSAGES.VOICE_INPUT_FAILED;
  }
}

/** The language to listen and speak in: the browser's own (the assistant answers in English). */
export function speechLanguage(): string {
  const language = typeof navigator === 'undefined' ? '' : navigator.language;
  return language || 'en-US';
}

/**
 * What to read aloud from an answer written in the assistant's Markdown subset: the plain words, lists as
 * sentences, "#42" as "number 42". Cards and buttons are not read (they stay on the screen).
 */
export function speakableText(markdown: string): string {
  const sentences: string[] = [];
  for (const block of parseMarkdown(markdown)) {
    const lines = block.type === 'paragraph' ? block.lines : block.items;
    for (const line of lines) {
      const text = line
        .map((node) => node.text)
        .join('')
        .trim();
      if (text) {
        sentences.push(/[.!?:;]$/.test(text) ? text : `${text}.`);
      }
    }
  }
  return sentences.join(' ').replace(/#(\d)/g, 'number $1').replace(/\s+/g, ' ').trim();
}

const MAX_CHUNK = 180;

/**
 * The text in pieces of at most ~180 characters, cut after sentences (or else at commas or spaces): some browsers
 * stop a long utterance after about 15 seconds, and short ones also let "Stop" act at once.
 */
export function speechChunks(text: string): string[] {
  const chunks: string[] = [];
  let current = '';
  const push = (piece: string) => {
    if (!piece) {
      return;
    }
    if (current && current.length + 1 + piece.length > MAX_CHUNK) {
      chunks.push(current);
      current = piece;
    } else {
      current = current ? `${current} ${piece}` : piece;
    }
  };
  for (const sentence of text.match(/[^.!?]+[.!?]*/g) ?? []) {
    const trimmed = sentence.trim();
    if (trimmed.length <= MAX_CHUNK) {
      push(trimmed);
      continue;
    }
    // A very long sentence: cut it at commas, then at spaces
    for (const part of trimmed.split(/(?<=,)\s+/)) {
      if (part.length <= MAX_CHUNK) {
        push(part);
      } else {
        part.split(/\s+/).forEach(push);
      }
    }
  }
  if (current) {
    chunks.push(current);
  }
  return chunks;
}

/** A voice of the device for the language (the same language first, then the same main language), or none. */
export function pickVoice(language: string): SpeechSynthesisVoice | null {
  if (!voiceOutputSupported()) {
    return null;
  }
  const voices = window.speechSynthesis.getVoices();
  const main = language.split('-')[0].toLowerCase();
  return (
    voices.find((voice) => voice.lang.toLowerCase() === language.toLowerCase()) ??
    voices.find((voice) => voice.lang.toLowerCase().startsWith(`${main}-`)) ??
    null
  );
}

// ---------- Remembering "read answers aloud" (this browser) ----------

const VOICE_REPLIES_KEY = 'front-app.assistant.voice-replies';

export function loadVoiceReplies(): boolean {
  try {
    return localStorage.getItem(VOICE_REPLIES_KEY) === 'on';
  } catch {
    return false;
  }
}

export function saveVoiceReplies(on: boolean): void {
  try {
    localStorage.setItem(VOICE_REPLIES_KEY, on ? 'on' : 'off');
  } catch {
    // Without storage the switch simply starts off next time
  }
}
