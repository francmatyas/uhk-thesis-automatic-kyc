import { makeCrudApi } from "@/api/makeCrudApi";
import axiosInstance from "@/api/axiosInstance";

const permissionsApi = makeCrudApi("/provider/permissions");

export const fetchPermissions = permissionsApi.fetchList;
export const fetchPermissionsForRoles = async ({ page = 0, size = 500 } = {}) => {
  const response = await axiosInstance.get("/provider/permissions", {
    params: {
      page,
      size,
      pageSize: size,
      limit: size,
    },
  });
  return response.data;
};
export const fetchPermission = permissionsApi.fetchOne;
export const createPermission = permissionsApi.create;
export const updatePermission = permissionsApi.update;
export const deletePermission = permissionsApi.remove;
