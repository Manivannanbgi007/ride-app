import { zodResolver } from '@hookform/resolvers/zod';
import { Lock, Mail, Phone, UserRound } from 'lucide-react';
import { useForm, useWatch } from 'react-hook-form';
import { Navigate, useNavigate, useSearchParams } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { AuthShell } from '@/components/business/Auth/AuthShell';
import { PasswordStrengthMeter } from '@/components/business/Auth/PasswordStrengthMeter';
import { Alert } from '@/components/common/Alert';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { PasswordInput } from '@/components/common/PasswordInput';
import { useToast } from '@/context/ToastContext';
import { useRegister } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES, customerRouteTo } from '@/utils/constants';
import {
  registerFormSchema,
  safeReturnTo,
  showServerErrors,
  type RegisterForm,
} from '@/utils/validationUtils';

/** "Create an account": a customer account, signed in straight away. */
export default function RegisterPage() {
  const [searchParams] = useSearchParams();
  const returnTo = safeReturnTo(searchParams.get('returnTo'));
  const navigate = useNavigate();
  const { showToast } = useToast();
  const registration = useRegister();
  const alreadySignedIn = useAppSelector(selectCustomer) !== null && !registration.isPending;
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerFormSchema),
    defaultValues: { fullName: '', email: '', phone: '', password: '', confirmPassword: '' },
  });

  // Typing a password moves the strength meter
  const typedPassword = useWatch({ control, name: 'password' });

  const submit = handleSubmit(async ({ fullName, email, phone, password }) => {
    try {
      const customer = await registration.mutateAsync({
        fullName,
        email,
        password,
        phone: phone || undefined,
      });
      if (customer) {
        showToast(`Welcome, ${customer.fullName.split(' ')[0]}! Your account is ready.`);
        void navigate(returnTo ?? CUSTOMER_ROUTES.home, { replace: true });
      } else {
        showToast('Your account is ready: please sign in.', 'info');
        void navigate(customerRouteTo.signIn(returnTo ?? undefined), { replace: true });
      }
    } catch (error) {
      // "Already registered" belongs next to the field it is about
      const message = toApiError(error).userMessage;
      if (message.startsWith('Email is already registered')) {
        setError('email', { message: 'This email already has an account: sign in instead.' });
      } else if (message.startsWith('Phone number is already registered')) {
        setError('phone', { message: 'This phone number already has an account.' });
      } else {
        showServerErrors(error, setError, ['fullName', 'email', 'phone', 'password']);
      }
    }
  });

  if (alreadySignedIn) {
    return <Navigate to={returnTo ?? CUSTOMER_ROUTES.home} replace />;
  }

  const signInLink = customerRouteTo.signIn(returnTo ?? undefined);

  return (
    <AuthShell
      audience="customer"
      eyebrow="Get started"
      title="Create an account"
      subtitle="Order from the restaurants of your city."
      alternative={{ prompt: 'Already have an account?', label: 'Sign in', to: signInLink }}
    >
      <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-4">
        <Input
          label="Full name"
          autoComplete="name"
          maxLength={100}
          leftIcon={<UserRound aria-hidden="true" className="size-4.5" />}
          error={errors.fullName?.message}
          {...register('fullName')}
        />
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          maxLength={255}
          placeholder="you@example.com"
          leftIcon={<Mail aria-hidden="true" className="size-4.5" />}
          error={errors.email?.message}
          {...register('email')}
        />
        <Input
          label="Phone (optional)"
          type="tel"
          autoComplete="tel"
          placeholder="+919800000000"
          leftIcon={<Phone aria-hidden="true" className="size-4.5" />}
          error={errors.phone?.message}
          hint="The restaurant and the delivery partner can call you about your order."
          {...register('phone')}
        />
        <div className="flex flex-col gap-2">
          <PasswordInput
            label="Password"
            autoComplete="new-password"
            maxLength={72}
            leftIcon={<Lock aria-hidden="true" className="size-4.5" />}
            error={errors.password?.message}
            {...register('password')}
          />
          <PasswordStrengthMeter password={typedPassword} />
        </div>
        <PasswordInput
          label="Password again"
          autoComplete="new-password"
          maxLength={72}
          leftIcon={<Lock aria-hidden="true" className="size-4.5" />}
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        {errors.root?.server && <Alert tone="error">{errors.root.server.message}</Alert>}
        <Button type="submit" size="lg" isLoading={isSubmitting} className="mt-1 w-full">
          Create account
        </Button>
      </form>
    </AuthShell>
  );
}
