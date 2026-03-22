import axiosInstance from "@/api/axiosInstance";

export const fetchTenantProvider = async (id) => {
  const response = await axiosInstance.get(`/provider/tenants/${id}`);
  return response.data;
};

export const createTenantProvider = async (data) => {
  const response = await axiosInstance.post("/provider/tenants", data);
  return response.data;
};

export const updateTenantProvider = async (id, data) => {
  const response = await axiosInstance.put(`/provider/tenants/${id}`, data);
  return response.data;
};

export const deleteTenantProvider = async (id) => {
  const response = await axiosInstance.delete(`/provider/tenants/${id}`);
  return response.data;
};

export const fetchTenantMemberRoles = async () => {
  const response = await axiosInstance.get("/tenants/members/roles");
  return response.data;
};

export const searchTenantMembers = async ({ tenantId, q, limit = 20 }) => {
  const response = await axiosInstance.get("/tenants/members/search", {
    params: { tenantId, q, limit },
  });
  return response.data;
};
