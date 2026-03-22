import * as React from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import {
  Plus,
  Folder,
  Trash,
  Pin,
  PinOff,
  ChevronRight,
  Folders,
  Pencil,
} from "lucide-react";
import { DynamicIcon } from "lucide-react/dynamic";
import { useLocation, useSearchParams } from "react-router";
import { useSecondarySidebar } from "@/contexts/SecondarySidebarContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getModulePath, MODULE_CONFIGS } from "@/lib/groups/moduleConfigs";
import {
  getScopeFromPath,
  getTenantSlugFromPath,
  getScopedPath,
} from "@/router/scope";
import { prefetchGroups } from "@/lib/groups/prefetchGroups";
import { ColorPicker } from "@/components/ui/color-picker";
import { IconSelect } from "@/components/ui/icon-select";
import { cn } from "@/lib/utils";
import { motion, AnimatePresence } from "framer-motion";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from "@/components/ui/context-menu";
import { Link } from "@/components/ui/link";

export default function GroupsSidebar({ moduleKey }) {
  let fetchGroups;
  let createGroup;
  let editGroup;
  let deleteGroup;

  // Determine which API functions to use based on moduleKey
  switch (moduleKey) {
   /*  case "simulations":
      fetchGroups = fetchSimulationGroups;
      createGroup = createSimulationGroup;
      editGroup = editSimulationGroup;
      deleteGroup = deleteSimulationGroup;
      break;
    // Add more cases here for other moduleKeys as needed */
    default:
      throw new Error(`Unsupported moduleKey: ${moduleKey}`);
  }

  const {
    hideSidebar,
    toggleCollapse,
    isCollapsed,
    markAsPrefetched,
    isPrefetched,
    isVisible,
  } = useSecondarySidebar();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const activeGroupId = searchParams.get("group");

  // Track if this is the initial render to prevent unwanted animations
  const [hasRendered, setHasRendered] = React.useState(false);
  const [contextMenuOpen, setContextMenuOpen] = React.useState(false);

  React.useEffect(() => {
    // Set hasRendered to true after the first render
    const timer = setTimeout(() => setHasRendered(true), 100);
    return () => clearTimeout(timer);
  }, []);

  const [editingGroupId, setEditingGroupId] = React.useState(null);
  const [newGroupOpen, setNewGroupOpen] = React.useState(false);
  const [newGroupName, setNewGroupName] = React.useState("");
  const [newGroupColor, setNewGroupColor] = React.useState("#F0CF65");
  const [newGroupIcon, setNewGroupIcon] = React.useState("folder");

  // Ensure data is prefetched when component mounts (for auto-opened sidebars)
  React.useEffect(() => {
    if (moduleKey && !isPrefetched(moduleKey)) {
      prefetchGroups(moduleKey);
      markAsPrefetched(moduleKey);
    }
  }, [moduleKey, isPrefetched, markAsPrefetched]);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["groups", moduleKey],
    queryFn: () => fetchGroups(moduleKey),
    staleTime: 60_000,
    enabled: !!moduleKey,
  });

  const createGroupMutation = useMutation({
    mutationFn: (newGroup) => createGroup(newGroup),
    onSuccess: () => {
      refetch();
      setNewGroupName("");
      setNewGroupColor("#F0CF65");
      setNewGroupIcon("folder");
    },
  });

  const deleteGroupMutation = useMutation({
    mutationFn: (groupId) => deleteGroup(groupId),
    onSuccess: () => {
      refetch();
    },
  });

  const editGroupMutation = useMutation({
    mutationFn: ({ groupId, updatedGroup }) => editGroup(groupId, updatedGroup),
    onSuccess: () => {
      refetch();
    },
  });

  const handleCreateGroup = () => {
    if (!newGroupName.trim()) return;
    createGroupMutation.mutate({
      name: newGroupName,
      color: newGroupColor,
      icon: newGroupIcon,
    });
  };

  const handleDeleteGroup = (groupId) => {
    deleteGroupMutation.mutate(groupId);
  };

  const handleEditGroup = (groupId, updatedData) => {
    editGroupMutation.mutate({ groupId, updatedGroup: updatedData });
  };

  const handleDialogOpenChange = (open) => {
    setNewGroupOpen(open);
    if (!open) {
      // Reset all dialog state when closing
      setEditingGroupId(null);
      setNewGroupName("");
      setNewGroupColor("#F0CF65");
    } else {
      // Ensure context menu is closed when dialog opens
      setContextMenuOpen(false);
    }
  };

  const handleDialogSubmit = () => {
    if (editingGroupId) {
      // Edit existing group
      handleEditGroup(editingGroupId, {
        name: newGroupName,
        color: newGroupColor,
        icon: newGroupIcon,
      });
    } else {
      handleCreateGroup();
    }
    handleDialogOpenChange(false); // Use the proper close handler
  };

  const baseInnerPath = getModulePath(moduleKey);
  const scope = getScopeFromPath(location.pathname);
  const tenantSlug = getTenantSlugFromPath(location.pathname);
  const basePath = getScopedPath({
    scope,
    tenantSlug,
    innerPath: baseInnerPath,
  });
  const moduleConfig = Object.values(MODULE_CONFIGS).find(
    (config) => config.moduleKey === moduleKey
  );
  const displayName = moduleConfig?.displayName || moduleKey;

  // Show loading fallback for initial component load
  if (!moduleConfig) {
    return (
      <AnimatePresence mode="wait">
        {isVisible && (
          <motion.aside
            key="groups-sidebar-loading"
            initial={{ width: 0, opacity: 0 }}
            animate={{
              width: isCollapsed ? 60 : 280,
              opacity: 1,
            }}
            exit={{ width: 0, opacity: 0 }}
            transition={{
              duration: 0.3,
              ease: "easeInOut",
              width: { duration: 0.3 },
              opacity: { duration: 0.2 },
            }}
            className="hidden lg:flex overflow-hidden"
          >
            <div className="border-r p-3 w-full bg-card/80">
              <div className="h-6 w-24 animate-pulse rounded bg-muted/60 mb-3" />
              {Array.from({ length: 8 }).map((_, i) => (
                <div
                  key={i}
                  className="h-8 animate-pulse rounded bg-muted/40 mb-2"
                />
              ))}
            </div>
          </motion.aside>
        )}
      </AnimatePresence>
    );
  }

  return (
    <AnimatePresence mode="wait">
      {isVisible && (
        <motion.aside
          key="groups-sidebar"
          initial={{ width: 0, opacity: 0 }}
          animate={{
            width: isCollapsed ? 60 : 280,
            opacity: 1,
          }}
          exit={{ width: 0, opacity: 0 }}
          transition={{
            duration: 0.3,
            ease: "easeInOut",
            width: { duration: 0.3 },
            opacity: { duration: 0.2 },
          }}
          className="hidden lg:flex overflow-hidden"
        >
          <motion.div
            initial={{ x: -20, opacity: 0 }}
            animate={{
              x: 0,
              opacity: 1,
            }}
            transition={{
              duration: 0.3,
              ease: "easeInOut",
              x: { duration: 0.3 },
              opacity: { duration: 0.2 },
            }}
            className="flex h-full shrink-0 flex-col border-r bg-card/80 w-full"
          >
            <Dialog open={newGroupOpen} onOpenChange={handleDialogOpenChange}>
              <motion.div className="h-16 flex items-center p-4 border-b">
                <AnimatePresence mode="wait">
                  {!isCollapsed && (
                    <motion.h2
                      key="title"
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: -10 }}
                      transition={{ duration: 0.1 }}
                      className="text-sm font-semibold"
                    >
                      {displayName}
                    </motion.h2>
                  )}
                </AnimatePresence>
                <motion.div className="flex items-center gap-1 ml-auto">
                  <motion.div
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                  >
                    <Button
                      variant="ghost"
                      className="p-1 h-7 w-7 cursor-pointer"
                      onClick={toggleCollapse}
                    >
                      <motion.div
                        animate={{ rotate: isCollapsed ? 0 : 180 }}
                        transition={{
                          duration: 0.0,
                          delay: 0.0,
                        }}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </motion.div>
                      <span className="sr-only">
                        {isCollapsed ? "Expand" : "Collapse"} Groups
                      </span>
                    </Button>
                  </motion.div>
                </motion.div>
              </motion.div>

              <motion.div className="flex-1 overflow-auto" layout={hasRendered}>
                {isLoading ? (
                  <motion.ul
                    className="p-2 space-y-1"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ duration: 0.3 }}
                  >
                    {Array.from({ length: 8 }).map((_, i) => (
                      <motion.li
                        key={i}
                        className="h-8 animate-pulse rounded bg-muted/50"
                        initial={{ opacity: 0, y: 5 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: i * 0.05 }}
                      />
                    ))}
                  </motion.ul>
                ) : isError ? (
                  <motion.div
                    className={`text-sm ${isCollapsed ? "p-1" : "p-3"}`}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ duration: 0.3 }}
                  >
                    <AnimatePresence mode="wait">
                      {!isCollapsed && (
                        <motion.div
                          key="error-content"
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          transition={{ duration: 0.2 }}
                        >
                          Failed to load groups.{" "}
                          <button
                            className="underline hover:no-underline"
                            onClick={() => refetch()}
                          >
                            Retry
                          </button>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </motion.div>
                ) : (
                  <motion.ul
                    className={isCollapsed ? "p-1" : "p-2"}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ duration: 0.3, delay: 0.1 }}
                    layout={hasRendered}
                  >
                    {/* All items link */}
                    <li className="mb-2">
                      <Link
                        to={basePath}
                        className={`flex items-center rounded hover:bg-muted/60 text-sm transition-colors ${
                          !activeGroupId ? "bg-muted/60 font-medium" : ""
                        } ${
                          isCollapsed
                            ? "p-2 justify-center"
                            : "px-2 py-2 justify-between"
                        }`}
                        tooltip={isCollapsed ? `All ${displayName}` : undefined}
                        tooltipProps={{ side: "right" }}
                      >
                        <span
                          className={`flex items-center min-w-0 ${
                            isCollapsed ? "flex-col gap-1" : "gap-2"
                          }`}
                        >
                          <span className="inline-block h-2 w-2 rounded-full bg-primary/70" />
                          <Folders className="h-4 w-4 flex-shrink-0" />
                          {!isCollapsed && (
                            <span className="text-nowrap">
                              All {displayName}
                            </span>
                          )}
                        </span>
                      </Link>
                    </li>

                    {/* Group items */}
                    {data && data.length > 0 ? (
                      <>
                        {/* Separator */}
                        <li className="border-t border-border/50 my-2" />

                        {data
                          .sort(
                            (a, b) =>
                              (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) ||
                              (a.order ?? 0) - (b.order ?? 0)
                          )
                          .map((g, index) => (
                            <ContextMenu
                              open={contextMenuOpen}
                              onOpenChange={setContextMenuOpen}
                              key={g.id}
                            >
                              <ContextMenuTrigger>
                                <motion.li
                                  className="mb-1"
                                  initial={{ opacity: 0, x: -10 }}
                                  animate={{ opacity: 1, x: 0 }}
                                  transition={{
                                    delay: index * 0.05,
                                    duration: 0.2,
                                  }}
                                  layout={hasRendered}
                                >
                                  <Link
                                    to={`${basePath}?group=${encodeURIComponent(
                                      g.slug ?? g.id
                                    )}`}
                                    className={`flex items-center rounded hover:bg-muted/60 text-sm transition-colors ${
                                      activeGroupId === (g.slug ?? g.id)
                                        ? "bg-muted/60 font-medium"
                                        : ""
                                    } ${
                                      isCollapsed
                                        ? "p-2 justify-center"
                                        : "px-2 py-2 justify-between"
                                    }`}
                                    tooltip={isCollapsed ? g.name : undefined}
                                    tooltipProps={{ side: "right" }}
                                  >
                                    <motion.div
                                      className={`flex items-center min-w-0 ${
                                        isCollapsed
                                          ? "justify-center w-full"
                                          : ""
                                      }`}
                                    >
                                      {/* Icons container - no layout animation to prevent movement */}
                                      <motion.div
                                        className="flex items-center"
                                        initial={{
                                          flexDirection: isCollapsed
                                            ? "column"
                                            : "row",
                                          gap: isCollapsed
                                            ? "0.25rem"
                                            : "0.5rem",
                                        }}
                                        animate={
                                          hasRendered
                                            ? {
                                                flexDirection: isCollapsed
                                                  ? "column"
                                                  : "row",
                                                gap: isCollapsed
                                                  ? "0.25rem"
                                                  : "0.5rem",
                                              }
                                            : {
                                                flexDirection: isCollapsed
                                                  ? "column"
                                                  : "row",
                                                gap: isCollapsed
                                                  ? "0.25rem"
                                                  : "0.5rem",
                                              }
                                        }
                                        transition={{
                                          delay:
                                            hasRendered && isCollapsed
                                              ? 0.1
                                              : 0,
                                          duration: hasRendered ? 0.2 : 0,
                                          ease: "easeInOut",
                                        }}
                                      >
                                        <motion.span
                                          className="inline-block h-2 w-2 rounded-full flex-shrink-0"
                                          style={{
                                            backgroundColor:
                                              g.color ?? "var(--primary)",
                                          }}
                                        />
                                        <motion.div>
                                          {g.icon ? (
                                            <DynamicIcon
                                              className="h-4 w-4 flex-shrink-0"
                                              name={g.icon}
                                            />
                                          ) : (
                                            <Folder className="h-4 w-4 flex-shrink-0" />
                                          )}
                                        </motion.div>
                                      </motion.div>

                                      {/* Text content - only show when expanded */}
                                      {!isCollapsed && (
                                        <motion.div
                                          className="flex items-center gap-2 min-w-0 ml-2"
                                          initial={{ opacity: 0, width: 0 }}
                                          animate={{
                                            opacity: 1,
                                            width: "auto",
                                          }}
                                          exit={{ opacity: 0, width: 0 }}
                                          transition={{
                                            opacity: {
                                              duration: 0.2,
                                              delay: 0.2,
                                            },
                                            width: {
                                              duration: 0.2,
                                              delay: 0.2,
                                            },
                                          }}
                                        >
                                          <span className="truncate">
                                            {g.name}
                                          </span>
                                          {g.pinned && (
                                            <Pin className="h-3 w-3 opacity-70 flex-shrink-0" />
                                          )}
                                        </motion.div>
                                      )}
                                    </motion.div>

                                    {/* Count badge */}
                                    {!isCollapsed &&
                                      typeof g._count?.[moduleKey] ===
                                        "number" && (
                                        <motion.span
                                          className="text-xs text-neutral-500 tabular-nums opacity-70 flex-shrink-0"
                                          initial={{ opacity: 0, scale: 0.8 }}
                                          animate={{ opacity: 1, scale: 1 }}
                                          exit={{ opacity: 0, scale: 0.8 }}
                                          transition={{ duration: 0.15 }}
                                        >
                                          {g._count?.[moduleKey]}
                                        </motion.span>
                                      )}
                                  </Link>
                                </motion.li>
                              </ContextMenuTrigger>
                              <ContextMenuContent>
                                <ContextMenuItem
                                  className={"cursor-pointer"}
                                  onSelect={() => {
                                    handleEditGroup(g.id, {
                                      pinned: !g.pinned,
                                    });
                                  }}
                                >
                                  {g.pinned ? <PinOff /> : <Pin />}
                                  {g.pinned ? "Unpin" : "Pin"}
                                </ContextMenuItem>
                                <ContextMenuItem
                                  className={"cursor-pointer"}
                                  onSelect={() => {
                                    // Close context menu first
                                    setContextMenuOpen(false);

                                    // Then open dialog with a small delay to ensure context menu is closed
                                    setTimeout(() => {
                                      setNewGroupName(g.name);
                                      setNewGroupColor(g.color || "#F0CF65");
                                      setEditingGroupId(g.id);
                                      setNewGroupOpen(true);
                                    }, 50);
                                  }}
                                >
                                  <Pencil />
                                  Edit
                                </ContextMenuItem>
                                <ContextMenuItem
                                  className={"cursor-pointer"}
                                  onSelect={() => {
                                    handleDeleteGroup(g.id);
                                  }}
                                >
                                  <Trash />
                                  Delete
                                </ContextMenuItem>
                              </ContextMenuContent>
                            </ContextMenu>
                          ))}
                      </>
                    ) : (
                      !isCollapsed && (
                        <motion.div
                          className={`text-sm text-muted-foreground ${
                            isCollapsed ? "p-1" : "p-3"
                          }`}
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          transition={{ duration: 0.3 }}
                        >
                          <AnimatePresence mode="wait">
                            <motion.div
                              key="no-groups"
                              initial={{ opacity: 0 }}
                              animate={{ opacity: 1 }}
                              exit={{ opacity: 0 }}
                              transition={{ duration: 0.2 }}
                            >
                              No groups found.
                            </motion.div>
                          </AnimatePresence>
                        </motion.div>
                      )
                    )}
                    <motion.li
                      className="border-t border-border/50 my-2"
                      layout={hasRendered}
                    />
                    <motion.li className="" layout={hasRendered}>
                      <DialogTrigger asChild>
                        <Button
                          variant="ghost"
                          className={cn(
                            "w-full flex items-center rounded hover:bg-muted/60 text-sm transition-colors cursor-pointer",
                            {
                              "p-2 py-5.5 justify-center": isCollapsed,
                              "px-1 py-3 justify-start": !isCollapsed,
                            }
                          )}
                          tooltip={isCollapsed ? "Create New Group" : undefined}
                          tooltipProps={{ side: "right" }}
                        >
                          <Plus className="h-4 w-4" />
                          <span className={cn(isCollapsed && "sr-only")}>
                            New Group
                          </span>
                        </Button>
                      </DialogTrigger>
                    </motion.li>
                  </motion.ul>
                )}
              </motion.div>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>
                    {editingGroupId ? "Edit Group" : "New Group"}
                  </DialogTitle>
                  <DialogDescription className="text-sm text-muted-foreground">
                    {editingGroupId
                      ? "Edit the group to organize your "
                      : "Create a new group to organize your "}
                    {displayName.toLowerCase()}.
                  </DialogDescription>
                </DialogHeader>

                <div className="flex flex-col gap-2">
                  <Input
                    autoFocus
                    type="text"
                    placeholder="Group Name"
                    value={newGroupName}
                    onChange={(e) => setNewGroupName(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        handleDialogSubmit();
                      }
                    }}
                    className=""
                  />
                  <div className="flex gap-2">
                    <ColorPicker
                      value={newGroupColor}
                      onChange={setNewGroupColor}
                      variant="hex"
                    />
                    <IconSelect
                      value={newGroupIcon}
                      onChange={setNewGroupIcon}
                    />
                  </div>

                  <Button
                    onClick={handleDialogSubmit}
                    className={"cursor-pointer mt-2"}
                  >
                    {editingGroupId ? "Save Changes" : "Create"}
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          </motion.div>
        </motion.aside>
      )}
    </AnimatePresence>
  );
}
