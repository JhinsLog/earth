import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 0.0.0.0으로 바인딩해 같은 네트워크의 다른 기기에서도 접속할 수 있게 한다.
    // 기본값(localhost)이면 이 노트북 안에서만 열린다 — 폰에서 GPS 동작을 확인하려면
    // 실제 기기로 접속해야 하므로 필요하다.
    host: true,
  },
  define: {
    // sockjs-client references the Node-style `global` object
    global: 'globalThis',
  },
})
