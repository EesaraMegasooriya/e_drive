import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Mail, Send } from "lucide-react";
import authApi from "../api/authApi";
import { authErrorDetails } from "../api/authError";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError("Enter a valid email address.");
      return;
    }
    try {
      setLoading(true);
      await authApi.requestPasswordReset(email.trim());
      setSent(true);
    } catch (requestError) {
      setError(authErrorDetails(requestError, "send the reset email").text);
    } finally {
      setLoading(false);
    }
  };

  return <AuthCard title={sent ? "Check your inbox" : "Reset your password"} description={sent ? "If an account uses that email address, a reset link is on its way." : "Enter your email and we'll send you a secure reset link."}>
    {sent ? <Link to="/login" className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-3 text-sm font-medium text-white hover:bg-[#184A42]"><ArrowLeft size={16} />Back to sign in</Link> : <form onSubmit={submit} className="space-y-5" noValidate>
      <label className="block text-sm font-medium text-[#1B1D1B]" htmlFor="email">Email</label>
      <div className="flex items-center rounded-lg border border-[#E4E1DA] bg-white px-3 focus-within:ring-2 focus-within:ring-[#1F5C52]"><Mail size={16} className="text-[#8A8D89]" /><input id="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" className="w-full bg-transparent p-3 text-sm outline-none" /></div>
      {error && <p className="text-xs text-[#C4432B]">{error}</p>}
      <button disabled={loading} className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-3 text-sm font-medium text-white hover:bg-[#184A42] disabled:opacity-60"><Send size={16} />{loading ? "Sending…" : "Send reset link"}</button>
    </form>}
    {!sent && <Link to="/login" className="mt-6 flex items-center justify-center gap-2 text-sm font-medium text-[#1F5C52] hover:underline"><ArrowLeft size={15} />Back to sign in</Link>}
  </AuthCard>;
}

export function AuthCard({ title, description, children }) {
  return <main className="flex min-h-screen items-center justify-center bg-[#F7F6F2] px-4 font-[Inter,ui-sans-serif,system-ui]"><section className="w-full max-w-md rounded-2xl border border-[#E4E1DA] bg-white p-7 shadow-sm sm:p-9"><span className="font-mono text-xs uppercase tracking-wide text-[#1F5C52]">EDrive · Account recovery</span><h1 className="mt-3 text-3xl font-bold tracking-tight text-[#1B1D1B]">{title}</h1><p className="mt-2 mb-7 text-sm leading-relaxed text-[#5B5F5C]">{description}</p>{children}</section></main>;
}
