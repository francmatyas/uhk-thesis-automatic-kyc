import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import axiosInstance from "@/api/axiosInstance";
import { cn } from "@/lib/utils";

function fetchImageBlob({ queryKey }) {
  const [, src] = queryKey;
  return axiosInstance
    .get(src, { responseType: "blob" })
    .then((res) => res.data);
}

export function Image({ src, alt, className, loaderClassName }) {
  const {
    data: blob,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["image", src],
    queryFn: fetchImageBlob,
    staleTime: Infinity,
    cacheTime: 1000 * 60 * 60, // keep in cache for 1h
    enabled: !!src,
  });

  const [objectUrl, setObjectUrl] = useState(null);
  useEffect(() => {
    if (!blob) return;
    const newUrl = URL.createObjectURL(blob);
    setObjectUrl((oldUrl) => {
      if (oldUrl) URL.revokeObjectURL(oldUrl);
      return newUrl;
    });
  }, [blob]);

  if (isLoading && objectUrl == null) {
    return (
      <div
        className={cn(
          "w-32 h-32 bg-neutral-800 animate-pulse rounded",
          loaderClassName
        )}
      />
    );
  }

  if (isError) {
    return <div className="text-red-500">Failed to load image</div>;
  }

  return <img src={objectUrl} alt={alt} className={className} />;
}
