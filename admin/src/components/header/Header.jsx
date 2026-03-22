import { Breadcrumb } from "./Breadcrumb";
import { Button } from "@/components/ui/button";
import { Search, Bell } from "lucide-react";
import { SearchToggle } from "./SearchToggle";
import { NotificationCenter } from "./NotificationCenter";
import { useLocation, matchRoutes } from "react-router";
import { routes } from "@/router/routes";
import { SidebarTrigger } from "@/components/ui/sidebar";

export default function Header() {
  const location = useLocation();
  const matches = matchRoutes(routes, location) || [];

  // Remove the root wrapper route and trailing index match for more accurate matching
  const filteredMatches = [...matches];
  if (filteredMatches.length) filteredMatches.shift();
  if (
    filteredMatches.length &&
    filteredMatches[filteredMatches.length - 1].route.index
  ) {
    filteredMatches.pop();
  }

  // Find deepest route that declares custom header controls
  let headerControls = null;
  for (let i = filteredMatches.length - 1; i >= 0; i--) {
    const m = filteredMatches[i];
    if (m?.route?.headerControls !== undefined) {
      headerControls = m.route.headerControls;
      break;
    }
  }

  return (
    <header className="h-16 p-4 flex items-center justify-between gap-4 shadow-sm shadow-neutral-800 bg-card">
      <div className="flex items-center gap-4">
        <SidebarTrigger className="md:hidden" />
        <div className="hidden sm:block">
          {headerControls ?? <Breadcrumb />}
        </div>
      </div>
      {/* <div className="flex items-center gap-2">
        <SearchToggle />
        <NotificationCenter />
      </div> */}
    </header>
  );
}
