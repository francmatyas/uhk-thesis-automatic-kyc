import { cn } from "@/lib/utils";
export function Loader({ screen }) {
  const loader = (
    <div className="w-16 h-16 flex justify-center items-center">
      <div className={cn("loader")}></div>
    </div>
  );

  if (screen) {
    return (
      <div className="w-full h-full flex justify-center items-center">
        {loader}
      </div>
    );
  }
  return loader;
}