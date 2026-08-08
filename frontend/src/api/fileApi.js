import axios from "./axios";

const fileApi = {
  uploadFile: async (file, folderUuid = null, options = {}) => {
    const { onUploadProgress, signal } = options;

    const formData = new FormData();

    formData.append("file", file);

    if (folderUuid) {
      formData.append("folderUuid", folderUuid);
    }

    const response = await axios.post(
      "/files/upload",
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data",
        },
        onUploadProgress,
        signal,
      }
    );

    return response.data;
  },

  listFiles: async (folderUuid = null) => {
    const response = await axios.get("/files", {
      params: {
        folderUuid,
      },
    });

    return response.data;
  },

  renameFile: async (uuid, data) => {
    const response = await axios.put(
      `/files/${uuid}`,
      data
    );

    return response.data;
  },

  moveFile: async (uuid, folderUuid) => {
    const response = await axios.put(
      `/files/${uuid}/move`,
      null,
      {
        params: {
          folderUuid,
        },
      }
    );

    return response.data;
  },

  deleteFile: async (uuid) => {
    await axios.delete(`/files/${uuid}`);
  },

  setVisibility: async (uuid, isPublic) => (await axios.put(`/files/${uuid}/visibility`, null, { params: { isPublic } })).data,

  downloadFile: async (uuid) => {
    const response = await axios.get(
      `/files/download/${uuid}`,
      {
        responseType: "blob",
      }
    );

    return response;
  },

};

export default fileApi;
