import { makeCrudApi } from "@/api/makeCrudApi";

const rolesApi = makeCrudApi("/provider/roles");

export const fetchRoles = rolesApi.fetchList;
export const fetchRole = rolesApi.fetchOne;
export const createRole = rolesApi.create;
export const updateRole = rolesApi.update;
export const deleteRole = rolesApi.remove;
