import React, { useEffect, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router";

import { fetchPage, fetchSubPanel } from "@/api/table";

import { DataTable } from "./DataTable";
import { TableHeader } from "./TableHeader";
import { cn } from "@/lib/utils";

export default function TableWrapper({
  module,
  basePath,
  enumConfig,
  parentId = null,
  pageSize,
  buttons,
  className,
  isSubPanel = false,
  enableSearch = true,
  enableRowSelection = false,
  translations,
}) {
  const [searchParams, setSearchParams] = useSearchParams();

  const [globalFilter, setGlobalFilter] = useState(searchParams.get("q") || "");
  const [sorting, setSorting] = useState(
    searchParams.get("sorting") ? JSON.parse(searchParams.get("sorting")) : [],
  );
  const [pagination, setPagination] = useState({
    pageIndex: Number(searchParams.get("page")) || 0,
    pageSize: pageSize,
  });
  const [selectedRows, setSelectedRows] = useState([]);

  const handleGlobalFilterChange = (value) => {
    setGlobalFilter(value);
    setPagination((prev) => ({
      ...prev,
      pageIndex: 0,
    }));

    if (!isSubPanel) {
      setSearchParams((prev) => {
        const params = new URLSearchParams(prev);
        if (value.length > 0) {
          params.set("q", value);
        } else {
          params.delete("q");
        }
        params.delete("page");
        return params;
      });
    }
  };
  const handleSortingChange = (value) => {
    setSorting(value);
    setPagination((prev) => ({
      ...prev,
      pageIndex: 0,
    }));

    if (!isSubPanel) {
      setSearchParams((prev) => {
        const params = new URLSearchParams(prev);
        if (value.length > 0) {
          params.set("sorting", JSON.stringify(value));
        } else {
          params.delete("sorting");
        }
        params.delete("page");
        return params;
      });
    }
  };
  const handlePaginationChange = (value) => {
    setPagination(value);
    if (!isSubPanel) {
      setSearchParams((prev) => {
        const params = new URLSearchParams(prev);
        if (value.pageIndex > 0) {
          params.set("page", value.pageIndex);
        } else {
          params.delete("page");
        }
        return params;
      });
    }
  };

  const queryKey = [
    isSubPanel ? "subPanelData" : "tableData",
    module,
    basePath,
    pagination,
    globalFilter,
    sorting,
  ];
  const { data, isLoading, error } = useQuery({
    queryKey,
    queryFn: () =>
      isSubPanel
        ? fetchSubPanel({
            module,
            parentId,
            pageIndex: pagination.pageIndex,
            pageSize: pagination.pageSize,
            globalFilter,
            sorting,
          })
        : fetchPage({
            module,
            basePath,
            pageIndex: pagination.pageIndex,
            pageSize: pagination.pageSize,
            globalFilter,
            sorting,
          }),
    keepPreviousData: true,
    placeholderData: keepPreviousData,
    retry: (failureCount, queryError) => {
      const status = queryError?.response?.status;
      if (status === 403) return false;
      return failureCount < 2;
    },
  });

  useEffect(() => {
    setGlobalFilter(searchParams.get("q") || "");
    setSorting(
      searchParams.get("sorting")
        ? JSON.parse(searchParams.get("sorting"))
        : [],
    );
    setPagination({
      pageIndex: Number(searchParams.get("page")) || 0,
      pageSize: pageSize,
    });
  }, [module, searchParams]);

  useEffect(() => {
    setPagination((prev) => ({
      ...prev,
      pageSize: pageSize,
    }));
  }, [pageSize]);

  return (
    <div className={cn("h-full flex flex-col", className)}>
      {/* {!isSubPanel && (
        <TableHeader buttons={buttons} rowsSelected={selectedRows.length > 0} />
      )} */}
      <div className="h-full">
        <DataTable
          data={data}
          loading={isLoading}
          error={error}
          enumConfig={enumConfig}
          state={{
            globalFilter,
            sorting,
            pagination,
          }}
          onGlobalFilterChange={handleGlobalFilterChange}
          onSortingChange={handleSortingChange}
          onPaginationChange={handlePaginationChange}
          onSelectionChange={setSelectedRows}
          enableSearch={enableSearch}
          enableRowSelection={enableRowSelection}
          buttons={buttons}
          isSubPanel={isSubPanel}
          translations={translations}
        />
      </div>
    </div>
  );
}
