import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Build output lands directly in the backend's static resource folder (../BE/src/main/resources/
// static), built by BE/pom.xml's frontend-maven-plugin so `mvn spring-boot:run` serves the freshly
// built SPA. SecurityConfiguration.defaultSecurityFilterChain permits the resulting paths (/,
// /assets/**, /index.html) and SinglePageApplicationController forwards every other client-side route
// to index.html. `emptyOutDir: true` is required since that folder is outside this UI project's root.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: '../BE/src/main/resources/static',
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
