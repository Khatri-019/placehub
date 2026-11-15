import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import { Route, Routes } from 'react-router';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { Layout } from './components/Layout';
import { Landing } from './pages/public/Landing';
import { Login, PortalChooser } from './pages/public/Login';
import { NotFound } from './pages/public/NotFound';
import type { Role } from './types';
import { Skeleton } from './ui/Skeleton';

// Each role area is its own chunk (BUILD_SPEC 20.5): a student never downloads recruiter or admin code.
type Area = Record<string, ComponentType>;
const named = (load: () => Promise<Area>, key: string) => lazy(() => load().then((m) => ({ default: m[key] })));

const student = () => import('./pages/student') as unknown as Promise<Area>;
const recruiter = () => import('./pages/recruiter') as unknown as Promise<Area>;
const admin = () => import('./pages/admin') as unknown as Promise<Area>;
const register = () => import('./pages/public/register') as unknown as Promise<Area>;
const shared = () => import('./pages/Notifications') as unknown as Promise<Area>;

const StudentDashboard = named(student, 'StudentDashboard');
const StudentJobs = named(student, 'StudentJobs');
const StudentJobDetails = named(student, 'StudentJobDetails');
const StudentApplications = named(student, 'StudentApplications');
const StudentProfile = named(student, 'StudentProfile');
const RecruiterDashboard = named(recruiter, 'RecruiterDashboard');
const RecruiterCompany = named(recruiter, 'RecruiterCompany');
const RecruiterJobs = named(recruiter, 'RecruiterJobs');
const RecruiterJobForm = named(recruiter, 'RecruiterJobForm');
const RecruiterApplicants = named(recruiter, 'RecruiterApplicants');
const RecruiterApplicationDetail = named(recruiter, 'RecruiterApplicationDetail');
const AdminDashboard = named(admin, 'AdminDashboard');
const AdminUsers = named(admin, 'AdminUsers');
const AdminJobs = named(admin, 'AdminJobs');
const AdminApplications = named(admin, 'AdminApplications');
const AdminPolicy = named(admin, 'AdminPolicy');
const AdminPlacements = named(admin, 'AdminPlacements');
const AdminConsents = named(admin, 'AdminConsents');
const RegisterStudent = named(register, 'RegisterStudent');
const RegisterRecruiter = named(register, 'RegisterRecruiter');
const Notifications = named(shared, 'Notifications');

const fallback = <Skeleton lines={6} card label="Loading page" />;

function As({ role, children }: { role: Role; children: ReactNode }) {
  return <ProtectedRoute roles={[role]}><Suspense fallback={fallback}>{children}</Suspense></ProtectedRoute>;
}

/** Route table from spec section 7.2 plus the v3 login portals and admin policy pages. */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<PortalChooser />} />
      <Route path="/login/:portal" element={<Login />} />
      <Route path="/register/student" element={<Suspense fallback={fallback}><RegisterStudent /></Suspense>} />
      <Route path="/register/recruiter" element={<Suspense fallback={fallback}><RegisterRecruiter /></Suspense>} />

      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/student" element={<As role="STUDENT"><StudentDashboard /></As>} />
        <Route path="/student/jobs" element={<As role="STUDENT"><StudentJobs /></As>} />
        <Route path="/student/jobs/:id" element={<As role="STUDENT"><StudentJobDetails /></As>} />
        <Route path="/student/applications" element={<As role="STUDENT"><StudentApplications /></As>} />
        <Route path="/student/profile" element={<As role="STUDENT"><StudentProfile /></As>} />

        <Route path="/recruiter" element={<As role="RECRUITER"><RecruiterDashboard /></As>} />
        <Route path="/recruiter/company" element={<As role="RECRUITER"><RecruiterCompany /></As>} />
        <Route path="/recruiter/jobs" element={<As role="RECRUITER"><RecruiterJobs /></As>} />
        <Route path="/recruiter/jobs/new" element={<As role="RECRUITER"><RecruiterJobForm /></As>} />
        <Route path="/recruiter/jobs/:id/edit" element={<As role="RECRUITER"><RecruiterJobForm /></As>} />
        <Route path="/recruiter/jobs/:id/applicants" element={<As role="RECRUITER"><RecruiterApplicants /></As>} />
        <Route path="/recruiter/applications/:id" element={<As role="RECRUITER"><RecruiterApplicationDetail /></As>} />

        <Route path="/admin" element={<As role="ADMIN"><AdminDashboard /></As>} />
        <Route path="/admin/policy" element={<As role="ADMIN"><AdminPolicy /></As>} />
        <Route path="/admin/placements" element={<As role="ADMIN"><AdminPlacements /></As>} />
        <Route path="/admin/consents" element={<As role="ADMIN"><AdminConsents /></As>} />
        <Route path="/admin/users" element={<As role="ADMIN"><AdminUsers /></As>} />
        <Route path="/admin/jobs" element={<As role="ADMIN"><AdminJobs /></As>} />
        <Route path="/admin/applications" element={<As role="ADMIN"><AdminApplications /></As>} />

        <Route path="/notifications" element={<Suspense fallback={fallback}><Notifications /></Suspense>} />
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
