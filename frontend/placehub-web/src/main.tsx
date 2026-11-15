import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AppRoutes } from './App';
import { AuthProvider } from './auth/AuthContext';
import { Toaster } from './components/Toaster';
import './styles/global.css';
import { applyTheme, savedTheme } from './utils/theme';

// Apply the saved theme before the first render so there is no light/dark flash.
applyTheme(savedTheme());

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
        <Toaster />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
