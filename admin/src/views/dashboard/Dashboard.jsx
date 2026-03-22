import { useQuery } from "@tanstack/react-query";
//import { fetchSimulations } from "@/lib/api/simulation";

import Card from "../Card";
import { Loader } from "@/components/ui/loader";

export default function Dashboard({}) {
  /* const {
    data: dashboard,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => fetchSimulations(),
  }); */

  /* if (isLoading) return <Loader screen />;
  if (error) return <div>Error: {error.message}</div>; */

  return null;
}
