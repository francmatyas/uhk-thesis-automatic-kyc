import providerMenuConfig from "../../modules/provider/_providerMenuConfig";
import tenantMenuConfig from "../../modules/tenant/_tenantMenuConfig";

export default function _menuConfig({ scope, tenantSlug }) {
  if (scope === "tenant" && tenantSlug) {
    return tenantMenuConfig({ tenantSlug });
  }

  return providerMenuConfig();
}
