import { Bike, Car, Motorbike, Scooter, type LucideIcon } from 'lucide-react';

import type { VehicleType } from '@/types/delivery';

/** The icon of each kind of vehicle (the partner's chips, the vehicle picker). */
export const VEHICLE_ICONS: Record<VehicleType, LucideIcon> = {
  BICYCLE: Bike,
  SCOOTER: Scooter,
  MOTORCYCLE: Motorbike,
  CAR: Car,
};
