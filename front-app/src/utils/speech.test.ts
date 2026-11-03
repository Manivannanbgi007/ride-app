import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  speakableText,
  speechChunks,
  voiceInputErrorMessage,
  voiceInputSupport,
  voiceOutputSupported,
} from '@/utils/speech';

afterEach(() => vi.unstubAllGlobals());

describe('what is read aloud', () => {
  it('turns the answer Markdown into plain sentences', () => {
    expect(speakableText('**Order #42** is `READY`\n\n1. Paneer Wrap x2\n- Total: 262 INR')).toBe(
      'Order number 42 is READY. Paneer Wrap x2. Total: 262 INR.',
    );
    expect(speakableText('  \n\n ')).toBe('');
  });

  it('cuts long text into short pieces at sentences, then commas, then spaces', () => {
    const sentence = 'This sentence is about forty characters. ';
    const chunks = speechChunks(sentence.repeat(10).trim());
    expect(chunks.length).toBeGreaterThan(1);
    expect(chunks.every((chunk) => chunk.length <= 180)).toBe(true);
    expect(chunks.join(' ')).toBe(sentence.repeat(10).trim());

    const endless = `${'word, '.repeat(50)}end`;
    expect(speechChunks(endless).every((chunk) => chunk.length <= 180)).toBe(true);
    expect(speechChunks('')).toEqual([]);
  });
});

describe('voice support and errors', () => {
  it('needs the recognition API and a secure page', () => {
    expect(voiceInputSupport()).toBe('unsupported');

    vi.stubGlobal('webkitSpeechRecognition', class {});
    vi.stubGlobal('isSecureContext', false);
    expect(voiceInputSupport()).toBe('insecure');

    vi.stubGlobal('isSecureContext', true);
    expect(voiceInputSupport()).toBe('supported');
  });

  it('knows whether the browser can speak', () => {
    expect(voiceOutputSupported()).toBe(false);
    vi.stubGlobal('speechSynthesis', { speak: vi.fn() });
    vi.stubGlobal('SpeechSynthesisUtterance', class {});
    expect(voiceOutputSupported()).toBe(true);
  });

  it('turns recognition errors into something the person can act on', () => {
    expect(voiceInputErrorMessage('not-allowed')).toMatch(/Microphone access is blocked/);
    expect(voiceInputErrorMessage('service-not-allowed')).toMatch(/Microphone access is blocked/);
    expect(voiceInputErrorMessage('audio-capture')).toMatch(/No microphone/);
    expect(voiceInputErrorMessage('network')).toMatch(/speech service could not be reached/);
    expect(voiceInputErrorMessage('no-speech')).toMatch(/did not hear anything/);
    expect(voiceInputErrorMessage('something-new')).toMatch(/stopped unexpectedly/);
  });
});
