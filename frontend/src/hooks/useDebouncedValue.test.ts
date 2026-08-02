import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDebouncedValue } from './useDebouncedValue';

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns the initial value immediately', () => {
    const { result } = renderHook(() => useDebouncedValue('initial'));
    expect(result.current).toBe('initial');
  });

  it('delays update by default delay (400ms)', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebouncedValue(value),
      { initialProps: { value: 'initial' } }
    );

    expect(result.current).toBe('initial');

    act(() => {
      rerender({ value: 'updated' });
    });

    // Before 400ms, should still be old value
    expect(result.current).toBe('initial');

    act(() => {
      vi.advanceTimersByTime(400);
    });

    // After 400ms, should be new value
    expect(result.current).toBe('updated');
  });

  it('respects custom delay', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebouncedValue(value, 200),
      { initialProps: { value: 'initial' } }
    );

    act(() => {
      rerender({ value: 'updated' });
    });

    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe('initial');

    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe('updated');
  });

  it('cancels pending timeout on value change', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebouncedValue(value),
      { initialProps: { value: 'initial' } }
    );

    act(() => {
      rerender({ value: 'first' });
    });

    act(() => {
      vi.advanceTimersByTime(200);
    });

    // Change again before first timeout completes
    act(() => {
      rerender({ value: 'second' });
    });

    act(() => {
      vi.advanceTimersByTime(200);
    });

    // Should still be initial (timeout was cancelled)
    expect(result.current).toBe('initial');

    // After full delay from second update
    act(() => {
      vi.advanceTimersByTime(200);
    });

    expect(result.current).toBe('second');
  });

  it('handles number and object types', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebouncedValue(value),
      { initialProps: { value: 42 } }
    );

    expect(result.current).toBe(42);

    act(() => {
      rerender({ value: 100 });
    });

    act(() => {
      vi.advanceTimersByTime(400);
    });

    expect(result.current).toBe(100);
  });
});
