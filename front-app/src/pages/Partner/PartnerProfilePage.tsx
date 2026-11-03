import { zodResolver } from '@hookform/resolvers/zod';
import { Building2, CircleCheck, Hash, Mail, MapPin, Phone, RotateCcw } from 'lucide-react';
import type { ReactNode } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useNavigate } from 'react-router';

import { NumberPlate, VehiclePicker } from '@/components/business/PartnerArea';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { DescriptionList } from '@/components/common/DescriptionList';
import { Input } from '@/components/common/Input';
import { Skeleton } from '@/components/common/Loader';
import { useToast } from '@/context/ToastContext';
import { usePartnerProfile, useUpdatePartnerProfile } from '@/hooks/api/partnerHooks';
import { useAppSelector } from '@/store/hooks';
import { selectPartner } from '@/store/slices/sessionSlice';
import type { Partner } from '@/types/delivery';
import { cn } from '@/utils/classNames';
import { PARTNER_ROUTES } from '@/utils/constants';
import { formatDateTime, formatMonthYear } from '@/utils/dateUtils';
import { formatNumber, initialsOf } from '@/utils/formatUtils';
import { PARTNER_STATUS_WORDS } from '@/utils/partnerAreaUtils';
import {
  partnerProfileFormSchema,
  showServerErrors,
  type PartnerProfileForm,
} from '@/utils/validationUtils';

/** The details a partner needs before going online, and the phone they can be reached on. */
function missingDetailsOf(partner: Partner): string[] {
  const missing: string[] = [];
  if (!partner.vehicleType) {
    missing.push('vehicle');
  }
  if (!partner.vehicleNumber) {
    missing.push('vehicle number');
  }
  if (!partner.city) {
    missing.push('city');
  }
  if (!partner.phone) {
    missing.push('phone');
  }
  return missing;
}

const TOTAL_DETAILS = 4;

/**
 * "Profile": who the partner is to the platform, and the vehicle and the city they deliver in (needed before going
 * online: deliveries are offered from restaurants in that city).
 */
export default function PartnerProfilePage() {
  const profile = usePartnerProfile();

  return (
    <AsyncContent
      isLoading={profile.isPending}
      error={profile.data ? null : profile.error}
      errorTitle="Your partner profile could not be loaded"
      onRetry={() => void profile.refetch()}
      isRetrying={profile.isFetching}
      loading={
        <div role="status" aria-label="Loading" className="flex flex-col gap-6">
          <Skeleton className="h-56 rounded-3xl" />
          <div className="grid gap-6 lg:grid-cols-3">
            <Skeleton className="h-96 rounded-3xl lg:col-span-2" />
            <Skeleton className="h-72 rounded-3xl" />
          </div>
        </div>
      }
    >
      {profile.data && (
        <div className="flex flex-col gap-6">
          <ProfileHero partner={profile.data} />
          <div className="grid items-start gap-6 lg:grid-cols-3">
            {/* Keyed by version: the form starts again from what was saved */}
            <div className="lg:col-span-2">
              <ProfileForm key={profile.data.version} partner={profile.data} />
            </div>
            <AccountCard partner={profile.data} />
          </div>
        </div>
      )}
    </AsyncContent>
  );
}

/** The partner's card: name, status, how complete the profile is, and a few numbers. */
function ProfileHero({ partner }: { partner: Partner }) {
  const online = partner.status === 'AVAILABLE' || partner.status === 'BUSY';
  const missing = missingDetailsOf(partner);
  const done = TOTAL_DETAILS - missing.length;
  const percent = Math.round((done / TOTAL_DETAILS) * 100);
  const facts = [
    { label: 'Deliveries', value: formatNumber(partner.completedDeliveries) },
    { label: 'Partner since', value: formatMonthYear(partner.createdAt) },
    { label: 'Delivers in', value: partner.city ?? '—' },
  ];
  return (
    <section
      aria-labelledby="partner-profile-name"
      className="relative isolate overflow-hidden rounded-3xl bg-linear-to-br from-brand-600 via-brand-700 to-brand-900 p-6 text-white shadow-card sm:p-8"
    >
      <span
        aria-hidden="true"
        className="absolute -top-20 -right-12 -z-10 size-64 rounded-full bg-white/10 blur-2xl"
      />
      <span
        aria-hidden="true"
        className="absolute -bottom-28 left-1/3 -z-10 size-72 rounded-full bg-lime-500/20 blur-3xl"
      />
      <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-4 sm:gap-5">
          <span
            aria-hidden="true"
            className="flex size-18 shrink-0 items-center justify-center rounded-full bg-white/15 text-2xl font-extrabold ring-4 ring-white/25 backdrop-blur-sm sm:size-20"
          >
            {initialsOf(partner.fullName)}
          </span>
          <div className="flex min-w-0 flex-1 flex-col items-start gap-1.5">
            <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-semibold backdrop-blur-sm">
              <span
                aria-hidden="true"
                className={cn(
                  'size-2 rounded-full',
                  online && 'bg-lime-500',
                  partner.status === 'OFFLINE' && 'bg-white/60',
                  partner.status === 'SUSPENDED' && 'bg-danger-500',
                )}
              />
              {PARTNER_STATUS_WORDS[partner.status]}
            </span>
            <h1
              id="partner-profile-name"
              className="max-w-full truncate text-2xl font-extrabold tracking-tight sm:text-3xl"
            >
              {partner.fullName}
            </h1>
            <p className="text-sm text-white/80">Delivery partner</p>
          </div>
        </div>

        <div className="w-full max-w-sm rounded-2xl bg-white/10 p-4 backdrop-blur-sm">
          <div className="flex items-center justify-between gap-3 text-sm font-semibold">
            <span>Profile completeness</span>
            <span className="tabular-nums">{percent}%</span>
          </div>
          <div
            role="progressbar"
            aria-label="Profile completeness"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percent}
            className="mt-2 h-2 overflow-hidden rounded-full bg-white/20"
          >
            <div
              className="h-full rounded-full bg-lime-500 transition-[width] duration-500"
              style={{ width: `${percent}%` }}
            />
          </div>
          <p className="mt-2 flex items-center gap-1.5 text-xs text-white/80">
            {missing.length === 0 ? (
              <>
                <CircleCheck aria-hidden="true" className="size-3.5" />
                All details are filled in.
              </>
            ) : (
              <>Still to add: {missing.join(', ')}.</>
            )}
          </p>
        </div>
      </div>
      <dl className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {facts.map((fact, index) => (
          <div
            key={fact.label}
            className={cn(
              'rounded-2xl bg-white/10 px-4 py-3 backdrop-blur-sm',
              // Three facts in two columns: the last one takes the whole row
              index === facts.length - 1 && 'col-span-2 sm:col-span-1',
            )}
          >
            <dt className="text-xs font-medium text-white/75">{fact.label}</dt>
            <dd className="mt-0.5 truncate text-lg font-extrabold sm:text-xl">{fact.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/** Who the platform knows: contact, status and when the account last changed. */
function AccountCard({ partner }: { partner: Partner }) {
  const signedIn = useAppSelector(selectPartner);
  return (
    <Card title="Account" subtitle="How the platform and customers reach you.">
      <div className="flex flex-col gap-5">
        <ul className="flex flex-col gap-3 text-sm">
          <li className="flex items-center gap-3">
            <ContactIcon icon={Mail} />
            <span className="min-w-0 truncate">{signedIn?.email ?? '—'}</span>
          </li>
          <li className="flex items-center gap-3">
            <ContactIcon icon={Phone} />
            <span>{partner.phone ?? '—'}</span>
          </li>
          <li className="flex items-center gap-3">
            <ContactIcon icon={MapPin} />
            <span>{partner.city ?? '—'}</span>
          </li>
        </ul>
        <DescriptionList
          columns={1}
          items={[
            { label: 'Status', value: PARTNER_STATUS_WORDS[partner.status] },
            ...(partner.statusReason ? [{ label: 'Note', value: partner.statusReason }] : []),
            { label: 'Last updated', value: formatDateTime(partner.updatedAt) },
          ]}
        />
      </div>
    </Card>
  );
}

function ContactIcon({ icon: Icon }: { icon: typeof Mail }) {
  return (
    <span
      aria-hidden="true"
      className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-text-strong"
    >
      <Icon className="size-4.5" />
    </span>
  );
}

function ProfileForm({ partner }: { partner: Partner }) {
  const save = useUpdatePartnerProfile();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<PartnerProfileForm>({
    resolver: zodResolver(partnerProfileFormSchema),
    defaultValues: {
      vehicleType: partner.vehicleType ?? undefined,
      vehicleNumber: partner.vehicleNumber ?? '',
      city: partner.city ?? '',
    },
  });
  const vehicleNumber = useWatch({ control, name: 'vehicleNumber' });

  const submit = handleSubmit(async (form) => {
    try {
      await save.mutateAsync(form);
      if (partner.readyToWork) {
        showToast('Saved.');
      } else {
        showToast('All set: go online to receive your first delivery.');
        void navigate(PARTNER_ROUTES.home);
      }
    } catch (error) {
      showServerErrors(error, setError, ['vehicleType', 'vehicleNumber', 'city']);
    }
  });

  return (
    <Card
      title="Vehicle and city"
      subtitle={
        partner.readyToWork
          ? 'Deliveries are offered to you from restaurants in your city.'
          : 'Needed before you can go online: deliveries are offered from restaurants in your city.'
      }
    >
      <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-8">
        <FormSection step={1} title="Your vehicle">
          <Controller
            control={control}
            name="vehicleType"
            render={({ field }) => (
              <VehiclePicker
                value={field.value}
                onChange={field.onChange}
                error={errors.vehicleType?.message}
              />
            )}
          />
          <div className="grid items-start gap-5 sm:grid-cols-2">
            <Input
              label="Vehicle number"
              maxLength={20}
              placeholder="KA01AB1234"
              leftIcon={<Hash aria-hidden="true" className="size-4.5" />}
              hint="As on the number plate; saved in capital letters."
              error={errors.vehicleNumber?.message}
              {...register('vehicleNumber')}
            />
            <div className="flex flex-col gap-2 rounded-2xl bg-page p-4">
              <NumberPlate number={vehicleNumber} />
              <p className="text-xs text-ink-soft">
                A preview of your number plate: check that it matches your vehicle.
              </p>
            </div>
          </div>
        </FormSection>

        <FormSection step={2} title="Where you deliver">
          <Input
            label="City"
            maxLength={100}
            placeholder="Bengaluru"
            leftIcon={<Building2 aria-hidden="true" className="size-4.5" />}
            hint="Write it as the restaurants do (for example Bengaluru): deliveries are matched on the city."
            error={errors.city?.message}
            {...register('city')}
          />
        </FormSection>

        {errors.root?.server && (
          <p className="text-sm text-danger-text">{errors.root.server.message}</p>
        )}
        <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-5">
          <p
            aria-live="polite"
            className={cn(
              'inline-flex items-center gap-2 text-sm',
              isDirty ? 'font-semibold text-warning-text' : 'text-ink-soft',
            )}
          >
            <span
              aria-hidden="true"
              className={cn('size-2 rounded-full', isDirty ? 'bg-sunset-500' : 'bg-brand-500')}
            />
            {isDirty ? 'You have unsaved changes' : partner.readyToWork ? 'All changes saved' : ''}
          </p>
          <div className="flex w-full flex-col-reverse gap-3 sm:w-auto sm:flex-row">
            {isDirty && partner.readyToWork && (
              <Button
                variant="ghost"
                size="lg"
                leftIcon={<RotateCcw aria-hidden="true" className="size-4" />}
                onClick={() => reset()}
              >
                Discard
              </Button>
            )}
            <Button
              type="submit"
              size="lg"
              className="w-full sm:w-auto"
              isLoading={isSubmitting}
              disabled={partner.readyToWork && !isDirty}
            >
              {partner.readyToWork ? 'Save changes' : 'Save and continue'}
            </Button>
          </div>
        </div>
      </form>
    </Card>
  );
}

/** A numbered part of the form, so the partner sees how much there is to fill in. */
function FormSection({
  step,
  title,
  children,
}: {
  step: number;
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-5">
      <h3 className="flex items-center gap-3 text-sm font-bold tracking-tight">
        <span
          aria-hidden="true"
          className="flex size-7 items-center justify-center rounded-full bg-brand-500 text-xs font-extrabold text-on-brand"
        >
          {step}
        </span>
        {title}
      </h3>
      {children}
    </section>
  );
}
