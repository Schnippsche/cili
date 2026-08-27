const rtf = new Intl.RelativeTimeFormat('de-DE', {numeric: 'auto'});

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const MONTH = 30 * DAY;

/**
 * Formatiert einen ISO-Zeitstempel als deutsche Relativzeit ("vor 3 Stunden").
 * Für Zeitpunkte, die länger als ~einen Monat zurückliegen, wird auf ein
 * absolutes Datum zurückgefallen.
 */
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const diffSeconds = Math.round((now.getTime() - then.getTime()) / 1000);

  if (diffSeconds < MINUTE) return 'gerade eben';
  if (diffSeconds < HOUR) return rtf.format(-Math.floor(diffSeconds / MINUTE), 'minute');
  if (diffSeconds < DAY) return rtf.format(-Math.floor(diffSeconds / HOUR), 'hour');
  if (diffSeconds < MONTH) return rtf.format(-Math.floor(diffSeconds / DAY), 'day');
  return then.toLocaleDateString('de-DE');
}
