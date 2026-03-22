import {
  useState,
  useRef,
  useEffect,
  useLayoutEffect,
  Children,
  Fragment,
} from "react";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { MoreHorizontal } from "lucide-react";

// Section component to group controls
function ControlBarSection({ children, align = "start", priority = 0 }) {
  return <>{children}</>;
}

export function ControlBar({ children }) {
  const containerRef = useRef(null);
  const itemsRef = useRef([]);
  const moreButtonRef = useRef(null);
  const [visibleItems, setVisibleItems] = useState(null); // null means not calculated yet
  const [mounted, setMounted] = useState(false);

  // Parse children to extract sections and flatten controls
  const sections = [];
  const controls = [];

  // Helper to recursively flatten fragments
  const flattenChildren = (children) => {
    const result = [];
    Children.forEach(children, (child) => {
      if (!child) return;

      // If it's a fragment, recursively flatten its children
      if (
        child.type === Fragment ||
        child.type === Symbol.for("react.fragment")
      ) {
        result.push(...flattenChildren(child.props.children));
      } else {
        result.push(child);
      }
    });
    return result;
  };

  Children.forEach(children, (child) => {
    if (child?.type === ControlBarSection) {
      const sectionControls = [];
      const align = child.props.align || "start";
      const priority = child.props.priority || 0;

      // Flatten any fragments in the section children
      const flattenedChildren = flattenChildren(child.props.children);

      flattenedChildren.forEach((control) => {
        if (control) {
          const controlIndex = controls.length;
          controls.push({
            element: control,
            sectionIndex: sections.length,
            align,
            priority,
            controlIndex,
          });
          sectionControls.push(controlIndex);
        }
      });

      sections.push({
        align,
        priority,
        controls: sectionControls,
      });
    } else if (child) {
      // Direct child without section wrapper - also flatten if it's a fragment
      const flattenedChildren = flattenChildren([child]);
      flattenedChildren.forEach((control) => {
        if (control) {
          const controlIndex = controls.length;
          controls.push({
            element: control,
            sectionIndex: -1,
            align: "start",
            priority: 0,
            controlIndex,
          });
        }
      });
    }
  });

  useLayoutEffect(() => {
    setMounted(true);
  }, []);

  useLayoutEffect(() => {
    if (!mounted || !containerRef.current || controls.length === 0) {
      return;
    }

    const calculateVisibleItems = () => {
      const container = containerRef.current;
      if (!container) return;

      // Check if all items have been measured
      const allItemsMeasured = controls.every(
        (_, index) => itemsRef.current[index]
      );
      if (!allItemsMeasured || !moreButtonRef.current) {
        // Wait for next frame and try again
        requestAnimationFrame(calculateVisibleItems);
        return;
      }

      // Get the actual available width from the container
      const containerWidth = container.getBoundingClientRect().width;
      const moreButtonWidth = moreButtonRef.current.offsetWidth;
      const gap = 8;

      // Group controls by section and alignment
      const startControls = [];
      const endControls = [];

      controls.forEach((control, index) => {
        if (control.align === "end") {
          endControls.push({ ...control, index });
        } else {
          startControls.push({ ...control, index });
        }
      });

      // Sort by priority (higher priority = less likely to overflow)
      const sortByPriority = (a, b) => b.priority - a.priority;
      startControls.sort(sortByPriority);
      endControls.sort(sortByPriority);

      // First pass: Calculate end-aligned items width
      let endWidth = 0;
      const endItemWidths = endControls.map((control) => {
        const item = itemsRef.current[control.index];
        return item ? item.offsetWidth : 0;
      });

      endWidth = endItemWidths.reduce((sum, width, idx) => {
        return sum + width + (idx > 0 ? gap : 0);
      }, 0);

      // Calculate available space for start items (accounting for space between sections)
      const sectionGap = endWidth > 0 ? gap : 0;
      const availableForStart = containerWidth - endWidth - sectionGap;

      // Second pass: Fit as many start items as possible
      const visibleStart = [];
      let startWidth = 0;

      for (let i = 0; i < startControls.length; i++) {
        const control = startControls[i];
        const item = itemsRef.current[control.index];
        if (!item) continue;

        const itemWidth = item.offsetWidth;
        const gapWidth = visibleStart.length > 0 ? gap : 0;
        const newWidth = startWidth + itemWidth + gapWidth;

        // Check if we have more items after this one
        const remainingItems = startControls.length - (i + 1);
        const hasMoreItems = remainingItems > 0;

        // If there are more items, we need to reserve space for the More button
        const moreButtonSpace = hasMoreItems ? moreButtonWidth + gap : 0;
        const spaceNeeded = newWidth + moreButtonSpace;

        if (spaceNeeded <= availableForStart) {
          startWidth = newWidth;
          visibleStart.push(control.index);
        } else {
          // Can't fit this item, stop here
          break;
        }
      }

      // Third pass: Check if we need to sacrifice end items too
      const hasStartOverflow = visibleStart.length < startControls.length;

      if (hasStartOverflow) {
        const totalVisibleWidth =
          startWidth + endWidth + sectionGap + moreButtonWidth + gap;

        if (totalVisibleWidth > containerWidth) {
          // Even end items need to overflow - recalculate everything
          const allControls = [...startControls, ...endControls].sort(
            sortByPriority
          );
          const recalculatedVisible = [];
          let totalWidth = 0;

          for (let i = 0; i < allControls.length; i++) {
            const control = allControls[i];
            const item = itemsRef.current[control.index];
            if (!item) continue;

            const itemWidth = item.offsetWidth;
            const gapWidth = recalculatedVisible.length > 0 ? gap : 0;
            const newWidth = totalWidth + itemWidth + gapWidth;
            const remainingItems = allControls.length - (i + 1);
            const hasMoreItems = remainingItems > 0;
            const moreButtonSpace = hasMoreItems ? moreButtonWidth + gap : 0;
            const spaceNeeded = newWidth + moreButtonSpace;

            if (spaceNeeded <= containerWidth) {
              totalWidth = newWidth;
              recalculatedVisible.push(control.index);
            } else {
              break;
            }
          }

          // Ensure we always show at least one item if possible
          if (recalculatedVisible.length === 0 && allControls.length > 0) {
            const firstItem = itemsRef.current[allControls[0].index];
            if (
              firstItem &&
              firstItem.offsetWidth + moreButtonWidth + gap <= containerWidth
            ) {
              recalculatedVisible.push(allControls[0].index);
            }
          }

          setVisibleItems(recalculatedVisible);
        } else {
          // All end items fit, just some start items overflow
          const visibleEnd = endControls.map((c) => c.index);
          setVisibleItems([...visibleStart, ...visibleEnd]);
        }
      } else {
        // Everything fits, no overflow
        const visibleEnd = endControls.map((c) => c.index);
        setVisibleItems([...visibleStart, ...visibleEnd]);
      }
    };

    calculateVisibleItems();

    const resizeObserver = new ResizeObserver(() => {
      calculateVisibleItems();
    });

    if (containerRef.current) {
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      resizeObserver.disconnect();
    };
  }, [mounted, controls.length]);

  // If not yet calculated, show all items temporarily
  const effectiveVisibleItems =
    visibleItems !== null ? visibleItems : controls.map((_, i) => i);

  const overflowItems = controls
    .filter((control, index) => !effectiveVisibleItems.includes(index))
    .map((control) => control.element);

  return (
    <div className="w-full overflow-hidden p-1">
      <div
        ref={containerRef}
        className="flex items-center justify-between w-full"
      >
        {/* Measurement container - all items rendered invisibly for width calculation */}
        <div className="fixed -top-[9999px] left-0 flex items-center gap-2 pointer-events-none">
          {controls.map((control, index) => (
            <div
              key={`measure-${index}`}
              ref={(el) => (itemsRef.current[index] = el)}
            >
              {control.element}
            </div>
          ))}
          {/* Measure the More button too */}
          <Button
            variant="outline"
            size="sm"
            className="gap-2 shrink-0"
            ref={moreButtonRef}
          >
            <MoreHorizontal className="h-4 w-4" />
            More
          </Button>
        </div>

        {/* Left section - visible start-aligned items */}
        <div className="flex items-center gap-2 min-w-0 shrink">
          {controls
            .filter(
              (control, index) =>
                effectiveVisibleItems.includes(index) &&
                control.align === "start"
            )
            .map((control, idx) => (
              <Fragment key={idx}>{control.element}</Fragment>
            ))}

          {/* More button for overflow */}
          {overflowItems.length > 0 && (
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" size="sm" className="shrink-0">
                  <MoreHorizontal className="h-4 w-4" />
                  <span className="ml-1">More</span>
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-2" align="end">
                <div className="flex flex-col gap-2">{overflowItems}</div>
              </PopoverContent>
            </Popover>
          )}
        </div>

        {/* Right section - visible end-aligned items */}
        <div className="flex items-center gap-2 shrink-0">
          {controls
            .filter(
              (control, index) =>
                effectiveVisibleItems.includes(index) &&
                control.align === "end"
            )
            .map((control, idx) => (
              <Fragment key={idx}>{control.element}</Fragment>
            ))}
        </div>
      </div>
    </div>
  );
}

// Attach Section as a property of ControlBar
ControlBar.Section = ControlBarSection;
