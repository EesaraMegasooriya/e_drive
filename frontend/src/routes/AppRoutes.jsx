import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import MainLayout from "../layouts/MainLayout";

import Login from "../pages/Login";
import Register from "../pages/Register";
import ForgotPassword from "../pages/ForgotPassword";
import ResetPassword from "../pages/ResetPassword";
import Profile from "../pages/Profile";
import Admin from "../pages/Admin";
import Drive from "../pages/Drive";
import NotFound from "../pages/NotFound";
import PublicFolderShare from "../pages/PublicFolderShare";

import PrivateRoute from "./PrivateRoute";

export default function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>

        <Route
          path="/"
          element={<Navigate to="/drive" replace />}
        />


        <Route path="/share/folder/:token" element={<PublicFolderShare />} />

        <Route
          path="/login"
          element={<Login />}
        />


        <Route
          path="/register"
          element={<Register />}
        />

        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />

        <Route
    element={
        <PrivateRoute>
            <MainLayout />
        </PrivateRoute>
    }
>

    <Route
        path="/drive"
        element={<Drive />}
    />
    <Route path="/profile" element={<Profile />} />
    <Route path="/admin" element={<PrivateRoute requireAdmin><Admin /></PrivateRoute>} />

</Route>

        <Route
          path="*"
          element={<NotFound />}
        />

      </Routes>
    </BrowserRouter>
  );
}
