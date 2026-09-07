import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Gallery from "../pages/Gallery";
import PublicFolderShare from "../pages/PublicFolderShare";

export default function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Gallery />} />
        <Route path="/gallery" element={<Gallery />} />
        <Route path="/gallery/:galleryId" element={<Gallery />} />
        <Route path="/share/folder/:token" element={<PublicFolderShare />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
