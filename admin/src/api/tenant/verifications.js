import axiosInstance from "@/api/axiosInstance";

export const fetchVerification = async (id) => {
  const response = await axiosInstance.get(`/tenants/kyc/verifications/${id}`);
  return response.data;
};

export const transitionVerification = async (id, status) => {
  const response = await axiosInstance.post(
    `/tenants/kyc/verifications/${id}/transition`,
    { status }
  );
  return response.data;
};
