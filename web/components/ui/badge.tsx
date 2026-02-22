import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold",
  {
    variants: {
      variant: {
        default: "bg-muted text-foreground",
        success: "bg-success/10 text-success",
        danger: "bg-danger/10 text-danger",
        warning: "bg-warning/15 text-warning"
      }
    },
    defaultVariants: {
      variant: "default"
    }
  }
);

export function Badge({ className, variant, ...props }: React.HTMLAttributes<HTMLDivElement> & VariantProps<typeof badgeVariants>) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}
