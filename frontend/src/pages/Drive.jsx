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
  ChevronLeft,
  ArrowUpDown,
  Copy,
  Move,
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
import bulkApi from "../api/bulkApi";

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
  const mb = kb / 1024;
  if (mb < 1024) return `${mb.toFixed(2)} MB`;
  return `${(mb / 1024).toFixed(2)} GB`;
}

function formatDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "Estimating…";
  if (seconds < 60) return `${Math.max(1, Math.ceil(seconds))}s remaining`;
  const minutes = Math.ceil(seconds / 60);
  if (minutes < 60) return `${minutes}m remaining`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m remaining`;
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function MimeIcon({ mime = "", ...props }) {
  if (mime.startsWith("image/")) return <FileImage {...props} />;
  if (mime.startsWith("audio/")) return <FileAudio {...props} />;
  if (mime.startsWith("video/")) return <FileVideo {...props} />;
  if (mime.includes("zip") || mime.includes("compressed") || mime.includes("tar"))
    return <FileArchive {...props} />;
  if (mime.includes("sheet") || mime.includes("csv") || mime.includes("excel"))
    return <FileSpreadsheet {...props} />;
  if (
    mime.includes("json") ||
    mime.includes("javascript") ||
    mime.includes("xml") ||
    mime.includes("html")
  )
    return <FileCode {...props} />;
  return <FileText {...props} />;
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
  const currentFolderUuidRef = useRef(null);

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

  // ---- Move / Copy picker ----
  // { mode: 'move' | 'copy', type: 'file' | 'folder', item } | null
  const [picker, setPicker] = useState(null);
  const [pickerBusy, setPickerBusy] = useState(false);
  const [selectedItems, setSelectedItems] = useState(() => new Set());

  // ---- Upload queue (supports multiple concurrent uploads with progress) ----
  // Each entry: { id, name, size, progress (0-100), status: 'uploading' | 'success' | 'error', error, controller }
  const [uploads, setUploads] = useState([]);
  const uploadsRef = useRef(uploads);

  useEffect(() => {
    uploadsRef.current = uploads;
  }, [uploads]);

  const fileInputRef = useRef(null);
  const folderInputRef = useRef(null);
  const dragCounter = useRef(0);
  const uploadQueueRef = useRef(Promise.resolve());

  const hasActiveUploads = uploads.some((u) => u.status === "uploading");

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------

  const loadDrive = async (folderUuid = null, { keepSelection = false } = {}) => {
    try {
      setLoading(true);

      const data = await driveApi.getDrive(folderUuid);

      setCurrentFolder(data.currentFolder);
      currentFolderUuidRef.current = data.currentFolder?.uuid ?? null;
      setFolders(data.folders || []);
      setFiles(data.files || []);

      if (!keepSelection) {
        setSelectedFile(null);
        setSelectedFolder(null);
        setFolderShare(null);
        setSelectedItems(new Set());
      }
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
    // Initial server synchronization.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadDrive();
  }, []);

  useEffect(() => {
    if (!selectedFile || !selectedFile.mimeType?.startsWith("image/")) {
      // Clear the object URL when the selected resource is not previewable.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAssetUrl(null);
      return;
    }

    let cancelled = false;
    let objectUrl = null;

    const loadAsset = async () => {
      try {
        const response = await fileApi.downloadFile(selectedFile.uuid);
        objectUrl = URL.createObjectURL(response.data);

        if (!cancelled) {
          setAssetUrl(objectUrl);
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
      if (objectUrl) URL.revokeObjectURL(objectUrl);
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
  const bulkSelection = useMemo(() => ({
    fileUuids: [...selectedItems].filter((key) => key.startsWith("file:" )).map((key) => key.slice(5)),
    folderUuids: [...selectedItems].filter((key) => key.startsWith("folder:" )).map((key) => key.slice(7)),
  }), [selectedItems]);
  const selectedCount = selectedItems.size;

  const toggleSelected = (type, uuid) => {
    const key = `${type}:${uuid}`;
    setSelectedItems((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  const clearSelected = () => setSelectedItems(new Set());

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

  const uploadFile = async (file, folderUuid) => {
    if (!file) return;

    if (file.size > 15 * 1024 ** 3) {
      toast.fire({ icon: "error", title: `${file.name} exceeds the 15 GB limit` });
      return false;
    }

    const id = uid();
    const startedAt = Date.now();
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
        file,
        folderUuid,
        speed: 0,
        etaSeconds: null,
      },
    ]);

    try {
      const upload = file.size >= 20 * 1024 * 1024
        ? fileApi.uploadFileResumable
        : fileApi.uploadFile;
      await upload(file, folderUuid, {
        signal: controller?.signal,
        onUploadProgress: (evt) => {
          if (!evt?.total) return;
          const pct = Math.round((evt.loaded / evt.total) * 100);
          const transferred = evt.transferred ?? evt.loaded;
          const elapsedSeconds = Math.max(0.1, (Date.now() - startedAt) / 1000);
          const speed = transferred / elapsedSeconds;
          patchUpload(id, {
            progress: pct,
            speed,
            etaSeconds: speed > 0 ? (evt.total - evt.loaded) / speed : null,
          });
        },
      });

      patchUpload(id, { progress: 100, status: "success" });
      toast.fire({ icon: "success", title: `${file.name} uploaded` });

      // Auto-clear successful entries after a short delay
      setTimeout(() => dismissUpload(id), 2500);
      return true;
    } catch (error) {
      if (error?.name === "CanceledError" || error?.name === "AbortError") {
        // Already removed by cancelUpload; nothing else to do.
        return;
      }
      patchUpload(id, {
        status: "error",
        error:
          error.response?.status === 413
            ? "This file exceeds the 15 GB upload limit."
            : error.code === "ECONNABORTED"
              ? "The upload timed out. Please try again."
              : error.response?.data?.message ?? "Upload failed. Please try again.",
      });
      return false;
    }
  };

  const runUploadEntries = async (entries, refreshFolderUuid) => {
    if (entries.length === 0) return;
    let nextIndex = 0;
    let uploadedAny = false;

    // Limiting parallel requests prevents several large files from exhausting
    // browser, proxy, and server resources while still keeping the UI usable.
    const worker = async () => {
      while (nextIndex < entries.length) {
        const entry = entries[nextIndex++];
        uploadedAny = (await uploadFile(entry.file, entry.folderUuid)) || uploadedAny;
      }
    };

    await Promise.all(
      Array.from({ length: Math.min(2, entries.length) }, () => worker())
    );

    if (uploadedAny && currentFolderUuidRef.current === refreshFolderUuid) {
      await loadDrive(refreshFolderUuid);
    }
  };

  const enqueueUploads = (entries, refreshFolderUuid) => {
    const run = () => runUploadEntries(entries, refreshFolderUuid);
    const batch = uploadQueueRef.current.then(run, run);
    uploadQueueRef.current = batch.catch(() => {});
    return batch;
  };

  const uploadFiles = (fileList) => {
    const folderUuid = currentFolder?.uuid ?? null;
    return enqueueUploads(
      Array.from(fileList || []).map((file) => ({ file, folderUuid })),
      folderUuid
    );
  };

  const uploadFolderContents = async (fileList) => {
    const chosenFiles = Array.from(fileList || []);
    if (chosenFiles.length === 0) return;
    const rootUuid = currentFolder?.uuid ?? null;
    const folderCache = new Map();

    const ensureFolder = async (parentUuid, name) => {
      const key = `${parentUuid || "root"}/${name}`;
      if (!folderCache.has(key)) {
        folderCache.set(key, (async () => {
          const existing = (await folderApi.listFolders(parentUuid))
            .find((folder) => folder.name === name);
          return existing || folderApi.createFolder({ name, parentUuid });
        })());
      }
      return folderCache.get(key);
    };

    const entries = [];
    for (const file of chosenFiles) {
      const parts = (file.webkitRelativePath || file.name).split("/").filter(Boolean);
      parts.pop();
      let parentUuid = rootUuid;
      for (const name of parts) {
        const folder = await ensureFolder(parentUuid, name);
        parentUuid = folder.uuid;
      }
      entries.push({ file, folderUuid: parentUuid });
    }
    return enqueueUploads(entries, rootUuid);
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

  const handleFolderCover = async () => {
    if (!selectedFolder) return;
    const result = await Swal.fire({
      title: "Folder cover",
      html: `<label class="swal2-input-label" for="folder-cover-url">Image URL</label><input id="folder-cover-url" class="swal2-input" type="url" placeholder="https://example.com/cover.jpg"><label class="swal2-input-label" for="folder-cover-icon">Or an icon / emoji</label><input id="folder-cover-icon" class="swal2-input" maxlength="8" placeholder="📁">`,
      showCancelButton: true,
      showDenyButton: Boolean(selectedFolder.coverImageUrl || selectedFolder.coverIcon),
      confirmButtonText: "Save cover",
      denyButtonText: "Remove cover",
      didOpen: () => {
        document.getElementById("folder-cover-url").value = selectedFolder.coverImageUrl || "";
        document.getElementById("folder-cover-icon").value = selectedFolder.coverIcon || "";
      },
      preConfirm: () => {
        const coverImageUrl = document.getElementById("folder-cover-url").value.trim();
        const coverIcon = document.getElementById("folder-cover-icon").value.trim();
        if (coverImageUrl) {
          try {
            const url = new URL(coverImageUrl);
            if (!["http:", "https:"].includes(url.protocol)) throw new Error();
          } catch {
            Swal.showValidationMessage("Enter a valid http or https image URL");
            return false;
          }
        }
        return { coverImageUrl: coverImageUrl || null, coverIcon: coverImageUrl ? null : (coverIcon || null) };
      },
    });
    if (result.isDismissed) return;
    try {
      const updated = await folderApi.updateCover(selectedFolder.uuid,
        result.isDenied ? { coverImageUrl: null, coverIcon: null } : result.value);
      setSelectedFolder(updated);
      setFolders((items) => items.map((item) => item.uuid === updated.uuid ? updated : item));
      toast.fire({ icon: "success", title: result.isDenied ? "Cover removed" : "Cover updated" });
    } catch (error) {
      Swal.fire({ icon: "error", title: "Couldn't update cover", text: error.response?.data?.message ?? "Please try again." });
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
        showDenyButton: true,
        confirmButtonText: "Copy share link",
        denyButtonText: "Copy public links API",
      });

      if (result.isConfirmed) {
        await copyText(share.shareUrl, "Folder share link copied");
      } else if (result.isDenied) {
        await copyText(share.linksUrl, "Public links API copied");
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
  // Move / Copy
  // -------------------------------------------------------------------------

  // Opens the destination picker. mode: 'move' | 'copy', type: 'file' | 'folder'.
  const openPicker = (mode, type, item) => setPicker({ mode, type, item });
  const closePicker = () => {
    if (pickerBusy) return;
    setPicker(null);
  };

  const confirmPicker = async (destination) => {
    if (!picker) return;
    const { mode, type, item } = picker;
    const destFolderUuid = destination?.uuid ?? null;

    // Guard against moving/copying a folder into itself.
    if (type === "folder" && destFolderUuid === item.uuid) {
      toast.fire({ icon: "error", title: "Can't move a folder into itself" });
      return;
    }

    setPickerBusy(true);
    try {
      if (type === "bulk") {
        await bulkApi[mode](item.selection, destFolderUuid);
      } else if (type === "file") {
        if (mode === "move") {
          await fileApi.moveFile(item.uuid, destFolderUuid);
        } else {
          await fileApi.copyFile(item.uuid, destFolderUuid);
        }
      } else {
        if (mode === "move") {
          await folderApi.moveFolder(item.uuid, destFolderUuid);
        } else {
          await folderApi.copyFolder(item.uuid, destFolderUuid);
        }
      }

      toast.fire({
        icon: "success",
        title: `${type === "bulk" ? `${item.count} items` : type === "file" ? item.originalName : item.name} ${
          mode === "move" ? "moved" : "copied"
        }${destination ? ` to "${destination.name}"` : " to My Drive"}`,
      });

      setPicker(null);
      clearSelected();
      await loadDrive(currentFolder?.uuid ?? null);
    } catch (error) {
      Swal.fire({
        icon: "error",
        title: mode === "move" ? "Move failed" : "Copy failed",
        text: error.response?.data?.message ?? "Please try again.",
      });
    } finally {
      setPickerBusy(false);
    }
  };

  const deleteSelected = async () => {
    if (!selectedCount) return;
    const result = await Swal.fire({
      icon: "warning",
      title: `Delete ${selectedCount} selected item${selectedCount === 1 ? "" : "s"}?`,
      text: "Selected folders and everything inside them will be permanently deleted.",
      showCancelButton: true,
      confirmButtonText: "Delete selected",
      confirmButtonColor: "#C4432B",
    });
    if (!result.isConfirmed) return;
    try {
      await bulkApi.delete(bulkSelection);
      clearSelected();
      await loadDrive(currentFolder?.uuid ?? null);
      toast.fire({ icon: "success", title: "Selected items deleted" });
    } catch (error) {
      Swal.fire({ icon: "error", title: "Delete failed", text: error.response?.data?.message ?? "Please try again." });
    }
  };

  const downloadSelected = async () => {
    if (!selectedCount) return;
    try {
      const response = await bulkApi.download(bulkSelection);
      const url = URL.createObjectURL(response.data);
      const link = document.createElement("a");
      link.href = url;
      link.download = "drive-selection.zip";
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      Swal.fire({ icon: "error", title: "Download failed", text: error.response?.data?.message ?? "Please try again." });
    }
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
          <div className="grid gap-4 grid-cols-2 sm:gap-5 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="h-32 sm:h-40 animate-pulse rounded-xl border border-[#E4E1DA] bg-white"
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
        <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center bg-[#1B1D1B]/70 backdrop-blur-sm px-4">
          <div className="mx-auto w-full max-w-sm rounded-2xl border-2 border-dashed border-[#8FBBAF] bg-[#1F5C52] px-6 py-7 text-center text-white shadow-2xl sm:max-w-none sm:px-12 sm:py-10">
            <Upload size={40} className="mx-auto mb-3" />
            <p className="text-lg font-semibold">Drop to upload</p>
            <p className="text-sm text-white/70">
              Adds to {currentFolder ? currentFolder.name : "My Drive"}
            </p>
          </div>
        </div>
      )}

      <div className="mx-auto max-w-7xl p-3 sm:p-6 md:p-8">
        {/* Header / breadcrumb */}
        <div className="mb-5 sm:mb-6">
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
                      ? "max-w-[120px] truncate px-1.5 py-0.5 text-[#1B1D1B] sm:max-w-none"
                      : "max-w-[90px] truncate px-1.5 py-0.5 sm:max-w-none"
                  }
                >
                  {crumb}
                </span>
              </span>
            ))}
          </nav>

          <h1 className="break-words text-xl font-bold tracking-tight text-[#1B1D1B] sm:text-3xl md:text-4xl">
            {currentFolder ? currentFolder.name : "My Drive"}
          </h1>
        </div>

        {/* Toolbar */}
        <div className="mb-5 flex flex-col gap-3 sm:mb-6 sm:gap-4">
          <div className="flex flex-wrap items-center gap-2 sm:gap-3">
            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center gap-2 whitespace-nowrap rounded-lg bg-[#1F5C52] px-3.5 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2 sm:px-5"
            >
              <Upload size={16} />
              <span className="hidden sm:inline">Upload file</span>
              <span className="sm:hidden">Upload</span>
            </button>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden"
              onChange={handleUploadInput}
            />

            <button
              onClick={() => folderInputRef.current?.click()}
              className="flex items-center gap-2 whitespace-nowrap rounded-lg border border-[#D8D4CA] bg-white px-3.5 py-2.5 text-sm font-medium text-[#1B1D1B] transition hover:bg-[#F0EEE7]"
            >
              <Folder size={16} />
              <span className="hidden sm:inline">Upload folder</span>
              <span className="sm:hidden">Folder</span>
            </button>
            <input
              ref={folderInputRef}
              type="file"
              multiple
              webkitdirectory=""
              className="hidden"
              onChange={(event) => {
                uploadFolderContents(event.target.files);
                event.target.value = "";
              }}
            />

            <button
              onClick={handleCreateFolder}
              className="flex items-center gap-2 whitespace-nowrap rounded-lg border border-[#E4E1DA] bg-white px-3.5 py-2.5 text-sm font-medium text-[#1B1D1B] shadow-sm transition hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2 sm:px-5"
            >
              <Folder size={16} className="text-[#C9971C]" />
              <span className="hidden sm:inline">New folder</span>
              <span className="sm:hidden">New</span>
            </button>

            {currentFolder && (
              <button
                onClick={() => loadDrive()}
                className="flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm text-[#5B5F5C] hover:bg-[#EAE7DF] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] sm:ml-auto"
              >
                <ChevronRight size={14} className="rotate-180" />
                <span className="hidden xs:inline">Back to My Drive</span>
                <span className="xs:hidden">Back</span>
              </button>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-2 sm:gap-3">
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

            <div className="flex flex-1 flex-wrap items-center gap-2 sm:flex-none sm:gap-3">
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

        {selectedCount > 0 && (
          <div className="mb-5 flex flex-wrap items-center gap-2 rounded-xl border border-[#B9D2CD] bg-[#EEF4F2] p-3">
            <span className="mr-auto text-sm font-semibold text-[#1F5C52]">{selectedCount} selected</span>
            <button onClick={() => openPicker("move", "bulk", { name: `${selectedCount} selected items`, count: selectedCount, selection: bulkSelection })} className="inline-flex items-center gap-1.5 rounded-lg border border-[#B9D2CD] bg-white px-3 py-2 text-xs font-medium"><Move size={14} />Move</button>
            <button onClick={() => openPicker("copy", "bulk", { name: `${selectedCount} selected items`, count: selectedCount, selection: bulkSelection })} className="inline-flex items-center gap-1.5 rounded-lg border border-[#B9D2CD] bg-white px-3 py-2 text-xs font-medium"><Copy size={14} />Copy</button>
            <button onClick={downloadSelected} className="inline-flex items-center gap-1.5 rounded-lg border border-[#B9D2CD] bg-white px-3 py-2 text-xs font-medium"><Download size={14} />ZIP</button>
            <button onClick={deleteSelected} className="inline-flex items-center gap-1.5 rounded-lg border border-[#F3D3CB] bg-white px-3 py-2 text-xs font-medium text-[#C4432B]"><Trash2 size={14} />Delete</button>
            <button onClick={clearSelected} aria-label="Clear selection" className="rounded p-2 text-[#5B5F5C] hover:bg-white"><X size={15} /></button>
          </div>
        )}

        {/* Main layout */}
        <div className="grid grid-cols-1 gap-5 sm:gap-6 lg:grid-cols-12 lg:gap-8">
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
                    checked={selectedItems.has(`folder:${folder.uuid}`)}
                    onToggle={() => toggleSelected("folder", folder.uuid)}
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
                    checked={selectedItems.has(`file:${file.uuid}`)}
                    onToggle={() => toggleSelected("file", file.uuid)}
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
                selectedItems={selectedItems}
                onToggleSelected={toggleSelected}
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
            <div className="rounded-xl border border-[#E4E1DA] bg-white p-4 shadow-sm sm:p-6 lg:sticky lg:top-8">
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
                  onMove={() => openPicker("move", "file", selectedFile)}
                  onCopy={() => openPicker("copy", "file", selectedFile)}
                />
              ) : selectedFolder ? (
                <FolderDetail
                  folder={selectedFolder}
                  share={folderShare}
                  onOpen={() => openFolder(selectedFolder)}
                  onCopy={copyText}
                  onShare={handleFolderShare}
                  onRename={handleFolderRename}
                  onDelete={handleFolderDelete}
                  onMoveFolder={() => openPicker("move", "folder", selectedFolder)}
                  onCopyFolder={() => openPicker("copy", "folder", selectedFolder)}
                  onCover={handleFolderCover}
                />
              ) : (
                <div className="py-10 text-center sm:py-20">
                  <FileText size={56} className="mx-auto mb-4 text-[#D8D4CA] sm:mb-5 sm:h-16 sm:w-16" />
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
        onRetry={async (id) => {
          const failed = uploadsRef.current.find((u) => u.id === id);
          if (!failed?.file) return;
          dismissUpload(id);
          if (await uploadFile(failed.file, failed.folderUuid)) {
            await loadDrive(failed.folderUuid);
          }
        }}
      />

      {/* Move / Copy destination picker */}
      {picker && (
        <FolderPickerModal
          mode={picker.mode}
          type={picker.type}
          item={picker.item}
          busy={pickerBusy}
          onClose={closePicker}
          onConfirm={confirmPicker}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Presentational pieces
// ---------------------------------------------------------------------------

function EmptyState({ onUpload }) {
  return (
    <div className="rounded-xl border border-dashed border-[#D8D4CA] bg-white/60 px-4 py-14 text-center sm:py-24">
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

function SelectionToggle({ checked, label, onToggle }) {
  return <span role="checkbox" aria-checked={checked} aria-label={label} tabIndex={0} onClick={(event) => { event.stopPropagation(); onToggle(); }} onKeyDown={(event) => { if (event.key === " " || event.key === "Enter") { event.preventDefault(); event.stopPropagation(); onToggle(); } }} className={`absolute bottom-2.5 right-2.5 flex h-5 w-5 items-center justify-center rounded border text-white sm:bottom-3 sm:right-3 ${checked ? "border-[#1F5C52] bg-[#1F5C52]" : "border-[#C7C3B8] bg-white"}`}>{checked && <CheckCircle2 size={14} />}</span>;
}

function FolderCard({ folder, tag, active, checked, onToggle, onClick, onDoubleClick }) {
  return (
    <button
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      title="Double-click to open folder"
      className={`group relative rounded-xl border p-3 text-left shadow-sm transition hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] sm:p-5 ${
        active ? "border-[#1F5C52] bg-[#EEF4F2]" : "border-[#E4E1DA] bg-white"
      }`}
    >
      <span className="absolute right-2.5 top-2.5 font-mono text-[10px] tracking-wide text-[#C7C3B8] sm:right-3 sm:top-3">
        {tag}
      </span>
      <FolderCover folder={folder} className="mb-2 sm:mb-3" />
      <h3 className="truncate pr-8 text-sm font-semibold text-[#1B1D1B]">
        {folder.name}
      </h3>
      <p className="mt-1 hidden text-xs text-[#8A8D89] sm:block">Double-click to open</p>
      <SelectionToggle checked={checked} label={`Select ${folder.name}`} onToggle={onToggle} />
    </button>
  );
}

function FolderCover({ folder, className = "", large = false }) {
  if (folder.coverImageUrl) {
    return <img src={folder.coverImageUrl} alt="" className={`${large ? "h-40 w-full" : "h-16 w-full sm:h-24"} ${className} rounded-lg object-cover`} loading="lazy" referrerPolicy="no-referrer" />;
  }
  if (folder.coverIcon) {
    return <span className={`${large ? "text-6xl" : "text-4xl"} ${className} block leading-none`} aria-hidden="true">{folder.coverIcon}</span>;
  }
  return <Folder size={large ? 56 : 30} className={`${className} text-[#C9971C] sm:h-10 sm:w-10`} />;
}

function FileCard({ file, tag, active, checked, onToggle, onClick }) {
  return (
    <button
      onClick={onClick}
      className={`group relative rounded-xl border p-3 text-left shadow-sm transition hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] sm:p-5 ${
        active ? "border-[#1F5C52] bg-[#EEF4F2]" : "border-[#E4E1DA] bg-white"
      }`}
    >
      <span className="absolute right-2.5 top-2.5 font-mono text-[10px] tracking-wide text-[#C7C3B8] sm:right-3 sm:top-3">
        {tag}
      </span>
      <MimeIcon mime={file.mimeType} size={30} className="mb-2 text-[#1F5C52] sm:mb-3 sm:h-10 sm:w-10" />
      <h3 className="truncate pr-8 text-sm font-semibold text-[#1B1D1B]">
        {file.originalName}
      </h3>
      <p className="mt-1 truncate font-mono text-[11px] text-[#8A8D89] sm:text-xs">
        {formatBytes(file.fileSize)} · {formatDate(file.createdAt)}
      </p>
      <SelectionToggle checked={checked} label={`Select ${file.originalName}`} onToggle={onToggle} />
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
  selectedItems,
  onToggleSelected,
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-[#E4E1DA] bg-white shadow-sm">
      <div className="grid grid-cols-[1fr_auto_auto_auto] gap-3 border-b border-[#E4E1DA] bg-[#FAF9F6] px-3 py-2.5 font-mono text-[11px] uppercase tracking-wide text-[#8A8D89] sm:gap-4 sm:px-5">
        <span>Name</span>
        <span className="hidden w-20 text-right sm:block">Size</span>
        <span className="w-16 text-right sm:w-24">Modified</span>
        <span className="w-5" />
      </div>
      <ul className="divide-y divide-[#EEECE5]">
        {folders.map((folder) => (
          <li key={folder.uuid}>
            <button
              onClick={() => onFolderClick(folder)}
              onDoubleClick={() => onFolderDoubleClick(folder)}
              className={`grid w-full grid-cols-[1fr_auto_auto_auto] items-center gap-3 px-3 py-3 text-left hover:bg-[#F7F6F2] focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#1F5C52] sm:gap-4 sm:px-5 ${
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
              <span role="checkbox" aria-checked={selectedItems.has(`folder:${folder.uuid}`)} onClick={(event) => { event.stopPropagation(); onToggleSelected("folder", folder.uuid); }} className={`flex h-5 w-5 items-center justify-center rounded border text-white ${selectedItems.has(`folder:${folder.uuid}`) ? "border-[#1F5C52] bg-[#1F5C52]" : "border-[#C7C3B8] bg-white"}`}>{selectedItems.has(`folder:${folder.uuid}`) && <CheckCircle2 size={14} />}</span>
            </button>
          </li>
        ))}
        {files.map((file) => (
            <li key={file.uuid}>
              <button
                onClick={() => onFileClick(file)}
                className={`grid w-full grid-cols-[1fr_auto_auto_auto] items-center gap-3 px-3 py-3 text-left hover:bg-[#F7F6F2] focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#1F5C52] sm:gap-4 sm:px-5 ${
                  selectedFile?.uuid === file.uuid ? "bg-[#EEF4F2]" : ""
                }`}
              >
                <span className="flex items-center gap-3 truncate text-sm font-medium text-[#1B1D1B]">
                  <MimeIcon mime={file.mimeType} size={18} className="shrink-0 text-[#1F5C52]" />
                  <span className="truncate">{file.originalName}</span>
                </span>
                <span className="hidden w-20 text-right font-mono text-xs text-[#8A8D89] sm:block">
                  {formatBytes(file.fileSize)}
                </span>
                <span className="w-16 text-right font-mono text-xs text-[#8A8D89] sm:w-24">
                  {formatDate(file.createdAt)}
                </span>
                <span role="checkbox" aria-checked={selectedItems.has(`file:${file.uuid}`)} onClick={(event) => { event.stopPropagation(); onToggleSelected("file", file.uuid); }} className={`flex h-5 w-5 items-center justify-center rounded border text-white ${selectedItems.has(`file:${file.uuid}`) ? "border-[#1F5C52] bg-[#1F5C52]" : "border-[#C7C3B8] bg-white"}`}>{selectedItems.has(`file:${file.uuid}`) && <CheckCircle2 size={14} />}</span>
              </button>
            </li>
        ))}
      </ul>
    </div>
  );
}

function FileDetail({
  file,
  assetUrl,
  onAssetError,
  onDownload,
  onShare,
  onToggleVisibility,
  onRename,
  onDelete,
  onMove,
  onCopy,
}) {
  return (
    <>
      <div className="mb-5 flex justify-center rounded-lg bg-[#F7F6F2] p-5 sm:mb-6 sm:p-6">
        {file.mimeType.startsWith("image/") && assetUrl ? (
          <img
            src={assetUrl}
            alt={file.originalName}
            className="max-h-48 rounded-md object-contain sm:max-h-56"
            onError={onAssetError}
          />
        ) : (
          <MimeIcon mime={file.mimeType} size={64} className="text-[#1F5C52] sm:h-[72px] sm:w-[72px]" />
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

      <div className="mt-6 flex flex-col gap-2.5 sm:mt-7">
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
            onClick={onMove}
            className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
          >
            <Move size={16} />
            Move
          </button>
          <button
            onClick={onCopy}
            className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
          >
            <Copy size={16} />
            Copy
          </button>
        </div>
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

function FolderDetail({ folder, share, onOpen, onCopy, onShare, onRename, onDelete, onMoveFolder, onCopyFolder, onCover }) {
  return (
    <>
      <div className="mb-5 text-center sm:mb-6">
        <FolderCover folder={folder} large className="mx-auto" />
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
          onClick={onOpen}
          className="flex w-full items-center justify-center gap-2 rounded-lg border border-[#1F5C52] bg-white py-2.5 text-sm font-medium text-[#1F5C52] hover:bg-[#EEF4F2]"
        >
          <Folder size={16} />
          Open folder
        </button>
        <button
          onClick={onShare}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#1F5C52] py-2.5 text-sm font-medium text-white hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
        >
          <Share2 size={16} />
          Share folder
        </button>
        <div className="grid grid-cols-2 gap-2.5">
          <button onClick={onMoveFolder} className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7]"><Move size={16} />Move</button>
          <button onClick={onCopyFolder} className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7]"><Copy size={16} />Copy</button>
        </div>
        <div className="grid grid-cols-2 gap-2.5">
          <button onClick={onRename} className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7]"><Pencil size={16} />Rename</button>
          <button onClick={onCover} className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7]"><FileImage size={16} />Cover</button>
        </div>
        <div className="grid grid-cols-1 gap-2.5">
          <button onClick={onDelete} className="flex items-center justify-center gap-2 rounded-lg border border-[#F3D3CB] bg-white py-2.5 text-sm font-medium text-[#C4432B] hover:bg-[#FCEDE9]"><Trash2 size={16} />Delete</button>
        </div>
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Move / Copy destination picker modal
// ---------------------------------------------------------------------------

function FolderPickerModal({ mode, type, item, busy, onClose, onConfirm }) {
  // stack of visited folders: [{ uuid: null, name: 'My Drive' }, ...]
  const [stack, setStack] = useState([{ uuid: null, name: "My Drive" }]);
  const [subfolders, setSubfolders] = useState([]);
  const [loadingList, setLoadingList] = useState(true);
  const [listError, setListError] = useState(null);

  const current = stack[stack.length - 1];
  const itemName = type === "file" ? item.originalName : item.name;

  useEffect(() => {
    let cancelled = false;
    // Reset this async request's state when navigating inside the picker.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoadingList(true);
    setListError(null);

    folderApi
      .listFolders(current.uuid)
      .then((list) => {
        if (cancelled) return;
        // Don't allow navigating into (or selecting) the folder being moved,
        // to avoid moving it into itself.
        const filtered =
          type === "folder" ? list.filter((f) => f.uuid !== item.uuid) : list;
        setSubfolders(filtered || []);
      })
      .catch(() => {
        if (!cancelled) setListError("Couldn't load folders here.");
      })
      .finally(() => {
        if (!cancelled) setLoadingList(false);
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current.uuid]);

  const goInto = (folder) => setStack((s) => [...s, { uuid: folder.uuid, name: folder.name }]);
  const goBack = () => setStack((s) => (s.length > 1 ? s.slice(0, -1) : s));
  const goToCrumb = (index) => setStack((s) => s.slice(0, index + 1));

  // Can't drop a file/folder into the folder it's already directly in — still
  // allowed, but flagged as a no-op via disabled state is unnecessary; keep simple.
  const isSameAsSource = type === "folder" && current.uuid === item.uuid;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-[#1B1D1B]/60 p-0 backdrop-blur-sm sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="flex w-full max-w-md flex-col rounded-t-2xl border border-[#E4E1DA] bg-white shadow-2xl sm:max-h-[85vh] sm:rounded-2xl">
        {/* Header */}
        <div className="flex items-center justify-between gap-3 border-b border-[#E4E1DA] px-4 py-3.5 sm:px-5">
          <div className="min-w-0">
            <h2 className="truncate text-base font-bold text-[#1B1D1B]">
              {mode === "move" ? "Move" : "Copy"} "{itemName}"
            </h2>
            <p className="text-xs text-[#8A8D89]">Choose a destination folder</p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            disabled={busy}
            className="shrink-0 rounded p-1.5 text-[#8A8D89] hover:bg-[#F0EEE7] hover:text-[#1B1D1B] disabled:opacity-50"
          >
            <X size={18} />
          </button>
        </div>

        {/* Breadcrumb */}
        <div className="flex items-center gap-1 overflow-x-auto border-b border-[#EEECE5] px-4 py-2.5 font-mono text-xs uppercase tracking-wide text-[#8A8D89] sm:px-5">
          <button
            onClick={goBack}
            disabled={stack.length === 1 || busy}
            aria-label="Back"
            className="mr-1 shrink-0 rounded p-1 text-[#5B5F5C] hover:bg-[#F0EEE7] disabled:opacity-30"
          >
            <ChevronLeft size={14} />
          </button>
          {stack.map((crumb, i) => (
            <span key={crumb.uuid ?? "root"} className="flex shrink-0 items-center gap-1">
              {i > 0 && <ChevronRight size={12} className="shrink-0" />}
              <button
                onClick={() => goToCrumb(i)}
                disabled={busy}
                className={`max-w-[100px] truncate rounded px-1.5 py-0.5 hover:bg-[#EAE7DF] ${
                  i === stack.length - 1 ? "text-[#1B1D1B]" : ""
                }`}
              >
                {crumb.name}
              </button>
            </span>
          ))}
        </div>

        {/* Folder list */}
        <div className="min-h-[220px] flex-1 overflow-y-auto px-2 py-2 sm:px-3">
          {loadingList ? (
            <div className="space-y-2 p-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-10 animate-pulse rounded-lg bg-[#F0EEE7]" />
              ))}
            </div>
          ) : listError ? (
            <div className="p-6 text-center text-sm text-[#C4432B]">{listError}</div>
          ) : subfolders.length === 0 ? (
            <div className="p-8 text-center text-sm text-[#8A8D89]">
              No subfolders here.
            </div>
          ) : (
            <ul className="divide-y divide-[#EEECE5]">
              {subfolders.map((folder) => (
                <li key={folder.uuid}>
                  <button
                    onClick={() => goInto(folder)}
                    disabled={busy}
                    className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-[#1B1D1B] hover:bg-[#F7F6F2] disabled:opacity-50"
                  >
                    <Folder size={18} className="shrink-0 text-[#C9971C]" />
                    <span className="truncate">{folder.name}</span>
                    <ChevronRight size={14} className="ml-auto shrink-0 text-[#C7C3B8]" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Footer */}
        <div className="flex flex-col-reverse gap-2.5 border-t border-[#E4E1DA] px-4 py-3.5 sm:flex-row sm:items-center sm:justify-between sm:px-5">
          <p className="truncate text-xs text-[#8A8D89]">
            Destination:&nbsp;<span className="font-medium text-[#1B1D1B]">{current.name}</span>
          </p>
          <div className="flex gap-2.5">
            <button
              onClick={onClose}
              disabled={busy}
              className="flex-1 rounded-lg border border-[#E4E1DA] bg-white px-4 py-2.5 text-sm font-medium text-[#1B1D1B] hover:bg-[#F0EEE7] disabled:opacity-50 sm:flex-none"
            >
              Cancel
            </button>
            <button
              onClick={() => onConfirm(current.uuid ? current : null)}
              disabled={busy || isSameAsSource}
              className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-[#1F5C52] px-4 py-2.5 text-sm font-medium text-white hover:bg-[#184A42] disabled:cursor-not-allowed disabled:opacity-50 sm:flex-none"
            >
              {mode === "move" ? <Move size={16} /> : <Copy size={16} />}
              {busy ? "Working…" : mode === "move" ? "Move here" : "Copy here"}
            </button>
          </div>
        </div>
      </div>
    </div>
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
      className="fixed bottom-0 right-0 z-40 w-full max-w-full overflow-hidden border border-[#E4E1DA] bg-white shadow-xl sm:bottom-6 sm:right-6 sm:w-[calc(100%-2rem)] sm:max-w-sm sm:rounded-xl"
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

      <ul className="max-h-60 divide-y divide-[#EEECE5] overflow-y-auto sm:max-h-72">
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
                    <div className="mt-1 flex justify-between gap-2 font-mono text-[11px] text-[#8A8D89]">
                      <span>{u.speed > 0 ? `${formatBytes(u.speed)}/s · ${formatDuration(u.etaSeconds)}` : "Estimating…"}</span>
                      <span>{u.progress}%</span>
                    </div>
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
                      Retry
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
