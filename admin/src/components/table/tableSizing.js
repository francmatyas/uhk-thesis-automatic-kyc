const DEFAULT_ROW_HEIGHT = 38; // px: simple single-line row
const DEFAULT_TABLE_RESERVE = 160; // px: header + controls + pagination

const TABLE_SIZING_BY_MODULE = {
  // Example:
  // permissions: { rowHeight: 48, tableReserve: 210 },
};

export function getTableSizing(moduleKey) {
  if (!moduleKey) {
    return {
      rowHeight: DEFAULT_ROW_HEIGHT,
      tableReserve: DEFAULT_TABLE_RESERVE,
    };
  }

  const sizing = TABLE_SIZING_BY_MODULE[moduleKey];

  return {
    rowHeight: sizing?.rowHeight ?? DEFAULT_ROW_HEIGHT,
    tableReserve: sizing?.tableReserve ?? DEFAULT_TABLE_RESERVE,
  };
}

