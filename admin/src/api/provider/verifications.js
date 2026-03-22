import axiosInstance from "@/api/axiosInstance";
import { makeCrudApi } from "@/api/makeCrudApi";

const verificationsApi = makeCrudApi("/provider/kyc/verifications");

export const fetchVerification = verificationsApi.fetchOne;

export const transitionVerification = async (id, status) => {
  const response = await axiosInstance.post(
    `/provider/kyc/verifications/${id}/transition`,
    { status },
  );
  return response.data;
};
