import { Outlet } from "react-router";

export default function AuthLayout() {
  return (
    <div className="w-full h-full flex items-center justify-center">
      <div className="md:max-w-3xl md:w-full p-2">
        <Outlet />
      </div>
    </div>
  );
}
