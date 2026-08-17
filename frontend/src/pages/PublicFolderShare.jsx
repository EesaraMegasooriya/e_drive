import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import {
  AlertCircle,
  Check,
  Copy,
  Download,
  File,
  FileAudio,
  FileImage,
  FileText,
  FileVideo,
  FolderOpen,
  HardDrive,
  LoaderCircle,
  Music,
  X,
} from "lucide-react";
import shareApi from "../api/shareApi";

function fileKind(file) {
  const mimeType = file.mimeType?.toLowerCase() || "";
  if (mimeType.startsWith("image/")) return "image";
  if (mimeType === "application/pdf") return "pdf";
  if (mimeType.startsWith("video/")) return "video";
  if (mimeType.startsWith("audio/")) return "audio";
  return "file";
}

function FileIcon({ file, size = 26 }) {
  switch (fileKind(file)) {
    case "image": return <FileImage size={size} />;
    case "pdf": return <FileText size={size} />;
    case "video": return <FileVideo size={size} />;
    case "audio": return <FileAudio size={size} />;
    default: return <File size={size} />;
  }
}

function formatBytes(bytes) {
  if (bytes == null) return "—";
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`;
}

function errorMessage(error) {
  const message = error.response?.data?.message || error.response?.data?.error;
  if (message) return message;
  if (error.response?.status === 404) return "This shared folder does not exist or is no longer available.";
  if (error.response?.status === 401 || error.response?.status === 403) return "This shared folder is disabled or has expired.";
  return "We couldn't load this shared folder. Please check the link and try again.";
}

export default function PublicFolderShare() {
  const { token } = useParams();
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedFile, setSelectedFile] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const pageSize = 100;

  const loadShare = async () => {
    try {
      setLoading(true);
      setError("");
      const data = await shareApi.getPublicFolderContents(token, 0, pageSize);
      setFiles(Array.isArray(data) ? data : []);
      setHasMore(Array.isArray(data) && data.length === pageSize);
    } catch (requestError) {
      setFiles([]);
      setError(errorMessage(requestError));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;

    shareApi.getPublicFolderContents(token, 0, pageSize)
      .then((data) => {
        if (!cancelled) {
          setFiles(Array.isArray(data) ? data : []);
          setHasMore(Array.isArray(data) && data.length === pageSize);
          setError("");
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          setFiles([]);
          setError(errorMessage(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  const loadMore = async () => {
    try {
      setLoadingMore(true);
      const next = await shareApi.getPublicFolderContents(token, files.length, pageSize);
      setFiles((current) => [...current, ...next]);
      setHasMore(next.length === pageSize);
    } finally {
      setLoadingMore(false);
    }
  };

  const folderName = useMemo(() => {
    const firstPath = files[0]?.path;
    return firstPath?.split("/").filter(Boolean)[0] || "Shared folder";
  }, [files]);

  return (
    <div className="min-h-screen bg-[#F7F6F2] font-[Inter,ui-sans-serif,system-ui] text-[#1B1D1B]">
      <header className="border-b border-[#E4E1DA] bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-2.5 px-4 py-4 sm:px-6">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#1F5C52] text-white shadow-sm"><HardDrive size={19} /></span>
          <span className="text-lg font-bold tracking-tight">E-Drive</span>
          <span className="ml-1 border-l border-[#D8D4CA] pl-3 text-sm text-[#5B5F5C]">Shared folder</span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
        {loading && <LoadingState />}

        {!loading && error && (
          <section className="mx-auto max-w-xl rounded-xl border border-[#F3D3CB] bg-[#FFF8F6] p-6 text-center shadow-sm">
            <AlertCircle className="mx-auto text-[#C4432B]" size={36} />
            <h1 className="mt-4 text-xl font-bold">Shared folder unavailable</h1>
            <p className="mt-2 text-sm leading-6 text-[#5B5F5C]">{error}</p>
            <button onClick={loadShare} className="button mt-5">Try again</button>
          </section>
        )}

        {!loading && !error && (
          <>
            <section className="mb-7 flex flex-col gap-4 border-b border-[#E4E1DA] pb-6 sm:flex-row sm:items-end sm:justify-between">
              <div className="min-w-0">
                <span className="font-mono text-xs uppercase tracking-wide text-[#1F5C52]">Public share</span>
                <h1 className="mt-2 flex items-center gap-3 text-2xl font-bold tracking-tight sm:text-3xl"><FolderOpen className="shrink-0 text-[#C9971C]" size={30} /> <span className="truncate">{folderName}</span></h1>
                <p className="mt-2 text-sm text-[#5B5F5C]">{files.length}{hasMore ? "+" : ""} {files.length === 1 ? "file" : "files"} shared with you</p>
              </div>
            </section>

            {files.length === 0 ? <EmptyState /> : (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                {files.map((file) => <FileCard key={file.uuid} file={file} onPreview={setSelectedFile} />)}
              </div>
            )}
            {hasMore && <div className="mt-8 text-center"><button type="button" onClick={loadMore} disabled={loadingMore} className="button disabled:opacity-60">{loadingMore ? "Loading…" : "Load more files"}</button></div>}
          </>
        )}
      </main>

      {selectedFile && <PreviewModal file={selectedFile} onClose={() => setSelectedFile(null)} />}
    </div>
  );
}

function LoadingState() {
  return <div className="flex min-h-[360px] flex-col items-center justify-center text-center"><LoaderCircle className="animate-spin text-[#1F5C52]" size={38} /><p className="mt-4 text-sm text-[#5B5F5C]">Loading shared folder…</p></div>;
}

function EmptyState() {
  return <div className="flex min-h-[320px] flex-col items-center justify-center rounded-xl border border-dashed border-[#D8D4CA] bg-white text-center"><FolderOpen size={46} className="text-[#C9971C]" /><h2 className="mt-4 font-semibold">This folder is empty</h2><p className="mt-1 text-sm text-[#8A8D89]">There are no files available in this shared folder.</p></div>;
}

function FileCard({ file, onPreview }) {
  const kind = fileKind(file);
  const [copied, setCopied] = useState(false);

  const copyDirectLink = async () => {
    try {
      await navigator.clipboard.writeText(file.url);
    } catch {
      const input = document.createElement("textarea");
      input.value = file.url;
      input.style.position = "fixed";
      input.style.opacity = "0";
      document.body.appendChild(input);
      input.select();
      document.execCommand("copy");
      input.remove();
    }
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  };

  return <article className="overflow-hidden rounded-xl border border-[#E4E1DA] bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
    <button type="button" onClick={() => onPreview(file)} className="block h-48 w-full overflow-hidden bg-[#F0EEE7] text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#1F5C52]">
      {kind === "image" ? <img src={file.url} alt={file.name} className="h-full w-full object-cover" loading="lazy" /> : <PreviewTile file={file} />}
    </button>
    <div className="p-4">
      <p className="truncate font-semibold" title={file.name}>{file.name}</p>
      <p className="mt-1 truncate text-xs text-[#8A8D89]" title={file.path}>{file.path || "Shared file"}</p>
      <div className="mt-3 flex items-center justify-between gap-2 text-xs text-[#5B5F5C]"><span>{file.extension?.toUpperCase() || "FILE"}</span><span>{formatBytes(file.size)}</span></div>
      <div className="mt-4 grid grid-cols-[1fr_auto_1fr] gap-2">
        <button type="button" onClick={() => onPreview(file)} className="rounded-lg border border-[#D8D4CA] px-3 py-2 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7]">Preview</button>
        <button type="button" onClick={copyDirectLink} title="Copy direct link" aria-label={`Copy direct link for ${file.name}`} className="flex items-center justify-center rounded-lg border border-[#D8D4CA] px-2.5 text-[#1B1D1B] hover:bg-[#F0EEE7]">{copied ? <Check size={16} className="text-[#1F5C52]" /> : <Copy size={16} />}</button>
        <a href={file.url} download className="flex items-center justify-center gap-1.5 rounded-lg bg-[#1F5C52] px-3 py-2 text-sm font-medium text-white hover:bg-[#184A42]"><Download size={15} />Download</a>
      </div>
    </div>
  </article>;
}

function PreviewTile({ file }) {
  return <div className="flex h-full flex-col items-center justify-center text-[#5B5F5C]"><FileIcon file={file} size={48} /><span className="mt-3 text-xs font-medium">{file.extension?.toUpperCase() || "FILE"}</span></div>;
}

function PreviewModal({ file, onClose }) {
  const kind = fileKind(file);
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" role="dialog" aria-modal="true" aria-label={`Preview ${file.name}`} onClick={onClose}>
    <section className="max-h-[92vh] w-full max-w-5xl overflow-hidden rounded-xl bg-white shadow-2xl" onClick={(event) => event.stopPropagation()}>
      <header className="flex items-center justify-between gap-4 border-b border-[#E4E1DA] px-4 py-3 sm:px-5"><div className="min-w-0"><h2 className="truncate font-semibold">{file.name}</h2><p className="truncate text-xs text-[#8A8D89]">{file.path}</p></div><button type="button" onClick={onClose} aria-label="Close preview" className="rounded-lg p-2 text-[#5B5F5C] hover:bg-[#F0EEE7]"><X size={20} /></button></header>
      <div className="max-h-[calc(92vh-136px)] overflow-auto bg-[#F7F6F2] p-4">
        {kind === "image" && <img src={file.url} alt={file.name} className="mx-auto max-h-[72vh] max-w-full object-contain" />}
        {kind === "pdf" && <iframe src={file.url} title={file.name} className="h-[72vh] w-full rounded bg-white" />}
        {kind === "video" && <video src={file.url} controls autoPlay className="mx-auto max-h-[72vh] max-w-full" />}
        {kind === "audio" && <div className="flex min-h-64 flex-col items-center justify-center"><Music size={58} className="text-[#1F5C52]" /><audio src={file.url} controls className="mt-6 w-full max-w-xl" /></div>}
        {kind === "file" && <div className="flex min-h-64 flex-col items-center justify-center text-center"><FileIcon file={file} size={58} /><p className="mt-4 text-sm text-[#5B5F5C]">A browser preview is not available for this file type.</p></div>}
      </div>
      <footer className="flex justify-end border-t border-[#E4E1DA] px-4 py-3 sm:px-5"><a href={file.url} download className="button"><Download size={16} />Download</a></footer>
    </section>
  </div>;
}
