import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { PasswordInput } from '@/components/common/PasswordInput';

describe('PasswordInput', () => {
  it('hides what is typed, and is found by its own label', async () => {
    render(<PasswordInput label="Password" />);

    const field = screen.getByLabelText('Password');
    expect(field).toHaveAttribute('type', 'password');
    await userEvent.type(field, 'Eater1234');
    expect(field).toHaveValue('Eater1234');
  });

  it('shows the password with the eye, and hides it again', async () => {
    render(<PasswordInput label="Password" />);
    const field = screen.getByLabelText('Password');

    const show = screen.getByRole('button', { name: 'Show password' });
    expect(show).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(show);
    expect(field).toHaveAttribute('type', 'text');

    const hide = screen.getByRole('button', { name: 'Hide password' });
    expect(hide).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(hide);
    expect(field).toHaveAttribute('type', 'password');
  });

  it('keeps what was typed while the eye is used, and shows the error of the field', async () => {
    render(<PasswordInput label="Password" error="At least 8 characters." />);
    const field = screen.getByLabelText('Password');

    await userEvent.type(field, 'abc');
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));

    expect(field).toHaveValue('abc');
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('At least 8 characters.')).toBeInTheDocument();
  });

  it('does not submit a form when the eye is clicked', async () => {
    let submitted = false;
    render(
      <form
        onSubmit={(event) => {
          event.preventDefault();
          submitted = true;
        }}
      >
        <PasswordInput label="Password" />
      </form>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));

    expect(submitted).toBe(false);
  });
});
