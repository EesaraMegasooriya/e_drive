import { useEffect, useState } from "react";
import { ShieldCheck, Users, FileText, Folder, Link2 } from "lucide-react";
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
    load().catch(() => {});
  }, []);
  const toggle = async (user) => {
    await adminApi.setUserActive(user.uuid, !user.active);
    await load();
  };
  const cards = overview
    ? [
        [Users, "Users", overview.users],
        [FileText, "Files", overview.files],
        [Folder, "Folders", overview.folders],
        [Link2, "Public links", overview.shares],
      ]
    : [];
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
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-[#8A8D89]">
              <tr>
                <th className="p-4">User</th>
                <th className="p-4">Role</th>
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
                    {user.active ? "Active" : "Suspended"}
                  </td>
                  <td className="p-4">
                    <button
                      onClick={() => toggle(user)}
                      className="rounded border border-[#E4E1DA] px-3 py-1.5 text-xs"
                    >
                      {user.active ? "Suspend" : "Activate"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-[#8A8D89]">
              <tr>
                <th className="p-4">File</th>
                <th className="p-4">Owner</th>
                <th className="p-4">Visibility</th>
              </tr>
            </thead>
            <tbody>
              {files.map((file) => (
                <tr key={file.uuid} className="border-t border-[#EEECE5]">
                  <td className="p-4">{file.name}</td>
                  <td className="p-4">{file.owner}</td>
                  <td className="p-4">
                    {file.isPublic ? "Public" : "Private"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
