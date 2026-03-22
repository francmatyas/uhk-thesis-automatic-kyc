import axiosInstance from "@/api/axiosInstance";

export const getAvatarPresignUpload = async (userId, extension) => {
  const response = await axiosInstance.post(
    `/avatars/presign-upload?userId=${userId}&extension=${extension}`
  );
  return response.data;
};
