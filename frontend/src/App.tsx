import { Navigate, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage'
import OAuthRedirectPage from './pages/OAuthRedirectPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/home" replace />} />
      <Route path="/home" element={<HomePage />} />
      <Route path="/oauth2/redirect" element={<OAuthRedirectPage />} />
      <Route path="*" element={<Navigate to="/home" replace />} />
    </Routes>
  )
}
