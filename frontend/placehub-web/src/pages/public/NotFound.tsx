import { Link } from 'react-router';
import { AuthLayout } from '../../components/AuthLayout';
import { EmptyState } from '../../ui/EmptyState';

export function NotFound() {
  return (
    <AuthLayout heading="This page does not exist." blurb="Check the address, or head back to your dashboard.">
      <h1>Page not found</h1>
      <EmptyState title="We could not find that page" illustration="search">
        <Link to="/" className="btn btn-primary">Go home</Link>
      </EmptyState>
    </AuthLayout>
  );
}
