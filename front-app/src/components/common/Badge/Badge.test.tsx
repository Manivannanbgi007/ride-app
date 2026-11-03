import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { RoleBadge, UserStatusBadge } from '@/components/business/StatusBadges';
import { Badge } from '@/components/common/Badge';

describe('Badge', () => {
  it('gives every tone its own clear background and text colour', () => {
    render(
      <>
        <Badge tone="success">ok</Badge>
        <Badge tone="danger">bad</Badge>
        <Badge tone="warning">wait</Badge>
        <Badge tone="info">going</Badge>
        <Badge>none</Badge>
      </>,
    );

    expect(screen.getByText('ok')).toHaveClass('bg-brand-200', 'text-brand-text-deep');
    expect(screen.getByText('bad')).toHaveClass('bg-danger-100', 'text-danger-ink');
    expect(screen.getByText('wait')).toHaveClass('bg-warning-100', 'text-warning-ink');
    expect(screen.getByText('going')).toHaveClass('bg-info-100', 'text-info-ink');
    expect(screen.getByText('none')).toHaveClass('bg-line', 'text-ink');
  });

  it('is medium-weight text in a padded pill, and has a dot only when asked', () => {
    const { container } = render(
      <>
        <Badge tone="success">plain</Badge>
        <Badge tone="success" dot>
          with dot
        </Badge>
      </>,
    );

    expect(screen.getByText('plain')).toHaveClass('rounded-full', 'px-3', 'py-1', 'font-semibold');
    expect(container.querySelectorAll('[aria-hidden="true"]')).toHaveLength(1);
  });
});

describe('status badges of the customer list', () => {
  it('shows Active in green and Inactive in red, each with a dot', () => {
    render(
      <>
        <UserStatusBadge status="ACTIVE" />
        <UserStatusBadge status="INACTIVE" />
        <RoleBadge role="CUSTOMER" />
      </>,
    );

    expect(screen.getByText('Active')).toHaveClass('bg-brand-200');
    expect(screen.getByText('Inactive')).toHaveClass('bg-danger-100');
    expect(screen.getByText('Active').querySelector('[aria-hidden="true"]')).toBeInTheDocument();
    expect(screen.getByText('Inactive').querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });
});
