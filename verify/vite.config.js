import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const apiUrl = process.env.VITE_API_URL ?? 'http://localhost:8080'

export default defineConfig({
  server: {
    proxy: {
      '/flow': apiUrl,
      '/auth': apiUrl,
    },
  },
  plugins: [
    tailwindcss(),
    react({
      babel: {
        plugins: [['babel-plugin-react-compiler']],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
