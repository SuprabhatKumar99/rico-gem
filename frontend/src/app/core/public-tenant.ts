/**
 * Resolves the tenant for anonymous public Explore requests.
 *
 * Local development:
 *   http://localhost:4200/explore?tenant=testorg
 *
 * Production:
 *   https://testorg.ricozknow.com/explore
 *
 * Priority: explicit ?tenant=... first, then the first hostname label.
 */
export function resolvePublicTenant(): string {
  const params = new URLSearchParams(window.location.search);
  const queryTenant = params.get("tenant")?.trim();

  if (queryTenant) {
    return queryTenant;
  }

  const hostname = window.location.hostname.toLowerCase();

  if (hostname === "localhost" || hostname === "127.0.0.1" || hostname.endsWith(".run.app")) {
    return "demo";
  }

  const parts = hostname.split(".");

  // testorg.ricozknow.com -> testorg
  if (parts.length >= 3) {
    return parts[0];
  }

  return "demo";
}
