import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { FoodPhoto } from '@/components/common/FoodPhoto';

const FALLBACK = <span>no picture</span>;

describe('FoodPhoto', () => {
  it('shows the first picture', () => {
    const { container } = render(<FoodPhoto sources={['/a.jpg', '/b.jpg']} fallback={FALLBACK} />);

    expect(container.querySelector('img')).toHaveAttribute('src', '/a.jpg');
    expect(screen.queryByText('no picture')).not.toBeInTheDocument();
  });

  it('skips missing pictures', () => {
    const { container } = render(<FoodPhoto sources={[null, '', '/b.jpg']} fallback={FALLBACK} />);

    expect(container.querySelector('img')).toHaveAttribute('src', '/b.jpg');
  });

  it('tries the next picture when one cannot be loaded, then the fallback', () => {
    const { container } = render(<FoodPhoto sources={['/a.jpg', '/b.jpg']} fallback={FALLBACK} />);

    fireEvent.error(container.querySelector('img')!);
    expect(container.querySelector('img')).toHaveAttribute('src', '/b.jpg');

    fireEvent.error(container.querySelector('img')!);
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText('no picture')).toBeInTheDocument();
  });

  it('shows the fallback when there is no picture at all', () => {
    render(<FoodPhoto sources={[null]} fallback={FALLBACK} />);

    expect(screen.getByText('no picture')).toBeInTheDocument();
  });
});
