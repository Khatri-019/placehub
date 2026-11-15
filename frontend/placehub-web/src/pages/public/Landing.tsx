import { Link, Navigate } from 'react-router';
import { useAuth } from '../../auth/useAuth';
import { ROLE_HOME } from '../../types/enums';

export function Landing() {
  const { user } = useAuth();
  if (user) return <Navigate to={ROLE_HOME[user.role]} replace />;
  return (
    <div className="landing">
      <section className="landing-hero">
        <h1>Campus placements, without the spreadsheets.</h1>
        <p>
          PlaceHub Lite connects students, recruiters and the placement office. Recruiters publish jobs with eligibility
          rules, students see exactly why they qualify, and everyone gets notified as applications move.
        </p>
        <div className="button-row">
          <Link to="/login" className="btn btn-primary">Sign in</Link>
          <Link to="/register/student" className="btn btn-secondary">I am a student</Link>
          <Link to="/register/recruiter" className="btn btn-secondary">I am a recruiter</Link>
        </div>
      </section>
      <section className="feature-grid" aria-label="Features">
        <div className="card">
          <h2>Clear eligibility</h2>
          <p className="muted">CGPA, backlogs, branch and graduation year are checked against your verified profile, with every failing reason listed.</p>
        </div>
        <div className="card">
          <h2>One application flow</h2>
          <p className="muted">Apply once, track status from applied to selected, and withdraw any time before a final decision.</p>
        </div>
        <div className="card">
          <h2>Recruiter workspace</h2>
          <p className="muted">Manage your company profile, publish openings and move applicants through review.</p>
        </div>
      </section>
    </div>
  );
}
