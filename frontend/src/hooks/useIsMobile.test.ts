import { renderHook } from '@testing-library/react';
import { useIsMobile } from './useIsMobile';

describe('useIsMobile', () => {
  it('returns false when viewport is wide (desktop default)', () => {
    // setup.ts mock returns matches: false by default
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(false);
  });

  it('returns true when matchMedia reports a narrow viewport', () => {
    vi.mocked(window.matchMedia).mockReturnValueOnce({
      matches: true,
      media: '(max-width:899.95px)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    });
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(true);
  });
});
