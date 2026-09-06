import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // App-shell (static assets) only. Financial data must stay live, so
      // API responses are never precached or runtime-cached here - every
      // /api/* request always hits the network. If it's offline, API
      // calls fail normally rather than silently serving stale data.
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico}'],
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [
          {
            urlPattern: /\/api\/.*/,
            handler: 'NetworkOnly',
          },
        ],
      },
      manifest: {
        name: 'Undertow',
        short_name: 'Undertow',
        description: 'Attention-aware market watchlist. Know what changed, know what deserves your attention.',
        theme_color: '#0F5C5C',
        background_color: '#FAFAF8',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  server: {
    host: true,
    allowedHosts: ['undertow-frontend-2026.onrender.com'],
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
