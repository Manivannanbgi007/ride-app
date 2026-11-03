import { useParams } from 'react-router';

/**
 * The record id in the address (/admin/orders/42 -> 42), or null when it is not a valid id (the page then shows
 * "not found" instead of asking the server).
 */
export function useIdParam(name: string): number | null {
  const value = useParams()[name];
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }
  const id = Number(value);
  return id > 0 && Number.isSafeInteger(id) ? id : null;
}
