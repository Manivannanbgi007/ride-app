import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';

import { ToastProvider } from '@/context/ToastProvider';
import { makeStore, type AppStore } from '@/store/store';

interface ProviderOptions extends Omit<RenderOptions, 'wrapper'> {
  /** The address the test starts at */
  route?: string;
  store?: AppStore;
}

/**
 * Renders a component the way the application does (Redux, React Query, toasts, router), with a fresh store and
 * cache for every test, and no automatic retries (a failure shows at once).
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', store = makeStore(), ...options }: ProviderOptions = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const result = render(ui, {
    wrapper: ({ children }) => (
      <Provider store={store}>
        <QueryClientProvider client={queryClient}>
          <ToastProvider>
            <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
          </ToastProvider>
        </QueryClientProvider>
      </Provider>
    ),
    ...options,
  });
  return { ...result, store, queryClient };
}
