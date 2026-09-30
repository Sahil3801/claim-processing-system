import { useEffect } from 'react';

/** Keeps the browser tab title in step with the page, e.g. "Claim #4 · Claims Portal". */
export function usePageTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · Claims Portal` : 'Claims Portal';
  }, [title]);
}
