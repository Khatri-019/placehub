import { Route, Routes } from 'react-router';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { Layout } from './components/Layout';
import { AdminApplications } from './pages/admin/AdminApplications';
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { AdminJobs } from './pages/admin/AdminJobs';
import { AdminUsers } from './pages/admin/AdminUsers';
import { Notifications } from './pages/Notifications';
import { Landing } from './pages/public/Landing';
import { Login } from './pages/public/Login';
import { NotFound } from './pages/public/NotFound';
import { RegisterRecruiter } from './pages/public/RegisterRecruiter';
import { RegisterStudent } from './pages/public/RegisterStudent';
import { RecruiterApplicants } from './pages/recruiter/RecruiterApplicants';
import { RecruiterApplicationDetail } from './pages/recruiter/RecruiterApplicationDetail';
import { RecruiterCompany } from './pages/recruiter/RecruiterCompany';
import { RecruiterDashboard } from './pages/recruiter/RecruiterDashboard';
import { RecruiterJobForm } from './pages/recruiter/RecruiterJobForm';
import { RecruiterJobs } from './pages/recruiter/RecruiterJobs';
import { StudentApplications } from './pages/student/StudentApplications';
import { StudentDashboard } from './pages/student/StudentDashboard';
import { StudentJobDetails } from './pages/student/StudentJobDetails';
import { StudentJobs } from './pages/student/StudentJobs';
import { StudentProfile } from './pages/student/StudentProfile';

/** Route table from spec section 7.2. */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register/student" element={<RegisterStudent />} />
      <Route path="/register/recruiter" element={<RegisterRecruiter />} />

      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/student" element={<ProtectedRoute roles={['STUDENT']}><StudentDashboard /></ProtectedRoute>} />
        <Route path="/student/jobs" element={<ProtectedRoute roles={['STUDENT']}><StudentJobs /></ProtectedRoute>} />
        <Route path="/student/jobs/:id" element={<ProtectedRoute roles={['STUDENT']}><StudentJobDetails /></ProtectedRoute>} />
        <Route path="/student/applications" element={<ProtectedRoute roles={['STUDENT']}><StudentApplications /></ProtectedRoute>} />
        <Route path="/student/profile" element={<ProtectedRoute roles={['STUDENT']}><StudentProfile /></ProtectedRoute>} />

        <Route path="/recruiter" element={<ProtectedRoute roles={['RECRUITER']}><RecruiterDashboard /></ProtectedRoute>} />
        <Route path="/recruiter/company" element={<ProtectedRoute roles={['RECRUITER']}><RecruiterCompany /></ProtectedRoute>} />
        <Route path="/recruiter/jobs" element={<ProtectedRoute roles={['RECRUITER']}><RecruiterJobs /></ProtectedRoute>} />
        <Route path="/recruiter/jobs/new" element={<ProtectedRoute roles={['RECRUITER']}><RecruiterJobForm /></ProtectedRoute>} />
        <Route path="/recruiter/jobs/:id/edit" element={<ProtectedRoute roles={['RECRUITER']}><RecruiterJobForm /></ProtectedRoute>} />
        <Route path="/recruiter/jobs/:id/applicants" element={<ProtectedRoute roles={['RECRUITER']}><RecruiterApplicants /></ProtectedRoute>} />
        <Route path="/recruiter/applications/:id" element={<ProtectedRoute roles={['RECRUITER']}><RecruiterApplicationDetail /></ProtectedRoute>} />

        <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminDashboard /></ProtectedRoute>} />
        <Route path="/admin/users" element={<ProtectedRoute roles={['ADMIN']}><AdminUsers /></ProtectedRoute>} />
        <Route path="/admin/jobs" element={<ProtectedRoute roles={['ADMIN']}><AdminJobs /></ProtectedRoute>} />
        <Route path="/admin/applications" element={<ProtectedRoute roles={['ADMIN']}><AdminApplications /></ProtectedRoute>} />

        <Route path="/notifications" element={<Notifications />} />
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
