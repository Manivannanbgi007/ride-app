import { zodResolver } from '@hookform/resolvers/zod';
import { Lock, Mail } from 'lucide-react';
import { Suspense } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useNavigate, useSearchParams } from 'react-router';

import { toApiError } from '@/api/apiErrorHandler';
import { AuthShell } from '@/components/business/Auth/AuthShell';
import { DevAccounts } from '@/components/business/Auth/DevAccounts';
import { Alert } from '@/components/common/Alert';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { PasswordInput } from '@/components/common/PasswordInput';
import { useToast } from '@/context/ToastContext';
import { useSignIn } from '@/hooks/api/authHooks';
import { useAppSelector } from '@/store/hooks';
import { selectCustomer } from '@/store/slices/sessionSlice';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { safeReturnTo, signInFormSchema, type SignInForm } from '@/utils/validationUtils';

/** The message for a refused sign-in: wrong password, deactivated account, or the service's own. */
function signInProblem(error: unknown): string {
  const apiError = toApiError(error);
  if (apiError.kind === 'unauthorized') {
    return ERROR_MESSAGES.SIGN_IN_FAILED;
  }
  return apiError.status === 403 ? ERROR_MESSAGES.ACCOUNT_DEACTIVATED : apiError.userMessage;
}

/**
 * "Sign in" (user-service checks the password and starts the customer area's session: an access token in memory and
 * a refresh cookie). Afterwards the customer goes back to the page that asked them to sign in (?returnTo=), or
 * else to their dashboard.
 */
export default function SignInPage() {
  const [searchParams] = useSearchParams();
  const returnTo = safeReturnTo(searchParams.get('returnTo'));
  const navigate = useNavigate();
  const { showToast } = useToast();
  const signIn = useSignIn();
  const alreadySignedIn = useAppSelector(selectCustomer) !== null && !signIn.isPending;
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<SignInForm>({
    resolver: zodResolver(signInFormSchema),
    defaultValues: { email: '', password: '' },
  });

  const submit = handleSubmit(async (form) => {
    try {
      const customer = await signIn.mutateAsync(form);
      showToast(`Welcome back, ${customer.fullName.split(' ')[0]}!`);
      void navigate(returnTo ?? CUSTOMER_ROUTES.dashboard, { replace: true });
    } catch (error) {
      setError('root.server', { message: signInProblem(error) });
    }
  });

  /** A test account of the panel under the form (npm run dev only): filled in and sent like a typed one */
  const signInAs = (account: SignInForm) => {
    setValue('email', account.email);
    setValue('password', account.password);
    void submit();
  };

  if (alreadySignedIn) {
    // Signed in already (another tab, the back button): straight on
    return <Navigate to={returnTo ?? CUSTOMER_ROUTES.dashboard} replace />;
  }

  const registerLink = returnTo
    ? `${CUSTOMER_ROUTES.register}?returnTo=${encodeURIComponent(returnTo)}`
    : CUSTOMER_ROUTES.register;

  return (
    <AuthShell
      audience="customer"
      eyebrow="Welcome back"
      title="Sign in"
      subtitle={
        returnTo ? 'Sign in to continue.' : 'Sign in to order from the restaurants of your city.'
      }
      alternative={{ prompt: 'New here?', label: 'Create an account', to: registerLink }}
    >
      <form onSubmit={(event) => void submit(event)} noValidate className="flex flex-col gap-4">
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          leftIcon={<Mail aria-hidden="true" className="size-4.5" />}
          error={errors.email?.message}
          {...register('email')}
        />
        <PasswordInput
          label="Password"
          autoComplete="current-password"
          leftIcon={<Lock aria-hidden="true" className="size-4.5" />}
          error={errors.password?.message}
          {...register('password')}
        />
        {errors.root?.server && <Alert tone="error">{errors.root.server.message}</Alert>}
        <Button type="submit" size="lg" isLoading={isSubmitting} className="mt-1 w-full">
          Sign in
        </Button>
      </form>
      {DevAccounts && (
        <Suspense fallback={null}>
          <DevAccounts audience="customer" onUse={signInAs} isBusy={isSubmitting} />
        </Suspense>
      )}
    </AuthShell>
  );
}
