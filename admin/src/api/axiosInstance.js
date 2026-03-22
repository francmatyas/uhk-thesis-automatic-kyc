import axios from "axios";
import { buildLoginUrl, getCurrentRelativeUrl } from "@/router/authRedirect";
const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:4000/";

const api = axios.create({
  baseURL: API_URL,
  withCredentials: true,
});

let csrfConfig = null;
let csrfConfigPromise = null;

function isCsrfEndpoint(url = "") {
  return url.endsWith("/auth/csrf") || url.includes("/auth/csrf");
}

function isAuthLifecycleEndpoint(url = "") {
  return ["/auth/login", "/auth/register", "/auth/logout"].some(
    (p) => url.endsWith(p) || url.includes(p)
  );
}

function resetCsrfCache() {
  csrfConfig = null;
  csrfConfigPromise = null;
}

async function fetchCsrfConfig() {
  if (csrfConfig?.token) return csrfConfig;
  if (csrfConfigPromise) return csrfConfigPromise;

  csrfConfigPromise = axios
    .get("/auth/csrf", {
      baseURL: API_URL,
      withCredentials: true,
    })
    .then(({ data }) => {
      csrfConfig = {
        token: data?.token ?? "",
        headerName: data?.headerName || "X-XSRF-TOKEN",
        parameterName: data?.parameterName || "_csrf",
      };
      return csrfConfig;
    })
    .finally(() => {
      csrfConfigPromise = null;
    });

  return csrfConfigPromise;
}

api.interceptors.request.use(async (config) => {
  if (isCsrfEndpoint(config?.url)) return config;

  const { token, headerName } = await fetchCsrfConfig();
  if (!token) return config;

  const headers = config.headers || {};
  headers[headerName] = token;
  headers["X-XSRF-TOKEN"] = token;
  config.headers = headers;

  return config;
});

// Centralized auth failure handling: redirect to login on 401s
let isRedirectingToLogin = false;
api.interceptors.response.use(
  (res) => {
    const url = res?.config?.url || "";
    if (isAuthLifecycleEndpoint(url)) {
      // Backend may rotate CSRF token after auth lifecycle changes.
      resetCsrfCache();
    }
    return res;
  },
  async (err) => {
    console.log("API error interceptor triggered", err);
    const { response, config } = err || {};
    const status = response?.status;
    const url = config?.url || "";
    const method = (config?.method || "").toLowerCase();

    const canRetryWithFreshCsrf =
      !isCsrfEndpoint(url) &&
      !config?._csrfRetried &&
      ["post", "put", "patch", "delete"].includes(method) &&
      (status === 403 || status === 419);

    if (canRetryWithFreshCsrf) {
      try {
        resetCsrfCache();
        const { token, headerName } = await fetchCsrfConfig();
        const retryConfig = { ...config, _csrfRetried: true };
        retryConfig.headers = retryConfig.headers || {};
        retryConfig.headers[headerName] = token;
        retryConfig.headers["X-XSRF-TOKEN"] = token;
        return api.request(retryConfig);
      } catch {
        // Fall through to regular error handling.
      }
    }

    // Do not hijack errors for auth endpoints where UI should show messages
    const isAuthEndpoint = isAuthLifecycleEndpoint(url);

    /* if (code === "ERR_NETWORK") {
      // Network error (server unreachable, CORS issue, etc.)
      window.location.href = "/error";
      return Promise.reject(err);
    } */

    if (status === 401 && !isAuthEndpoint) {
      if (!isRedirectingToLogin) {
        isRedirectingToLogin = true;
        // Avoid redirect loop if we're already on a public route
        const currentPath = window.location?.pathname || "";
        const publicPaths = ["/login", "/register", "/forgot-password"];
        if (!publicPaths.includes(currentPath)) {
          // Hard redirect to reset app state and land on login
          window.location.href = buildLoginUrl(getCurrentRelativeUrl());
        } else {
          isRedirectingToLogin = false;
        }
      }
    }
    // Let the original error propagate so callers can handle if needed
    return Promise.reject(err);
  }
);

export { api as axiosInstance };
export default api;
