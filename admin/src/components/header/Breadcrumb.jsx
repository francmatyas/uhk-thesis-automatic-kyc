import React from "react";
import { useLocation, matchRoutes, useSearchParams } from "react-router";
import { routes } from "@/router/routes";
import { useBreadcrumb } from "@/contexts/BreadcrumbContext";

import {
  Breadcrumb as BreadcrumbCore,
  BreadcrumbList,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbSeparator,
  BreadcrumbPage,
} from "@/components/ui/breadcrumb";

export function Breadcrumb() {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const matches = matchRoutes(routes, location);

  if (!matches) return null;

  matches.shift();
  if (matches[matches.length - 1].route.index) {
    matches.pop();
  }

  const { labels } = useBreadcrumb();

  function getLabel(match) {
    const { breadcrumb } = match.route;
    if (typeof breadcrumb === "object") {
      return labels[`${breadcrumb.key}:${match.params[breadcrumb.param]}`];
    }
    return breadcrumb;
  }

  function getPath(match) {
    const { preserveSearchParams } = match.route;
    let path = match.pathnameBase;
    
    if (preserveSearchParams && preserveSearchParams.length > 0) {
      const paramsToPreserve = new URLSearchParams();
      preserveSearchParams.forEach(param => {
        const value = searchParams.get(param);
        if (value) {
          paramsToPreserve.append(param, value);
        }
      });
      
      const queryString = paramsToPreserve.toString();
      if (queryString) {
        path = `${path}?${queryString}`;
      }
    }
    
    return path;
  }

  // Filter out any routes without a breadcrumb definition so we don't render blanks
  const crumbMatches = matches.filter((m) => !!m.route.breadcrumb);

  if (!crumbMatches.length) return null;

  return (
    <BreadcrumbCore>
      <BreadcrumbList>
        {crumbMatches.map((match, idx) => {
          const isLast = idx === crumbMatches.length - 1;
          return (
            <React.Fragment key={match.pathnameBase + idx}>
              <BreadcrumbItem>
                {!isLast ? (
                  <BreadcrumbLink to={getPath(match)}>
                    {getLabel(match)}
                  </BreadcrumbLink>
                ) : (
                  <BreadcrumbPage>{getLabel(match)}</BreadcrumbPage>
                )}
              </BreadcrumbItem>
              {!isLast && <BreadcrumbSeparator />}
            </React.Fragment>
          );
        })}
      </BreadcrumbList>
    </BreadcrumbCore>
  );
}
