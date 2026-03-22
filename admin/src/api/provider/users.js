import { makeCrudApi } from "@/api/makeCrudApi";

const usersApi = makeCrudApi("/provider/users");

export const fetchUsers = async (_group = "") => usersApi.fetchList();
export const fetchUser = usersApi.fetchOne;
export const createUser = usersApi.create;
export const updateUser = usersApi.update;
export const deleteUser = usersApi.remove;
