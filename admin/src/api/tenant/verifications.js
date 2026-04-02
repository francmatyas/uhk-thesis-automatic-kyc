import axiosInstance from "@/api/axiosInstance";

export const fetchVerification = async (id) => {
  const response = await axiosInstance.get(`/verifications/${id}`);
  return response.data;
};

export const transitionVerification = async (id, status) => {
  const response = await axiosInstance.post(
    `/verifications/${id}/transition`,
    { status }
  );
  return response.data;
};

export const approveVerification = async (id) => {
  const response = await axiosInstance.post(`/verifications/${id}/approve`);
  return response.data;
};

export const rejectVerification = async (id) => {
  const response = await axiosInstance.post(`/verifications/${id}/reject`);
  return response.data;
};
