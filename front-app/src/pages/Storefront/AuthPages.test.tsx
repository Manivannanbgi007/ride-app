import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import OwnerSignInPage from '@/pages/Owner/OwnerSignInPage';
import RegisterPage from '@/pages/Storefront/RegisterPage';
import SignInPage from '@/pages/Storefront/SignInPage';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/services/userService', () => ({ userService: { login: vi.fn(), register: vi.fn() } }));

describe('the sign-in and registration pages', () => {
  it('sign-in: one heading, what the account is for, the form and the way to register', () => {
    renderWithProviders(<SignInPage />, { route: '/customer/sign-in' });

    expect(screen.getByRole('heading', { name: 'Sign in', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Follow your order until it is delivered')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email');
    // The password is hidden until the eye is used
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: 'Show password' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute(
      'href',
      '/customer/register',
    );
  });

  it('sign-in keeps the page to go back to in the link to register', () => {
    renderWithProviders(<SignInPage />, {
      route: '/customer/sign-in?returnTo=%2Fcustomer%2Fcart',
    });

    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute(
      'href',
      '/customer/register?returnTo=%2Fcustomer%2Fcart',
    );
  });

  it('register: the strength of the password shows while typing, and both passwords can be shown', async () => {
    renderWithProviders(<RegisterPage />, { route: '/customer/register' });

    expect(
      screen.getByRole('heading', { name: 'Create an account', level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Use at least 8 characters, with a letter and a digit.'),
    ).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Password'), 'abc');
    expect(screen.getByText('Password strength: Weak')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Password'));
    await userEvent.type(screen.getByLabelText('Password'), 'Eater1234!longer');
    expect(screen.getByText('Password strength: Strong')).toBeInTheDocument();

    // One eye for each of the two password fields
    expect(screen.getAllByRole('button', { name: 'Show password' })).toHaveLength(2);
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/customer/sign-in',
    );
  });

  it('owner sign-in: its own heading and pitch, and a way to the customer sign-in', () => {
    renderWithProviders(<OwnerSignInPage />, { route: '/owner/sign-in' });

    expect(
      screen.getByRole('heading', { name: 'Restaurant sign in', level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByText('Run your restaurant from one place.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Customer sign in' })).toHaveAttribute(
      'href',
      '/customer/sign-in',
    );
  });
});
