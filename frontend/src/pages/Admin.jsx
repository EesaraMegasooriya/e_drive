import { useEffect, useState } from "react";
import { Users, FileText, Folder, Link2, KeyRound, Trash2, HardDrive } from "lucide-react";
import Swal from "sweetalert2";
import adminApi from "../api/adminApi";

export default function Admin() {
  const [overview, setOverview] = useState(null);
  const [users, setUsers] = useState([]);
  const [files, setFiles] = useState([]);
  const [tab, setTab] = useState("users");
  const load = async () => {
    const [o, u, f] = await Promise.all([
      adminApi.overview(),
      adminApi.users(),
      adminApi.files(),
    ]);
    setOverview(o);
    setUsers(u);
    setFiles(f);
  };
  useEffect(() => {
    // Initial synchronization with the administration API.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load().catch(() => {});
  }, []);
  const toggle = async (user) => {
    await adminApi.setUserActive(user.uuid, !user.active);
    await load();
  };
  const resetPassword = async (user) => {
    const { value: password } = await Swal.fire({
      title: `Reset password for ${user.name}`,
      input: "password",
      inputLabel: "New password",
      inputAttributes: { minLength: 6, autoComplete: "new-password" },
      showCancelButton: true,
      confirmButtonText: "Reset password",
      inputValidator: (value) => value?.length >= 6 ? undefined : "Use at least 6 characters.",
    });
    if (!password) return;
    try {
      await adminApi.resetPassword(user.uuid, password);
      Swal.fire({ icon: "success", title: "Password reset", timer: 1400, showConfirmButton: false });
    } catch (error) {
      Swal.fire({ icon: "error", title: "Password reset failed", text: error.response?.data?.message ?? "Please try again." });
    }
  };
  const deleteUser = async (user) => {
    const result = await Swal.fire({
      title: `Delete ${user.name}?`,
      text: "All of this user's files, folders, public links, and stored data will be permanently deleted.",
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: "Delete user and data",
      confirmButtonColor: "#C4432B",
    });
    if (!result.isConfirmed) return;
    try {
      await adminApi.deleteUser(user.uuid);
      await load();
      Swal.fire({ icon: "success", title: "User and data deleted", timer: 1500, showConfirmButton: false });
    } catch (error) {
      Swal.fire({ icon: "error", title: "Delete failed", text: error.response?.data?.message ?? "Please try again." });
    }
  };
  const formatBytes = (bytes) => {
    if (!bytes) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`;
  };
  const cards = overview
    ? [
        [Users, "Users", overview.users],
        [FileText, "Files", overview.files],
        [Folder, "Folders", overview.folders],
        [Link2, "Public links", overview.shares],
      ]
    : [];
  const storageTotal = overview?.storageTotal || 0;
  const storageUsed = overview?.storageUsed || 0;
  const storageAvailable = overview?.storageAvailable || 0;
  const storagePercent = storageTotal > 0
    ? Math.min(100, (storageUsed / storageTotal) * 100)
    : 0;
  const userStoragePercent = (user) => {
    if (!user.storageLimit || user.storageLimit >= Number.MAX_SAFE_INTEGER) return 0;
    return Math.min(100, (user.usedStorage / user.storageLimit) * 100);
  };
  return (
    <div className="space-y-7">
      <div>
        <span className="font-mono text-xs uppercase tracking-wide text-[#1F5C52]">
          Administration
        </span>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#1B1D1B]">
          Drive control centre
        </h1>
        <p className="mt-1 text-sm text-[#5B5F5C]">
          Monitor accounts and the data stored across EDrive.
        </p>
      </div>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {cards.map(([Icon, label, value]) => (
          <div
            key={label}
            className="rounded-xl border border-[#E4E1DA] bg-white p-4 shadow-sm"
          >
            <Icon size={18} className="text-[#1F5C52]" />
            <p className="mt-4 text-2xl font-bold text-[#1B1D1B]">{value}</p>
            <p className="text-sm text-[#8A8D89]">{label}</p>
          </div>
        ))}
      </div>
      {overview && (
        <section className="grid gap-4 rounded-xl border border-[#E4E1DA] bg-white p-5 shadow-sm md:grid-cols-[auto_1fr] md:items-center md:gap-7">
          <div className="relative mx-auto h-36 w-36 shrink-0 rounded-full" style={{ background: `conic-gradient(#1F5C52 ${storagePercent}%, #E4E1DA 0)` }}>
            <div className="absolute inset-4 flex flex-col items-center justify-center rounded-full bg-white text-center">
              <HardDrive size={20} className="text-[#1F5C52]" />
              <strong className="mt-1 text-lg text-[#1B1D1B]">{storagePercent.toFixed(1)}%</strong>
              <span className="text-[11px] text-[#8A8D89]">disk used</span>
            </div>
          </div>
          <div>
            <h2 className="text-lg font-bold text-[#1B1D1B]">Server storage</h2>
            <p className="mt-1 text-sm text-[#5B5F5C]">Capacity of the mounted upload volume.</p>
            <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
              <StorageMetric label="Available" value={formatBytes(storageAvailable)} accent />
              <StorageMetric label="Disk used" value={formatBytes(storageUsed)} />
              <StorageMetric label="Drive files" value={formatBytes(overview.applicationStorageUsed)} />
            </div>
            <p className="mt-3 text-xs text-[#8A8D89]">Total capacity: {formatBytes(storageTotal)}</p>
          </div>
        </section>
      )}
      <div className="rounded-xl border border-[#E4E1DA] bg-white shadow-sm">
        <div className="flex gap-2 border-b border-[#E4E1DA] p-3">
          <button
            onClick={() => setTab("users")}
            className={`rounded-lg px-3 py-2 text-sm font-medium ${tab === "users" ? "bg-[#EEF4F2] text-[#1F5C52]" : "text-[#5B5F5C]"}`}
          >
            Users
          </button>
          <button
            onClick={() => setTab("files")}
            className={`rounded-lg px-3 py-2 text-sm font-medium ${tab === "files" ? "bg-[#EEF4F2] text-[#1F5C52]" : "text-[#5B5F5C]"}`}
          >
            All files
          </button>
        </div>
        {tab === "users" ? (
          <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm">
            <thead className="text-xs text-[#8A8D89]">
              <tr>
                <th className="p-4">User</th>
                <th className="p-4">Role</th>
                <th className="p-4">Storage used</th>
                <th className="p-4">Status</th>
                <th className="p-4" />
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.uuid} className="border-t border-[#EEECE5]">
                  <td className="p-4">
                    <b>{user.name}</b>
                    <small className="block text-[#8A8D89]">{user.email}</small>
                  </td>
                  <td className="p-4">{user.role}</td>
                  <td className="p-4">
                    <div className="font-mono text-xs">{formatBytes(user.usedStorage)}</div>
                    <div className="mt-1.5 h-1.5 w-28 overflow-hidden rounded-full bg-[#E4E1DA]">
                      <div className="h-full rounded-full bg-[#1F5C52]" style={{ width: `${userStoragePercent(user)}%` }} />
                    </div>
                    <div className="mt-1 text-[10px] text-[#8A8D89]">{user.storageLimit >= Number.MAX_SAFE_INTEGER ? "Unlimited" : `of ${formatBytes(user.storageLimit)}`}</div>
                  </td>
                  <td className="p-4">
                    {user.active ? "Active" : "Suspended"}
                  </td>
                  <td className="p-4">
                    {user.role === "ADMIN" ? (
                      <span className="text-xs text-[#8A8D89]">Administrator</span>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        <button onClick={() => toggle(user)} className="rounded border border-[#E4E1DA] px-3 py-1.5 text-xs">{user.active ? "Suspend" : "Activate"}</button>
                        <button onClick={() => resetPassword(user)} className="inline-flex items-center gap-1 rounded border border-[#E4E1DA] px-3 py-1.5 text-xs"><KeyRound size={12} />Reset password</button>
                        <button onClick={() => deleteUser(user)} className="inline-flex items-center gap-1 rounded border border-[#F3D3CB] px-3 py-1.5 text-xs text-[#C4432B]"><Trash2 size={12} />Delete</button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></div>
        ) : (
          <div className="overflow-x-auto"><table className="w-full min-w-[620px] text-left text-sm">
            <thead className="text-xs text-[#8A8D89]">
              <tr>
                <th className="p-4">File</th>
                <th className="p-4">Owner</th>
                <th className="p-4">Size</th>
                <th className="p-4">Visibility</th>
              </tr>
            </thead>
            <tbody>
              {files.map((file) => (
                <tr key={file.uuid} className="border-t border-[#EEECE5]">
                  <td className="p-4">{file.name}</td>
                  <td className="p-4">{file.owner}</td>
                  <td className="p-4 font-mono text-xs">{formatBytes(file.size)}</td>
                  <td className="p-4">
                    {file.isPublic ? "Public" : "Private"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </div>
    </div>
  );
}

function StorageMetric({ label, value, accent = false }) {
  return <div className={`rounded-lg border p-3 ${accent ? "border-[#B9D2CD] bg-[#EEF4F2]" : "border-[#EEECE5] bg-[#FAF9F6]"}`}><p className="text-xs text-[#8A8D89]">{label}</p><p className="mt-1 font-mono text-sm font-semibold text-[#1B1D1B]">{value}</p></div>;
}
