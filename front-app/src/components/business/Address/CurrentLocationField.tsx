import { LocateFixed, MapPin, X } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { geolocationSupport, PERMISSION_DENIED, roundCoordinate } from '@/utils/geolocationUtils';

/** A position this exact or better (metres) is not worth a remark */
const GOOD_ACCURACY_METERS = 100;

export interface AddressPin {
  latitude: number;
  longitude: number;
}

interface CurrentLocationFieldProps {
  /** The address's map pin, or null */
  value: AddressPin | null;
  onChange: (pin: AddressPin | null) => void;
}

/**
 * The address's map pin from the device's position ("Use my current location"): the delivery partner and the
 * tracking map then know exactly where to come. The written address stays what the customer typed (there is no
 * service that turns a position into an address). Nothing is asked for until the button is pressed.
 */
export function CurrentLocationField({ value, onChange }: CurrentLocationFieldProps) {
  const [support] = useState(geolocationSupport);
  const [locating, setLocating] = useState(false);
  const [accuracy, setAccuracy] = useState<number | null>(null);
  const [problem, setProblem] = useState<string | null>(null);

  const locate = () => {
    setLocating(true);
    setProblem(null);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocating(false);
        setAccuracy(Math.round(coords.accuracy));
        onChange({
          latitude: roundCoordinate(coords.latitude),
          longitude: roundCoordinate(coords.longitude),
        });
      },
      (error) => {
        setLocating(false);
        setProblem(
          error.code === PERMISSION_DENIED
            ? 'Location access is blocked for this site. Allow it in your browser settings, or save the address without a pin.'
            : 'Your position could not be found. Try again, or save the address without a pin.',
        );
      },
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    );
  };

  return (
    <div className="flex flex-col gap-2 rounded-2xl bg-page p-4">
      <p className="text-sm font-semibold">Map pin (optional)</p>
      {value ? (
        <div className="flex items-start gap-3">
          <MapPin aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-text" />
          <p className="min-w-0 flex-1 text-sm">
            Pin set at {value.latitude.toFixed(5)}, {value.longitude.toFixed(5)}
            {accuracy !== null && accuracy > GOOD_ACCURACY_METERS && (
              <span className="block text-warning-text">
                Accurate to about {accuracy} m only: check it is the right building.
              </span>
            )}
          </p>
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<X aria-hidden="true" className="size-4" />}
            onClick={() => {
              setAccuracy(null);
              onChange(null);
            }}
          >
            Remove pin
          </Button>
        </div>
      ) : (
        <p className="text-sm text-ink-soft">
          Stand at this address and use your location: your delivery partner finds you on the map.
        </p>
      )}
      {support === 'ok' ? (
        <Button
          variant="outline"
          size="sm"
          className="self-start"
          leftIcon={<LocateFixed aria-hidden="true" className="size-4" />}
          isLoading={locating}
          onClick={locate}
        >
          {value ? 'Use my current location again' : 'Use my current location'}
        </Button>
      ) : (
        <p className="text-sm text-ink-soft">
          {support === 'insecure'
            ? 'Your location can be used only on a secure (https) page.'
            : 'This browser cannot share your location.'}
        </p>
      )}
      {problem && (
        <p role="alert" className="text-sm text-danger-text">
          {problem}
        </p>
      )}
    </div>
  );
}
