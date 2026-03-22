import { makeCrudApi } from "@/api/makeCrudApi";

const usersApi = makeCrudApi("/users");

export const fetchUsers = async (_group = "") => usersApi.fetchList();
export const fetchUser = usersApi.fetchOne;
export const updateUser = usersApi.update;
