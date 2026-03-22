import { stripScopePrefix } from "@/router/scope";

// Configuration for modules that support secondary sidebar with groups
export const MODULE_CONFIGS = {
  '/simulations': {
    moduleKey: 'simulations',
    hasGroups: true,
    displayName: 'Simulations',
  },
  // Add more modules here as needed
  // '/risk-management-tpls': {
  //   moduleKey: 'risk-management-tpls',
  //   hasGroups: true,
  //   displayName: 'Risk Management Templates',
  // },
  // '/market-tickers': {
  //   moduleKey: 'market-tickers',
  //   hasGroups: true,
  //   displayName: 'Market Tickers',
  // },
};

// Helper function to get module config by path
export function getModuleConfig(path) {
  const normalizedPath = stripScopePrefix(path);
  return Object.entries(MODULE_CONFIGS).find(([configPath]) => 
    normalizedPath === configPath || normalizedPath.startsWith(configPath + '/')
  )?.[1] || null;
}

// Helper function to check if a path supports groups
export function supportsGroups(path) {
  const config = getModuleConfig(path);
  return config?.hasGroups || false;
}

// Helper function to get module path from module key
export function getModulePath(moduleKey) {
  const entry = Object.entries(MODULE_CONFIGS).find(([, config]) => 
    config.moduleKey === moduleKey
  );
  return entry?.[0] || `/${moduleKey}`;
}
