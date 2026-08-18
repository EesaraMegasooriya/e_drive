import { Link, Outlet } from "react-router-dom";
import { LogOut, Settings, ShieldCheck } from "lucide-react";
import useAuth from "../contexts/useAuth";
import E_Drive_Logo from "../assets/E_Drive_Logo.webp";

function getInitials(name, email) {
  const source = name?.trim() || email?.trim() || "";

  if (!source) return "?";

  const parts = source.split(" ").filter(Boolean);

  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }

  return source.slice(0, 2).toUpperCase();
}

export default function MainLayout() {
  const { user, logout } = useAuth();

  const initials = getInitials(user?.name, user?.email);

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      {/* Header */}
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur supports-[backdrop-filter]:bg-white/75">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3.5 sm:px-6">

          {/* Brand */}
          <Link
            to="/"
            className="flex min-w-0 items-center gap-2.5"
            aria-label="E-Drive Home"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-blue-600 shadow-sm">
              <img
                src={E_Drive_Logo}
                alt="E-Drive Logo"
                className="h-full w-full object-contain"
              />
            </span>

            <h1 className="truncate text-lg font-bold tracking-tight text-slate-900 sm:text-xl">
              E-Drive
            </h1>
          </Link>

          {/* User */}
          <div className="flex shrink-0 items-center gap-3">

            {/* User Details */}
            <div className="hidden text-right sm:block">
              <p className="max-w-[180px] truncate text-sm font-semibold leading-tight text-slate-900">
                {user?.name || user?.email || "User"}
              </p>

              {user?.name && user?.email && (
                <p className="max-w-[180px] truncate text-xs leading-tight text-slate-500">
                  {user.email}
                </p>
              )}
            </div>

            {/* Avatar */}
            <div
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-100 text-xs font-semibold text-blue-700 ring-1 ring-inset ring-blue-200"
              title={user?.name || user?.email}
            >
              {initials}
            </div>

            <div
              className="h-6 w-px bg-slate-200"
              aria-hidden="true"
            />

            {/* Profile Settings */}
            <Link
              to="/profile"
              aria-label="Profile settings"
              title="Profile settings"
              className="rounded-lg p-2 text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
            >
              <Settings size={16} />
            </Link>

            {/* Admin */}
            {user?.role === "ADMIN" && (
              <Link
                to="/admin"
                aria-label="Admin panel"
                title="Admin panel"
                className="rounded-lg p-2 text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
              >
                <ShieldCheck size={16} />
              </Link>
            )}

            {/* Logout */}
            <button
              type="button"
              onClick={logout}
              aria-label="Log out"
              title="Log out"
              className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-2 text-sm font-medium text-slate-600 transition hover:border-red-200 hover:bg-red-50 hover:text-red-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
            >
              <LogOut size={16} />

              <span className="hidden sm:inline">
                Log out
              </span>
            </button>
          </div>
        </div>
      </header>

      {/* Page Content */}
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-7xl px-4 py-4 sm:px-6">
          <p className="text-center text-xs text-slate-400">
            E-Drive · eesara.com
          </p>
        </div>
      </footer>
    </div>
  );
}
