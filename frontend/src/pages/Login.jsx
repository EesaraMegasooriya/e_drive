import { useEffect, useState } from "react";
import { useNavigate, Link, useLocation } from "react-router-dom";
import Swal from "sweetalert2";
import {
  LogIn,
  Mail,
  Lock,
  Eye,
  EyeOff,
  Folder,
  FileText,
  FileImage,
  Home,
} from "lucide-react";

import { useAuth } from "../contexts/AuthContext";
import { authErrorDetails } from "../api/authError";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [form, setForm] = useState({ email: "", password: "" });
  const [errors, setErrors] = useState({});

  useEffect(() => {
    const notice = sessionStorage.getItem("auth_notice");
    if (notice === "session_expired") {
      sessionStorage.removeItem("auth_notice");
      Swal.fire({ icon: "info", title: "Session expired", text: "Please sign in again to continue.", confirmButtonColor: "#1F5C52" });
    } else if (location.state?.passwordReset) {
      Swal.fire({ icon: "success", title: "Password updated", text: "Sign in with your new password.", confirmButtonColor: "#1F5C52" });
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) setErrors((prev) => ({ ...prev, [name]: undefined }));
  };

  const validate = () => {
    const next = {};
    if (!form.email.trim()) next.email = "Email is required.";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
      next.email = "Enter a valid email address.";
    if (!form.password.trim()) next.password = "Password is required.";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;

    try {
      setLoading(true);
      const response = await login(form);

      Swal.fire({
        icon: "success",
        title: "Welcome back",
        text: "Login successful.",
        timer: 1200,
        showConfirmButton: false,
      });

      navigate(response.user?.role === "ADMIN" ? "/admin" : "/drive");
    } catch (error) {
      const details = authErrorDetails(error, "sign you in");
      Swal.fire({
        icon: "error",
        title: details.title,
        text: details.text,
        confirmButtonColor: "#1F5C52",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="grid min-h-screen grid-cols-1 bg-[#F7F6F2] font-[Inter,ui-sans-serif,system-ui] lg:grid-cols-2">
      {/* LEFT: brand / archive preview panel (hidden on mobile) */}
      <div className="relative hidden overflow-hidden bg-[#1F5C52] p-10 lg:flex lg:flex-col lg:justify-between xl:p-14">
        {/* subtle grid texture */}
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.06]"
          style={{
            backgroundImage:
              "linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)",
            backgroundSize: "32px 32px",
          }}
        />

        <div className="relative z-10 flex items-center gap-2 font-mono text-xs uppercase tracking-wide text-[#B9D4CC]">
          <Home size={14} />
          E-Drive · Archive Access
        </div>

        {/* Signature: floating catalog-card mockup, echoing Drive.jsx's FileCard/FolderCard */}
        <div className="relative z-10 my-auto flex h-72 items-center justify-center xl:h-80">
          <div className="absolute w-48 -rotate-6 rounded-xl border border-[#2E6E63] bg-[#245F55] p-4 shadow-lg">
            <span className="font-mono text-[10px] tracking-wide text-[#8FBBAF]">
              D-003
            </span>
            <Folder size={28} className="mb-6 mt-2 text-[#C9971C]" />
            <div className="h-2 w-3/4 rounded bg-[#3A796D]" />
          </div>

          <div className="absolute w-52 translate-x-16 translate-y-6 rotate-3 rounded-xl border border-[#E4E1DA] bg-white p-4 shadow-2xl">
            <span className="font-mono text-[10px] tracking-wide text-[#C7C3B8]">
              F-014
            </span>
            <FileImage size={28} className="mb-6 mt-2 text-[#1F5C52]" />
            <div className="h-2 w-2/3 rounded bg-[#EEECE5]" />
            <div className="mt-1.5 h-2 w-1/3 rounded bg-[#EEECE5]" />
          </div>

          <div className="absolute w-44 -translate-x-20 translate-y-16 -rotate-3 rounded-xl border border-[#2E6E63] bg-[#1B5349] p-4 shadow-lg">
            <span className="font-mono text-[10px] tracking-wide text-[#8FBBAF]">
              F-021
            </span>
            <FileText size={26} className="mb-5 mt-2 text-[#EDEAE1]" />
            <div className="h-2 w-1/2 rounded bg-[#3A796D]" />
          </div>
        </div>

        <div className="relative z-10">
          <p className="max-w-xs text-lg font-semibold leading-snug text-white">
            Every file, catalogued and where you left it.
          </p>
          <p className="mt-2 max-w-xs text-sm text-[#B9D4CC]">
            Sign in to pick up right where you were.
          </p>
        </div>
      </div>

      {/* RIGHT: form */}
      <div className="flex items-center justify-center px-4 py-12 sm:px-6 lg:px-10">
        <div className="w-full max-w-sm">
          <div className="mb-8">
            <span className="font-mono text-xs uppercase tracking-wide text-[#8A8D89]">
              Access · Sign in
            </span>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-[#1B1D1B] sm:text-3xl">
              Welcome back
            </h1>
            <p className="mt-1.5 text-sm text-[#8A8D89]">
              Enter your details to open your drive.
            </p>
          </div>

          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            <div>
              <label
                htmlFor="email"
                className="mb-1.5 block text-sm font-medium text-[#1B1D1B]"
              >
                Email
              </label>
              <div
                className={`flex items-center rounded-lg border bg-white px-3 transition focus-within:ring-2 focus-within:ring-[#1F5C52] ${
                  errors.email ? "border-[#C4432B]" : "border-[#E4E1DA]"
                }`}
              >
                <Mail size={16} className="shrink-0 text-[#8A8D89]" />
                <input
                  id="email"
                  type="email"
                  name="email"
                  autoComplete="email"
                  value={form.email}
                  onChange={handleChange}
                  placeholder="you@example.com"
                  aria-invalid={!!errors.email}
                  aria-describedby={errors.email ? "email-error" : undefined}
                  className="w-full border-none bg-transparent p-3 text-sm text-[#1B1D1B] outline-none placeholder:text-[#B7B3A8]"
                />
              </div>
              {errors.email && (
                <p id="email-error" className="mt-1.5 text-xs text-[#C4432B]">
                  {errors.email}
                </p>
              )}
            </div>

            <div>
              <div className="mb-1.5 flex items-center justify-between">
                <label
                  htmlFor="password"
                  className="text-sm font-medium text-[#1B1D1B]"
                >
                  Password
                </label>
                <Link
                  to="/forgot-password"
                  className="text-xs font-medium text-[#1F5C52] hover:underline"
                >
                  Forgot password?
                </Link>
              </div>
              <div
                className={`flex items-center rounded-lg border bg-white px-3 transition focus-within:ring-2 focus-within:ring-[#1F5C52] ${
                  errors.password ? "border-[#C4432B]" : "border-[#E4E1DA]"
                }`}
              >
                <Lock size={16} className="shrink-0 text-[#8A8D89]" />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  name="password"
                  autoComplete="current-password"
                  value={form.password}
                  onChange={handleChange}
                  placeholder="Enter your password"
                  aria-invalid={!!errors.password}
                  aria-describedby={
                    errors.password ? "password-error" : undefined
                  }
                  className="w-full border-none bg-transparent p-3 text-sm text-[#1B1D1B] outline-none placeholder:text-[#B7B3A8]"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="shrink-0 rounded p-1 text-[#8A8D89] hover:text-[#1B1D1B] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52]"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && (
                <p id="password-error" className="mt-1.5 text-xs text-[#C4432B]">
                  {errors.password}
                </p>
              )}
            </div>

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-3 text-sm font-medium text-white shadow-sm transition hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <LogIn size={16} />
              {loading ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <p className="mt-8 text-center text-sm text-[#8A8D89]">
            Don't have an account?{" "}
            <Link
              to="/register"
              className="font-medium text-[#1F5C52] hover:underline"
            >
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
