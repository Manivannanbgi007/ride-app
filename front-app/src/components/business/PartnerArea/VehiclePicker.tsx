import { Check } from 'lucide-react';
import { useId } from 'react';

import { VEHICLE_ICONS } from '@/components/business/PartnerArea/vehicleIcons';
import { VEHICLE_TYPES, type VehicleType } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { VEHICLE_TYPE_LABELS } from '@/utils/constants';
import { plateTextOf } from '@/utils/partnerAreaUtils';

interface VehiclePickerProps {
  value: VehicleType | undefined;
  onChange: (value: VehicleType) => void;
  error?: string;
}

/**
 * The partner's vehicle as four tiles with a picture each. Real radio buttons underneath (hidden): the arrow keys
 * move between them and screen readers announce the group "Vehicle".
 */
export function VehiclePicker({ value, onChange, error }: VehiclePickerProps) {
  const name = useId();
  const errorId = `${name}-error`;
  return (
    <fieldset aria-describedby={error ? errorId : undefined} className="flex flex-col">
      <legend className="mb-3 text-sm font-semibold">Vehicle</legend>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {VEHICLE_TYPES.map((type) => {
          const Icon = VEHICLE_ICONS[type];
          const checked = value === type;
          return (
            <label
              key={type}
              className={cn(
                'relative flex cursor-pointer flex-col items-center gap-2.5 rounded-2xl border-2 px-3 py-4 text-center',
                'transition-[border-color,background-color,box-shadow,translate] duration-200',
                'has-focus-visible:ring-4 has-focus-visible:ring-brand-500/30 motion-safe:hover:-translate-y-0.5',
                checked
                  ? 'border-brand-500 bg-brand-50 shadow-brand'
                  : 'border-line bg-surface hover:border-brand-300',
              )}
            >
              <input
                type="radio"
                name={name}
                value={type}
                checked={checked}
                onChange={() => onChange(type)}
                className="sr-only"
              />
              <span
                aria-hidden="true"
                className={cn(
                  'flex size-12 items-center justify-center rounded-xl transition-colors duration-200',
                  checked ? 'bg-brand-500 text-on-brand' : 'bg-page text-ink-soft',
                )}
              >
                <Icon className="size-6" />
              </span>
              <span className="text-sm font-semibold">{VEHICLE_TYPE_LABELS[type]}</span>
              {checked && (
                <span
                  aria-hidden="true"
                  className="absolute top-2 right-2 flex size-5 items-center justify-center rounded-full bg-brand-500 text-on-brand"
                >
                  <Check className="size-3" strokeWidth={3} />
                </span>
              )}
            </label>
          );
        })}
      </div>
      {error && (
        <p id={errorId} className="mt-2 text-xs text-danger-500">
          {error}
        </p>
      )}
    </fieldset>
  );
}

/**
 * What the number plate will look like (an Indian plate: white, black letters, the blue "IND" band). Decoration
 * only: the field above it is what is read out.
 */
export function NumberPlate({ number }: { number: string }) {
  const text = plateTextOf(number);
  return (
    <div
      aria-hidden="true"
      className="inline-flex h-14 items-stretch overflow-hidden rounded-lg border-2 border-black bg-white text-black shadow-md"
    >
      <span className="flex w-7 rotate-180 items-center justify-center bg-blue-700 text-[9px] font-bold tracking-wider text-white [writing-mode:vertical-rl]">
        IND
      </span>
      <span
        className={cn(
          'flex items-center px-4 font-mono text-xl font-bold tracking-[0.15em] whitespace-nowrap sm:text-2xl',
          !text && 'text-black/30',
        )}
      >
        {text || 'KA 01 AB 1234'}
      </span>
    </div>
  );
}
