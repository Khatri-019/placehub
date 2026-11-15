import { Link, Navigate } from 'react-router';
import { ArrowRight, Building2, ClipboardCheck, GraduationCap, Landmark, Scale, Search, Sparkles } from 'lucide-react';
import { useAuth } from '../../auth/useAuth';
import { ThemeToggle } from '../../components/ThemeToggle';
import { ROLE_HOME } from '../../types/enums';
import styles from './Landing.module.css';

const PORTALS = [
  { to: '/login/student', title: 'Student', accent: styles.student, icon: <GraduationCap aria-hidden="true" />,
    text: 'Browse openings, see exactly why you qualify, apply with your resume and track every step.',
    register: '/register/student' },
  { to: '/login/recruiter', title: 'Recruiter', accent: styles.recruiter, icon: <Building2 aria-hidden="true" />,
    text: 'Publish jobs with eligibility rules, auto-shortlist strong applicants and review resumes.',
    register: '/register/recruiter' },
  { to: '/login/admin', title: 'Placement Office', accent: styles.office, icon: <Landmark aria-hidden="true" />,
    text: 'Set the placement policy, record placements and keep an audited trail of every decision.' },
];

const STEPS = [
  { icon: <Search aria-hidden="true" />, title: 'Discover', text: 'Published jobs with CTC, deadlines and eligibility at a glance.' },
  { icon: <Scale aria-hidden="true" />, title: 'Check fit', text: 'Eligibility and the placement policy are evaluated before you apply.' },
  { icon: <Sparkles aria-hidden="true" />, title: 'Get shortlisted', text: 'Recruiters review manually or auto-shortlist on clear criteria.' },
  { icon: <ClipboardCheck aria-hidden="true" />, title: 'Get placed', text: 'Selections are recorded and everyone is notified instantly.' },
];

export function Landing() {
  const { user } = useAuth();
  if (user) return <Navigate to={ROLE_HOME[user.role]} replace />;
  return (
    <div className={styles.page}>
      <header className={styles.hero}>
        <nav className={styles.nav} aria-label="Site">
          <span className="brand-mark"><span className="dot" aria-hidden="true">P</span>PlaceHub <span className="lite">Lite</span></span>
          <div className="row">
            <ThemeToggle className={`btn btn-icon ${styles.heroToggle}`} />
            <Link to="/login" className={`btn ${styles.heroButton}`}>Sign in</Link>
          </div>
        </nav>
        <div className={styles.heroBody}>
          <p className={styles.eyebrow}>Campus placement management</p>
          <h1 className={styles.title}>Placements that are fair, fast and transparent.</h1>
          <p className={styles.lede}>
            One platform for students, recruiters and the placement office: clear eligibility, a configurable
            placement policy, and real-time updates at every step.
          </p>
          <div className="button-row">
            <Link to="/login/student" className="btn btn-primary">I am a student <ArrowRight aria-hidden="true" /></Link>
            <Link to="/login/recruiter" className={`btn ${styles.heroButton}`}>I am hiring</Link>
          </div>
        </div>
      </header>
      <div className={styles.band} aria-hidden="true" />

      <main id="main-content" className={styles.main}>
        <section aria-labelledby="portals-title">
          <h2 id="portals-title" className="label">Choose your portal</h2>
          <div className={styles.portals}>
            {PORTALS.map((p) => (
              <article key={p.title} className={`card ${styles.portal} ${p.accent}`}>
                <span className={styles.portalIcon}>{p.icon}</span>
                <h3 className={styles.portalTitle}>{p.title}</h3>
                <p className="muted">{p.text}</p>
                <div className="button-row">
                  <Link to={p.to} className="btn btn-primary">Sign in</Link>
                  {p.register && <Link to={p.register} className="btn btn-secondary">Create account</Link>}
                </div>
              </article>
            ))}
          </div>
        </section>

        <section aria-labelledby="how-title" className={styles.how}>
          <h2 id="how-title" className="label">How it works</h2>
          <ol className={styles.steps}>
            {STEPS.map((s, i) => (
              <li key={s.title} className={styles.step}>
                <span className={styles.stepIcon}>{s.icon}</span>
                <div>
                  <div className={styles.stepTitle}><span className="num">{i + 1}.</span> {s.title}</div>
                  <p className="muted small">{s.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      </main>

      <footer className={styles.footer}>
        <span>PlaceHub Lite · campus placement management</span>
        <span className="small">Demo build. Data shown is sample data.</span>
      </footer>
    </div>
  );
}
