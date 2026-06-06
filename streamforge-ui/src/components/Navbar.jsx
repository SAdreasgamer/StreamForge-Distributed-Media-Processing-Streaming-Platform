import { Link, useLocation } from 'react-router-dom';

export default function Navbar() {
  const location = useLocation();

  const isActive = (path) => location.pathname === path;

  return (
    <nav className="sticky top-0 z-50 border-b border-border bg-surface/80 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
        <Link to="/" className="flex items-center gap-3 no-underline">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-accent to-accent-cyan">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="5 3 19 12 5 21 5 3" />
            </svg>
          </div>
          <span className="text-xl font-bold tracking-tight gradient-text">StreamForge</span>
        </Link>

        <div className="flex items-center gap-2">
          <Link
            to="/"
            className={`rounded-lg px-4 py-2 text-sm font-medium transition-all duration-200 no-underline ${
              isActive('/')
                ? 'bg-accent/15 text-accent'
                : 'text-text-secondary hover:bg-surface-elevated hover:text-text-primary'
            }`}
          >
            Dashboard
          </Link>
          <Link
            to="/upload"
            className={`rounded-lg px-4 py-2 text-sm font-medium transition-all duration-200 no-underline ${
              isActive('/upload')
                ? 'bg-accent/15 text-accent'
                : 'text-text-secondary hover:bg-surface-elevated hover:text-text-primary'
            }`}
          >
            Upload
          </Link>
        </div>
      </div>
    </nav>
  );
}
