import React, { createContext, useContext, useState, useCallback } from "react";

const BreadcrumbContext = createContext({
  labels: {},
  setLabel: () => {},
});

export function useBreadcrumb() {
  return useContext(BreadcrumbContext);
}

export function BreadcrumbProvider({ children }) {
  const [labels, setLabels] = useState({});

  const setLabel = useCallback((key, label) => {
    setLabels((prev) =>
      prev[key] === label ? prev : { ...prev, [key]: label }
    );
  }, []);

  return (
    <BreadcrumbContext.Provider value={{ labels, setLabel }}>
      {children}
    </BreadcrumbContext.Provider>
  );
}
