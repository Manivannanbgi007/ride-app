import { Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';

import { Input, type InputProps } from '@/components/common/Input';

/**
 * A password field with an eye that shows what was typed, so a mistyped password can be seen and corrected. It is
 * an ordinary Input otherwise (label, hint, error, the form library's `register`). The eye is not part of the
 * field's label: the field is still found by its own label, e.g. "Password".
 */
export function PasswordInput(props: Omit<InputProps, 'rightAction' | 'type'>) {
  const [visible, setVisible] = useState(false);

  return (
    <Input
      {...props}
      type={visible ? 'text' : 'password'}
      rightAction={
        <button
          type="button"
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          onClick={() => setVisible((shown) => !shown)}
          className="flex size-8 cursor-pointer items-center justify-center rounded-lg text-ink-soft transition-colors hover:bg-brand-50 hover:text-brand-text focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none"
        >
          {visible ? (
            <EyeOff aria-hidden="true" className="size-4.5" />
          ) : (
            <Eye aria-hidden="true" className="size-4.5" />
          )}
        </button>
      }
    />
  );
}
