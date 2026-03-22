import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Link } from "react-router";

export function TableHeader({ buttons, rowsSelected }) {
  return (
    <div className="flex flex-col gap-2">
      {/* Header Top Row */}
      <div className="flex items-center gap-2">
        {buttons?.map((button, index) => {
          if (button.onlySelected && !rowsSelected) {
            return null;
          }
          return (
            <Link to={button.link} key={button.key}>
              <Button className="cursor-pointer" variant={button.variant}>
                {button.icon}
                <span className="text-sm">{button.label}</span>
              </Button>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
