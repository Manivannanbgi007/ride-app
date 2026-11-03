import { describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/apiErrorHandler';
import { withoutEmptyValues } from '@/utils/objectUtils';
import { pageButtons, shownRange } from '@/utils/paginationUtils';
import {
  changeRoleFormSchema,
  dateRangeError,
  idFilterError,
  oneOf,
  reasonFormSchema,
  showServerErrors,
  toIdFilter,
} from '@/utils/validationUtils';

describe('pagination', () => {
  it('shows the first, the last and two pages on each side of the current one', () => {
    expect(pageButtons(9, 20)).toEqual([0, 'gap', 7, 8, 9, 10, 11, 'gap', 19]);
    expect(pageButtons(0, 3)).toEqual([0, 1, 2]);
    expect(pageButtons(0, 1)).toEqual([0]);
  });

  it('says which rows are shown', () => {
    expect(shownRange(1, 20, 303)).toBe('21-40 of 303');
    expect(shownRange(15, 20, 303)).toBe('301-303 of 303');
    expect(shownRange(0, 20, 0)).toBe('0 of 0');
  });
});

describe('validation rules', () => {
  it('requires a reason of at most 500 characters, without surrounding spaces', () => {
    expect(reasonFormSchema.safeParse({ reason: '   ' }).success).toBe(false);
    expect(reasonFormSchema.safeParse({ reason: 'x'.repeat(501) }).success).toBe(false);
    expect(reasonFormSchema.parse({ reason: '  Duplicate account ' })).toEqual({
      reason: 'Duplicate account',
    });
  });

  it('requires a known role', () => {
    expect(changeRoleFormSchema.safeParse({ role: 'ADMIN', reason: 'x' }).success).toBe(false);
    expect(changeRoleFormSchema.safeParse({ role: 'RESTAURANT_ADMIN', reason: 'x' }).success).toBe(
      true,
    );
  });

  it('accepts only digits as an id filter', () => {
    expect(idFilterError('42')).toBeUndefined();
    expect(idFilterError('')).toBeUndefined();
    expect(idFilterError('4a')).toBe('Numbers only.');
    expect(toIdFilter(' 42 ')).toBe(42);
    expect(toIdFilter('')).toBeUndefined();
  });

  it('turns a select value back into a typed value', () => {
    expect(oneOf('PLACED', ['PLACED', 'DELIVERED'] as const)).toBe('PLACED');
    expect(oneOf('', ['PLACED'] as const)).toBeUndefined();
  });

  it('refuses a date range that ends before it starts', () => {
    expect(dateRangeError('2026-10-08', '2026-10-01')).toBeDefined();
    expect(dateRangeError('2026-10-01', '2026-10-01')).toBeUndefined();
    expect(dateRangeError('2026-10-01', undefined)).toBeUndefined();
  });

  it("shows the server's field messages next to the fields, and its message above the buttons", () => {
    const setError = vi.fn();
    const refused = new ApiError({
      kind: 'badRequest',
      status: 400,
      userMessage: 'Some fields are invalid',
      fieldErrors: { reason: 'size must be between 0 and 500', unknownField: 'x' },
      technicalMessage: 'PUT failed with 400',
    });

    showServerErrors(refused, setError, ['reason']);

    expect(setError).toHaveBeenCalledWith('reason', { message: 'size must be between 0 and 500' });
    expect(setError).toHaveBeenCalledWith('root.server', { message: 'Some fields are invalid' });
    expect(setError).toHaveBeenCalledTimes(2);
  });
});

describe('withoutEmptyValues', () => {
  it('drops undefined, null and empty text, keeps false and 0', () => {
    expect(withoutEmptyValues({ a: undefined, b: null, c: '', d: false, e: 0, f: 'x' })).toEqual({
      d: false,
      e: 0,
      f: 'x',
    });
  });
});
