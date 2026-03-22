import { axiosInstance } from "@/api/axiosInstance";

export const fetchPage = ({
  module,
  basePath,
  pageIndex,
  pageSize,
  globalFilter,
  sorting = [],
}) => {
  const base = basePath || `/${module}`;
  const params = {
    page: pageIndex,
    size: pageSize,
  };

  if (globalFilter) params.q = globalFilter;
  if (sorting.length) {
    params.sort = sorting[0].id;
    params.dir = sorting[0].desc ? "desc" : "asc";
  }

  // manually serialize into query string
  const queryString = Object.entries(params)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join("&");

  return axiosInstance.get(`${base}?${queryString}`).then((res) => res.data);
};

export const fetchSubPanel = ({
  module,
  parentId,
  pageIndex,
  pageSize,
  globalFilter,
  sorting = [],
}) => {
  const base = `/${module}/${parentId}/items`;
  const params = {
    page: pageIndex,
    size: pageSize,
  };

  if (globalFilter) params.q = globalFilter;
  if (sorting.length) {
    params.sort = sorting[0].id;
    params.dir = sorting[0].desc ? "desc" : "asc";
  }

  // manually serialize into query string
  const queryString = Object.entries(params)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join("&");

  return axiosInstance.get(`${base}?${queryString}`).then((res) => res.data);
};
