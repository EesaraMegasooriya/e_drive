import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Eye, EyeOff, Folder, Image, LogOut, Pencil, Plus, Star, Trash2, Upload, X } from "lucide-react";
import "./GalleryAdmin.css";

const TOKEN = "gallery-admin-session";
async function api(path, { method = "GET", body, signal } = {}) {
  const response = await fetch(`/api/gallery-admin${path}`, {
    method, signal,
    headers: { "X-Gallery-Token": sessionStorage.getItem(TOKEN) || "", ...(body && !(body instanceof FormData) ? { "Content-Type": "application/json" } : {}) },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    if (response.status === 401 && path !== "/login") window.dispatchEvent(new Event("gallery-admin-expired"));
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || "The request failed. Please try again.");
  }
  return response;
}
async function json(path, options) {
  const response = await api(path, options);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function AdminImage({ item, large = false }) {
  const element = useRef(null);
  const [url, setUrl] = useState("");
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    let objectUrl;
    let started = false;
    async function load() {
      if (started) return;
      started = true;
      try {
        const path = item.contentUrl.replace("/api/gallery-admin", "").replace(/\/content$/, "/preview");
        const response = await api(`${path}?size=${large ? 1920 : 640}`, { signal: controller.signal });
        const blob = await response.blob();
        if (!controller.signal.aborted) { objectUrl = URL.createObjectURL(blob); setUrl(objectUrl); }
      } catch { if (!controller.signal.aborted) setFailed(true); }
    }
    const observer = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting)) { load(); observer.disconnect(); } }, { rootMargin: "150px" });
    observer.observe(element.current);
    return () => { observer.disconnect(); controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [item.contentUrl, item.modifiedAt, large]);
  return <div className="ga-preview" ref={element}>{url ? <img src={url} alt={item.name} /> : <span><Image size={30} />{failed ? "Preview unavailable" : "Loading preview…"}</span>}</div>;
}

function Modal({ title, children, onClose, busy = false }) {
  const dialog = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    dialog.current.showModal();
    return () => previous?.focus();
  }, []);
  return <dialog className="ga-dialog" ref={dialog} aria-label={title} onCancel={e => { if (busy) e.preventDefault(); else onClose(); }}>
    <div className="ga-dialog-heading"><h2>{title}</h2><button disabled={busy} className="ga-icon" onClick={onClose} aria-label="Close"><X /></button></div>{children}
  </dialog>;
}

export default function GalleryAdmin() {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN));
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const expire = () => { sessionStorage.removeItem(TOKEN); setToken(null); setError("Your session expired. Please sign in again."); };
    window.addEventListener("gallery-admin-expired", expire);
    return () => window.removeEventListener("gallery-admin-expired", expire);
  }, []);
  async function login(event) {
    event.preventDefault(); setBusy(true); setError("");
    try { const result = await json("/login", { method: "POST", body: { username, password } }); sessionStorage.setItem(TOKEN, result.token); setToken(result.token); setPassword(""); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function logout() {
    try { await api("/logout", { method: "POST" }); } catch { /* Clear the local session even if the server is unavailable. */ } finally { sessionStorage.removeItem(TOKEN); setToken(null); }
  }
  if (token) return <Dashboard onLogout={logout} />;
  return <main className="ga-login"><form onSubmit={login} className="ga-login-card"><Link to="/" className="ga-back"><ArrowLeft size={16} /> Back to gallery</Link><span className="ga-eyebrow">E GALLERY</span><h1>Admin access.</h1><p>Manage your collections and the moments inside.</p>
    <label>Username<input autoFocus autoComplete="username" required value={username} onChange={e => setUsername(e.target.value)} /></label>
    <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} /></label>
    {error && <p role="alert" className="ga-error">{error}</p>}<button className="ga-primary" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
  </form></main>;
}

function Dashboard({ onLogout }) {
  const [folders, setFolders] = useState([]);
  const [selected, setSelected] = useState(null);
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [action, setAction] = useState(null);
  const [name, setName] = useState("");
  const [view, setView] = useState(null);
  const [search, setSearch] = useState("");
  const folder = folders.find(f => f.id === selected);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([json("/galleries", { signal: controller.signal }), selected ? json(`/galleries/${selected}/media?limit=100`, { signal: controller.signal }) : Promise.resolve(null)])
      .then(([list, page]) => { setFolders(list); setItems(page?.items || []); setTotal(page?.total || 0); })
      .catch(e => { if (!controller.signal.aborted) setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [selected, refresh]);
  function openFolder(id) { setSelected(id); setLoading(true); setSearch(""); setError(""); setNotice(""); }
  function prompt(type, target) { setAction({ type, target }); setName(type === "create" ? "" : target.name); setError(""); }
  async function run(path, method, body, message = "Saved.") {
    setBusy(true); setError(""); setNotice("");
    try { const result = await json(path, { method, body }); if (alive.current) { setNotice(message); setRefresh(n => n + 1); } return result || {}; }
    catch (e) { if (alive.current) setError(e.message); return null; }
    finally { if (alive.current) setBusy(false); }
  }
  async function confirm(event) {
    event.preventDefault();
    const { type, target } = action;
    let result;
    if (type === "create") result = await run("/galleries", "POST", { name }, "Folder created.");
    if (type === "rename-folder") { result = await run(`/galleries/${target.id}/name`, "PUT", { name }, "Folder renamed."); if (result && selected === target.id) setSelected(result.id); }
    if (type === "delete-folder") { result = await run(`/galleries/${target.id}`, "DELETE", null, "Folder moved to recovery storage."); if (result && selected === target.id) setSelected(null); }
    if (type === "rename-media") result = await run(`/galleries/${selected}/media/${target.id}/name`, "PUT", { name }, "File renamed.");
    if (type === "delete-media") result = await run(`/galleries/${selected}/media/${target.id}`, "DELETE", null, "File moved to recovery storage.");
    if (result) setAction(null);
  }
  async function upload(event) {
    const files = [...(event.target.files || [])]; event.target.value = "";
    if (!files.length) return;
    setBusy(true); setError("");
    const failures = []; let success = 0;
    for (let i = 0; i < files.length; i++) {
      if (!alive.current) break;
      setNotice(`Uploading ${i + 1} of ${files.length}: ${files[i].name}`);
      const body = new FormData(); body.append("file", files[i]);
      try { await api(`/galleries/${selected}/media`, { method: "POST", body }); success++; }
      catch (e) { failures.push(`${files[i].name}: ${e.message}`); }
    }
    if (alive.current) { setNotice(`${success} of ${files.length} files uploaded.`); setError(failures.join("\n")); setBusy(false); setRefresh(n => n + 1); }
  }
  async function more() {
    setBusy(true);
    try { const page = await json(`/galleries/${selected}/media?offset=${items.length}&limit=100`); setItems(old => [...old, ...page.items]); setTotal(page.total); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  const displayed = (selected ? items : folders).filter(item => item.name.toLowerCase().includes(search.toLowerCase()));
  return <div className="ga-shell"><header className="ga-header"><Link to="/">e. <span>GALLERY ADMIN</span></Link><div><a href="/" target="_blank" rel="noreferrer">View gallery ↗</a><button disabled={busy} onClick={onLogout}><LogOut size={16} /> Sign out</button></div></header>
    <main className="ga-main"><div className="ga-heading"><div>{selected && <button className="ga-back" disabled={busy} onClick={() => openFolder(null)}><ArrowLeft size={16} /> All folders</button>}<span className="ga-eyebrow">YOUR COLLECTIONS</span><h1>{selected ? folder?.name || "Folder" : "Gallery manager"}</h1><p>{selected ? `${total} files · ${folder?.hidden ? "Hidden from visitors" : "Visible to visitors"}` : `${folders.length} folders, including hidden collections`}</p></div><div className="ga-heading-actions">{selected ? <label className={`ga-primary ga-upload ${busy ? "ga-disabled" : ""}`}><Upload size={17} /> Upload files<input disabled={busy} type="file" multiple accept="image/*,video/*" onChange={upload} /></label> : <button disabled={busy} className="ga-primary" onClick={() => prompt("create")}><Plus size={17} /> New folder</button>}</div></div>
    {folder && <div className="ga-folder-actions"><button disabled={busy} onClick={() => prompt("rename-folder", folder)}><Pencil size={15} /> Rename folder</button><button disabled={busy} onClick={() => run(`/galleries/${folder.id}/visibility`, "PUT", { hidden: !folder.hidden })}>{folder.hidden ? <Eye size={15} /> : <EyeOff size={15} />}{folder.hidden ? "Show folder" : "Hide folder"}</button>{folder.coverId && <button disabled={busy} onClick={() => run(`/galleries/${folder.id}/cover`, "PUT", { mediaId: null })}>Reset cover</button>}<button disabled={busy} className="ga-danger" onClick={() => prompt("delete-folder", folder)}><Trash2 size={15} /> Delete folder</button></div>}
    <div className="ga-toolbar"><input aria-label="Search loaded items" type="search" placeholder={selected ? "Search loaded files…" : "Search folders…"} value={search} onChange={e => setSearch(e.target.value)} /><button disabled={busy} onClick={() => { setLoading(true); setRefresh(n => n + 1); setError(""); }}>Refresh</button></div>
    {notice && <p className="ga-notice" role="status">{notice}</p>}{error && !action && <p className="ga-error" role="alert">{error}</p>}
    {loading ? <p role="status">Loading collections…</p> : <div className={selected ? "ga-media-grid" : "ga-folders"}>{displayed.map(item => selected ? <article className={`ga-media-card ${item.hidden ? "ga-hidden" : ""}`} key={`${item.id}:${item.modifiedAt}`}><button className="ga-image-button" onClick={() => setView(item)}><AdminImage item={item} /></button><div className="ga-card-body"><h2 title={item.name}>{item.name}</h2><div className="ga-badges">{item.hidden && <span>Hidden</span>}{folder?.coverId === item.id && <span>Cover photo</span>}<span>{(item.size / 1024 / 1024).toFixed(1)} MB</span></div><div className="ga-card-actions"><button disabled={busy} title="Rename file" aria-label={`Rename ${item.name}`} onClick={() => prompt("rename-media", item)}><Pencil size={16} /></button><button disabled={busy} title={item.hidden ? "Show file" : "Hide file"} aria-label={`${item.hidden ? "Show" : "Hide"} ${item.name}`} onClick={() => run(`/galleries/${selected}/media/${item.id}/visibility`, "PUT", { hidden: !item.hidden })}>{item.hidden ? <Eye size={16} /> : <EyeOff size={16} />}</button>{item.type === "image" && <button disabled={busy || item.hidden} title="Set as folder cover" aria-label={`Set ${item.name} as cover`} onClick={() => run(`/galleries/${selected}/cover`, "PUT", { mediaId: item.id }, "Cover photo saved.")}><Star size={16} fill={folder?.coverId === item.id ? "currentColor" : "none"} /></button>}<button disabled={busy} className="ga-danger" title="Delete file" aria-label={`Delete ${item.name}`} onClick={() => prompt("delete-media", item)}><Trash2 size={16} /></button></div></div></article> : <article className="ga-folder-card" key={item.id}><button disabled={busy} className="ga-folder-open" onClick={() => openFolder(item.id)}><Folder size={34} /><h2>{item.name}</h2><span>{item.hidden ? "Hidden" : "Visible"}</span></button><div className="ga-card-actions"><button disabled={busy} onClick={() => prompt("rename-folder", item)}><Pencil size={15} /> Rename</button><button disabled={busy} onClick={() => run(`/galleries/${item.id}/visibility`, "PUT", { hidden: !item.hidden })}>{item.hidden ? "Show" : "Hide"}</button><button disabled={busy} className="ga-danger" onClick={() => prompt("delete-folder", item)}><Trash2 size={15} /></button></div></article>)}</div>}
    {!loading && !displayed.length && <p className="ga-empty">{search ? "No matching items." : "Nothing here yet. Add a folder or upload your first photos."}</p>}
    {selected && items.length < total && <button disabled={busy} className="ga-primary ga-more" onClick={more}>{busy ? "Loading…" : `Load more (${items.length} / ${total})`}</button>}
    </main>
    {action && <Modal title={action.type.startsWith("delete") ? "Delete this item?" : action.type === "create" ? "New folder" : "Rename"} busy={busy} onClose={() => setAction(null)}><form onSubmit={confirm}>{action.type.startsWith("delete") ? <p>“{action.target.name}” will be removed from the gallery and moved to recovery storage on the drive.</p> : <label>Name<input autoFocus required maxLength={200} value={name} onChange={e => setName(e.target.value)} /></label>}{error && <p className="ga-error" role="alert">{error}</p>}<div className="ga-dialog-actions"><button type="button" disabled={busy} onClick={() => setAction(null)}>Cancel</button><button className={action.type.startsWith("delete") ? "ga-delete" : "ga-primary"} disabled={busy}>{busy ? "Saving…" : action.type.startsWith("delete") ? "Delete" : "Save"}</button></div></form></Modal>}
    {view && <Modal title={view.name} onClose={() => setView(null)}><AdminImage item={view} large /></Modal>}
  </div>;
}
