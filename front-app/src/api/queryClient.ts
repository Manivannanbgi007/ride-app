import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';

import { isRetryable } from '@/api/apiErrorHandler';
import { logger } from '@/utils/logger';

/**
 * React Query: loads API data, keeps it (cache), and tells components whether it is loading, there, or failed.
 * The API Gateway already retries reads that could not reach a service, so the browser tries once more only
 * when the connection itself failed or timed out.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Data younger than 30 s is shown without asking the server again
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => failureCount < 1 && isRetryable(error),
      },
      mutations: {
        // Never repeat a change by itself (it could, for example, cancel an order twice)
        retry: false,
      },
    },
    queryCache: new QueryCache({
      onError: (error, query) =>
        logger.error(`Loading ${JSON.stringify(query.queryKey)} failed`, error),
    }),
    mutationCache: new MutationCache({
      onError: (error) => logger.error('A change failed', error),
    }),
  });
}

export const queryClient = createQueryClient();
