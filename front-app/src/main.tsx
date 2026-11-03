// The application's font (variable: every weight from 200 to 800 in one file)
import '@fontsource-variable/plus-jakarta-sans';
import './index.css';

import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router';

import { queryClient } from '@/api/queryClient';
import { App } from '@/App';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { ThemeProvider } from '@/theme/ThemeProvider';
import { ToastProvider } from '@/context/ToastProvider';
import { ConfigurationErrorPage } from '@/pages/ConfigurationError/ConfigurationErrorPage';
import { store } from '@/store/store';
import { configurationProblem, env } from '@/utils/env';
import { logger } from '@/utils/logger';

// Errors nobody caught (a failed promise without .catch, an error in a timer) still reach the log
window.addEventListener('unhandledrejection', (event) =>
  logger.error('Unhandled promise rejection', event.reason),
);
window.addEventListener('error', (event) => logger.error('Unexpected error', event.error));

// The cached API data, in a panel at the bottom; only while developing (never in a build)
const QueryDevtools =
  import.meta.env.DEV && env.features.queryDevtools
    ? lazy(() =>
        import('@tanstack/react-query-devtools').then((module) => ({
          default: module.ReactQueryDevtools,
        })),
      )
    : null;

const root = createRoot(document.getElementById('root') as HTMLElement);

if (configurationProblem) {
  logger.error(configurationProblem);
  root.render(<ConfigurationErrorPage problem={configurationProblem} />);
} else {
  logger.info(`${env.appName} (${env.environmentName}) talks to ${env.apiBaseUrl}`);
  root.render(
    <StrictMode>
      {/* Outermost safety net: even a crash in a provider shows a message instead of a blank page */}
      <ErrorBoundary>
        <ThemeProvider>
          <Provider store={store}>
            <QueryClientProvider client={queryClient}>
              <ToastProvider>
                <BrowserRouter>
                  <App />
                </BrowserRouter>
              </ToastProvider>
              {QueryDevtools && (
                <Suspense fallback={null}>
                  <QueryDevtools />
                </Suspense>
              )}
            </QueryClientProvider>
          </Provider>
        </ThemeProvider>
      </ErrorBoundary>
    </StrictMode>,
  );
}
