/// <reference types="vitest" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(function (_a) {
    var _b;
    var mode = _a.mode;
    // Liest .env, .env.production etc. — VITE_BASE_PATH=/cili in .env.production
    var env = loadEnv(mode, process.cwd(), '');
    var rawBase = (_b = env.VITE_BASE_PATH) !== null && _b !== void 0 ? _b : '';
    // Sicherstellen dass base immer mit / endet: '' → '/', '/cili' → '/cili/'
    var base = rawBase ? rawBase.replace(/\/?$/, '/') : '/';
    return {
        base: base,
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
                    manualChunks: function (id) {
                        if (id.includes('video.js'))
                            return 'vendor-videojs';
                        if (id.includes('pdfjs-dist'))
                            return 'vendor-pdfjs';
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
