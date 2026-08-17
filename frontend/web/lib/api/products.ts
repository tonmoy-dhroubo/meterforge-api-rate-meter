import { apiClient } from "./client";
import { ApiProduct, ApiRoute } from "./types";

export interface CreateProductPayload {
  name: string;
  slug: string;
  upstreamBaseUrl: string;
  gatewayBasePath: string;
}

export interface CreateRoutePayload {
  httpMethod: string;
  pathPattern: string;
  upstreamPath?: string | null;
  costUnits?: number;
  priority?: number;
}

export interface UpdateRoutePayload {
  upstreamPath?: string | null;
  costUnits?: number;
  priority?: number;
}

export async function getProducts(workspaceId: string): Promise<ApiProduct[]> {
  return apiClient<ApiProduct[]>(`/api/v1/workspaces/${workspaceId}/products`);
}

export async function getProduct(
  workspaceId: string,
  productId: string
): Promise<ApiProduct> {
  return apiClient<ApiProduct>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}`
  );
}

export async function createProduct(
  workspaceId: string,
  payload: CreateProductPayload
): Promise<ApiProduct> {
  return apiClient<ApiProduct>(`/api/v1/workspaces/${workspaceId}/products`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function activateProduct(
  workspaceId: string,
  productId: string
): Promise<ApiProduct> {
  return apiClient<ApiProduct>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}/activate`,
    {
      method: "POST",
    }
  );
}

export async function disableProduct(
  workspaceId: string,
  productId: string
): Promise<ApiProduct> {
  return apiClient<ApiProduct>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}/disable`,
    {
      method: "POST",
    }
  );
}

export async function getRoutes(
  workspaceId: string,
  productId: string
): Promise<ApiRoute[]> {
  return apiClient<ApiRoute[]>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}/routes`
  );
}

export async function createRoute(
  workspaceId: string,
  productId: string,
  payload: CreateRoutePayload
): Promise<ApiRoute> {
  return apiClient<ApiRoute>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}/routes`,
    {
      method: "POST",
      body: JSON.stringify(payload),
    }
  );
}

export async function updateRoute(
  workspaceId: string,
  productId: string,
  routeId: string,
  payload: UpdateRoutePayload
): Promise<ApiRoute> {
  return apiClient<ApiRoute>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}/routes/${routeId}`,
    {
      method: "PATCH",
      body: JSON.stringify(payload),
    }
  );
}

export async function activateRoute(
  workspaceId: string,
  productId: string,
  routeId: string
): Promise<ApiRoute> {
  return apiClient<ApiRoute>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}/routes/${routeId}/activate`,
    {
      method: "POST",
    }
  );
}

export async function disableRoute(
  workspaceId: string,
  productId: string,
  routeId: string
): Promise<ApiRoute> {
  return apiClient<ApiRoute>(
    `/api/v1/workspaces/${workspaceId}/products/${productId}/routes/${routeId}/disable`,
    {
      method: "POST",
    }
  );
}
