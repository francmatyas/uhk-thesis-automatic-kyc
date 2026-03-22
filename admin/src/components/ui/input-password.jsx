import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { useState } from "react";
import { EyeOff, EyeIcon } from "lucide-react";
import { TooltipSimple } from "@/components/ui/tooltip";

export function InputPassword({ onChange, ...props }) {
  const [showPassword, setShowPassword] = useState(false);

  const handleChange = (e) => {
    if (onChange) {
      onChange(e);
    }
  };
  const handleToggleShow = (e) => {
    e.preventDefault();
    setShowPassword(!showPassword);
  };

  return (
    <Input
      type={showPassword ? "text" : "password"}
      autoComplete="current-password"
      onChange={handleChange}
      {...props}
      append={
        <TooltipSimple
          content={showPassword ? "Hide password" : "Show password"}
        >
          <div className="cursor-pointer" onClick={handleToggleShow}>
            {showPassword ? <EyeOff size={16} /> : <EyeIcon size={16} />}
          </div>
        </TooltipSimple>
      }
    />
  );
}

export function InputNewPassword({ onChange, ...props }) {
  const [showPassword, setShowPassword] = useState(false);
  const [strength, setStrength] = useState(null);

  const handleChange = (e) => {
    setStrength(testStrength(e.target.value));
    if (onChange) {
      onChange(e);
    }
  };

  const testStrength = (password) => {
    let score = 0;

    if (!password) {
      return null; // Return weak if no password provided
    }

    // Check for minimum length (8+ characters)
    if (password.length >= 8) {
      score++;
    }

    // Check for at least one uppercase letter
    if (/[A-Z]/.test(password)) {
      score++;
    }

    // Check for at least one lowercase letter
    if (/[a-z]/.test(password)) {
      score++;
    }

    // Check for at least one number
    if (/\d/.test(password)) {
      score++;
    }

    // Check for at least one special character
    if (/[\W_]/.test(password)) {
      score++;
    }
    return score;
  };

  const strengthLevels = [
    {
      label: "Very Weak",
    },
    {
      label: "Weak",
      color: "bg-red-600",
    },
    {
      label: "Fair",
      color: "bg-amber-500",
    },
    {
      label: "Good",
      color: "bg-green-500",
    },
    {
      label: "Strong",
      color: "bg-emerald-500",
    },
  ];

  const renderStrengthBar = () => {
    const level = strengthLevels[strength - 1];
    const bars = [];
    for (let i = 0; i < strengthLevels.length - 1; i++) {
      if (i < strength - 1) {
        bars.push(
          <div className={cn("h-full w-full rounded-full", level.color)} />
        );
      } else {
        bars.push(
          <div
            className={cn("h-full w-full rounded-full bg-muted-foreground/30")}
          />
        );
      }
    }
    return bars;
  };

  return (
    <div className="flex flex-col gap-2">
      <Input
        type={showPassword ? "text" : "password"}
        autoComplete="current-password"
        onChange={handleChange}
        {...props}
        append={
          <TooltipSimple
            content={showPassword ? "Hide password" : "Show password"}
          >
            <div
              className="cursor-pointer"
              onClick={() => setShowPassword(!showPassword)}
            >
              {showPassword ? <EyeOff size={16} /> : <EyeIcon size={16} />}
            </div>
          </TooltipSimple>
        }
      />
      <div className="h-7 flex flex-col gap-1">
        {strength && (
          <>
            <div className="h-1 w-full flex gap-0.5">{renderStrengthBar()}</div>
            <div className="flex items-center justify-end">
              <p className={cn("text-[11px] text-muted-foreground")}>
                {strengthLevels[strength - 1].label}
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
