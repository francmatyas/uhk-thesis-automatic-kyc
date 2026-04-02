import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import processTableData from "@/components/table/processTableData";

import {
  ArrowUpDown,
  ArrowDownNarrowWide,
  ArrowUpWideNarrow,
  Search,
  CircleHelp,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { Loader } from "@/components/ui/loader";
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { TooltipSimple } from "@/components/ui/tooltip";
import { ControlBar } from "@/views/ControlBar";

export function DataTable({
  module,
  data,
  loading,
  error,
  enumConfig,
  className,
  state,
  onGlobalFilterChange,
  onSortingChange,
  onPaginationChange,
  onSelectionChange,
  enableSearch,
  enableRowSelection = false,
  buttons = [],
  translations,
}) {
  const { t } = useTranslation();
  const tr = (value) =>
    typeof value === "string" ? t(value, { defaultValue: value }) : value;
  const hasTranslation = (key) => {
    if (typeof key !== "string" || key.length === 0) return false;
    return t(key, { defaultValue: "__MISSING_TRANSLATION__" }) !== "__MISSING_TRANSLATION__";
  };
  const { tenantSlug } = useParams();
  const prepared = processTableData({
    columns: data?.columns || [],
    rows: data?.rows || [],
    enableRowSelection,
    context: {
      module,
      tenantSlug,
      enumConfig,
      translate: tr,
      hasTranslation,
    },
  });
  const table = useReactTable({
    data: prepared.rows,
    columns: prepared.columns,
    manualSorting: true,
    manualFiltering: true,
    manualPagination: true,
    pageCount: data?.totalPages ?? 0,
    state,
    onSortingChange,
    onGlobalFilterChange,
    onPaginationChange,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => row.id,
  });
  const rowSelection = table.getState().rowSelection;

  useEffect(() => {
    if (onSelectionChange) {
      onSelectionChange(Object.keys(rowSelection));
    }
  }, [rowSelection, onSelectionChange]);

  const errorStatus = error?.response?.status;
  const rawErrorMessage = error?.response?.data?.message || error?.message;
  const errorMessage =
    typeof rawErrorMessage === "string" && rawErrorMessage.trim().length > 0
      ? rawErrorMessage
      : t("shared.table.unableToLoadData");

  if (loading) return <Loader screen />;
  if (!data || error) {
    const title =
      errorStatus === 403
        ? t("shared.table.accessDenied")
        : t("shared.table.somethingWentWrong");
    const message =
      errorStatus === 403
        ? t("shared.table.permissionDenied")
        : errorMessage;

    return (
      <div
        className={cn(
          "h-36 flex flex-col items-center justify-center",
          className,
        )}
      >
        <h2 className="text-lg font-semibold">{title}</h2>
        <p className="text-sm text-muted-foreground">{message}</p>
      </div>
    );
  }

  return (
    <div
      className={cn("h-full flex flex-col justify-between gap-2", className)}
    >
      <div className="flex flex-col gap-2">
        <ControlBar>
          <ControlBar.Section align="start" priority={1}>
            {buttons}
          </ControlBar.Section>
          <ControlBar.Section align="end" priority={2}>
            {enableSearch && (
              <GlobalFilter
                globalFilter={state.globalFilter}
                onGlobalFilterChange={onGlobalFilterChange}
                columns={prepared.columns}
                translations={translations}
              />
            )}
          </ControlBar.Section>
        </ControlBar>
        <div className={cn("rounded-md")}>
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => {
                    if (header.column.columnDef.hidden) return null;
                    const canSort = header.column.columnDef.sortable;
                    const isSorted = header.column.getIsSorted();
                    return (
                      <TableHead
                        key={header.id}
                        className={header.column.columnDef.meta?.width}
                        onClick={
                          canSort
                            ? () =>
                                onSortingChange([
                                  {
                                    id: header.column.id,
                                    desc: isSorted === "asc",
                                  },
                                ])
                            : undefined
                        }
                        style={{ cursor: canSort ? "pointer" : "default" }}
                      >
                        <div className="flex items-center justify-between">
                          {flexRender(
                            header.column.columnDef.header,
                            header.getContext(),
                          )}
                          {canSort && (
                            <span>
                              {isSorted === "asc" ? (
                                <ArrowDownNarrowWide className="w-4 text-muted-foreground ml-1" />
                              ) : isSorted === "desc" ? (
                                <ArrowUpWideNarrow className="w-4 text-muted-foreground ml-1" />
                              ) : (
                                <ArrowUpDown className="w-4 text-muted-foreground ml-1" />
                              )}
                            </span>
                          )}
                        </div>
                      </TableHead>
                    );
                  })}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {table.getRowModel().rows?.length ? (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    data-state={row.getIsSelected() && "selected"}
                  >
                    {row.getVisibleCells().map((cell) => {
                      if (cell.column.columnDef.hidden) return null;
                      return (
                        <TableCell
                          key={cell.id}
                          className={cell.column.columnDef.width}
                        >
                          {flexRender(
                            cell.column.columnDef.cell,
                            cell.getContext(),
                          )}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell
                    colSpan={prepared.columns.length}
                    className="h-36 text-center"
                  >
                    <h2 className="text-lg font-semibold">
                      {t("shared.table.noResults")}
                    </h2>
                    <p className="text-sm text-muted-foreground">
                      {t("shared.table.noDataForFilter")}
                    </p>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>
      {data.totalPages > 1 && (
        <TablePagination
          pageIndex={state.pagination.pageIndex}
          pageCount={data.totalPages}
          onPageChange={(pageIndex) => {
            onPaginationChange({
              pageIndex,
              pageSize: state.pagination.pageSize,
            });
          }}
        />
      )}
    </div>
  );
}

function GlobalFilter({
  globalFilter,
  onGlobalFilterChange,
  columns,
  translations,
}) {
  const { t } = useTranslation();
  const filterableColumns = columns.filter((column) => column?.filterable);
  return (
    <Input
      type="text"
      value={globalFilter || ""}
      onChange={(e) => onGlobalFilterChange(e.target.value)}
      placeholder={translations?.SEARCH_PLACEHOLDER || t("shared.table.search")}
      className="w-72"
      prepend={
        <span className="text-muted-foreground">
          <Search className="w-4 h-4" />
        </span>
      }
      append={
        <TooltipSimple
          content={
            <div className="flex flex-col">
              <div className="flex flex-col">
                <span className="text-sm font-medium">
                  {t("shared.table.filterableColumns")}
                </span>
              </div>
              <p>{t("shared.table.filterByColumns")}</p>
              <div>
                {filterableColumns.map((column) => (
                  <span
                    key={`search-help-${column.accessorKey}`}
                    className="text-xs"
                  >
                    {column.header.innerText}{" "}
                  </span>
                ))}
              </div>
            </div>
          }
        >
          <CircleHelp className="w-4 h-4 text-muted-foreground cursor-help" />
        </TooltipSimple>
      }
    />
  );
}

function TablePagination({ pageIndex, pageCount, onPageChange }) {
  // 1) build a set of pages to show (0-based)
  const pagesToShow = new Set([
    0, // first
    pageCount - 1, // last
    pageIndex, // current
    pageIndex - 1, // one before
    pageIndex + 1, // one after
  ]);

  // 2) filter out invalid pages (<0 or >=pageCount) and sort
  const pages = Array.from(pagesToShow)
    .filter((p) => p >= 0 && p < pageCount)
    .sort((a, b) => a - b);

  return (
    <Pagination>
      <PaginationContent>
        {/* Prev button */}
        <PaginationItem>
          <PaginationPrevious
            onClick={() => onPageChange(Math.max(pageIndex - 1, 0))}
            disabled={pageIndex === 0}
          />
        </PaginationItem>

        {/* Page buttons with ellipses */}
        {pages.map((p, idx) => {
          const prev = pages[idx - 1];
          const gap = prev != null ? p - prev : 1;

          return (
            <React.Fragment key={p}>
              {/* insert ellipsis if there's a gap > 1 */}
              {gap > 1 && (
                <PaginationItem>
                  <PaginationEllipsis />
                </PaginationItem>
              )}

              {/* the actual page button */}
              <PaginationItem>
                <PaginationLink
                  isActive={p === pageIndex}
                  onClick={() => onPageChange(p)}
                >
                  {p + 1}
                </PaginationLink>
              </PaginationItem>
            </React.Fragment>
          );
        })}

        {/* Next button */}
        <PaginationItem>
          <PaginationNext
            onClick={() => onPageChange(Math.min(pageIndex + 1, pageCount - 1))}
            disabled={pageIndex + 1 >= pageCount}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  );
}
