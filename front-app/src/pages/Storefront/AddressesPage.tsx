import { MapPin, Plus } from 'lucide-react';
import { useState } from 'react';

import { toApiError } from '@/api/apiErrorHandler';
import { AddressCard, AddressFormDialog } from '@/components/business/Address';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { ConfirmationDialog } from '@/components/common/ConfirmationDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useAddresses, useDeleteAddress, useMakeDefaultAddress } from '@/hooks/api/addressHooks';
import type { Address } from '@/types/shopping';
import { ADDRESS_TYPE_LABELS } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** user-service keeps at most this many addresses per customer. */
const MAX_ADDRESSES = 10;

/** "My addresses": add, change, delete, and choose the default (offered first at checkout). */
export default function AddressesPage() {
  const addresses = useAddresses();
  const makeDefault = useMakeDefaultAddress();
  const remove = useDeleteAddress();
  const { showToast } = useToast();
  const [editing, setEditing] = useState<Address | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Address | null>(null);
  const count = addresses.data?.length ?? 0;

  const failed = (error: unknown) => showToast(toApiError(error).userMessage, 'error');

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="My addresses"
        subtitle="Where your food can be delivered"
        actions={
          <div className="flex flex-col items-end gap-1">
            <Button
              onClick={() => setEditing('new')}
              disabled={!addresses.data || count >= MAX_ADDRESSES}
              leftIcon={<Plus aria-hidden="true" className="size-4" />}
            >
              Add address
            </Button>
            {count >= MAX_ADDRESSES && (
              <p className="text-xs text-ink-soft">At most {MAX_ADDRESSES}: delete one first.</p>
            )}
          </div>
        }
      />
      <AsyncContent
        isLoading={addresses.isPending}
        error={addresses.error}
        isEmpty={count === 0}
        errorTitle={ERROR_MESSAGES.ADDRESSES_LOAD_FAILED}
        onRetry={() => void addresses.refetch()}
        isRetrying={addresses.isFetching}
        loading={<SkeletonLoader lines={4} className="rounded-2xl bg-surface p-6" />}
        empty={
          <EmptyState
            icon={MapPin}
            title="No addresses yet"
            message="Add where your food should go; you can also add one at checkout."
          />
        }
      >
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {addresses.data?.map((address) => (
            <AddressCard
              key={address.id}
              address={address}
              actions={
                <>
                  <Button variant="outline" size="sm" onClick={() => setEditing(address)}>
                    Change
                  </Button>
                  {!address.defaultAddress && (
                    <Button
                      variant="ghost"
                      size="sm"
                      isLoading={makeDefault.isPending && makeDefault.variables === address.id}
                      onClick={() =>
                        makeDefault.mutate(address.id, {
                          onSuccess: () =>
                            showToast(
                              `${ADDRESS_TYPE_LABELS[address.type]} is now your default address.`,
                            ),
                          onError: failed,
                        })
                      }
                    >
                      Make default
                    </Button>
                  )}
                  <Button variant="ghost" size="sm" onClick={() => setDeleting(address)}>
                    Delete
                  </Button>
                </>
              }
            />
          ))}
        </div>
      </AsyncContent>

      {editing !== null && (
        <AddressFormDialog
          address={editing === 'new' ? undefined : editing}
          isFirst={editing === 'new' && count === 0}
          onSaved={() => showToast(editing === 'new' ? 'Address added.' : 'Address saved.')}
          onClose={() => setEditing(null)}
        />
      )}
      <ConfirmationDialog
        open={deleting !== null}
        title="Delete this address?"
        message={
          deleting?.defaultAddress
            ? 'It is your default address: your newest other address becomes the default. Orders already placed keep their address.'
            : 'Orders already placed keep their address.'
        }
        confirmLabel="Delete"
        tone="danger"
        isLoading={remove.isPending}
        onConfirm={() => {
          if (deleting) {
            remove.mutate(deleting.id, {
              onSuccess: () => showToast('Address deleted.'),
              onError: failed,
              onSettled: () => setDeleting(null),
            });
          }
        }}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}
