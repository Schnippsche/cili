import { useState, useEffect } from 'react';

/**
 * Hook that delays updates to a value by a fixed delay.
 * Useful for debouncing search inputs to reduce API calls on every keystroke.
 *
 * @param value The value to debounce
 * @param delayMs Delay in milliseconds (default 400ms)
 * @returns The debounced value
 */
export function useDebouncedValue<T>(value: T, delayMs = 400): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delayMs);

    return () => clearTimeout(handler);
  }, [value, delayMs]);

  return debouncedValue;
}
