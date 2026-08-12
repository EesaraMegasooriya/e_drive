import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import {
  Folder,
  FileText,
  FileImage,
  FileAudio,
  FileVideo,
  FileArchive,
  FileSpreadsheet,
  FileCode,
  Upload,
  Download,
  Share2,
  Trash2,
  Pencil,
  Search,
  LayoutGrid,
  List as ListIcon,
  ChevronRight,
  ArrowUpDown,
  Copy,
  X,
  Home,
  CheckCircle2,
  AlertCircle,
  RotateCcw,
} from "lucide-react";
import Swal from "sweetalert2";

import fileApi from "../api/fileApi";
import folderApi from "../api/folderApi";
import driveApi from "../api/driveApi";
import shareApi from "../api/shareApi";

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

const toast = Swal.mixin({
  toast: true,
  position: "top-end",
  showConfirmButton: false,
  timer: 2200,
  timerProgressBar: true,
});

function formatBytes(bytes) {
  if (bytes === 0 || bytes == null) return "0 KB";
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(kb < 10 ? 2 : 0)} KB`;
  return `${(kb / 1024).toFixed(2)} MB`;
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function iconForMime(mime = "") {
  if (mime.startsWith("image/")) return FileImage;
  if (mime.startsWith("audio/")) return FileAudio;
  if (mime.startsWith("video/")) return FileVideo;
  if (mime.includes("zip") || mime.includes("compressed") || mime.includes("tar"))
    return FileArchive;
  if (mime.includes("sheet") || mime.includes("csv") || mime.includes("excel"))
    return FileSpreadsheet;
  if (
    mime.includes("json") ||
    mime.includes("javascript") ||
    mime.includes("xml") ||
    mime.includes("html")
  )
    return FileCode;
  return FileText;
}

// Catalog tag: gives every card a small archival index, e.g. "F-014"
function catalogTag(prefix, index) {
  return `${prefix}-${String(index + 1).padStart(3, "0")}`;
}

function uid() {
  return typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function Drive() {
  const [loading, setLoading] = useState(true);
  const [assetUrl, setAssetUrl] = useState(null);
  const [currentFolder, setCurrentFolder] = useState(null);

  const [folders, setFolders] = useState([]);
  const [files, setFiles] = useState([]);

  const [selectedFile, setSelectedFile] = useState(null);
  const [selectedFolder, setSelectedFolder] = useState(null);
  const [folderShare, setFolderShare] = useState(null);

  const [query, setQuery] = useState("");
  const [view, setView] = useState("grid"); // grid | list
  const [sortBy, setSortBy] = useState("name"); // name | date | size
  const [sortDir, setSortDir] = useState("asc");
  const [isDragging, setIsDragging] = useState(false);

  // ---- Upload queue (supports multiple concurrent uploads with progress) ----
  // Each entry: { id, name, size, progress (0-100), status: 'uploading' | 'success' | 'error', error, controller }
  const [uploads, setUploads] = useState([]);
  const uploadsRef = useRef(uploads);
  uploadsRef.current = uploads;

  const fileInputRef = useRef(null);
  const dragCounter = useRef(0);

  const hasActiveUploads = uploads.some((u) => u.status === "uploading");

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------

  const loadDrive = async (folderUuid = null) => {
    try {
      setLoading(true);

      const data = await driveApi.getDrive(folderUuid);

      setCurrentFolder(data.currentFolder);
      setFolders(data.folders || []);
      setFiles(data.files || []);

      setSelectedFile(null);
      setSelectedFolder(null);
      setFolderShare(null);
    } catch (error) {
      Swal.fire({
        icon: "error",
        title: "Couldn't load this folder",
        text: error.response?.data?.message ?? "Please try again.",
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDrive();
  }, []);

  useEffect(() => {
    if (!selectedFile || !selectedFile.mimeType?.startsWith("image/")) {
      setAssetUrl(null);
      return;
    }

    let cancelled = false;

    const loadAsset = async () => {
      try {
        const asset = await shareApi.getFileUrl(selectedFile.uuid);

        if (!cancelled) {
          setAssetUrl(asset.url);
        }
      } catch {
        if (!cancelled) {
          setAssetUrl(null);
        }
      }
    };

    loadAsset();

    return () => {
      cancelled = true;
    };
  }, [selectedFile]);

  // Warn before leaving the tab if an upload is still in flight
  useEffect(() => {
    const handler = (e) => {
      if (hasActiveUploads) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [hasActiveUploads]);

  // -------------------------------------------------------------------------
  // Derived / view state
  // -------------------------------------------------------------------------

  const breadcrumbs = useMemo(() => {
    if (!currentFolder?.path) return [];
    return currentFolder.path.split("/").filter(Boolean);
  }, [currentFolder]);

  const filteredFolders = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = q
      ? folders.filter((f) => f.name.toLowerCase().includes(q))
      : folders;
    return [...list].sort((a, b) => {
      const dir = sortDir === "asc" ? 1 : -1;
      if (sortBy === "date")
        return dir * (new Date(a.createdAt) - new Date(b.createdAt));
      return dir * a.name.localeCompare(b.name);
    });
  }, [folders, query, sortBy, sortDir]);

  const filteredFiles = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = q
      ? files.filter((f) => f.originalName.toLowerCase().includes(q))
      : files;
    return [...list].sort((a, b) => {
      const dir = sortDir === "asc" ? 1 : -1;
      if (sortBy === "date")
        return dir * (new Date(a.createdAt) - new Date(b.createdAt));
      if (sortBy === "size") return dir * (a.fileSize - b.fileSize);
      return dir * a.originalName.localeCompare(b.originalName);
    });
  }, [files, query, sortBy, sortDir]);

  const isEmpty = folders.length === 0 && files.length === 0;
  const noResults =
    !isEmpty && filteredFolders.length === 0 && filteredFiles.length === 0;

  // -------------------------------------------------------------------------
  // Upload queue helpers
  // -------------------------------------------------------------------------

  const patchUpload = useCallback((id, patch) => {
    setUploads((prev) =>
      prev.map((u) => (u.id === id ? { ...u, ...patch } : u))
    );
  }, []);

  const dismissUpload = useCallback((id) => {
    setUploads((prev) => prev.filter((u) => u.id !== id));
  }, []);

  const cancelUpload = useCallback(
    (id) => {
      const entry = uploadsRef.current.find((u) => u.id === id);
      entry?.controller?.abort();
      dismissUpload(id);
    },
    [dismissUpload]
  );

  const uploadFile = async (file) => {
    if (!file) return;

    const id = uid();
    const controller =
      typeof AbortController !== "undefined" ? new AbortController() : null;

    setUploads((prev) => [
      ...prev,
      {
        id,
        name: file.name,
        size: file.size,
        progress: 0,
        status: "uploading",
        error: null,
        controller,
      },
    ]);

    try {
      // fileApi.uploadFile is expected to accept an options object as the
      // 3rd argument with { onUploadProgress, signal } — see note below the
      // component for the matching one-line change in fileApi.js.
      await fileApi.uploadFile(file, currentFolder?.uuid ?? null, {
        signal: controller?.signal,
        onUploadProgress: (evt) => {
          if (!evt?.total) return;
          const pct = Math.round((evt.loaded / evt.total) * 100);
          patchUpload(id, { progress: pct });
        },
      });

      patchUpload(id, { progress: 100, status: "success" });
      toast.fire({ icon: "success", title: `${file.name} uploaded` });
      await loadDrive(currentFolder?.uuid ?? null);

      // Auto-clear successful entries after a short delay
      setTimeout(() => dismissUpload(id), 2500);
    } catch (error) {
      if (error?.name === "CanceledError" || error?.name === "AbortError") {
        // Already removed by cancelUpload; nothing else to do.
        return;
      }
      patchUpload(id, {
        status: "error",
        error: error.response?.data?.message ?? "Upload failed. Please try again.",
      });
    }
  };

  const uploadFiles = (fileList) => {
    Array.from(fileList || []).forEach((file) => uploadFile(file));
  };

  // -------------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------------

  const openFolder = async (folder) => {
    await loadDrive(folder.uuid);
  };

  const selectFolder = (folder) => {
    setSelectedFile(null);
    setSelectedFolder(folder);
    setFolderShare(null);
  };

  const toggleFileVisibility = async (file) => {
    try {
      const updated = await fileApi.setVisibility(file.uuid, !file.isPublic);
      setSelectedFile(updated);
      setFiles((items) => items.map((item) => item.uuid === updated.uuid ? updated : item));
      toast.fire({ icon: "success", title: updated.isPublic ? "File is public" : "File is private" });
    } catch { Swal.fire({ icon: "error", title: "Couldn't update visibility" }); }
  };

  const handleFolderRename = async () => {
    if (!selectedFolder) return;

    const { value: name } = await Swal.fire({
      title: "Rename folder",
      input: "text",
      inputValue: selectedFolder.name,
      showCancelButton: true,
      confirmButtonText: "Save",
      inputValidator: (value) => (!value?.trim() ? "Name can't be empty" : undefined),
    });

    if (!name?.trim()) return;

    try {
      const updated = await folderApi.renameFolder(selectedFolder.uuid, { name: name.trim() });
      setSelectedFolder(updated);
      setFolders((items) => items.map((item) => item.uuid === updated.uuid ? updated : item));
      toast.fire({ icon: "success", title: "Folder renamed" });
    } catch (error) {
      Swal.fire({ icon: "error", title: "Rename failed", text: error.response?.data?.message ?? "Please try again." });
    }
  };

  const handleFolderDelete = async () => {
    if (!selectedFolder) return;

    const result = await Swal.fire({
      title: `Delete "${selectedFolder.name}"?`,
      text: "The folder and its contents will no longer be available.",
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: "Delete",
      confirmButtonColor: "#C4432B",
    });

    if (!result.isConfirmed) return;

    try {
      await folderApi.deleteFolder(selectedFolder.uuid);
      await loadDrive(currentFolder?.uuid ?? null);
      toast.fire({ icon: "success", title: "Folder deleted" });
    } catch (error) {
      Swal.fire({ icon: "error", title: "Delete failed", text: error.response?.data?.message ?? "Please try again." });
    }
  };

  const handleFolderShare = async () => {
    if (!selectedFolder) return;

    try {
      const share = await shareApi.createShare(selectedFolder.uuid, "FOLDER");
      setFolderShare(share);

      const result = await Swal.fire({
        title: "Share this folder",
        text: "Anyone with this link can browse and download the shared folder.",
        input: "text",
        inputValue: share.shareUrl,
        inputAttributes: { readOnly: true, "aria-label": "Folder share URL" },
        showCancelButton: true,
        confirmButtonText: "Copy share link",
      });

      if (result.isConfirmed) {
        await copyText(share.shareUrl, "Folder share link copied");
      }
    } catch (error) {
      Swal.fire({ icon: "error", title: "Couldn't create a folder share link", text: error.response?.data?.message ?? "Please try again." });
    }
  };

  const handleDelete = async () => {
    const result = await Swal.fire({
      title: `Delete "${selectedFile.originalName}"?`,
      text: "This can't be undone.",
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: "Delete",
      confirmButtonColor: "#C4432B",
    });

    if (!result.isConfirmed) return;

    try {
      await fileApi.deleteFile(selectedFile.uuid);
      setSelectedFile(null);
      await loadDrive(currentFolder?.uuid);
      toast.fire({ icon: "success", title: "File deleted" });
    } catch (error) {
      Swal.fire({
        icon: "error",
        title: "Delete failed",
        text: error.response?.data?.message ?? "Please try again.",
      });
    }
  };

  const handleRename = async () => {
    const { value } = await Swal.fire({
      title: "Rename file",
      input: "text",
      inputValue: selectedFile.originalName,
      showCancelButton: true,
      confirmButtonText: "Save",
      inputValidator: (value) => (!value ? "Name can't be empty" : undefined),
    });

    if (!value) return;

    try {
      await fileApi.renameFile(selectedFile.uuid, { name: value });
      await loadDrive(currentFolder?.uuid);
      toast.fire({ icon: "success", title: "File renamed" });
    } catch (error) {
      Swal.fire({
        icon: "error",
        title: "Rename failed",
        text: error.response?.data?.message ?? "Please try again.",
      });
    }
  };

  const handleUploadInput = (event) => {
    uploadFiles(event.target.files);
    event.target.value = "";
  };

  const handleCreateFolder = async () => {
    const { value: name } = await Swal.fire({
      title: "Create folder",
      input: "text",
      inputLabel: "Folder name",
      inputPlaceholder: "Untitled folder",
      showCancelButton: true,
      confirmButtonText: "Create",
      inputValidator: (value) => (!value ? "Name can't be empty" : undefined),
    });

    if (!name) return;

    try {
      await folderApi.createFolder({
        name,
        parentUuid: currentFolder?.uuid ?? null,
      });
      toast.fire({ icon: "success", title: "Folder created" });
      await loadDrive(currentFolder?.uuid ?? null);
    } catch (error) {
      Swal.fire({
        icon: "error",
        title: "Couldn't create folder",
        text: error.response?.data?.message ?? "Please try again.",
      });
    }
  };

  const handleDownload = async (file) => {
    try {
      const response = await fileApi.downloadFile(file.uuid);
      const blob = new Blob([response.data]);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");

      link.href = url;
      link.download = file.originalName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      Swal.fire({ icon: "error", title: "Download failed" });
    }
  };

  const handleShare = async () => {
    try {
      const share = await shareApi.createShare(selectedFile.uuid, "FILE");

      const result = await Swal.fire({
        title: "Share this file",
        html: `
          <div class="share-dialog__content">
            <p class="share-dialog__intro">
              Create a link for <strong class="share-dialog__filename"></strong>.
            </p>
            <section class="share-dialog__link-card share-dialog__link-card--primary">
              <span class="share-dialog__eyebrow">Share page</span>
              <p class="share-dialog__description">A clean page for viewing or downloading the file.</p>
              <code class="share-dialog__url" data-share-url></code>
            </section>
            <section class="share-dialog__link-card">
              <span class="share-dialog__eyebrow">Direct asset URL</span>
              <p class="share-dialog__description">Use in an image, video, or download link.</p>
              <code class="share-dialog__url" data-asset-url></code>
            </section>
          </div>
        `,
        showCloseButton: true,
        confirmButtonText: "Copy share page",
        showDenyButton: true,
        denyButtonText: "Copy direct URL",
        customClass: {
          popup: "share-dialog",
          title: "share-dialog__title",
          actions: "share-dialog__actions",
          confirmButton: "share-dialog__copy-share",
          denyButton: "share-dialog__copy-asset",
        },
        didOpen: () => {
          document.querySelector(".share-dialog__filename").textContent =
            selectedFile.originalName;
          document.querySelector("[data-share-url]").textContent = share.shareUrl;
          document.querySelector("[data-asset-url]").textContent = share.assetUrl;
        },
      });

      if (result.isConfirmed) {
        await navigator.clipboard.writeText(share.shareUrl);
        toast.fire({ icon: "success", title: "Share link copied" });
      } else if (result.isDenied) {
        await navigator.clipboard.writeText(share.assetUrl);
        toast.fire({ icon: "success", title: "Asset URL copied" });
      }
    } catch {
      Swal.fire({ icon: "error", title: "Couldn't create a share link" });
    }
  };

  const copyText = async (text, label = "Copied") => {
    await navigator.clipboard.writeText(text);
    toast.fire({ icon: "success", title: label });
  };

  // -------------------------------------------------------------------------
  // Drag & drop upload (whole content area)
  // -------------------------------------------------------------------------

  const onDragEnter = (e) => {
    e.preventDefault();
    dragCounter.current += 1;
    setIsDragging(true);
  };

  const onDragLeave = (e) => {
    e.preventDefault();
    dragCounter.current -= 1;
    if (dragCounter.current <= 0) setIsDragging(false);
  };

  const onDragOver = (e) => e.preventDefault();

  const onDrop = (e) => {
    e.preventDefault();
    dragCounter.current = 0;
    setIsDragging(false);
    uploadFiles(e.dataTransfer.files);
  };

  const toggleSortDir = () =>
    setSortDir((d) => (d === "asc" ? "desc" : "asc"));

  // -------------------------------------------------------------------------
  // Loading state
  // -------------------------------------------------------------------------

  if (loading) {
    return (
      <div className="min-h-screen bg-[#F7F6F2]">
        <div className="mx-auto max-w-7xl p-4 sm:p-6 md:p-8">
          <div className="mb-8 h-10 w-40 sm:w-56 animate-pulse rounded bg-[#E4E1DA]" />
          <div className="mb-8 flex flex-wrap gap-3">
            <div className="h-11 w-32 sm:w-36 animate-pulse rounded-lg bg-[#E4E1DA]" />
            <div className="h-11 w-32 sm:w-36 animate-pulse rounded-lg bg-[#E4E1DA]" />
          </div>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="h-40 animate-pulse rounded-xl border border-[#E4E1DA] bg-white"
              />
            ))}
          </div>
        </div>
      </div>
    );
  }

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <div
      className="min-h-screen bg-[#F7F6F2] font-[Inter,ui-sans-serif,system-ui]"
      onDragEnter={onDragEnter}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      {/* Drag overlay */}
      {isDragging && (
        <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center bg-[#1B1D1B]/70 backdrop-blur-sm">
          <div className="mx-4 rounded-2xl border-2 border-dashed border-[#8FBBAF] bg-[#1F5C52] px-8 py-8 text-center text-white shadow-2xl sm:px-12 sm:py-10">
            <Upload size={40} className="mx-auto mb-3" />
            <p className="text-lg font-semibold">Drop to upload</p>
            <p className="text-sm text-white/70">
              Adds to {currentFolder ? currentFolder.name : "My Drive"}
            </p>
          </div>
        </div>
      )}

      <div className="mx-auto max-w-7xl p-4 sm:p-6 md:p-8">
        {/* Header / breadcrumb */}
        <div className="mb-6">
          <nav
            aria-label="Breadcrumb"
            className="mb-2 flex flex-wrap items-center gap-1 font-mono text-xs uppercase tracking-wide text-[#8A8D89]"
          >
            <button
              onClick={() => loadDrive()}
              className="flex items-center gap-1 rounded px-1.5 py-0.5 hover:bg-[#EAE7DF] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52]"
            >
              <Home size={12} />
              My Drive
            </button>
            {breadcrumbs.map((crumb, i) => (
              <span key={i} className="flex items-center gap-1">
                <ChevronRight size={12} className="shrink-0" />
                <span
                  className={
                    i === breadcrumbs.length - 1
                      ? "max-w-[160px] truncate px-1.5 py-0.5 text-[#1B1D1B] sm:max-w-none"
                      : "max-w-[120px] truncate px-1.5 py-0.5 sm:max-w-none"
                  }
                >
                  {crumb}
                </span>
              </span>
            ))}
          </nav>

          <h1 className="text-2xl font-bold tracking-tight text-[#1B1D1B] sm:text-3xl md:text-4xl">
            {currentFolder ? currentFolder.name : "My Drive"}
          </h1>
        </div>

        {/* Toolbar */}
        <div className="mb-6 flex flex-col gap-4">
          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center gap-2 whitespace-nowrap rounded-lg bg-[#1F5C52] px-4 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2 sm:px-5"
            >
              <Upload size={16} />
              Upload file
            </button>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden"
              onChange={handleUploadInput}
            />

            <button
              onClick={handleCreateFolder}
              className="flex items-center gap-2 whitespace-nowrap rounded-lg border border-[#E4E1DA] bg-white px-4 py-2.5 text-sm font-medium text-[#1B1D1B] shadow-sm transition hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2 sm:px-5"
            >
              <Folder size={16} className="text-[#C9971C]" />
              New folder
            </button>

            {currentFolder && (
              <button
                onClick={() => loadDrive()}
                className="flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm text-[#5B5F5C] hover:bg-[#EAE7DF] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] sm:ml-auto"
              >
                <ChevronRight size={14} className="rotate-180" />
                Back to My Drive
              </button>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="relative w-full sm:min-w-[220px] sm:flex-1">
              <Search
                size={16}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#8A8D89]"
              />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search this folder…"
                className="w-full rounded-lg border border-[#E4E1DA] bg-white py-2 pl-9 pr-8 text-sm text-[#1B1D1B] placeholder:text-[#8A8D89] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52]"
              />
              {query && (
                <button
                  onClick={() => setQuery("")}
                  aria-label="Clear search"
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[#8A8D89] hover:text-[#1B1D1B]"
                >
                  <X size={14} />
                </button>
              )}
            </div>

            <div className="flex flex-1 flex-wrap items-center gap-3 sm:flex-none">
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="flex-1 rounded-lg border border-[#E4E1DA] bg-white px-3 py-2 text-sm text-[#1B1D1B] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] sm:flex-none"
                aria-label="Sort by"
              >
                <option value="name">Name</option>
                <option value="date">Date</option>
                <option value="size">Size</option>
              </select>

              <button
                onClick={toggleSortDir}
                aria-label="Toggle sort direction"
                className="shrink-0 rounded-lg border border-[#E4E1DA] bg-white p-2 text-[#5B5F5C] hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52]"
              >
                <ArrowUpDown
                  size={16}
                  className={sortDir === "desc" ? "rotate-180 transition-transform" : "transition-transform"}
                />
              </button>

              <div className="flex shrink-0 overflow-hidden rounded-lg border border-[#E4E1DA] bg-white">
                <button
                  onClick={() => setView("grid")}
                  aria-label="Grid view"
                  aria-pressed={view === "grid"}
                  className={`p-2 ${view === "grid" ? "bg-[#1F5C52] text-white" : "text-[#5B5F5C] hover:bg-[#F0EEE7]"}`}
                >
                  <LayoutGrid size={16} />
                </button>
                <button
                  onClick={() => setView("list")}
                  aria-label="List view"
                  aria-pressed={view === "list"}
                  className={`p-2 ${view === "list" ? "bg-[#1F5C52] text-white" : "text-[#5B5F5C] hover:bg-[#F0EEE7]"}`}
                >
                  <ListIcon size={16} />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Main layout */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 lg:gap-8">
          {/* LEFT: browser */}
          <div className="lg:col-span-8">
            {isEmpty ? (
              <EmptyState onUpload={() => fileInputRef.current?.click()} />
            ) : noResults ? (
              <div className="rounded-xl border border-dashed border-[#D8D4CA] bg-white/60 px-4 py-16 text-center sm:py-20">
                <Search size={32} className="mx-auto mb-3 text-[#C7C3B8]" />
                <p className="break-words font-medium text-[#1B1D1B]">No matches for "{query}"</p>
                <p className="mt-1 text-sm text-[#8A8D89]">
                  Try a different search term.
                </p>
              </div>
            ) : view === "grid" ? (
              <div className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-2 xl:grid-cols-3">
                {filteredFolders.map((folder, i) => (
                  <FolderCard
                    key={folder.uuid}
                    folder={folder}
                    tag={catalogTag("D", i)}
                    active={selectedFolder?.uuid === folder.uuid}
                    onClick={() => selectFolder(folder)}
                    onDoubleClick={() => openFolder(folder)}
                  />
                ))}
                {filteredFiles.map((file, i) => (
                  <FileCard
                    key={file.uuid}
                    file={file}
                    tag={catalogTag("F", i)}
                    active={selectedFile?.uuid === file.uuid}
                    onClick={() => {
                      setSelectedFolder(null);
                      setFolderShare(null);
                      setSelectedFile(file);
                    }}
                  />
                ))}
              </div>
            ) : (
              <ListTable
                folders={filteredFolders}
                files={filteredFiles}
                selectedFile={selectedFile}
                selectedFolder={selectedFolder}
                onFolderClick={selectFolder}
                onFolderDoubleClick={openFolder}
                onFileClick={(file) => {
                  setSelectedFolder(null);
                  setFolderShare(null);
                  setSelectedFile(file);
                }}
              />
            )}
          </div>

          {/* RIGHT: detail panel */}
          <div className="lg:col-span-4">
            <div className="rounded-xl border border-[#E4E1DA] bg-white p-5 shadow-sm sm:p-6 lg:sticky lg:top-8">
              {selectedFile ? (
                <FileDetail
                  file={selectedFile}
                  assetUrl={assetUrl}
                  onAssetError={() => setAssetUrl(null)}
                  onDownload={() => handleDownload(selectedFile)}
                  onShare={handleShare}
                  onToggleVisibility={() => toggleFileVisibility(selectedFile)}
                  onRename={handleRename}
                  onDelete={handleDelete}
                />
              ) : selectedFolder ? (
                <FolderDetail
                  folder={selectedFolder}
                  share={folderShare}
                  onCopy={copyText}
                  onShare={handleFolderShare}
                  onRename={handleFolderRename}
                  onDelete={handleFolderDelete}
                />
              ) : (
                <div className="py-12 text-center sm:py-20">
                  <FileText size={64} className="mx-auto mb-5 text-[#D8D4CA]" />
                  <h2 className="text-base font-semibold text-[#5B5F5C]">
                    Nothing selected
                  </h2>
                  <p className="mt-1 text-sm text-[#8A8D89]">
                    Select a file or folder to see its details.
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Floating upload progress panel */}
      <UploadProgressPanel
        uploads={uploads}
        onCancel={cancelUpload}
        onDismiss={dismissUpload}
        onRetry={(id) => {
          // Retry isn't wired to a real File object (browsers don't let us
          // keep one around indefinitely) — this just clears the failed
          // entry so the user can re-pick the file. Swap in your own retry
          // logic if you cache the File object per upload.
          dismissUpload(id);
        }}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Presentational pieces
// ---------------------------------------------------------------------------

function EmptyState({ onUpload }) {
  return (
    <div className="rounded-xl border border-dashed border-[#D8D4CA] bg-white/60 px-4 py-16 text-center sm:py-24">
      <Folder size={40} className="mx-auto mb-4 text-[#D8D4CA]" />
      <h2 className="text-base font-semibold text-[#1B1D1B]">
        This folder is empty
      </h2>
      <p className="mx-auto mt-1 max-w-xs text-sm text-[#8A8D89]">
        Drag a file anywhere on this page, or upload one to get started.
      </p>
      <button
        onClick={onUpload}
        className="mx-auto mt-5 flex items-center gap-2 rounded-lg bg-[#1F5C52] px-5 py-2.5 text-sm font-medium text-white hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
      >
        <Upload size={16} />
        Upload a file
      </button>
    </div>
  );
}

function FolderCard({ folder, tag, active, onClick, onDoubleClick }) {
  return (
    <button
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      title="Double-click to open folder"
      className={`group relative rounded-xl border p-4 text-left shadow-sm transition hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] sm:p-5 ${
        active ? "border-[#1F5C52] bg-[#EEF4F2]" : "border-[#E4E1DA] bg-white"
      }`}
    >
      <span className="absolute right-3 top-3 font-mono text-[10px] tracking-wide text-[#C7C3B8]">
        {tag}
      </span>
      <Folder size={36} className="mb-3 text-[#C9971C] sm:h-10 sm:w-10" />
      <h3 className="truncate pr-8 text-sm font-semibold text-[#1B1D1B]">
        {folder.name}
      </h3>
      <p className="mt-1 text-xs text-[#8A8D89]">Double-click to open</p>
    </button>
  );
}

function FileCard({ file, tag, active, onClick }) {
  const Icon = iconForMime(file.mimeType);
  return (
    <button
      onClick={onClick}
      className={`group relative rounded-xl border p-4 text-left shadow-sm transition hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] sm:p-5 ${
        active ? "border-[#1F5C52] bg-[#EEF4F2]" : "border-[#E4E1DA] bg-white"
      }`}
    >
      <span className="absolute right-3 top-3 font-mono text-[10px] tracking-wide text-[#C7C3B8]">
        {tag}
      </span>
      <Icon size={36} className="mb-3 text-[#1F5C52] sm:h-10 sm:w-10" />
      <h3 className="truncate pr-8 text-sm font-semibold text-[#1B1D1B]">
        {file.originalName}
      </h3>
      <p className="mt-1 truncate font-mono text-xs text-[#8A8D89]">
        {formatBytes(file.fileSize)} · {formatDate(file.createdAt)}
      </p>
    </button>
  );
}

function ListTable({
  folders,
  files,
  selectedFile,
  selectedFolder,
  onFolderClick,
  onFolderDoubleClick,
  onFileClick,
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-[#E4E1DA] bg-white shadow-sm">
      <div className="grid grid-cols-[1fr_auto_auto] gap-3 border-b border-[#E4E1DA] bg-[#FAF9F6] px-3 py-2.5 font-mono text-[11px] uppercase tracking-wide text-[#8A8D89] sm:gap-4 sm:px-5">
        <span>Name</span>
        <span className="hidden w-20 text-right sm:block">Size</span>
        <span className="w-16 text-right sm:w-24">Modified</span>
      </div>
      <ul className="divide-y divide-[#EEECE5]">
        {folders.map((folder) => (
          <li key={folder.uuid}>
            <button
              onClick={() => onFolderClick(folder)}
              onDoubleClick={() => onFolderDoubleClick(folder)}
              className={`grid w-full grid-cols-[1fr_auto_auto] items-center gap-3 px-3 py-3 text-left hover:bg-[#F7F6F2] focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#1F5C52] sm:gap-4 sm:px-5 ${
                selectedFolder?.uuid === folder.uuid ? "bg-[#EEF4F2]" : ""
              }`}
            >
              <span className="flex items-center gap-3 truncate text-sm font-medium text-[#1B1D1B]">
                <Folder size={18} className="shrink-0 text-[#C9971C]" />
                <span className="truncate">{folder.name}</span>
              </span>
              <span className="hidden w-20 text-right text-xs text-[#8A8D89] sm:block">—</span>
              <span className="w-16 text-right font-mono text-xs text-[#8A8D89] sm:w-24">
                {formatDate(folder.createdAt)}
              </span>
            </button>
          </li>
        ))}
        {files.map((file) => {
          const Icon = iconForMime(file.mimeType);
          return (
            <li key={file.uuid}>
              <button
                onClick={() => onFileClick(file)}
                className={`grid w-full grid-cols-[1fr_auto_auto] items-center gap-3 px-3 py-3 text-left hover:bg-[#F7F6F2] focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#1F5C52] sm:gap-4 sm:px-5 ${
                  selectedFile?.uuid === file.uuid ? "bg-[#EEF4F2]" : ""
                }`}
              >
                <span className="flex items-center gap-3 truncate text-sm font-medium text-[#1B1D1B]">
                  <Icon size={18} className="shrink-0 text-[#1F5C52]" />
                  <span className="truncate">{file.originalName}</span>
                </span>
                <span className="hidden w-20 text-right font-mono text-xs text-[#8A8D89] sm:block">
                  {formatBytes(file.fileSize)}
                </span>
                <span className="w-16 text-right font-mono text-xs text-[#8A8D89] sm:w-24">
                  {formatDate(file.createdAt)}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function FileDetail({ file, assetUrl, onAssetError, onDownload, onShare, onToggleVisibility, onRename, onDelete }) {
  const Icon = iconForMime(file.mimeType);
  return (
    <>
      <div className="mb-6 flex justify-center rounded-lg bg-[#F7F6F2] p-6">
        {file.mimeType.startsWith("image/") && assetUrl ? (
          <img
            src={assetUrl}
            alt={file.originalName}
            className="max-h-56 rounded-md object-contain"
            onError={onAssetError}
          />
        ) : (
          <Icon size={72} className="text-[#1F5C52]" />
        )}
      </div>

      <h2 className="break-words text-lg font-bold text-[#1B1D1B]">
        {file.originalName}
      </h2>

      <dl className="mt-5 space-y-2.5 text-sm">
        <div className="flex justify-between gap-3">
          <dt className="shrink-0 text-[#8A8D89]">Type</dt>
          <dd className="truncate font-mono text-xs text-[#1B1D1B]">{file.mimeType}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt className="shrink-0 text-[#8A8D89]">Size</dt>
          <dd className="text-[#1B1D1B]">{formatBytes(file.fileSize)}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt className="shrink-0 text-[#8A8D89]">Uploaded</dt>
          <dd className="text-[#1B1D1B]">{formatDate(file.createdAt)}</dd>
        </div>
      </dl>

      <div className="mt-7 flex flex-col gap-2.5">
        <button onClick={onToggleVisibility} className="rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7]">{file.isPublic ? "Make private" : "Make public"}</button>
        <button
          onClick={onDownload}
          className="flex items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-2.5 text-sm font-medium text-white hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
        >
          <Download size={16} />
          Download
        </button>
        <button
          onClick={onShare}
          className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
        >
          <Share2 size={16} />
          Share
        </button>
        <div className="grid grid-cols-2 gap-2.5">
          <button
            onClick={onRename}
            className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
          >
            <Pencil size={16} />
            Rename
          </button>
          <button
            onClick={onDelete}
            className="flex items-center justify-center gap-2 rounded-lg border border-[#F3D3CB] bg-white py-2.5 text-sm font-medium text-[#C4432B] hover:bg-[#FCEDE9] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#C4432B] focus-visible:ring-offset-2"
          >
            <Trash2 size={16} />
            Delete
          </button>
        </div>
      </div>
    </>
  );
}

function FolderDetail({ folder, share, onCopy, onShare, onRename, onDelete }) {
  return (
    <>
      <div className="mb-6 text-center">
        <Folder size={64} className="mx-auto text-[#C9971C]" />
        <h2 className="mt-3 break-words text-lg font-bold text-[#1B1D1B]">{folder.name}</h2>
        <p className="text-sm text-[#8A8D89]">Double-click the folder to open it.</p>
      </div>

      {share?.shareUrl && (
        <div className="rounded-lg border border-[#B9D2CD] bg-[#EEF4F2] p-3">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-[#1F5C52]">Public folder link</p>
          <div className="flex items-center gap-2">
            <input readOnly value={share.shareUrl} className="w-full min-w-0 truncate rounded border border-[#D8D4CA] bg-white px-2 py-1.5 font-mono text-xs text-[#5B5F5C]" />
            <button onClick={() => onCopy(share.shareUrl, "Folder share link copied")} aria-label="Copy folder share link" className="shrink-0 rounded border border-[#D8D4CA] p-1.5 text-[#5B5F5C] hover:bg-[#F0EEE7]"><Copy size={14} /></button>
          </div>
        </div>
      )}

      <div className="mt-5 flex flex-col gap-2.5">
        <button
          onClick={onShare}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-2.5 text-sm font-medium text-white hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
        >
          <Share2 size={16} />
          Share folder
        </button>
        <div className="grid grid-cols-2 gap-2.5">
          <button onClick={onRename} className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7]"><Pencil size={16} />Rename</button>
          <button onClick={onDelete} className="flex items-center justify-center gap-2 rounded-lg border border-[#F3D3CB] bg-white py-2.5 text-sm font-medium text-[#C4432B] hover:bg-[#FCEDE9]"><Trash2 size={16} />Delete</button>
        </div>
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Upload progress panel
// ---------------------------------------------------------------------------

function UploadProgressPanel({ uploads, onCancel, onDismiss, onRetry }) {
  if (uploads.length === 0) return null;

  const activeCount = uploads.filter((u) => u.status === "uploading").length;

  return (
    <div
      className="fixed bottom-4 right-4 z-40 w-[calc(100%-2rem)] max-w-sm overflow-hidden rounded-xl border border-[#E4E1DA] bg-white shadow-xl sm:bottom-6 sm:right-6"
      role="status"
      aria-live="polite"
    >
      <div className="flex items-center justify-between border-b border-[#E4E1DA] bg-[#FAF9F6] px-4 py-2.5">
        <span className="text-sm font-semibold text-[#1B1D1B]">
          {activeCount > 0
            ? `Uploading ${activeCount} file${activeCount === 1 ? "" : "s"}…`
            : "Uploads complete"}
        </span>
      </div>

      <ul className="max-h-72 divide-y divide-[#EEECE5] overflow-y-auto">
        {uploads.map((u) => (
          <li key={u.id} className="px-4 py-3">
            <div className="flex items-start gap-3">
              <div className="mt-0.5 shrink-0">
                {u.status === "success" ? (
                  <CheckCircle2 size={18} className="text-[#1F5C52]" />
                ) : u.status === "error" ? (
                  <AlertCircle size={18} className="text-[#C4432B]" />
                ) : (
                  <Upload size={18} className="text-[#8A8D89]" />
                )}
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="truncate text-sm font-medium text-[#1B1D1B]">
                    {u.name}
                  </p>
                  {u.status === "uploading" && (
                    <button
                      onClick={() => onCancel(u.id)}
                      aria-label={`Cancel upload of ${u.name}`}
                      className="shrink-0 rounded p-0.5 text-[#8A8D89] hover:bg-[#F0EEE7] hover:text-[#1B1D1B]"
                    >
                      <X size={14} />
                    </button>
                  )}
                  {u.status !== "uploading" && (
                    <button
                      onClick={() => onDismiss(u.id)}
                      aria-label={`Dismiss ${u.name}`}
                      className="shrink-0 rounded p-0.5 text-[#8A8D89] hover:bg-[#F0EEE7] hover:text-[#1B1D1B]"
                    >
                      <X size={14} />
                    </button>
                  )}
                </div>

                <p className="mt-0.5 font-mono text-xs text-[#8A8D89]">
                  {formatBytes(u.size)}
                </p>

                {u.status === "uploading" && (
                  <div className="mt-2">
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-[#E4E1DA]">
                      <div
                        className="h-full rounded-full bg-[#1F5C52] transition-[width] duration-200 ease-out"
                        style={{ width: `${Math.max(u.progress, 4)}%` }}
                      />
                    </div>
                    <p className="mt-1 text-right font-mono text-[11px] text-[#8A8D89]">
                      {u.progress}%
                    </p>
                  </div>
                )}

                {u.status === "success" && (
                  <p className="mt-1 text-xs text-[#1F5C52]">Upload complete</p>
                )}

                {u.status === "error" && (
                  <div className="mt-1.5 flex items-center justify-between gap-2">
                    <p className="truncate text-xs text-[#C4432B]">{u.error}</p>
                    <button
                      onClick={() => onRetry(u.id)}
                      className="flex shrink-0 items-center gap-1 rounded border border-[#E4E1DA] px-2 py-0.5 text-[11px] font-medium text-[#5B5F5C] hover:bg-[#F0EEE7]"
                    >
                      <RotateCcw size={11} />
                      Dismiss
                    </button>
                  </div>
                )}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
