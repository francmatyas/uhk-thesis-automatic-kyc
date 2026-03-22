import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { useLocation, useSearchParams } from 'react-router';
import { supportsGroups, getModuleConfig } from '@/lib/groups/moduleConfigs';

const SecondarySidebarContext = createContext();

// Cookie helpers
const COOKIE_NAME = 'secondary-sidebar-collapsed';

const setCookie = (name, value, days = 365) => {
  const expires = new Date();
  expires.setTime(expires.getTime() + days * 24 * 60 * 60 * 1000);
  document.cookie = `${name}=${value};expires=${expires.toUTCString()};path=/`;
};

const getCookie = (name) => {
  const nameEQ = name + '=';
  const ca = document.cookie.split(';');
  for (let i = 0; i < ca.length; i++) {
    let c = ca[i];
    while (c.charAt(0) === ' ') c = c.substring(1, c.length);
    if (c.indexOf(nameEQ) === 0) return c.substring(nameEQ.length, c.length);
  }
  return null;
};

export function useSecondarySidebar() {
  const context = useContext(SecondarySidebarContext);
  if (!context) {
    throw new Error('useSecondarySidebar must be used within a SecondarySidebarProvider');
  }
  return context;
}

export function SecondarySidebarProvider({ children }) {
  const [isVisible, setIsVisible] = useState(false);
  // Initialize collapsed state from cookie
  const [isCollapsed, setIsCollapsed] = useState(() => {
    if (typeof document !== 'undefined') {
      const savedState = getCookie(COOKIE_NAME);
      return savedState === 'true';
    }
    return false;
  });
  const [moduleKey, setModuleKey] = useState(null);
  const [prefetchedModules, setPrefetchedModules] = useState(new Set());
  const [hasInitialized, setHasInitialized] = useState(false);
  const location = useLocation();
  const [searchParams] = useSearchParams();

  // Auto-show sidebar on initial load if there's a group parameter
  useEffect(() => {
    if (!hasInitialized) {
      const currentPath = location.pathname;
      const isSupported = supportsGroups(currentPath);
      //const hasGroupParam = searchParams.get('group');
      
      if (isSupported) {
        const moduleConfig = getModuleConfig(currentPath);
        if (moduleConfig) {
          // Add a small delay to prevent jarring loading effect
          setTimeout(() => {
            setModuleKey(moduleConfig.moduleKey);
            setIsVisible(true);
          }, 200);
        }
      }
      setHasInitialized(true);
    }
  }, [location.pathname, searchParams, hasInitialized]);

  // Auto-hide sidebar when navigating to unsupported routes
  useEffect(() => {
    if (!hasInitialized) return; // Skip during initial load
    
    const currentPath = location.pathname;
    const isSupported = supportsGroups(currentPath);
    
    if (!isSupported && isVisible) {
      setIsVisible(false);
      setModuleKey(null);
    }
  }, [location.pathname, isVisible, hasInitialized]);

  const showSidebar = useCallback((module) => {
    setModuleKey(module);
    setIsVisible(true);
    // Don't change collapsed state - preserve user preference
  }, []);

  const hideSidebar = useCallback(() => {
    setIsVisible(false);
    setModuleKey(null);
    // Don't reset collapsed state - preserve user preference
  }, []);

  const toggleSidebar = useCallback((module, forceShow = false) => {
    if (isVisible && moduleKey === module && !forceShow) {
      hideSidebar();
    } else {
      showSidebar(module);
    }
  }, [isVisible, moduleKey, showSidebar, hideSidebar]);

  const toggleCollapse = useCallback(() => {
    setIsCollapsed(prev => {
      const newState = !prev;
      // Save to cookie
      setCookie(COOKIE_NAME, newState.toString());
      return newState;
    });
  }, []);

  const markAsPrefetched = useCallback((module) => {
    setPrefetchedModules(prev => new Set([...prev, module]));
  }, []);

  const isPrefetched = useCallback((module) => {
    return prefetchedModules.has(module);
  }, [prefetchedModules]);

  return (
    <SecondarySidebarContext.Provider
      value={{
        isVisible,
        isCollapsed,
        moduleKey,
        showSidebar,
        hideSidebar,
        toggleSidebar,
        toggleCollapse,
        markAsPrefetched,
        isPrefetched,
      }}
    >
      {children}
    </SecondarySidebarContext.Provider>
  );
}
