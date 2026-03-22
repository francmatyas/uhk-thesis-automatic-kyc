import { useLayoutEffect, useRef, useState, useEffect } from "react";
import TableWrapper from "./TableWrapper";
import { getTableSizing } from "./tableSizing";

export default function Table({
  module,
  basePath,
  enumConfig,
  enableRowSelection,
  buttons,
  translations,
}) {
  const containerRef = useRef(null);
  const [pageSize, setPageSize] = useState(null);
  const sizing = getTableSizing(module);

  useLayoutEffect(() => {
    if (!containerRef.current) return;
    const top = containerRef.current.getBoundingClientRect().top;
    const avail = window.innerHeight - top;
    const rows = Math.floor(
      (avail - sizing.tableReserve) / sizing.rowHeight,
    );
    const ps = Math.max(rows, 1);
    setPageSize(ps);
  }, [sizing.rowHeight, sizing.tableReserve]);

  useLayoutEffect(() => {
    function updatePageSize() {
      if (!containerRef.current) return;
      const top = containerRef.current.getBoundingClientRect().top;
      const avail = window.innerHeight - top;
      const rows = Math.floor(
        (avail - sizing.tableReserve) / sizing.rowHeight,
      );
      const size = Math.max(rows, 1);
      setPageSize(size);
    }

    updatePageSize();
    window.addEventListener("resize", updatePageSize);
    return () => window.removeEventListener("resize", updatePageSize);
  }, [sizing.rowHeight, sizing.tableReserve]);

  return (
    <div ref={containerRef} className="h-full">
      {pageSize && (
        <TableWrapper
          module={module}
          basePath={basePath}
          enumConfig={enumConfig}
          pageSize={pageSize}
          enableRowSelection={enableRowSelection}
          buttons={buttons}
          translations={translations}
        />
      )}
    </div>
  );
}
