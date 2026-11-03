import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_TOAST_DURATIONS,
  TOAST_EXIT_MS,
  useToast,
  type ToastContextValue,
  type ToastTone,
} from '@/context/ToastContext';
import { ToastProvider } from '@/context/ToastProvider';

/** Renders the provider and hands back its showToast, as any component gets it from useToast(). */
function renderToasts(props: Omit<Parameters<typeof ToastProvider>[0], 'children'> = {}) {
  let api: ToastContextValue | undefined;
  function Grab() {
    api = useToast();
    return null;
  }
  const result = render(
    <ToastProvider {...props}>
      <Grab />
    </ToastProvider>,
  );
  const show = (...args: Parameters<ToastContextValue['showToast']>) =>
    act(() => {
      api?.showToast(...args);
    });
  return { ...result, show };
}

const toastOf = (text: string) => screen.getByText(text).closest('[data-tone]') as HTMLElement;
const elapse = (ms: number) => {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
};

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  // A top bar a test added never stays for the next one, even when the test failed
  document.querySelectorAll('[data-app-header]').forEach((header) => header.remove());
});

describe('toasts', () => {
  it.each<[ToastTone, string, 'status' | 'alert', string]>([
    ['success', 'from-toast-success-start', 'status', 'Success'],
    ['error', 'from-toast-error-start', 'alert', 'Error'],
    ['warning', 'from-toast-warning-start', 'alert', 'Warning'],
    ['info', 'from-toast-info-start', 'status', 'Information'],
  ])(
    'shows a %s toast in its own colour, with its kind for screen readers',
    (tone, colour, role, label) => {
      const { show } = renderToasts();
      show('Something happened', tone);

      const toast = screen.getByRole(role);
      expect(toast).toHaveClass(colour);
      expect(toast).toHaveAttribute('data-tone', tone);
      expect(toast).toHaveTextContent(`${label}: Something happened`);
      // Slides in from the right
      expect(toast).toHaveClass('toast-enter');
    },
  );

  it('shows a success toast when no kind is given (as before)', () => {
    const { show } = renderToasts();
    show('Saved');
    expect(toastOf('Saved')).toHaveAttribute('data-tone', 'success');
  });

  it('sits in the top-right corner, just under the pageâ€™s top bar', () => {
    const header = document.createElement('header');
    header.setAttribute('data-app-header', '');
    vi.spyOn(header, 'getBoundingClientRect').mockReturnValue({ bottom: 72 } as DOMRect);
    document.body.append(header);
    const { show } = renderToasts();
    show('Saved');

    const corner = toastOf('Saved').parentElement?.closest('[aria-live]') as HTMLElement;
    expect(corner).toHaveClass('fixed', 'sm:right-6', 'sm:w-96');
    expect(corner.style.top).toBe('84px');
  });

  it('moves under the top bar that appears after it (signing in, then the area opens)', async () => {
    vi.useRealTimers();
    const { show } = renderToasts();
    show('Welcome back, Ravi!');
    const corner = toastOf('Welcome back, Ravi!').closest('[aria-live]') as HTMLElement;
    expect(corner.style.top).toBe('16px');

    // The next page's top bar arrives (a new route)
    const header = document.createElement('header');
    header.setAttribute('data-app-header', '');
    vi.spyOn(header, 'getBoundingClientRect').mockReturnValue({ bottom: 68 } as DOMRect);
    document.body.append(header);

    await waitFor(() => expect(corner.style.top).toBe('80px'));
  });

  it('starts at the top edge on a page without a top bar (sign-in)', () => {
    const { show } = renderToasts();
    show('Signed out');
    const corner = toastOf('Signed out').closest('[aria-live]') as HTMLElement;
    expect(corner.style.top).toBe('16px');
  });

  it('hides by itself after its time: first the exit animation, then it is gone', () => {
    const { show } = renderToasts();
    show('Saved');

    elapse(DEFAULT_TOAST_DURATIONS.success - 1);
    expect(toastOf('Saved')).toHaveClass('toast-enter');

    elapse(1);
    expect(toastOf('Saved')).toHaveClass('toast-leave');

    elapse(TOAST_EXIT_MS);
    expect(screen.queryByText('Saved')).not.toBeInTheDocument();
  });

  it('gives errors more time to be read than successes', () => {
    expect(DEFAULT_TOAST_DURATIONS.error).toBeGreaterThan(DEFAULT_TOAST_DURATIONS.success);
    const { show } = renderToasts();
    show('Could not save', 'error');

    elapse(DEFAULT_TOAST_DURATIONS.success + TOAST_EXIT_MS);
    expect(toastOf('Could not save')).toHaveClass('toast-enter');
  });

  it('takes a time for one toast, or the applicationâ€™s own time per kind; 0 stays until dismissed', () => {
    const { show } = renderToasts({ durations: { info: 1_000 } });
    show('Quick note', 'info');
    show('Read me carefully', 'warning', { duration: 20_000 });
    show('Stays', 'error', { duration: 0 });

    elapse(1_000);
    expect(toastOf('Quick note')).toHaveClass('toast-leave');
    expect(toastOf('Read me carefully')).toHaveClass('toast-enter');

    elapse(19_000);
    expect(toastOf('Read me carefully')).toHaveClass('toast-leave');

    elapse(60_000);
    expect(toastOf('Stays')).toHaveClass('toast-enter');
    // A toast that stays has no countdown bar
    expect(toastOf('Stays').querySelector('.toast-countdown')).toBeNull();
  });

  it('pauses the countdown while pointed at, then finishes the time that was left', () => {
    const { show } = renderToasts();
    show('Saved');
    const countdown = toastOf('Saved').querySelector('.toast-countdown') as HTMLElement;
    expect(countdown.style.animationDuration).toBe(`${DEFAULT_TOAST_DURATIONS.success}ms`);

    elapse(3_000);
    fireEvent.mouseEnter(toastOf('Saved'));
    expect(countdown.style.animationPlayState).toBe('paused');
    elapse(30_000);
    expect(toastOf('Saved')).toHaveClass('toast-enter');

    fireEvent.mouseLeave(toastOf('Saved'));
    expect(countdown.style.animationPlayState).toBe('running');
    elapse(999);
    expect(toastOf('Saved')).toHaveClass('toast-enter');
    elapse(1);
    expect(toastOf('Saved')).toHaveClass('toast-leave');
  });

  it('pauses while the keyboard focus is on it', () => {
    const { show } = renderToasts();
    show('Saved');
    const dismiss = screen.getByRole('button', { name: 'Dismiss' });

    act(() => dismiss.focus());
    elapse(30_000);
    expect(toastOf('Saved')).toHaveClass('toast-enter');

    act(() => dismiss.blur());
    elapse(DEFAULT_TOAST_DURATIONS.success);
    expect(toastOf('Saved')).toHaveClass('toast-leave');
  });

  it('can be dismissed at once with its button, or with Escape', () => {
    const { show } = renderToasts();
    show('First');
    show('Second');

    fireEvent.click(screen.getAllByRole('button', { name: 'Dismiss' })[0]);
    expect(toastOf('First')).toHaveClass('toast-leave');
    elapse(TOAST_EXIT_MS);
    expect(screen.queryByText('First')).not.toBeInTheDocument();

    fireEvent.keyDown(screen.getByRole('button', { name: 'Dismiss' }), { key: 'Escape' });
    elapse(TOAST_EXIT_MS);
    expect(screen.queryByText('Second')).not.toBeInTheDocument();
  });

  it('stacks several toasts one under the other, oldest first, and keeps at most four', () => {
    const { show } = renderToasts();
    for (const message of ['One', 'Two', 'Three', 'Four']) {
      show(message);
    }
    const order = () =>
      Array.from(document.querySelectorAll('[data-tone]')).map((toast) =>
        toast.textContent?.replace('Success: ', ''),
      );
    expect(order()).toEqual(['One', 'Two', 'Three', 'Four']);

    // A fifth one: the oldest leaves (with its animation) so the corner never fills the screen
    show('Five');
    expect(toastOf('One')).toHaveClass('toast-leave');
    elapse(TOAST_EXIT_MS);
    expect(order()).toEqual(['Two', 'Three', 'Four', 'Five']);
  });
});
