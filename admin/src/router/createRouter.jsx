import { createBrowserRouter } from "react-router";
import { routes } from "./routes";
import { AuthProvider } from "@/contexts/AuthContext";
import { Outlet } from "react-router";
import { ScrollToHash } from "./ScrollToHash";

export default function createRouter(params) {
  return createBrowserRouter([
    {
      element: (
        <AuthProvider>
          <ScrollToHash />
          <Outlet />
        </AuthProvider>
      ),
      children: routes,
    },
  ]);
}
