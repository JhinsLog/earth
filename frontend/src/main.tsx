import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { startTelemetry } from './lib/telemetry'

// 첫 렌더보다 먼저 건다. 렌더 중에 나는 오류도 잡아야 한다.
startTelemetry()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
