/**
 * Basis-Pfad der App (ohne trailing slash).
 *   Dev:        ''  (BASE_URL = '/', context-path /cili via Vite-Proxy)
 *   Production: ''  (BASE_URL = '/', ROOT.war ohne context-path)
 *
 * Verwendung:  `${BASE}/api/resources/...`
 */
export const BASE = import.meta.env.BASE_URL.replace(/\/$/, '');
