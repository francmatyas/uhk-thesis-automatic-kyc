import axiosInstance from "@/api/axiosInstance";
import { makeCrudApi } from "@/api/makeCrudApi";

const verificationsApi = makeCrudApi("/provider/verifications");

export const fetchVerification = verificationsApi.fetchOne;

export const transitionVerification = async (id, status) => {
  const response = await axiosInstance.post(
    `/provider/verifications/${id}/transition`,
    { status },
  );
  return response.data;
};

export const approveVerification = async (id) => {
  const response = await axiosInstance.post(
    `/provider/verifications/${id}/approve`,
  );
  return response.data;
};

export const rejectVerification = async (id) => {
  const response = await axiosInstance.post(
    `/provider/verifications/${id}/reject`,
  );
  return response.data;
};
