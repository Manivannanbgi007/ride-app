import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { Input } from '@/components/common/Input';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

describe('Button', () => {
  it('blocks clicks and shows a spinner while its action runs', async () => {
    const onClick = vi.fn();
    render(
      <Button isLoading onClick={onClick}>
        Save
      </Button>,
    );

    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
    expect(screen.getByRole('status', { name: 'Please wait' })).toBeInTheDocument();
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('Input', () => {
  it('connects its label and marks an error for screen readers', () => {
    render(<Input label="Reason" error="Please give a reason" />);

    const field = screen.getByLabelText('Reason');
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveAccessibleDescription('Please give a reason');
  });
});

describe('AsyncContent', () => {
  const failure = new ApiError({
    kind: 'unavailable',
    status: 503,
    userMessage: ERROR_MESSAGES.SERVICE_UNAVAILABLE,
    requestId: 'req-7',
    technicalMessage: 'GET /x failed with 503',
  });

  it('shows the content once the data is there', () => {
    render(
      <AsyncContent isLoading={false} errorTitle="Unable to load">
        <p>The data</p>
      </AsyncContent>,
    );
    expect(screen.getByText('The data')).toBeInTheDocument();
  });

  it('shows a loading state, never the content, while loading', () => {
    render(
      <AsyncContent isLoading errorTitle="Unable to load">
        <p>The data</p>
      </AsyncContent>,
    );
    expect(screen.getByRole('status', { name: 'Loading' })).toBeInTheDocument();
    expect(screen.queryByText('The data')).not.toBeInTheDocument();
  });

  it('explains a failure in plain words, with its reference and a way to try again', async () => {
    const retry = vi.fn();
    render(
      <AsyncContent
        isLoading={false}
        error={failure}
        errorTitle="Unable to load the orders"
        onRetry={retry}
      >
        <p>The data</p>
      </AsyncContent>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load the orders');
    expect(screen.getByText(ERROR_MESSAGES.SERVICE_UNAVAILABLE)).toBeInTheDocument();
    expect(screen.getByText('Reference: req-7')).toBeInTheDocument();
    expect(screen.queryByText(/503/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('tells an empty answer apart from an error', () => {
    render(
      <AsyncContent isLoading={false} isEmpty errorTitle="Unable to load">
        <p>The data</p>
      </AsyncContent>,
    );
    expect(screen.getByText('Nothing to show yet')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('ConfirmationDialog', () => {
  it('confirms, and cancels with the Escape key', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <ConfirmationDialog
        open
        title="Cancel order 42?"
        message="The customer is told and refunded."
        tone="danger"
        confirmLabel="Cancel order"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    expect(screen.getByRole('dialog', { name: 'Cancel order 42?' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel order' }));
    await userEvent.keyboard('{Escape}');

    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onCancel).toHaveBeenCalledOnce();
  });
});

describe('ErrorBoundary', () => {
  it('replaces a crashed part with a message, and shows it again on "Try again"', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    let crash = true;
    function Fragile() {
      if (crash) {
        throw new Error('Cannot read properties of undefined');
      }
      return <p>Working again</p>;
    }
    render(
      <ErrorBoundary>
        <Fragile />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(ERROR_MESSAGES.PAGE_CRASHED);
    crash = false;
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(screen.getByText('Working again')).toBeInTheDocument();
    vi.restoreAllMocks();
  });
});
