import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { toApiError } from '@/api/apiErrorHandler';
import { Alert } from '@/components/common/Alert';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { Input } from '@/components/common/Input';
import { SkeletonLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { useToast } from '@/context/ToastContext';
import { useChangeMyPassword, useCurrentUser, useUpdateMyProfile } from '@/hooks/api/userHooks';
import type { UserProfile } from '@/types/user';
import { formatDateTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import {
  passwordFormSchema,
  profileFormSchema,
  showServerErrors,
  type PasswordForm,
  type ProfileForm,
} from '@/utils/validationUtils';

/** "My profile": name and phone, and changing the password. The email is the account's and stays. */
export default function ProfilePage() {
  const profile = useCurrentUser();
  return (
    <div className="mx-auto flex max-w-column-narrow flex-col gap-6">
      <PageHeader title="My profile" />
      <AsyncContent
        isLoading={profile.isPending}
        error={profile.error}
        errorTitle={ERROR_MESSAGES.PROFILE_LOAD_FAILED}
        onRetry={() => void profile.refetch()}
        isRetrying={profile.isFetching}
        loading={<SkeletonLoader lines={5} className="rounded-2xl bg-surface p-6" />}
      >
        {profile.data && (
          <>
            <DetailsForm profile={profile.data} />
            <PasswordFormCard />
          </>
        )}
      </AsyncContent>
    </div>
  );
}

function DetailsForm({ profile }: { profile: UserProfile }) {
  const update = useUpdateMyProfile();
  const { showToast } = useToast();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileForm>({
    resolver: zodResolver(profileFormSchema),
    defaultValues: { fullName: profile.fullName, phone: profile.phone ?? '' },
  });

  const submit = handleSubmit(async ({ fullName, phone }) => {
    try {
      const saved = await update.mutateAsync({ fullName, phone: phone || null });
      reset({ fullName: saved.fullName, phone: saved.phone ?? '' });
      showToast('Your details are saved.');
    } catch (error) {
      if (toApiError(error).userMessage.startsWith('Phone number is already registered')) {
        setError('phone', { message: 'This phone number belongs to another account.' });
      } else {
        showServerErrors(error, setError, ['fullName', 'phone']);
      }
    }
  });

  return (
    <Card title="Your details" subtitle={`Customer since ${formatDateTime(profile.createdAt)}`}>
      <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-4">
        <Input
          label="Email"
          value={profile.email}
          readOnly
          disabled
          hint="Your email cannot be changed."
        />
        <Input
          label="Full name"
          autoComplete="name"
          maxLength={100}
          error={errors.fullName?.message}
          {...register('fullName')}
        />
        <Input
          label="Phone (optional)"
          type="tel"
          autoComplete="tel"
          placeholder="+919800000000"
          error={errors.phone?.message}
          hint="The restaurant and the delivery partner can call you about your order."
          {...register('phone')}
        />
        {errors.root?.server && <Alert tone="error">{errors.root.server.message}</Alert>}
        <div>
          <Button type="submit" isLoading={isSubmitting} disabled={!isDirty}>
            Save details
          </Button>
        </div>
      </form>
    </Card>
  );
}

function PasswordFormCard() {
  const change = useChangeMyPassword();
  const { showToast } = useToast();
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<PasswordForm>({
    resolver: zodResolver(passwordFormSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const submit = handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      await change.mutateAsync({ currentPassword, newPassword });
      reset();
      showToast('Your password is changed.');
    } catch (error) {
      const message = toApiError(error).userMessage;
      if (message.startsWith('Current password is incorrect')) {
        setError('currentPassword', { message: 'This is not your current password.' });
      } else {
        showServerErrors(error, setError, ['currentPassword', 'newPassword']);
      }
    }
  });

  return (
    <Card title="Password">
      <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-4">
        <Input
          label="Current password"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <Input
          label="New password"
          type="password"
          autoComplete="new-password"
          maxLength={72}
          hint="At least 8 characters, with a letter and a digit."
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <Input
          label="New password again"
          type="password"
          autoComplete="new-password"
          maxLength={72}
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        {errors.root?.server && <Alert tone="error">{errors.root.server.message}</Alert>}
        <div>
          <Button type="submit" variant="outline" isLoading={isSubmitting}>
            Change password
          </Button>
        </div>
      </form>
    </Card>
  );
}
