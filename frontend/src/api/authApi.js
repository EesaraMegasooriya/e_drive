import axios from "./axios";

const authApi = {
  login: async (credentials) => {
    const response = await axios.post("/auth/login", credentials);

    // Save JWT if available
    if (response.data.token) {
      localStorage.setItem("token", response.data.token);
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
    localStorage.removeItem("token");
    localStorage.removeItem("user");
  },

  getToken: () => {
    return localStorage.getItem("token");
  },

  getUser: () => {
    const user = localStorage.getItem("user");

    return user ? JSON.parse(user) : null;
  },

  isAuthenticated: () => {
    return !!localStorage.getItem("token");
  },
};

export default authApi;
