// components/user-avatar-uploader.tsx
import React, { useCallback, useState, ChangeEvent, DragEvent } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

export function UserAvatarUploader({
  userName = "",
  initialAvatarUrl,
  maxFileSizeMb = 5,
  onUpload,
}) {
  const [previewUrl, setPreviewUrl] = useState(initialAvatarUrl ?? null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState(null);
  const initials =
    userName
      ?.trim()
      .split(" ")
      .map((p) => p[0])
      .join("")
      .toUpperCase() || "U";

  const maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;

  const validateFile = (file) => {
    if (!file.type.startsWith("image/")) {
      return "File must be an image.";
    }
    if (file.size > maxFileSizeBytes) {
      return `File is too large. Max ${maxFileSizeMb} MB.`;
    }
    return null;
  };

  const handleFile = (file) => {
    const validationError = validateFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setSelectedFile(file);

    // create preview
    const objectUrl = URL.createObjectURL(file);
    setPreviewUrl(objectUrl);
  };

  const onFileInputChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    handleFile(file);
  };

  const onDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (!file) return;
    handleFile(file);
  };

  const onDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const onDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleUploadClick = useCallback(async () => {
    if (!selectedFile) {
      setError("No file selected.");
      return;
    }
    setIsUploading(true);
    setError(null);
    try {
      const newUrl = await onUpload(selectedFile);
      // replace preview with final url (public R2 URL)
      setPreviewUrl(newUrl);
      setSelectedFile(null);
    } catch (err) {
      setError(err?.message || "Upload failed.");
    } finally {
      setIsUploading(false);
    }
  }, [onUpload, selectedFile]);

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
      <div className="flex flex-col items-center gap-3">
        <Avatar className="h-24 w-24 border border-border">
          {previewUrl && (
            <AvatarImage src={previewUrl} alt={userName || "Avatar"} />
          )}
          <AvatarFallback className="text-xl font-semibold">
            {initials}
          </AvatarFallback>
        </Avatar>
        <p className="text-xs text-muted-foreground text-center max-w-[12rem]">
          Recommended: square image, at least 256×256 px.
        </p>
      </div>

      <div className="flex-1 space-y-3">
        <Label htmlFor="avatar-input" className="text-sm font-medium">
          Profile picture
        </Label>

        <div
          className={cn(
            "flex flex-col items-center justify-center rounded-md border border-dashed p-4 text-center transition-colors",
            "cursor-pointer bg-muted/40 hover:bg-muted/70",
            isDragging && "border-primary bg-primary/5"
          )}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onClick={() => {
            const input = document.getElementById("avatar-input");
            input?.click();
          }}
        >
          <Input
            id="avatar-input"
            type="file"
            accept="image/*"
            className="hidden"
            onChange={onFileInputChange}
          />

          <p className="text-sm font-medium">
            Drag &amp; drop image here, or click to select
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            PNG, JPG, or WEBP up to {maxFileSizeMb} MB.
          </p>

          {selectedFile && (
            <p className="mt-2 text-xs text-foreground">
              Selected: <span className="font-medium">{selectedFile.name}</span>
            </p>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            disabled={!selectedFile || isUploading}
            onClick={handleUploadClick}
          >
            {isUploading ? "Uploading..." : "Save avatar"}
          </Button>

          {previewUrl &&
            initialAvatarUrl &&
            previewUrl !== initialAvatarUrl && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  setPreviewUrl(initialAvatarUrl);
                  setSelectedFile(null);
                  setError(null);
                }}
              >
                Cancel changes
              </Button>
            )}
        </div>

        {error && <p className="text-xs text-destructive">{error}</p>}
      </div>
    </div>
  );
}
