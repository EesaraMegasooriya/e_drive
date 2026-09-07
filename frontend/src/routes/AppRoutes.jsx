import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Gallery from "../pages/Gallery";
import { lazy, Suspense } from "react";
const GalleryAdmin = lazy(() => import("../pages/GalleryAdmin"));
const PublicFolderShare = lazy(() => import("../pages/PublicFolderShare"));

export default function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Gallery />} />
        <Route path="/admin" element={<Suspense fallback={<p role="status">Loading admin…</p>}><GalleryAdmin /></Suspense>} />
        <Route path="/gallery" element={<Gallery />} />
        <Route path="/gallery/:galleryId" element={<Gallery />} />
        <Route path="/share/folder/:token" element={<Suspense fallback={<p role="status">Loading…</p>}><PublicFolderShare /></Suspense>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
