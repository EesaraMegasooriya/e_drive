import { useCallback, useEffect, useState } from "react";
import authApi from "../api/authApi";
import { getTokenExpiry } from "../api/axios";
import AuthContext from "./auth-context";

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => authApi.isAuthenticated() ? authApi.getUser() : null);
  const loading = false;

  const logout = useCallback(() => {
    authApi.logout();
    setUser(null);
  }, []);

  useEffect(() => {
    if (!user) return undefined;

    const expiresAt = getTokenExpiry();
    if (!expiresAt) {
      authApi.logout();
      window.location.replace("/login");
      return undefined;
    }

    const timeout = window.setTimeout(() => {
      logout();
      window.location.replace("/login");
    }, Math.max(0, expiresAt - Date.now()));

    return () => window.clearTimeout(timeout);
  }, [user, logout]);

  const login = async (credentials) => {
    const response = await authApi.login(credentials);

    const loggedUser = authApi.getUser();

    setUser(loggedUser);

    return response;
  };

  const updateUser = useCallback((updatedUser) => {
    localStorage.setItem("user", JSON.stringify(updatedUser));
    setUser(updatedUser);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login,
        logout,
        updateUser,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
