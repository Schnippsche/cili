/// <reference types="vitest" />
import {defineConfig, loadEnv} from 'vite';
import react from '@vitejs/plugin-react';
import {VitePWA} from 'vite-plugin-pwa';

export default defineConfig(({mode}) => {
  // Liest .env, .env.production etc. — VITE_BASE_PATH=/cili in .env.production
  const env = loadEnv(mode, process.cwd(), '');
  const rawBase = env.VITE_BASE_PATH ?? '';
  // Sicherstellen dass base immer mit / endet: '' → '/', '/cili' → '/cili/'
  const base = rawBase ? rawBase.replace(/\/?$/, '/') : '/';

  return {
    base,
    plugins: [
      react(),
      VitePWA({
        registerType: 'autoUpdate',
        // Nur die App-Shell (JS/CSS/Icons) wird gecacht. API-Calls, Video-Streaming
        // (Range-Requests) und der Dokument-Viewer bleiben bewusst außen vor, damit
        // nichts Veraltetes oder Kaputtes aus dem Cache serviert wird.
        workbox: {
          globPatterns: ['**/*.{js,css,html,svg,png,ico}'],
          navigateFallbackDenylist: [/^\/api\//, /^\/stream\//],
        },
        includeAssets: ['favicon.svg', 'favicon.ico', 'apple-touch-icon.png'],
        manifest: {
          name: 'CILI',
          short_name: 'CILI',
          description: 'CILI Datei- und Medienverwaltung',
          start_url: base,
          scope: base,
          display: 'standalone',
          background_color: '#ffffff',
          theme_color: '#2563EB',
          icons: [
            {src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png'},
            {src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png'},
            {
              src: 'pwa-maskable-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'maskable',
            },
          ],
        },
      }),
    ],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: '../src/main/resources/static',
      emptyOutDir: true,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('video.js')) return 'vendor-videojs';
            if (id.includes('pdfjs-dist')) return 'vendor-pdfjs';
          },
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});
