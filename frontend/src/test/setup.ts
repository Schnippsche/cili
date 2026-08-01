import '@testing-library/jest-dom';

// jsdom does not implement object URLs; components that preview File objects
// (e.g. TestimonialForm) call these on mount. Array.prototype.forEach also
// validates the callback is callable before iterating, so an undefined
// revokeObjectURL throws even for an empty list.
if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:mock';
}
if (typeof URL.revokeObjectURL !== 'function') {
  URL.revokeObjectURL = () => {
  };
}

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});
