import { render, screen, within } from '@testing-library/react';
import { ShoppingBag, Truck } from 'lucide-react';
import { describe, expect, it } from 'vitest';

import { PageHero } from '@/components/common/PageHero';

describe('PageHero', () => {
  it('shows the heading, the part of the admin area, the actions and the figures at a glance', () => {
    render(
      <PageHero
        title="Orders"
        eyebrow="Sales"
        subtitle="Every order on the platform"
        icon={ShoppingBag}
        actions={<button type="button">Export</button>}
        stats={[
          { label: 'On this page', value: '20', icon: ShoppingBag },
          { label: 'Delivered', value: '1,234', icon: Truck },
        ]}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Orders', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Sales')).toBeInTheDocument();
    expect(screen.getByText('Every order on the platform')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export' })).toBeInTheDocument();
    // The figures show their value at once where motion is not wanted (as in the tests)
    const glance = screen.getByRole('list', { name: 'Orders at a glance' });
    expect(within(glance).getAllByRole('listitem')).toHaveLength(2);
    expect(within(glance).getByText('1,234')).toBeInTheDocument();
    expect(within(glance).getByText('Delivered')).toBeInTheDocument();
  });

  it('has no row of figures when none are given', () => {
    render(<PageHero title="Coupons" icon={ShoppingBag} />);

    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});
