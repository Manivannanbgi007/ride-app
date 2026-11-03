import { useState } from 'react';

/**
 * Whether the person signed in on this page has signed out while it was shown (with "Sign out", in another tab, or
 * because the session ended): true from then on. False for a visitor who arrived signed out. The area guards use it
 * to send a sign-out to a plain address, so the address shows no ?returnTo= and the next person to sign in does not
 * land on the page the last one left.
 */
export function useSignedOutHere(user: object | null): boolean {
  const [hadUser, setHadUser] = useState(user !== null);
  if (user !== null && !hadUser) {
    setHadUser(true);
  }
  return hadUser && user === null;
}
