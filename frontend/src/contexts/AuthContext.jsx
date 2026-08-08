import { createContext, useContext, useEffect, useState } from "react";
import authApi from "../api/authApi";

const AuthContext = createContext();

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const savedUser = authApi.getUser();

    if (savedUser) {
      setUser(savedUser);
    }

    setLoading(false);
  }, []);

  const login = async (credentials) => {
    const response = await authApi.login(credentials);

    const loggedUser = authApi.getUser();

    setUser(loggedUser);

    return response;
  };

  const logout = () => {
    authApi.logout();
    setUser(null);
  };

  const updateUser = (updatedUser) => {
    localStorage.setItem("user", JSON.stringify(updatedUser));
    setUser(updatedUser);
  };

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

export function useAuth() {
  return useContext(AuthContext);
}
