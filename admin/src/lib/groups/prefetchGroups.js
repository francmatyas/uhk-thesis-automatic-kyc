import { queryClient } from "@/main";
//import { fetchSimulationGroups } from "@/api/simulations";

// Generic function to prefetch groups for any module
export function prefetchGroups(moduleKey) {
  if (!moduleKey) return;

  let fetchFn;
  switch (moduleKey) {
    case "simulations":
      //fetchFn = fetchSimulationGroups;
      break;
    // Add cases for other modules as needed
    default:
      console.warn(`No fetch function defined for moduleKey: ${moduleKey}`);
      return;
  }

  // Warm the code-split chunk
  import("@/components/sidebar/GroupsSidebar");

  // Warm the data
  queryClient.prefetchQuery({
    queryKey: ["groups", moduleKey],
    queryFn: () => fetchFn(),
    staleTime: 60_000,
  });
}

// Legacy function for backward compatibility
export function prefetchSimulationsGroups() {
  prefetchGroups("simulations");
}
