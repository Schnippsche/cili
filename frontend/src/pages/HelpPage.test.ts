import { describe, it, expect } from 'vitest';
import { slugify } from './HelpPage';

describe('slugify (HelpPage anchor ids)', () => {
  it('matches the GitHub-style double-dash anchors used in the HANDBUCH.md table of contents', () => {
    // Diese IDs müssen exakt zu den handgeschriebenen Anker-Links in docs/HANDBUCH.md passen,
    // die der GitHub-Flavored-Markdown-Slug-Konvention folgen: ein entferntes Sonderzeichen
    // (z.B. "&") konsumiert nicht die Leerzeichen davor/danach, wodurch zwei Bindestriche entstehen.
    expect(slugify('2. Oberfläche & Navigation')).toBe('2-oberfläche--navigation');
    expect(slugify('3. Ordner & Dateien')).toBe('3-ordner--dateien');
    expect(slugify('4. Dateien betrachten & abspielen')).toBe('4-dateien-betrachten--abspielen');
  });

  it('still produces a single dash for normal single-space headings (no regression)', () => {
    expect(slugify('5. Untertitel')).toBe('5-untertitel');
    expect(slugify('6.1 Globale Suche')).toBe('61-globale-suche');
    expect(slugify('Suchtreffer zu einer Sammlung hinzufügen')).toBe('suchtreffer-zu-einer-sammlung-hinzufügen');
  });
});
