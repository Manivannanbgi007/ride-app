import { Link } from 'react-router';

import { PartnerStatusBadge } from '@/components/business/StatusBadges';
import { Table, type TableColumn } from '@/components/common/Table';
import type { Partner } from '@/types/delivery';
import { NOT_AVAILABLE, routeTo, VEHICLE_TYPE_LABELS } from '@/utils/constants';
import { formatDateTime } from '@/utils/dateUtils';
import { formatNumber, initialsOf } from '@/utils/formatUtils';

const COLUMNS: readonly TableColumn<Partner>[] = [
  {
    header: 'Partner',
    render: (partner) => (
      <Link to={routeTo.partnerDetails(partner.userId)} className="group flex items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-info-50 text-xs font-bold text-info-500">
          {initialsOf(partner.fullName)}
        </span>
        <span>
          <span className="block font-semibold group-hover:text-brand-text group-hover:underline">
            {partner.fullName}
          </span>
          <span className="block text-xs text-ink-soft">{partner.phone ?? NOT_AVAILABLE}</span>
        </span>
      </Link>
    ),
  },
  { header: 'City', render: (partner) => partner.city ?? NOT_AVAILABLE },
  {
    header: 'Vehicle',
    render: (partner) =>
      partner.vehicleType
        ? `${VEHICLE_TYPE_LABELS[partner.vehicleType]} ${partner.vehicleNumber ?? ''}`
        : 'Not given yet',
  },
  {
    header: 'Status',
    render: (partner) => (
      <div className="flex flex-col items-start gap-1">
        <PartnerStatusBadge status={partner.status} />
        {partner.currentDeliveryId && (
          <Link
            to={routeTo.deliveryDetails(partner.currentDeliveryId)}
            className="text-xs text-brand-text hover:underline"
          >
            Delivery #{partner.currentDeliveryId}
          </Link>
        )}
      </div>
    ),
  },
  {
    header: 'Delivered',
    align: 'right',
    render: (partner) => formatNumber(partner.completedDeliveries),
  },
  {
    header: 'Available since',
    render: (partner) =>
      partner.availableSince ? formatDateTime(partner.availableSince) : NOT_AVAILABLE,
  },
];

interface PartnerTableProps {
  partners: readonly Partner[];
  isUpdating?: boolean;
}

/** Delivery partners as a table; the name opens the partner, a busy partner links to their delivery. */
export function PartnerTable({ partners, isUpdating }: PartnerTableProps) {
  return (
    <Table
      caption="Delivery partners"
      columns={COLUMNS}
      rows={partners}
      rowKey={(partner) => partner.userId}
      isUpdating={isUpdating}
    />
  );
}
