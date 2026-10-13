import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The UI calls /api/...; the backend has no prefix, so strip it.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
