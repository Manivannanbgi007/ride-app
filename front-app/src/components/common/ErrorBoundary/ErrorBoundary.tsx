import { Component, type ErrorInfo, type ReactNode } from 'react';

import { ErrorFallback } from '@/components/common/ErrorBoundary/ErrorFallback';
import { logger } from '@/utils/logger';

interface ErrorBoundaryProps {
  children: ReactNode;
  /** When this value changes (e.g. the page address), a crashed page is shown again */
  resetKey?: unknown;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * Catches errors thrown while drawing the components inside it and shows ErrorFallback instead of a blank
 * screen. (React needs a class component for this.) Errors in event handlers and API calls are not caught here:
 * those are handled where they happen (ErrorMessage, toasts).
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    logger.error('A page crashed while being drawn', error, info.componentStack);
  }

  componentDidUpdate(previous: ErrorBoundaryProps): void {
    if (this.state.error && previous.resetKey !== this.props.resetKey) {
      this.reset();
    }
  }

  private readonly reset = () => this.setState({ error: null });

  render() {
    if (this.state.error) {
      return <ErrorFallback error={this.state.error} onReset={this.reset} />;
    }
    return this.props.children;
  }
}
