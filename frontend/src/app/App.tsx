import { RouterProvider } from "react-router-dom";

import { router } from "@/app/router";
import { CallProvider } from "@/features/video-call/model/CallProvider";
import { ToastProvider } from "@/shared/ui/toast";

export function App() {
  return (
    <ToastProvider>
      <CallProvider>
        <RouterProvider router={router} />
      </CallProvider>
    </ToastProvider>
  );
}
