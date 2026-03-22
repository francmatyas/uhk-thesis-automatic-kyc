import { useQuery, useMutation } from "@tanstack/react-query";

import {
  fetchSettingsSecurity,
  sessionRevoke,
  sessionRevokeAll,
} from "@/api/settings";

import { Loader } from "@/components/ui/loader";
import { Button } from "@/components/ui/button";
import { SettingsPageNav, SettingsPageNavItem } from "./SettingsPageNav";
import {
  CirclePlus,
  Laptop,
  LogOut,
  Smartphone,
  Tablet,
  Tv,
} from "lucide-react";

export default function SettingsSecurity() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["settingsSecurity"],
    queryFn: () => fetchSettingsSecurity(),
  });

  const sessionRevokeMutation = useMutation({
    mutationFn: (jti) => sessionRevoke(jti),
    onSuccess: () => {
      queryClient.invalidateQueries(["settingsSecurity"]);
      toast.success("Session revoked successfully.");
    },
  });
  const sessionRevokeAllMutation = useMutation({
    mutationFn: () => sessionRevokeAll(),
    onSuccess: () => {
      queryClient.invalidateQueries(["settingsSecurity"]);
      toast.success("All sessions revoked successfully.");
    },
  });

  const handleSessionRevoke = (jti) => {
    if (confirm("Are you sure you want to revoke this session?")) {
      sessionRevokeMutation.mutate(jti);
    }
  };
  const handleSessionRevokeAll = () => {
    if (confirm("Are you sure you want to revoke this session?")) {
      sessionRevokeAllMutation.mutate();
    }
  };

  if (isLoading) return <Loader screen />;
  if (error) return <div>Error: {error.message}</div>;

  return (
    <div className="w-full @container/size">
      <div className="grid grid-cols-1 gap-4 @3xl:grid-cols-3">
        <div className="col-span-2 flex gap-2 relative">
          <SettingsPageNav className="max-h-screen">
            <SettingsPageNavItem to="#change-password">
              Change Password
            </SettingsPageNavItem>
            <SettingsPageNavItem to="#2fa">
              Two-Factor Authentication
            </SettingsPageNavItem>
          </SettingsPageNav>
          <div className="flex-1 border-l pl-4">
            <div id="change-password" className="mb-8">
              <h2 className="text-2xl font-semibold mb-2">Change Password</h2>
              <p className="text-sm text-muted-foreground mb-4">
                Update your account password regularly to enhance security.
              </p>
              <div className="h-screen"></div>
              {/* Change password form goes here */}
            </div>
            <div id="2fa" className="mb-8">
              <h2 className="text-2xl font-semibold mb-2">
                Two-Factor Authentication
              </h2>
              <p className="text-sm text-muted-foreground mb-4">
                Add an extra layer of security to your account by enabling
                two-factor authentication (2FA).
              </p>
              {/* 2FA settings go here */}
            </div>
          </div>
        </div>
        <Sessions
          sessions={data.sessions}
          onRevoke={handleSessionRevoke}
          onRevokeAll={handleSessionRevokeAll}
        />
      </div>
    </div>
  );
}

function Sessions({ sessions, onRevoke, onRevokeAll }) {
  return (
    <div className="col-span-1 flex flex-col gap-8">
      <div>
        <h2 className="text-2xl font-semibold mb-2">Active Sessions</h2>
        <p className="text-sm text-muted-foreground">
          Manage and review your active sessions across different devices.
        </p>
        <Button
          variant="outline"
          size="sm"
          className="mt-4 w-full"
          onClick={onRevokeAll}
        >
          Log Out of All Sessions
        </Button>
      </div>
      <div className="flex flex-col gap-2">
        {sessions?.map((session) => (
          <SessionLine session={session} key={session.id} onRevoke={onRevoke} />
        ))}
      </div>
    </div>
  );
}

function SessionLine({ session, onRevoke }) {
  const deviceIconMap = {
    desktop: <Laptop size={22} />,
    phone: <Smartphone size={22} />,
    tablet: <Tablet size={22} />,
    smarttv: <Tv size={22} />,
  };
  return (
    <div className="p-4 border rounded-md flex justify-between items-center gap-4">
      <div className="flex items-center gap-4">
        <div className="bg-neutral-900 text-neutral-400 h-10 w-10 rounded-full flex items-center justify-center">
          {deviceIconMap[session.deviceType]}
        </div>
        <div>
          <div className="font-medium">{session.device}</div>
          <div className="flex flex-col">
            <span className="text-sm">{`${session.deviceVendor} ${session.deviceModel} - ${session.browserName}`}</span>
            <div className="text-xs text-muted-foreground">
              {new Date(session.lastSeenAt).toLocaleString("cs", {
                dateStyle: "medium",
                timeStyle: "short",
              })}
            </div>
          </div>
        </div>
      </div>
      <Button
        variant="icon"
        size="icon"
        tooltip="Log Out"
        onClick={() => onRevoke(session.jti)}
      >
        <LogOut />
      </Button>
    </div>
  );
}
