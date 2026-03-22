import axiosInstance from "@/api/axiosInstance";

export function makeCrudApi(basePath) {
  return {
    fetchList: async () => {
      const response = await axiosInstance.get(basePath);
      return response.data;
    },
    fetchOne: async (id) => {
      const response = await axiosInstance.get(`${basePath}/${id}`);
      return response.data;
    },
    create: async (payload) => {
      const response = await axiosInstance.post(basePath, payload);
      return response.data;
    },
    update: async (id, payload) => {
      const response = await axiosInstance.put(`${basePath}/${id}`, payload);
      return response.data;
    },
    remove: async (id) => {
      const response = await axiosInstance.delete(`${basePath}/${id}`);
      return response.data;
    },
  };
}
