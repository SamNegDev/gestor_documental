const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly details?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function buildApiError(response: Response, method: string, path: string): Promise<ApiError> {
  const contentType = response.headers.get("content-type") ?? "";
  let details: string | undefined;

  try {
    if (contentType.includes("application/json")) {
      const body = await response.json() as { message?: string; error?: string };
      details = body.message || body.error;
    } else {
      const text = await response.text();
      details = text.trim() || undefined;
    }
  } catch {
    details = undefined;
  }

  return new ApiError(`${method} ${path} failed with ${response.status}`, response.status, details);
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw await buildApiError(response, "GET", path);
  }

  return response.json() as Promise<T>;
}

async function apiRequest(path: string, init: RequestInit): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    ...init,
  });

  if (!response.ok) {
    throw await buildApiError(response, init.method ?? "REQUEST", path);
  }
}

export function apiPostForm(path: string, formData: FormData): Promise<void> {
  return apiRequest(path, {
    method: "POST",
    body: formData,
  });
}

export function apiPost(path: string): Promise<void> {
  return apiRequest(path, {
    method: "POST",
  });
}

export async function apiPostJson<T = void>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw await buildApiError(response, "POST", path);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function apiPutJson(path: string, body: unknown): Promise<void> {
  return apiRequest(path, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(body),
  });
}

export function apiPatchForm(path: string, formData: FormData): Promise<void> {
  return apiRequest(path, {
    method: "PATCH",
    body: formData,
  });
}

export function apiDelete(path: string): Promise<void> {
  return apiRequest(path, {
    method: "DELETE",
  });
}
