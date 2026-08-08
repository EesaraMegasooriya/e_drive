import { Folder } from "lucide-react";

export default function FolderCard({
  folder,
  onOpen,
}) {
  return (
    <button
      onClick={() => onOpen(folder)}
      className="
        w-full
        rounded-xl
        border
        bg-white
        p-5
        text-left
        shadow-sm
        transition-all
        hover:-translate-y-1
        hover:shadow-lg
      "
    >
      <Folder
        size={48}
        className="mb-3 text-yellow-500"
      />

      <h3 className="truncate font-semibold">
        {folder.name}
      </h3>

      <p className="mt-2 text-sm text-gray-500">
        Folder
      </p>
    </button>
  );
}