import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * Opens the dropdown (the Select field) and picks an item as a user would: by the text it shows, or by its value
 * ('12' for a month).
 */
export async function chooseOption(field: HTMLElement, option: string): Promise<void> {
  await userEvent.click(field);
  const list = await screen.findByRole('listbox');
  const items = within(list).getAllByRole('option');
  const item =
    items.find((candidate) => candidate.textContent === option) ??
    items.find((candidate) => candidate.getAttribute('data-value') === option);
  if (!item) {
    throw new Error(`No option "${option}" in: ${items.map((i) => i.textContent).join(', ')}`);
  }
  await userEvent.click(item);
}

/** The items the dropdown offers, in order (it opens and closes the list). */
export async function optionsOf(field: HTMLElement): Promise<string[]> {
  await userEvent.click(field);
  const texts = within(await screen.findByRole('listbox'))
    .getAllByRole('option')
    .map((item) => item.textContent ?? '');
  await userEvent.keyboard('{Escape}');
  return texts;
}
