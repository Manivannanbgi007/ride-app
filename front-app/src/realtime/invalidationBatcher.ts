import type { QueryClient, QueryKey } from '@tanstack/react-query';

/**
 * Collects the data that went out of date and reads it again once, a moment later. An order that moves through
 * several statuses in a second, or a dozen orders at once, cause one refresh of each screen, not one per event.
 */
export class InvalidationBatcher {
  private readonly pending = new Map<string, QueryKey>();
  private timer: number | undefined;
  private readonly queryClient: QueryClient;
  private readonly delayMs: number;

  constructor(queryClient: QueryClient, delayMs = 400) {
    this.queryClient = queryClient;
    this.delayMs = delayMs;
  }

  add(keys: readonly QueryKey[]): void {
    for (const key of keys) {
      this.pending.set(JSON.stringify(key), key);
    }
    if (this.pending.size > 0 && this.timer === undefined) {
      this.timer = window.setTimeout(() => this.flush(), this.delayMs);
    }
  }

  /** Everything on screen is read again (after a lost connection, when events may have been missed). */
  addEverything(): void {
    this.pending.clear();
    this.pending.set('*', []);
    if (this.timer === undefined) {
      this.timer = window.setTimeout(() => this.flush(), this.delayMs);
    }
  }

  cancel(): void {
    window.clearTimeout(this.timer);
    this.timer = undefined;
    this.pending.clear();
  }

  private flush(): void {
    const keys = [...this.pending.entries()];
    this.timer = undefined;
    this.pending.clear();
    for (const [name, key] of keys) {
      void this.queryClient.invalidateQueries(name === '*' ? undefined : { queryKey: key });
    }
  }
}
