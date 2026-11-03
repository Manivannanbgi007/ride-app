import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';

import { TabPanel, Tabs } from '@/components/common/Tabs';
import { useTabParam } from '@/hooks/useTabParam';
import { renderWithProviders } from '@/test/renderWithProviders';

const TABS = ['deliveries', 'delayed', 'partners'] as const;

function TabbedPage() {
  const [tab, setTab] = useTabParam(TABS);
  const { search } = useLocation();
  return (
    <>
      <Tabs
        label="Deliveries"
        tabs={[
          { id: 'deliveries', label: 'Deliveries' },
          { id: 'delayed', label: 'Delayed now', count: 3 },
          { id: 'partners', label: 'Partners' },
        ]}
        activeId={tab}
        onChange={setTab}
      />
      <TabPanel id={tab}>
        <p>Showing {tab}</p>
      </TabPanel>
      <p>Address: {search}</p>
    </>
  );
}

const renderAt = (route: string) =>
  renderWithProviders(
    <Routes>
      <Route path="/admin/deliveries" element={<TabbedPage />} />
    </Routes>,
    { route },
  );

describe('Tabs with the tab in the address', () => {
  it('opens the first tab by default, and keeps the chosen tab in the address', async () => {
    renderAt('/admin/deliveries');
    expect(screen.getByRole('tab', { name: 'Deliveries' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Showing deliveries');

    await userEvent.click(screen.getByRole('tab', { name: 'Partners' }));

    expect(screen.getByRole('tabpanel')).toHaveTextContent('Showing partners');
    expect(screen.getByText('Address: ?tab=partners')).toBeInTheDocument();
  });

  it('opens the tab named in the address, and moves with the arrow keys', async () => {
    renderAt('/admin/deliveries?tab=delayed');
    const delayed = screen.getByRole('tab', { name: 'Delayed now (3)' });
    expect(delayed).toHaveAttribute('aria-selected', 'true');

    delayed.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Partners' })).toHaveFocus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Deliveries' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('opens the first tab for an unknown tab name', () => {
    renderAt('/admin/deliveries?tab=nonsense');
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Showing deliveries');
  });
});
