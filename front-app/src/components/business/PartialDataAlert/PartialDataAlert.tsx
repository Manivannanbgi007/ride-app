import { Alert } from '@/components/common/Alert';
import { SERVICE_LABELS } from '@/utils/constants';

/**
 * admin-service answers with what it has when one of the services it asks is down, and names the missing ones
 * in `unavailable`. This tells the admin which figures are missing, instead of showing them as zero.
 */
export function PartialDataAlert({ unavailable }: { unavailable: readonly string[] }) {
  if (unavailable.length === 0) {
    return null;
  }
  const names = unavailable.map((service) => SERVICE_LABELS[service] ?? service).join(', ');
  return (
    <Alert tone="warning" title="Some figures could not be loaded">
      {names}: not available right now. Their figures show "—" until they are back; the rest is up
      to date.
    </Alert>
  );
}
