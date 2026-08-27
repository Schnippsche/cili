import {formatRelativeTime} from './formatRelativeTime';

const NOW = new Date('2026-08-27T12:00:00Z');

describe('formatRelativeTime', () => {
  it('shows "gerade eben" for timestamps within the last minute', () => {
    expect(formatRelativeTime('2026-08-27T11:59:30Z', NOW)).toBe('gerade eben');
  });

  it('shows minutes for timestamps within the last hour', () => {
    expect(formatRelativeTime('2026-08-27T11:45:00Z', NOW)).toBe('vor 15 Minuten');
  });

  it('shows hours for timestamps within the last day', () => {
    expect(formatRelativeTime('2026-08-27T09:00:00Z', NOW)).toBe('vor 3 Stunden');
  });

  it('uses natural wording for the day before yesterday', () => {
    expect(formatRelativeTime('2026-08-25T12:00:00Z', NOW)).toBe('vorgestern');
  });

  it('shows a day count for timestamps within the last month', () => {
    expect(formatRelativeTime('2026-08-20T12:00:00Z', NOW)).toBe('vor 7 Tagen');
  });

  it('falls back to an absolute date for older timestamps', () => {
    expect(formatRelativeTime('2026-06-01T12:00:00Z', NOW)).toBe(
      new Date('2026-06-01T12:00:00Z').toLocaleDateString('de-DE'),
    );
  });
});
