import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CategoryThumbnail } from '@/components/business/Category/CategoryThumbnail';

describe('CategoryThumbnail', () => {
  it('shows the category picture when it has one', () => {
    const { container } = render(
      <CategoryThumbnail category={{ imageUrl: '/images/pizza.jpg' }} />,
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/images/pizza.jpg');
  });

  it('shows a placeholder icon when it has none', () => {
    const { container } = render(<CategoryThumbnail category={{ imageUrl: null }} />);

    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('svg')).not.toBeNull();
  });
});
