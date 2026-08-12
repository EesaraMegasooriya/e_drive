import React, { useEffect, useState } from "react";
import {
  Image as ImageIcon,
  FileText,
  Video,
  Music,
  File,
  ExternalLink,
  Loader2,
  AlertCircle,
  X,
} from "lucide-react";

const API_URL =
  "http://localhost:8080/api/public/folders/TxPNPrEFykC_t6ZS-tJ2Xw";

function TestPage() {
  const [assets, setAssets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedAsset, setSelectedAsset] = useState(null);

  useEffect(() => {
    fetchAssets();
  }, []);

  const fetchAssets = async () => {
    try {
      setLoading(true);
      setError("");

      const response = await fetch(API_URL);

      if (!response.ok) {
        throw new Error(`Failed to fetch assets: ${response.status}`);
      }

      const data = await response.json();

      // Your API directly returns an array
      setAssets(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error("Error fetching assets:", err);
      setError("Failed to load gallery content.");
    } finally {
      setLoading(false);
    }
  };

  const getFileType = (asset) => {
    const mimeType = asset.mimeType?.toLowerCase() || "";

    if (mimeType.startsWith("image/")) {
      return "image";
    }

    if (mimeType === "application/pdf") {
      return "pdf";
    }

    if (mimeType.startsWith("video/")) {
      return "video";
    }

    if (mimeType.startsWith("audio/")) {
      return "audio";
    }

    return "file";
  };

  const getFileIcon = (asset) => {
    const type = getFileType(asset);

    switch (type) {
      case "image":
        return <ImageIcon size={40} />;

      case "pdf":
        return <FileText size={40} />;

      case "video":
        return <Video size={40} />;

      case "audio":
        return <Music size={40} />;

      default:
        return <File size={40} />;
    }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return "0 KB";

    if (bytes < 1024) {
      return `${bytes} B`;
    }

    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const renderPreview = (asset) => {
    const type = getFileType(asset);

    switch (type) {
      case "image":
        return (
          <img
            src={asset.url}
            alt={asset.name}
            className="h-56 w-full object-cover"
            loading="lazy"
          />
        );

      case "pdf":
        return (
          <div className="flex h-56 w-full items-center justify-center bg-gray-100">
            <div className="text-center">
              <FileText
                size={64}
                className="mx-auto mb-3 text-red-500"
              />

              <p className="text-sm font-medium text-gray-700">
                PDF Document
              </p>

              <p className="mt-1 text-xs text-gray-500">
                Click to preview
              </p>
            </div>
          </div>
        );

      case "video":
        return (
          <video
            src={asset.url}
            className="h-56 w-full object-cover"
            controls
          />
        );

      case "audio":
        return (
          <div className="flex h-56 w-full flex-col items-center justify-center bg-gray-100">
            <Music size={64} className="mb-4 text-blue-500" />

            <audio
              src={asset.url}
              controls
              className="w-[90%]"
            />
          </div>
        );

      default:
        return (
          <div className="flex h-56 w-full items-center justify-center bg-gray-100">
            <div className="text-center text-gray-500">
              {getFileIcon(asset)}

              <p className="mt-3 text-sm">
                {asset.extension?.toUpperCase()} file
              </p>
            </div>
          </div>
        );
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 px-6 py-10">
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900">
            Test Gallery
          </h1>

          <p className="mt-2 text-gray-500">
            Files and media from your Drive folder
          </p>
        </div>

        {/* Loading */}
        {loading && (
          <div className="flex min-h-[300px] items-center justify-center">
            <div className="text-center">
              <Loader2
                size={40}
                className="mx-auto animate-spin text-blue-600"
              />

              <p className="mt-3 text-gray-500">
                Loading gallery...
              </p>
            </div>
          </div>
        )}

        {/* Error */}
        {!loading && error && (
          <div className="flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-red-700">
            <AlertCircle size={22} />

            <span>{error}</span>

            <button
              onClick={fetchAssets}
              className="ml-auto rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
            >
              Retry
            </button>
          </div>
        )}

        {/* Empty */}
        {!loading && !error && assets.length === 0 && (
          <div className="flex min-h-[300px] items-center justify-center rounded-xl border border-dashed border-gray-300 bg-white">
            <div className="text-center">
              <File size={50} className="mx-auto text-gray-400" />

              <p className="mt-3 font-medium text-gray-700">
                No files found
              </p>
            </div>
          </div>
        )}

        {/* Gallery */}
        {!loading && !error && assets.length > 0 && (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {assets.map((asset) => (
              <div
                key={asset.uuid}
                className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg"
              >
                {/* Preview */}
                <button
                  type="button"
                  onClick={() => setSelectedAsset(asset)}
                  className="block w-full text-left"
                >
                  {renderPreview(asset)}
                </button>

                {/* Details */}
                <div className="p-4">
                  <h2
                    className="truncate font-semibold text-gray-900"
                    title={asset.name}
                  >
                    {asset.name}
                  </h2>

                  <div className="mt-2 flex items-center justify-between text-xs text-gray-500">
                    <span>
                      {asset.extension?.toUpperCase()}
                    </span>

                    <span>
                      {formatFileSize(asset.size)}
                    </span>
                  </div>

                  {/* Open */}
                  <a
                    href={asset.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={(e) => e.stopPropagation()}
                    className="mt-4 flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700"
                  >
                    <ExternalLink size={16} />
                    Open
                  </a>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Preview Modal */}
      {selectedAsset && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => setSelectedAsset(null)}
        >
          <div
            className="relative max-h-[95vh] w-full max-w-6xl overflow-hidden rounded-xl bg-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="flex items-center justify-between border-b px-5 py-4">
              <div className="min-w-0">
                <h2 className="truncate font-semibold text-gray-900">
                  {selectedAsset.name}
                </h2>

                <p className="text-xs text-gray-500">
                  {selectedAsset.mimeType}
                </p>
              </div>

              <button
                onClick={() => setSelectedAsset(null)}
                className="ml-4 rounded-full p-2 text-gray-500 hover:bg-gray-100 hover:text-gray-900"
              >
                <X size={22} />
              </button>
            </div>

            {/* Modal Content */}
            <div className="max-h-[calc(95vh-80px)] overflow-auto bg-gray-100 p-4">
              {getFileType(selectedAsset) === "image" && (
                <img
                  src={selectedAsset.url}
                  alt={selectedAsset.name}
                  className="mx-auto max-h-[80vh] max-w-full rounded-lg object-contain"
                />
              )}

              {getFileType(selectedAsset) === "pdf" && (
                <iframe
                  src={selectedAsset.url}
                  title={selectedAsset.name}
                  className="h-[75vh] w-full rounded-lg bg-white"
                />
              )}

              {getFileType(selectedAsset) === "video" && (
                <video
                  src={selectedAsset.url}
                  controls
                  autoPlay
                  className="mx-auto max-h-[80vh] max-w-full"
                />
              )}

              {getFileType(selectedAsset) === "audio" && (
                <div className="flex min-h-[300px] flex-col items-center justify-center">
                  <Music size={80} className="mb-6 text-blue-500" />

                  <h3 className="mb-6 text-lg font-semibold">
                    {selectedAsset.name}
                  </h3>

                  <audio
                    src={selectedAsset.url}
                    controls
                    className="w-full max-w-xl"
                  />
                </div>
              )}

              {getFileType(selectedAsset) === "file" && (
                <div className="flex min-h-[300px] flex-col items-center justify-center">
                  <File size={80} className="mb-6 text-gray-400" />

                  <h3 className="text-lg font-semibold">
                    {selectedAsset.name}
                  </h3>

                  <a
                    href={selectedAsset.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-5 rounded-lg bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700"
                  >
                    Open File
                  </a>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default TestPage;