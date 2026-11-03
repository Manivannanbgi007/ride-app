import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AuditLogListPage from '@/pages/AuditLog/AuditLogListPage';
import AuditRecordPage from '@/pages/AuditLog/AuditRecordPage';
import { auditLogService } from '@/services/auditLogService';
import { pageOf } from '@/test/fixtures';
import { chooseOption, optionsOf } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { AuditRecord } from '@/types/audit';

vi.mock('@/services/auditLogService', () => ({
  auditLogService: {
    searchAuditLogs: vi.fn(),
    getActions: vi.fn(),
    getAuditRecord: vi.fn(),
    backfill: vi.fn(),
  },
}));

function auditRecord(overrides: Partial<AuditRecord> = {}): AuditRecord {
  return {
    auditId: 900,
    service: 'restaurant-service',
    sourceAuditId: 12,
    adminUserId: 1,
    action: 'RESTAURANT_SUSPENDED',
    entityType: 'RESTAURANT',
    entityId: '501',
    oldValue: 'ACTIVE',
    newValue: 'SUSPENDED',
    reason: 'Food safety complaint',
    timestamp: '2026-10-08T10:00:00Z',
    ipAddress: '127.0.0.1',
    requestId: 'req-1',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(auditLogService.searchAuditLogs).mockResolvedValue(pageOf([auditRecord()]));
  vi.mocked(auditLogService.getActions).mockResolvedValue([
    { service: 'order-service', action: 'ORDER_CANCELLED_BY_ADMIN', records: 4 },
    { service: 'restaurant-service', action: 'RESTAURANT_APPROVED', records: 9 },
    { service: 'restaurant-service', action: 'RESTAURANT_SUSPENDED', records: 3 },
  ]);
});

describe('AuditLogListPage', () => {
  it('lists admin actions with a link to the record, and filters by service and action', async () => {
    renderWithProviders(<AuditLogListPage />);

    expect(await screen.findByText('Food safety complaint')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Restaurant #501' })).toHaveAttribute(
      'href',
      '/admin/restaurants/501',
    );
    expect(auditLogService.searchAuditLogs).toHaveBeenCalledWith({ sort: 'auditedAt,desc' }, 0, 20);

    await chooseOption(await screen.findByLabelText('Service'), 'Restaurants');
    expect(auditLogService.searchAuditLogs).toHaveBeenLastCalledWith(
      { sort: 'auditedAt,desc', service: 'restaurant-service' },
      0,
      20,
    );
    // Only the chosen service's actions, with how many records each has
    expect(await optionsOf(screen.getByLabelText('Action'))).toEqual([
      'All actions',
      'Restaurant approved (9)',
      'Restaurant suspended (3)',
    ]);
  });

  it('loads older records with a reason and says which services could not be reached', async () => {
    vi.mocked(auditLogService.backfill).mockResolvedValue({
      republished: { 'order-service': 4, 'restaurant-service': 12 },
      unavailable: ['payment-service'],
    });
    renderWithProviders(<AuditLogListPage />);

    await userEvent.click(screen.getByRole('button', { name: 'Load older records' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Reason'), 'Records before Phase 13');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Load records' }));

    expect(auditLogService.backfill).toHaveBeenCalledWith('Records before Phase 13');
    expect(await screen.findByText(/16 records sent again by 2 services/)).toBeInTheDocument();
    expect(screen.getByText(/Not reached: Payments/)).toBeInTheDocument();
  });
});

describe('AuditRecordPage', () => {
  it('shows a record in full with the other changes of the same request', async () => {
    vi.mocked(auditLogService.getAuditRecord).mockResolvedValue({
      record: auditRecord({
        action: 'ORDER_CANCELLED_BY_ADMIN',
        entityType: 'ORDER',
        entityId: '42',
      }),
      receivedAt: '2026-10-08T10:00:02Z',
      sameRequest: [
        auditRecord({
          auditId: 901,
          service: 'payment-service',
          action: 'REFUND_INITIATED',
          entityType: 'PAYMENT',
          entityId: '77',
        }),
      ],
    });
    renderWithProviders(
      <Routes>
        <Route path="/admin/audit-logs/:auditId" element={<AuditRecordPage />} />
      </Routes>,
      { route: '/admin/audit-logs/900' },
    );

    expect(
      await screen.findByRole('heading', { name: 'Order cancelled by admin' }),
    ).toBeInTheDocument();
    expect(screen.getByText('restaurant-service #12', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('Refund initiated')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Payment #77' })).toHaveAttribute(
      'href',
      '/admin/payments/77',
    );
  });
});
