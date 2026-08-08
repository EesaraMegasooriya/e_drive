import { FileText } from "lucide-react";

export default function FileCard({
  file,
}) {
  return (
    <div
      className="
        rounded-xl
        border
        bg-white
        p-5
        shadow-sm
        transition-all
        hover:-translate-y-1
        hover:shadow-lg
      "
    >
      <FileText
        size={48}
        className="mb-3 text-blue-600"
      />

      <h3 className="truncate font-semibold">
        {file.originalName}
      </h3>

      <p className="mt-2 text-sm text-gray-500">
        {(file.fileSize / 1024).toFixed(2)} KB
      </p>
    </div>
  );
}