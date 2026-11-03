import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Check, Trash2 } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';

import { ActionMenu, type ActionMenuItem } from '@/components/common/ActionMenu';
import { renderWithProviders } from '@/test/renderWithProviders';

const setup = (items?: ActionMenuItem[]) => {
  const onApprove = vi.fn();
  const onDelete = vi.fn();
  const menuItems: ActionMenuItem[] = items ?? [
    { key: 'open', label: 'Open it', icon: Check, to: '/somewhere' },
    { key: 'approve', label: 'Approve', icon: Check, onSelect: onApprove },
    { key: 'delete', label: 'Delete', icon: Trash2, tone: 'danger', onSelect: onDelete },
  ];
  renderWithProviders(
    <div>
      <ActionMenu label="Actions for thing #1" title="Thing #1" items={menuItems}>
        <span>Placed</span>
      </ActionMenu>
      <button type="button">elsewhere</button>
    </div>,
  );
  return { onApprove, onDelete };
};

describe('ActionMenu', () => {
  it('is closed until it is asked for, and opens from the badge or from the "..." button', async () => {
    setup();
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Placed' }));
    expect(screen.getByRole('menu', { name: 'Actions for thing #1' })).toBeInTheDocument();
    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      'Open it',
      'Approve',
      'Delete',
    ]);

    // The same trigger again closes it; the other one opens it
    await userEvent.click(screen.getByRole('button', { name: 'Placed' }));
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Actions for thing #1' }));
    expect(screen.getByRole('menu')).toBeInTheDocument();
  });

  it('says it is a menu to screen readers, and whether it is open', async () => {
    setup();
    const trigger = screen.getByRole('button', { name: 'Actions for thing #1' });
    expect(trigger).toHaveAttribute('aria-haspopup', 'menu');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(trigger);

    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(trigger).toHaveAttribute('aria-controls', screen.getByRole('menu').id);
  });

  it('does what an item says and closes; a link item goes to its page', async () => {
    const { onApprove, onDelete } = setup();
    await userEvent.click(screen.getByRole('button', { name: 'Actions for thing #1' }));

    expect(screen.getByRole('menuitem', { name: 'Open it' })).toHaveAttribute('href', '/somewhere');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Approve' }));

    expect(onApprove).toHaveBeenCalledTimes(1);
    expect(onDelete).not.toHaveBeenCalled();
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('opens with the down arrow, moves with the arrows, Home and End, and chooses with Enter', async () => {
    const { onDelete } = setup();
    screen.getByRole('button', { name: 'Actions for thing #1' }).focus();

    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'Open it' })).toHaveFocus();

    await userEvent.keyboard('{ArrowDown}{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveFocus();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'Open it' })).toHaveFocus(); // around
    await userEvent.keyboard('{End}');
    expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveFocus();
    await userEvent.keyboard('{Home}{ArrowUp}');
    expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveFocus(); // up from the first
    await userEvent.keyboard('{Enter}');

    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('closes with Escape and gives the focus back to the button; Tab leaves it', async () => {
    setup();
    const trigger = screen.getByRole('button', { name: 'Actions for thing #1' });
    await userEvent.click(trigger);
    expect(screen.getByRole('menuitem', { name: 'Open it' })).toHaveFocus();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();

    await userEvent.click(trigger);
    await userEvent.keyboard('{Tab}');
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('closes on a click elsewhere', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'Actions for thing #1' }));

    await userEvent.click(screen.getByRole('button', { name: 'elsewhere' }));

    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('marks what cannot be undone, and shows the menu where there is room', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'Actions for thing #1' }));

    expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveClass('text-danger-text');
    expect(screen.getByRole('menuitem', { name: 'Approve' })).not.toHaveClass('text-danger-text');
    // Drawn over the page (a portal), so no table or card can cut it
    expect(screen.getByRole('menu').parentElement).toBe(document.body);
  });
});
