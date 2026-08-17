export type Role = "OWNER" | "MEMBER" | "VIEWER";

export type ResourceStatus = "ACTIVE" | "DISABLED" | "ARCHIVED";

export type LimitPolicyKind = "RATE" | "QUOTA";

export type QuotaPeriod = "DAY" | "MONTH";

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

export interface Consumer {
  id: string;
  workspaceId: string;
  name: string;
  externalReference: string | null;
  status: ResourceStatus;
  applicationCount: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ConsumerApplication {
  id: string;
  workspaceId: string;
  consumerId: string;
  name: string;
  status: ResourceStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ApiCredential {
  id: string;
  workspaceId: string;
  applicationId: string;
  publicId: string;
  displayPrefix: string;
  displayLastFour: string;
  environment: string;
  status: ResourceStatus;
  expiresAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateCredentialResponse {
  id: string;
  workspaceId: string;
  applicationId: string;
  publicId: string;
  rawKey: string;
  displayPrefix: string;
  displayLastFour: string;
  environment: string;
  status: ResourceStatus;
  expiresAt: string | null;
  createdAt: string;
  version: number;
}

export interface LimitPolicy {
  id: string;
  workspaceId: string;
  planId: string;
  routeId: string | null;
  kind: LimitPolicyKind;
  capacity?: number;
  refillTokens?: number;
  refillPeriodSeconds?: number;
  quotaLimit?: number;
  quotaPeriod?: QuotaPeriod;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Plan {
  id: string;
  workspaceId: string;
  productId: string;
  name: string;
  slug: string;
  status: ResourceStatus;
  policies: LimitPolicy[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Subscription {
  id: string;
  workspaceId: string;
  applicationId: string;
  productId: string;
  planId: string;
  status: ResourceStatus;
  effectiveFrom: string;
  effectiveTo: string | null;
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
