import axios from "./axios";

export default {
  move: (selection, destinationFolderUuid) =>
    axios.post("/bulk/move", { ...selection, destinationFolderUuid }),
  copy: (selection, destinationFolderUuid) =>
    axios.post("/bulk/copy", { ...selection, destinationFolderUuid }),
  delete: (selection) => axios.post("/bulk/delete", selection),
  download: (selection) => axios.post("/bulk/download", selection, { responseType: "blob" }),
};
