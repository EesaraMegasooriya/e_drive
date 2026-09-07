export const version = (item) => `${item.size}-${item.modifiedAt}`;
export const originalUrl = (item) => `${item.contentUrl}?v=${version(item)}`;
export const previewUrl = (item, size = 640) =>
  `${item.contentUrl.replace(/\/content$/, "/preview")}?size=${size}&v=${version(item)}`;
