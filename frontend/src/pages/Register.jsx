import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Swal from "sweetalert2";
import {
  Eye,
  EyeOff,
  FolderPlus,
  Lock,
  Mail,
  User,
  UserPlus,
} from "lucide-react";

import authApi from "../api/authApi";
import { authErrorDetails } from "../api/authError";

export default function Register() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [form, setForm] = useState({
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
  });
  const [errors, setErrors] = useState({});

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
    if (errors[name]) {
      setErrors((previous) => ({ ...previous, [name]: undefined }));
    }
  };

  const validate = () => {
    const next = {};

    if (!form.name.trim()) next.name = "Name is required.";
    if (!form.email.trim()) next.email = "Email is required.";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      next.email = "Enter a valid email address.";
    }
    if (form.password.length < 6) {
      next.password = "Use at least 6 characters.";
    }
    if (form.password !== form.confirmPassword) {
      next.confirmPassword = "Passwords do not match.";
    }

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!validate()) return;

    try {
      setLoading(true);
      await authApi.register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
      });

      await Swal.fire({
        icon: "success",
        title: "Account created",
        text: "You can now sign in to open your drive.",
        confirmButtonText: "Continue to sign in",
        confirmButtonColor: "#1F5C52",
      });
      navigate("/login");
    } catch (error) {
      const details = authErrorDetails(error, "create your account");
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

  const fieldClass = (hasError) =>
    `flex items-center rounded-lg border bg-white px-3 transition focus-within:ring-2 focus-within:ring-[#1F5C52] ${
      hasError ? "border-[#C4432B]" : "border-[#E4E1DA]"
    }`;

  return (
    <div className="grid min-h-screen grid-cols-1 bg-[#F7F6F2] font-[Inter,ui-sans-serif,system-ui] lg:grid-cols-2">
      <div className="relative hidden overflow-hidden bg-[#1F5C52] p-10 lg:flex lg:flex-col lg:justify-between xl:p-14">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.06]"
          style={{
            backgroundImage:
              "linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)",
            backgroundSize: "32px 32px",
          }}
        />

        <div className="relative z-10 font-mono text-xs uppercase tracking-wide text-[#B9D4CC]">
          E-Drive · New archive
        </div>

        <div className="relative z-10 my-auto">
          <div className="mx-auto w-full max-w-sm rounded-2xl border border-[#3A796D] bg-[#245F55] p-7 shadow-2xl">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#DDEBE7] text-[#1F5C52]">
              <FolderPlus size={22} />
            </div>
            <p className="mt-6 font-mono text-[11px] uppercase tracking-[0.14em] text-[#9FC6BD]">
              Your private archive
            </p>
            <p className="mt-2 text-xl font-semibold leading-snug text-white">
              A calm place for every file that matters.
            </p>
            <div className="mt-7 space-y-2">
              <div className="h-2 w-full rounded bg-[#3A796D]" />
              <div className="h-2 w-4/5 rounded bg-[#3A796D]" />
              <div className="h-2 w-3/5 rounded bg-[#3A796D]" />
            </div>
          </div>
        </div>

        <p className="relative z-10 max-w-xs text-sm leading-relaxed text-[#B9D4CC]">
          Create an account to upload, organise, and share from one simple drive.
        </p>
      </div>

      <div className="flex items-center justify-center px-4 py-12 sm:px-6 lg:px-10">
        <div className="w-full max-w-sm">
          <div className="mb-8">
            <span className="font-mono text-xs uppercase tracking-wide text-[#8A8D89]">
              Access · Create account
            </span>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-[#1B1D1B] sm:text-3xl">
              Start your drive
            </h1>
            <p className="mt-1.5 text-sm text-[#8A8D89]">
              Set up your private archive in a minute.
            </p>
          </div>

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <Field label="Name" error={errors.name} htmlFor="name">
              <div className={fieldClass(errors.name)}>
                <User size={16} className="shrink-0 text-[#8A8D89]" />
                <input id="name" name="name" autoComplete="name" value={form.name} onChange={handleChange} placeholder="Your name" className="w-full border-none bg-transparent p-3 text-sm text-[#1B1D1B] outline-none placeholder:text-[#B7B3A8]" />
              </div>
            </Field>

            <Field label="Email" error={errors.email} htmlFor="email">
              <div className={fieldClass(errors.email)}>
                <Mail size={16} className="shrink-0 text-[#8A8D89]" />
                <input id="email" type="email" name="email" autoComplete="email" value={form.email} onChange={handleChange} placeholder="you@example.com" className="w-full border-none bg-transparent p-3 text-sm text-[#1B1D1B] outline-none placeholder:text-[#B7B3A8]" />
              </div>
            </Field>

            <Field label="Password" error={errors.password} htmlFor="password">
              <div className={fieldClass(errors.password)}>
                <Lock size={16} className="shrink-0 text-[#8A8D89]" />
                <input id="password" type={showPassword ? "text" : "password"} name="password" autoComplete="new-password" value={form.password} onChange={handleChange} placeholder="At least 6 characters" className="w-full border-none bg-transparent p-3 text-sm text-[#1B1D1B] outline-none placeholder:text-[#B7B3A8]" />
                <PasswordToggle shown={showPassword} onClick={() => setShowPassword((value) => !value)} />
              </div>
            </Field>

            <Field label="Confirm password" error={errors.confirmPassword} htmlFor="confirmPassword">
              <div className={fieldClass(errors.confirmPassword)}>
                <Lock size={16} className="shrink-0 text-[#8A8D89]" />
                <input id="confirmPassword" type={showConfirmation ? "text" : "password"} name="confirmPassword" autoComplete="new-password" value={form.confirmPassword} onChange={handleChange} placeholder="Repeat your password" className="w-full border-none bg-transparent p-3 text-sm text-[#1B1D1B] outline-none placeholder:text-[#B7B3A8]" />
                <PasswordToggle shown={showConfirmation} onClick={() => setShowConfirmation((value) => !value)} />
              </div>
            </Field>

            <button type="submit" disabled={loading} className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-3 text-sm font-medium text-white shadow-sm transition hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60">
              <UserPlus size={16} />
              {loading ? "Creating account…" : "Create account"}
            </button>
          </form>

          <p className="mt-8 text-center text-sm text-[#8A8D89]">
            Already have an account?{" "}
            <Link to="/login" className="font-medium text-[#1F5C52] hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

function Field({ label, error, htmlFor, children }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-medium text-[#1B1D1B]">
        {label}
      </label>
      {children}
      {error && <p className="mt-1.5 text-xs text-[#C4432B]">{error}</p>}
    </div>
  );
}

function PasswordToggle({ shown, onClick }) {
  return (
    <button type="button" onClick={onClick} aria-label={shown ? "Hide password" : "Show password"} className="shrink-0 rounded p-1 text-[#8A8D89] hover:text-[#1B1D1B] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52]">
      {shown ? <EyeOff size={16} /> : <Eye size={16} />}
    </button>
  );
}
