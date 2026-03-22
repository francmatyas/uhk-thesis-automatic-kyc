import { RouterProvider } from "react-router";
import createRouter from "@/router/createRouter";
import { Toaster } from "@/components/ui/sonner";

export default function App() {
  return (
    <div className="w-full h-full bg-background">
      <RouterProvider router={createRouter()} />
      <Toaster
        toastOptions={{
          classNames: {
            error: "!bg-red-950 !text-red-100",
            success: "!text-green-950 !bg-green-100",
            warning: "!text-yellow-950 !bg-yellow-100",
            info: "!bg-blue-950 !text-blue-100",
          },
        }}
      />
    </div>
  );
}
