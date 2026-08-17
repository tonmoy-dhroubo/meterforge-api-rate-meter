export type Role = "OWNER" | "MEMBER" | "VIEWER";

export type ResourceStatus = "ACTIVE" | "DISABLED" | "ARCHIVED";

export interface UserSummary {
  id: string;
  email: string;
  status: string;
}

export interface WorkspaceMembershipSummary {
  workspaceId: string;
  workspaceName: string;
  workspaceSlug: string;
  role: Role;
}

export interface MeResponse {
  user: UserSummary;
  memberships: WorkspaceMembershipSummary[];
}

export interface Workspace {
  id: string;
  name: string;
  slug: string;
  status: ResourceStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ApiProduct {
  id: string;
  workspaceId: string;
  name: string;
  slug: string;
  upstreamBaseUrl: string;
  gatewayBasePath: string;
  status: ResourceStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ApiRoute {
  id: string;
  workspaceId: string;
  productId: string;
  httpMethod: string;
  pathPattern: string;
  upstreamPath: string | null;
  costUnits: number;
  priority: number;
  status: ResourceStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ProblemDetail {
  title: string;
  status: number;
  detail: string;
  code: string;
  requestId: string;
  fieldErrors?: Record<string, string>;
}
