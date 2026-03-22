import { Outlet } from "react-router";

export default function SettingsLayout() {
  return (
    <div className="flex flex-col gap-2 p-2">
      <Outlet />
    </div>
  );
}
