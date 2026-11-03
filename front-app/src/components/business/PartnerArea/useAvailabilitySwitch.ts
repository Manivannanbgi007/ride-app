import { toApiError } from '@/api/apiErrorHandler';
import { useToast } from '@/context/ToastContext';
import { useChangeAvailability } from '@/hooks/api/partnerHooks';

/**
 * Going online or offline, with the toast that says what it means (or why the server refused). Shared by the
 * hero's switch and the "Go online" button of the offline panel.
 */
export function useAvailabilitySwitch() {
  const change = useChangeAvailability();
  const { showToast } = useToast();

  const switchTo = (goOnline: boolean) => {
    change.mutate(goOnline ? 'AVAILABLE' : 'OFFLINE', {
      onSuccess: () =>
        showToast(
          goOnline
            ? 'You are online: deliveries in your city will be offered to you.'
            : 'You are offline: no new deliveries will be offered to you.',
        ),
      onError: (error) => showToast(toApiError(error).userMessage, 'error'),
    });
  };

  return { switchTo, isPending: change.isPending };
}
