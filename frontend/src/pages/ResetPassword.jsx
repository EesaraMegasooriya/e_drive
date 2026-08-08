import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Check, Lock } from "lucide-react";
import authApi from "../api/authApi";
import { AuthCard } from "./ForgotPassword";

export default function ResetPassword() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const token = params.get("token");

  const submit = async (event) => {
    event.preventDefault();
    if (!token) return setError("This reset link is invalid.");
    if (password.length < 6) return setError("Use at least 6 characters.");
    if (password !== confirmation) return setError("Passwords do not match.");
    try { setLoading(true); setError(""); await authApi.resetPassword(token, password); navigate("/login", { state: { passwordReset: true } }); }
    catch (requestError) { setError(requestError.response?.data?.message ?? "This reset link is invalid or has expired."); }
    finally { setLoading(false); }
  };

  return <AuthCard title="Choose a new password" description="Use a password with at least 6 characters to secure your drive."><form onSubmit={submit} className="space-y-4"><Password label="New password" value={password} onChange={setPassword} /><Password label="Confirm password" value={confirmation} onChange={setConfirmation} />{error && <p className="text-xs text-[#C4432B]">{error}</p>}<button disabled={loading} className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-3 text-sm font-medium text-white hover:bg-[#184A42] disabled:opacity-60"><Check size={16}/>{loading ? "Saving…" : "Save new password"}</button></form><Link to="/login" className="mt-6 block text-center text-sm font-medium text-[#1F5C52] hover:underline">Back to sign in</Link></AuthCard>;
}

function Password({ label, value, onChange }) { return <div><label className="mb-1.5 block text-sm font-medium text-[#1B1D1B]">{label}</label><div className="flex items-center rounded-lg border border-[#E4E1DA] bg-white px-3 focus-within:ring-2 focus-within:ring-[#1F5C52]"><Lock size={16} className="text-[#8A8D89]"/><input type="password" autoComplete="new-password" value={value} onChange={(event) => onChange(event.target.value)} className="w-full bg-transparent p-3 text-sm outline-none"/></div></div>; }
