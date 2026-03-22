import { makeCrudApi } from "@/api/makeCrudApi";
import axiosInstance from "@/api/axiosInstance";

const webhooksApi = makeCrudApi("/integrations/webhooks");

export const fetchWebhooks = webhooksApi.fetchList;
export const fetchWebhook = webhooksApi.fetchOne;
export const fetchWebhookOptions = async () => {
  const response = await axiosInstance.get("/integrations/webhooks/options");
  return response.data;
};
export const createWebhook = webhooksApi.create;
export const updateWebhook = webhooksApi.update;
export const deleteWebhook = webhooksApi.remove;
