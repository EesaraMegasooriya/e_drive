import { Link } from "react-router-dom";
import { FileX2, Home, ArrowLeft } from "lucide-react";

export default function NotFound() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#F7F6F2] px-4 font-[Inter,ui-sans-serif,system-ui]">
      <div className="w-full max-w-sm text-center">
        {/* Signature: a catalog card for a record that doesn't exist */}
        <div className="relative mx-auto mb-8 w-56 rotate-[-2deg] rounded-xl border-2 border-dashed border-[#D8D4CA] bg-white/60 p-6 shadow-sm">
          <span className="font-mono text-[10px] tracking-wide text-[#C7C3B8]">
            F-404
          </span>
          <FileX2
            size={40}
            strokeWidth={1.5}
            className="mx-auto mb-4 mt-3 text-[#C4432B]"
          />
          <div className="mx-auto h-2 w-2/3 rounded bg-[#EEECE5]" />
          <div className="mx-auto mt-1.5 h-2 w-1/3 rounded bg-[#EEECE5]" />
        </div>

        <span className="font-mono text-xs uppercase tracking-wide text-[#8A8D89]">
          Error · Not found
        </span>

        <h1 className="mt-2 text-2xl font-bold tracking-tight text-[#1B1D1B] sm:text-3xl">
          This page isn't on file
        </h1>

        <p className="mx-auto mt-2 max-w-xs text-sm text-[#8A8D89]">
          The link may be broken, or the page may have been moved or deleted.
        </p>

        <div className="mt-8 flex flex-col gap-2.5 sm:flex-row sm:justify-center">
          <Link
            to="/drive"
            className="flex items-center justify-center gap-2 rounded-lg bg-[#1F5C52] px-5 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-[#184A42] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
          >
            <Home size={16} />
            Back to My Drive
          </Link>
          <button
            onClick={() => window.history.back()}
            className="flex items-center justify-center gap-2 rounded-lg border border-[#E4E1DA] bg-white px-5 py-2.5 text-sm font-medium text-[#1B1D1B] transition hover:bg-[#F0EEE7] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#1F5C52] focus-visible:ring-offset-2"
          >
            <ArrowLeft size={16} />
            Go back
          </button>
        </div>
      </div>
    </div>
  );
}