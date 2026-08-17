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

  uploadFileResumable: async (file, folderUuid = null, options = {}) => {
    const storageKey = `edrive-upload:${file.name}:${file.size}:${file.lastModified}`;
    const savedId = localStorage.getItem(storageKey);
    const init = await axios.post("/files/upload/resumable/init", null, {
      params: savedId ? { uploadId: savedId, size: file.size } : { size: file.size }, signal: options.signal,
    });
    const { uploadId, chunkSize } = init.data;
    let offset = Math.min(Number(init.data.offset) || 0, file.size);
    localStorage.setItem(storageKey, uploadId);

    while (offset < file.size) {
      const chunk = file.slice(offset, Math.min(offset + chunkSize, file.size));
      const body = new FormData();
      body.append("chunk", chunk);
      const response = await axios.put(`/files/upload/resumable/${uploadId}`, body, {
        params: { offset }, signal: options.signal,
      });
      offset = Number(response.data.offset);
      options.onUploadProgress?.({ loaded: offset, total: file.size });
    }

    const response = await axios.post(`/files/upload/resumable/${uploadId}/complete`, null, {
      params: { name: file.name, mimeType: file.type || "application/octet-stream", size: file.size, folderUuid },
      signal: options.signal,
    });
    localStorage.removeItem(storageKey);
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
