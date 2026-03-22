import { useEffect, useState } from "react";
import { Pencil } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { UserAvatarUploader } from "@/views/users/UserAvatarUploader";

export function UserAvatarEditorDialog({ userName = "", avatarUrl, onUpload }) {
  const [open, setOpen] = useState(false);
  const [currentAvatarUrl, setCurrentAvatarUrl] = useState(avatarUrl || null);

  useEffect(() => {
    setCurrentAvatarUrl(avatarUrl || null);
  }, [avatarUrl]);

  const initials =
    userName
      ?.trim()
      .split(" ")
      .map((part) => part[0])
      .join("")
      .toUpperCase() || "U";

  return (
    <>
      <div className="relative w-fit">
        <Avatar className="h-32 w-32 border border-border">
          {currentAvatarUrl && (
            <AvatarImage src={currentAvatarUrl} alt={userName || "Avatar"} />
          )}
          <AvatarFallback className="text-xl font-semibold">
            {initials}
          </AvatarFallback>
        </Avatar>

        <Button
          type="button"
          size="icon"
          className="absolute -bottom-1 -right-1 h-8 w-8 rounded-full"
          onClick={() => setOpen(true)}
          aria-label="Edit avatar"
        >
          <Pencil className="h-4 w-4" />
        </Button>
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Update Profile Picture</DialogTitle>
            <DialogDescription>
              Upload a new avatar image.
            </DialogDescription>
          </DialogHeader>

          <UserAvatarUploader
            userName={userName}
            initialAvatarUrl={currentAvatarUrl}
            onUpload={async (file) => {
              const nextUrl = await onUpload(file);
              setCurrentAvatarUrl(nextUrl);
              setOpen(false);
              return nextUrl;
            }}
          />
        </DialogContent>
      </Dialog>
    </>
  );
}

