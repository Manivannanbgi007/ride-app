import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SearchInput } from '@/components/common/SearchInput';
import { idFilterError } from '@/utils/validationUtils';

describe('SearchInput', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  const type = (text: string) =>
    fireEvent.change(screen.getByLabelText('Search'), { target: { value: text } });
  const pause = (ms: number) =>
    act(() => {
      vi.advanceTimersByTime(ms);
    });

  it('searches once the typing stops, not for every key press', () => {
    const onSearch = vi.fn();
    render(<SearchInput label="Search" value="" onSearch={onSearch} />);

    type('p');
    pause(200);
    type('pri');
    pause(200);
    expect(onSearch).not.toHaveBeenCalled();

    pause(400);
    expect(onSearch).toHaveBeenCalledOnce();
    expect(onSearch).toHaveBeenCalledWith('pri');
  });

  it('does not search for invalid text, and says why', () => {
    const onSearch = vi.fn();
    render(<SearchInput label="Search" value="" onSearch={onSearch} validate={idFilterError} />);

    type('4a');
    pause(500);

    expect(onSearch).not.toHaveBeenCalled();
    expect(screen.getByText('Numbers only.')).toBeInTheDocument();
  });

  it('follows a search cleared elsewhere, without bringing the old text back', () => {
    const onSearch = vi.fn();
    const { rerender } = render(<SearchInput label="Search" value="" onSearch={onSearch} />);
    type('priya');
    pause(500);
    rerender(<SearchInput label="Search" value="priya" onSearch={onSearch} />);

    // "Clear filters" on the page
    rerender(<SearchInput label="Search" value="" onSearch={onSearch} />);
    pause(500);

    expect(screen.getByLabelText('Search')).toHaveValue('');
    expect(onSearch).toHaveBeenCalledOnce();
  });
});
