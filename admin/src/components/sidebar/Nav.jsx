import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarGroupLabel,
} from "@/components/ui/sidebar";
import { Link } from "react-router";
import { useLocation } from "react-router";
import { useSecondarySidebar } from "@/contexts/SecondarySidebarContext";
import { prefetchGroups } from "@/lib/groups/prefetchGroups";
import { getModuleConfig } from "@/lib/groups/moduleConfigs";
import { cn } from "@/lib/utils";

export default function Nav({ conf, admin = false, ...props }) {
  const location = useLocation();
  const { items, label } = conf;
  const { toggleSidebar, isPrefetched, markAsPrefetched } =
    useSecondarySidebar();
  const normalizePath = (path = "/") =>
    path && path !== "/" ? path.replace(/\/+$/, "") : path || "/";

  const handleItemHover = (url) => {
    const moduleConfig = getModuleConfig(url);
    if (
      moduleConfig &&
      moduleConfig.hasGroups &&
      !isPrefetched(moduleConfig.moduleKey)
    ) {
      prefetchGroups(moduleConfig.moduleKey);
      markAsPrefetched(moduleConfig.moduleKey);
    }
  };

  const handleItemClick = (url) => {
    const moduleConfig = getModuleConfig(url);
    if (moduleConfig && moduleConfig.hasGroups) {
      // Small delay to allow navigation to complete first and create smoother UX
      setTimeout(() => {
        toggleSidebar(moduleConfig.moduleKey, true);
      }, 150);
    }
  };

  return (
    <SidebarGroup {...props}>
      {label && (
        <SidebarGroupLabel
          className={cn(
            "text-sm text-muted-foreground",
            admin && "text-orange-500"
          )}
        >
          {label}
        </SidebarGroupLabel>
      )}
      <SidebarGroupContent>
        <SidebarMenu>
          {items.map((item) => {
            const currentPath = normalizePath(location.pathname);
            const itemPath = normalizePath(item.url);
            const isScopeHomePath = /^\/(?:p|t\/[^/]+)$/.test(itemPath);
            const isActive =
              currentPath === itemPath ||
              (!isScopeHomePath && currentPath.startsWith(itemPath + "/"));

            return (
              <SidebarMenuItem key={item.title}>
                <SidebarMenuButton
                  className={cn(admin && "data-[active=true]:opacity-90 hover:opacity-90")}
                  asChild
                  isActive={isActive}
                >
                  <Link
                    to={item.url}
                    data-active={isActive}
                    onMouseEnter={() => handleItemHover(item.url)}
                    onFocus={() => handleItemHover(item.url)}
                    onClick={(e) => {
                      handleItemClick(item.url);
                      item.action && item.action(e);
                    }}
                  >
                    <item.icon />
                    <span>{item.title}</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            );
          })}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  );
}
