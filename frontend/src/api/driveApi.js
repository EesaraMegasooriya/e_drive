import axios from "./axios";

const driveApi = {
  getDrive: async (folderUuid = null) => {
    const response = await axios.get("/drive", {
      params: {
        folderUuid,
      },
    });

    return response.data;
  },
};

export default driveApi;