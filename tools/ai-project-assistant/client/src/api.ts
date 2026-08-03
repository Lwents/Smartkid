import type { ApiResponse } from "@smartkid/shared";

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly details: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

export async function api<T>(
  endpoint: string,
  options: Omit<RequestInit, "body"> & { body?: BodyInit | Record<string, unknown> } = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  let body = options.body;
  if (body && typeof body === "object" && !(body instanceof FormData) && !(body instanceof Blob)) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(body);
  }
  const response = await fetch(`/api${endpoint}`, { ...options, body: body as BodyInit, headers });
  let result: ApiResponse<T>;
  try {
    result = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new ApiError("INVALID_RESPONSE", "Server trả về dữ liệu không hợp lệ");
  }
  if (!result.success) {
    throw new ApiError(result.error.code, result.error.message, result.error.details);
  }
  return result.data;
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Đã xảy ra lỗi không xác định";
}
