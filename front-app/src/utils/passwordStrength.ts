export interface PasswordStrength {
  /** 0 (nothing typed) to 4 (strong): how many segments of the meter are filled */
  level: 0 | 1 | 2 | 3 | 4;
  label: string;
}

const LABELS = ['', 'Weak', 'Fair', 'Good', 'Strong'] as const;

/**
 * A hint about how hard a password is to guess: it only helps the person choosing it. The rules that decide whether
 * a password is accepted are user-service's (8 to 72 characters, a letter and a digit; utils/validationUtils.ts).
 * Longer and more varied is stronger: length, upper and lower case, digits and other characters all count.
 */
export function passwordStrength(password: string): PasswordStrength {
  if (password.length === 0) {
    return { level: 0, label: '' };
  }
  let points = 0;
  if (password.length >= 8) {
    points += 1;
  }
  if (password.length >= 12) {
    points += 1;
  }
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) {
    points += 1;
  }
  if (/\d/.test(password) && /[A-Za-z]/.test(password)) {
    points += 1;
  }
  if (/[^A-Za-z0-9]/.test(password)) {
    points += 1;
  }
  // A password shorter than 8 is never better than weak
  const level = password.length < 8 ? 1 : (Math.min(4, Math.max(1, points - 1)) as 1 | 2 | 3 | 4);
  return { level, label: LABELS[level] };
}
