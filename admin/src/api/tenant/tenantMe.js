import axiosInstance from "@/api/axiosInstance";

export const fetchTenantMe = async () => {
  const response = await axiosInstance.get("/tenants/me");
  return response.data;
};

export const updateTenantMe = async (data) => {
  const response = await axiosInstance.put("/tenants/me", data);
  return response.data;
};
