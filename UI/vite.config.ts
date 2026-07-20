import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Build output lands directly in the Spring Boot app's static resource folder — no separate
// copy step, no frontend-maven-plugin. SecurityConfiguration.defaultSecurityFilterChain permits
// the resulting paths (/, /assets/**, /index.html) and SinglePageApplicationController forwards
// every other client-side route to index.html.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5030,
    proxy: {
      '/api': 'http://localhost:9090',
      '/oauth2': 'http://localhost:9090',
      '/login/oauth2/code': 'http://localhost:9090',
      '/.well-known': 'http://localhost:9090',
      '/connect': 'http://localhost:9090',
    },
  },
})
