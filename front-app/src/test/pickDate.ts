import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { longDate } from '@/utils/calendarUtils';
import { parseIsoDate } from '@/utils/dateUtils';

/** Opens the date picker labelled `label` and picks a day ('YYYY-MM-DD'), as a user would. */
export async function pickDate(label: string, isoDate: string): Promise<void> {
  await userEvent.click(screen.getByLabelText(label));
  const calendar = screen.getByRole('dialog', { name: `Choose: ${label}` });
  const date = parseIsoDate(isoDate);
  await userEvent.selectOptions(
    within(calendar).getByLabelText('Year'),
    String(date.getFullYear()),
  );
  await userEvent.selectOptions(within(calendar).getByLabelText('Month'), String(date.getMonth()));
  await userEvent.click(within(calendar).getByRole('button', { name: longDate(date) }));
}
