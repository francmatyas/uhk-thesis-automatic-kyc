import axiosInstance from "@/api/axiosInstance";
export async function fetchProfile() {
  const { data } = await axiosInstance.get(`/users/me/profile`);
  return data;
}
export async function updateProfile(payload) {
  const { data } = await axiosInstance.put(`/users/me/profile`, payload);
  return data;
}