import { useState, useEffect } from "react";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Kbd } from "@/components/ui/kbd";
import { Search } from "lucide-react";

export function SearchToggle({}) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const down = (e) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((open) => !open);
      }
    };
    document.addEventListener("keydown", down);
    return () => document.removeEventListener("keydown", down);
  }, []);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          variant="outline"
          className="text-muted-foreground flex items-center sm:flex-initial cursor-text px-3 xl:px-4"
        >
          <div className="flex items-center gap-2">
            <Search />
            <span className="hidden xl:block">Search...</span>
          </div>
          <Kbd className="ml-auto hidden xl:block">⌘ K</Kbd>
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg sm:min-w-[400px]">
        <DialogHeader>
          <DialogTitle>Search</DialogTitle>
          <DialogDescription>Search functionality goes here.</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose asChild>
            <Button>Close</Button>
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
