/// <reference types="vitest" />
import {defineConfig, loadEnv} from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({mode}) => {
  // Liest .env, .env.production etc. — VITE_BASE_PATH=/cili in .env.production
  const env = loadEnv(mode, process.cwd(), '');
  const rawBase = env.VITE_BASE_PATH ?? '';
  // Sicherstellen dass base immer mit / endet: '' → '/', '/cili' → '/cili/'
  const base = rawBase ? rawBase.replace(/\/?$/, '/') : '/';

  return {
    base,
    plugins: [react()],
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
