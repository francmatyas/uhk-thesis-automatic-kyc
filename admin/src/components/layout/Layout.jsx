import { Outlet, useLocation, matchPath } from "react-router";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import Sidebar from "@/components/sidebar/Sidebar";
import Header from "@/components/header/Header";
import { BreadcrumbProvider } from "@/contexts/BreadcrumbContext";
import {
  SecondarySidebarProvider,
  useSecondarySidebar,
} from "@/contexts/SecondarySidebarContext";
import GroupsSidebar from "@/components/sidebar/GroupsSidebar";
import { useAuth } from "@/contexts/AuthContext";
import { Loader } from "../ui/loader";

function LayoutContent() {
  const { pathname } = useLocation();
  const { isVisible, moduleKey } = useSecondarySidebar();

  return (
    <BreadcrumbProvider>
      <SidebarProvider>
        <div className="flex h-screen w-full overflow-hidden">
          {/* Main sidebar */}
          <Sidebar />

          {/* Secondary sidebar for groups */}
          {isVisible && moduleKey && <GroupsSidebar moduleKey={moduleKey} />}

          {/* Main content area */}
          <SidebarInset className="flex-1 flex flex-col">
            {/* header stays fixed at top */}
            <Header />

            {/* scrollable content */}
            <div className="flex-1 overflow-y-auto">
              <div className="appContent grid min-h-full">
                <Outlet />
              </div>
            </div>
          </SidebarInset>
        </div>
      </SidebarProvider>
    </BreadcrumbProvider>
  );
}

export default function Layout() {
  const { user } = useAuth();

  if (!user) {
    return (
      <div className="w-full h-full flex items-center justify-center">
        <Loader fullPage />
      </div>
    );
  }

  return (
    <SecondarySidebarProvider>
      <LayoutContent />
    </SecondarySidebarProvider>
  );
}
