import axios from "./axios";

const shareApi = {

  /**
   * Create (or return existing) share link
   */
  createShare: async (uuid, type = "FILE") => {

    const response = await axios.post("/share", {
      uuid,
      type,
    });

    return response.data;
  },

  /**
   * Get direct public asset URL for a file
   */
  getFileUrl: async (uuid) => {

    const response = await axios.get(
      `/share/file/${uuid}`
    );

    return response.data;
  },

  /**
   * Get direct public URLs for every file
   * inside a folder
   */
  getFolderUrls: async (uuid) => {

    const response = await axios.get(
      `/share/folder/${uuid}`
    );

    return response.data;
  },

  /**
   * Copy text to clipboard
   */
  copy: async (text) => {

    await navigator.clipboard.writeText(text);

  }

};

export default shareApi;