import axiosInstance from "@/api/axiosInstance";

export async function login(email, password, rememberMe) {
  return await axiosInstance.post(
    "/auth/login",
    {
      email,
      password,
      rememberMe,
    },
  );
}

export async function logout() {
  return await axiosInstance.post("/auth/logout");
}

export async function register(givenName, familyName, email, password) {
  return await axiosInstance.post("/auth/register", {
    givenName,
    familyName,
    email,
    password,
  });
}

export async function fetchProfile() {
  const { data } = await axiosInstance.get(`/users/me`);
  return data;
}
