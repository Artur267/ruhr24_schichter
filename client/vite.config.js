// client/vite.config.js

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Leitet alle Anfragen, die mit /api beginnen, an deinen Server auf Port 3000 weiter
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
      }
    }
  }
})