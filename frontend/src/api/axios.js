import axios from "axios";

export const AUTH_TOKEN_STORAGE_KEY = "token";
const USER_STORAGE_KEY = "user";

export function getStoredToken() {
  return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
}

export function getTokenExpiry(token = getStoredToken()) {
  if (!token) return null;

  try {
    const payload = token.split(".")[1];
    const normalizedPayload = payload
      .replace(/-/g, "+")
      .replace(/_/g, "/")
      .padEnd(Math.ceil(payload.length / 4) * 4, "=");
    const decoded = JSON.parse(
      atob(normalizedPayload)
    );

    return typeof decoded.exp === "number" ? decoded.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function isStoredTokenExpired(token = getStoredToken()) {
  const expiry = getTokenExpiry(token);
  return !expiry || Date.now() >= expiry;
}

export function clearStoredAuth() {
  localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  localStorage.removeItem(USER_STORAGE_KEY);
}

function redirectToLogin() {
  clearStoredAuth();

  if (window.location.pathname !== "/login") {
    window.location.replace("/login");
  }
}

const axiosInstance = axios.create({
  baseURL: "/api",
  headers: {
    "Content-Type": "application/json",
  },
  // Large uploads may legitimately take longer than an hour on slower links.
  timeout: 43200000,
});

// Attach JWT token to every request
axiosInstance.interceptors.request.use(
  (config) => {
    const token = getStoredToken();

    if (token) {
      if (isStoredTokenExpired(token)) {
        redirectToLogin();
        return Promise.reject(new axios.CanceledError("Authentication expired"));
      }

      if (typeof config.headers?.set === "function") {
        config.headers.set("Authorization", `Bearer ${token}`);
      } else {
        config.headers = {
          ...config.headers,
          Authorization: `Bearer ${token}`,
        };
      }
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Handle unauthorized responses
axiosInstance.interceptors.response.use(
  (response) => response,

  (error) => {
    if (error.response?.status === 401 && !error.config?.skipAuthRedirect) {
      redirectToLogin();
    }

    return Promise.reject(error);
  }
);

export default axiosInstance;
