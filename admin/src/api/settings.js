import axiosInstance from "@/api/axiosInstance";

export const fetchSettingsSecurity = async () => {
  const response = await axiosInstance.get("/settings/security");
  return response.data;
};

export const sessionRevoke = async (jti) => {
  const response = await axiosInstance.post("/sessions/revoke", { jti });
  return response.data;
};
export const sessionRevokeAll = async () => {
  const response = await axiosInstance.post("/sessions/revoke-all");
  return response.data;
};

export const fetchSettingsGeneral = async () => {
  const response = await axiosInstance.get("/users/preferences");
  return response.data;
};

export const updateSettingsGeneral = async (payload) => {
  const response = await axiosInstance.put("/users/preferences", payload);
  return response.data;
};
