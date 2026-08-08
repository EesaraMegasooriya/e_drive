import axios from "./axios";
export default {
  overview: async () => (await axios.get("/admin/overview")).data,
  users: async () => (await axios.get("/admin/users")).data,
  files: async () => (await axios.get("/admin/files")).data,
  setUserActive: (uuid, active) =>
    axios.put(`/admin/users/${uuid}/active`, null, { params: { active } }),
};
