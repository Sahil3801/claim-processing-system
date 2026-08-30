import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { AppShell } from './components/AppShell';
import { ProtectedRoute } from './components/ProtectedRoute';
import { ClaimantDashboard } from './pages/ClaimantDashboard';
import { ClaimDetailPage } from './pages/ClaimDetailPage';
import { ClaimsListPage } from './pages/ClaimsListPage';
import { CreateClaimPage } from './pages/CreateClaimPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { OfficerDashboard } from './pages/OfficerDashboard';
import { RegisterPage } from './pages/RegisterPage';
import { ReportingPage } from './pages/ReportingPage';

function HomeRedirect() {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  return <Navigate to={session.role === 'CLAIMANT' ? '/dashboard' : '/officer'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/" element={<ProtectedRoute><AppShell /></ProtectedRoute>}>
        <Route index element={<HomeRedirect />} />
        <Route path="dashboard" element={<ProtectedRoute roles={['CLAIMANT']}><ClaimantDashboard /></ProtectedRoute>} />
        <Route path="claims" element={<ProtectedRoute roles={['CLAIMANT']}><ClaimsListPage /></ProtectedRoute>} />
        <Route path="claims/new" element={<ProtectedRoute roles={['CLAIMANT']}><CreateClaimPage /></ProtectedRoute>} />
        <Route path="claims/:id" element={<ProtectedRoute roles={['CLAIMANT', 'CLAIMS_OFFICER', 'ADMIN']}><ClaimDetailPage /></ProtectedRoute>} />
        <Route path="officer" element={<ProtectedRoute roles={['CLAIMS_OFFICER', 'ADMIN']}><OfficerDashboard /></ProtectedRoute>} />
        <Route path="officer/claims" element={<ProtectedRoute roles={['CLAIMS_OFFICER', 'ADMIN']}><ClaimsListPage pendingOnly /></ProtectedRoute>} />
        <Route path="reports" element={<ProtectedRoute roles={['ADMIN']}><ReportingPage /></ProtectedRoute>} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
