import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RatingBadge } from '@/components/business/Rating';

describe('RatingBadge', () => {
  it('shows the average with one decimal and the number of reviews', () => {
    render(<RatingBadge rating={{ average: 4.3, count: 12 }} />);

    const badge = screen.getByRole('img', { name: 'Rated 4.3 out of 5, 12 reviews' });
    expect(badge).toHaveTextContent('4.3 (12)');
  });

  it('says "No ratings yet" for a restaurant or dish nobody has rated', () => {
    render(<RatingBadge rating={null} />);

    expect(screen.getByRole('img', { name: 'No ratings yet' })).toHaveTextContent('No ratings yet');
  });

  it('shows nothing while the rating is not known', () => {
    const { container } = render(<RatingBadge rating={undefined} />);

    expect(container).toBeEmptyDOMElement();
  });
});
