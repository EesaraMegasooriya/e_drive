import axios, { AUTH_TOKEN_STORAGE_KEY, getStoredToken } from "./axios";

const authApi = {
  login: async (credentials) => {
    const response = await axios.post("/auth/login", credentials);

    // Save JWT if available
    if (response.data.token) {
      localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, response.data.token);
    }

    // Save user if available
    if (response.data.user) {
      localStorage.setItem(
        "user",
        JSON.stringify(response.data.user)
      );
    }

    return response.data;
  },

  register: async (data) => {
    const response = await axios.post("/auth/register", data);
    return response.data;
  },

  requestPasswordReset: async (email) => {
    await axios.post("/auth/forgot-password", { email });
  },

  resetPassword: async (token, password) => {
    await axios.post("/auth/reset-password", { token, password });
  },

  getProfile: async () => (await axios.get("/profile")).data,
  updateProfile: async (data) => (await axios.put("/profile", data)).data,
  changePassword: async (data) => axios.put("/profile/password", data),

  logout: () => {
    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
    localStorage.removeItem("user");
  },

  getToken: () => {
    return getStoredToken();
  },

  getUser: () => {
    const user = localStorage.getItem("user");

    return user ? JSON.parse(user) : null;
  },

  isAuthenticated: () => {
    return !!getStoredToken();
  },
};

export default authApi;
