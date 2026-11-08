import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import AppShell from './components/AppShell'
import { ToastProvider } from './components/ui/Toast'
import Catalog from './pages/Catalog'
import CourseDetail from './pages/CourseDetail'
import Dashboard from './pages/Dashboard'
import Login from './pages/Login'
import NotFound from './pages/NotFound'
import Schedule from './pages/Schedule'
import { RegistrationPeriodProvider } from './registration/RegistrationPeriodContext'
import { RegistrationsProvider } from './registration/RegistrationsContext'

function ProtectedLayout() {
  return (
    <RegistrationsProvider>
      <AppShell />
    </RegistrationsProvider>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <RegistrationPeriodProvider>
          <ToastProvider>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route element={<RequireAuth />}>
                <Route element={<ProtectedLayout />}>
                  <Route path="/" element={<Dashboard />} />
                  <Route path="/dashboard" element={<Dashboard />} />
                  <Route path="/catalog" element={<Catalog />} />
                  <Route path="/courses" element={<Catalog />} />
                  <Route path="/courses/:id" element={<CourseDetail />} />
                  <Route path="/schedule" element={<Schedule />} />
                </Route>
              </Route>
              <Route path="*" element={<NotFound />} />
            </Routes>
          </ToastProvider>
        </RegistrationPeriodProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
