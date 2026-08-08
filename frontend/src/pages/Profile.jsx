import { useEffect, useState } from "react";
import { KeyRound, Save, UserRound } from "lucide-react";
import Swal from "sweetalert2";
import authApi from "../api/authApi";
import { useAuth } from "../contexts/AuthContext";

export default function Profile() {
  const { user, updateUser } = useAuth();
  const [name, setName] = useState(user?.name ?? "");
  const [password, setPassword] = useState({ currentPassword: "", newPassword: "", confirm: "" });
  const [saving, setSaving] = useState(false);

  useEffect(() => { authApi.getProfile().then((profile) => { setName(profile.name); updateUser(profile); }).catch(() => {}); }, []);

  const saveProfile = async (event) => {
    event.preventDefault();
    if (!name.trim()) return;
    try { setSaving(true); const profile = await authApi.updateProfile({ name: name.trim() }); updateUser(profile); Swal.fire({ icon: "success", title: "Profile updated", timer: 1300, showConfirmButton: false }); }
    catch (error) { Swal.fire({ icon: "error", title: "Couldn't save profile", text: error.response?.data?.message ?? "Please try again." }); }
    finally { setSaving(false); }
  };

  const savePassword = async (event) => {
    event.preventDefault();
    if (password.newPassword.length < 6) return Swal.fire({ icon: "error", title: "Use at least 6 characters" });
    if (password.newPassword !== password.confirm) return Swal.fire({ icon: "error", title: "Passwords do not match" });
    try { setSaving(true); await authApi.changePassword({ currentPassword: password.currentPassword, newPassword: password.newPassword }); setPassword({ currentPassword: "", newPassword: "", confirm: "" }); Swal.fire({ icon: "success", title: "Password updated", timer: 1300, showConfirmButton: false }); }
    catch (error) { Swal.fire({ icon: "error", title: "Couldn't change password", text: error.response?.data?.message ?? "Check your current password and try again." }); }
    finally { setSaving(false); }
  };

  return <div className="mx-auto max-w-3xl space-y-6"><div><span className="font-mono text-xs uppercase tracking-wide text-[#8A8D89]">Account settings</span><h1 className="mt-2 text-3xl font-bold tracking-tight text-[#1B1D1B]">Your profile</h1><p className="mt-1 text-sm text-[#5B5F5C]">Manage the details that identify your private drive.</p></div><section className="rounded-xl border border-[#E4E1DA] bg-white p-6 shadow-sm"><div className="mb-6 flex items-center gap-3"><span className="rounded-lg bg-[#EEF4F2] p-2 text-[#1F5C52]"><UserRound size={20}/></span><div><h2 className="font-semibold text-[#1B1D1B]">Personal details</h2><p className="text-sm text-[#8A8D89]">Your email is tied to this account and can’t be changed here.</p></div></div><form onSubmit={saveProfile} className="space-y-4"><Field label="Name"><input value={name} onChange={(e) => setName(e.target.value)} className="input" /></Field><Field label="Email"><input value={user?.email ?? ""} disabled className="input cursor-not-allowed bg-[#F7F6F2] text-[#8A8D89]" /></Field><button disabled={saving} className="button"><Save size={16}/>{saving ? "Saving…" : "Save details"}</button></form></section><section className="rounded-xl border border-[#E4E1DA] bg-white p-6 shadow-sm"><div className="mb-6 flex items-center gap-3"><span className="rounded-lg bg-[#EEF4F2] p-2 text-[#1F5C52]"><KeyRound size={20}/></span><div><h2 className="font-semibold text-[#1B1D1B]">Password</h2><p className="text-sm text-[#8A8D89]">Choose a strong password with at least 6 characters.</p></div></div><form onSubmit={savePassword} className="space-y-4"><Field label="Current password"><input type="password" value={password.currentPassword} onChange={(e) => setPassword({...password, currentPassword:e.target.value})} className="input" /></Field><Field label="New password"><input type="password" value={password.newPassword} onChange={(e) => setPassword({...password, newPassword:e.target.value})} className="input" /></Field><Field label="Confirm new password"><input type="password" value={password.confirm} onChange={(e) => setPassword({...password, confirm:e.target.value})} className="input" /></Field><button disabled={saving} className="button"><KeyRound size={16}/>{saving ? "Saving…" : "Change password"}</button></form></section></div>;
}
function Field({label, children}) { return <label className="block text-sm font-medium text-[#1B1D1B]"><span className="mb-1.5 block">{label}</span>{children}</label>; }
