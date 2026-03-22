import { makeCrudApi } from "@/api/makeCrudApi";

const apiKeysApi = makeCrudApi("/integrations/api-keys");

export const fetchApiKeys = apiKeysApi.fetchList;
export const fetchApiKey = apiKeysApi.fetchOne;
export const createApiKey = apiKeysApi.create;
export const updateApiKey = apiKeysApi.update;
export const deleteApiKey = apiKeysApi.remove;