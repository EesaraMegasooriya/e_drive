import axios from "./axios";

const folderApi = {
  createFolder: async (data) => {
    const response = await axios.post("/folders", data);
    return response.data;
  },

  listFolders: async (parentUuid = null) => {
    const response = await axios.get("/folders", {
      params: {
        parentUuid,
      },
    });

    return response.data;
  },

  getFolder: async (uuid) => {
    const response = await axios.get(`/folders/${uuid}`);
    return response.data;
  },

  renameFolder: async (uuid, data) => {
    const response = await axios.put(`/folders/${uuid}`, data);
    return response.data;
  },

  deleteFolder: async (uuid) => {
    await axios.delete(`/folders/${uuid}`);
  },

  moveFolder: async (uuid, parentUuid) => {
    const response = await axios.put(`/folders/${uuid}/move`, null, {
      params: { parentUuid },
    });
    return response.data;
  },

  copyFolder: async (uuid, parentUuid) => {
    const response = await axios.post(`/folders/${uuid}/copy`, null, {
      params: { parentUuid },
    });
    return response.data;
  },

  setVisibility: async (uuid, isPublic) => (await axios.put(`/folders/${uuid}/visibility`, null, { params: { isPublic } })).data,

  setStreaming: async (uuid, enabled) => (await axios.put(`/folders/${uuid}/streaming`, null, { params: { enabled } })).data,

  updateCover: async (uuid, data) => (await axios.put(`/folders/${uuid}/cover`, data)).data,
};

export default folderApi;
