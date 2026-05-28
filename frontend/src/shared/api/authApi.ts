import { ApiError } from "./http";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

function normalizeRedirectTarget(location: string) {
  const url = new URL(location, window.location.origin);
  return `${url.pathname}${url.search}`;
}

export async function login(username: string, password: string): Promise<string> {
  const formData = new FormData();
  formData.append("username", username);
  formData.append("password", password);

  const response = await fetch(`${API_BASE_URL}/api/login`, {
    method: "POST",
    credentials: "include",
    body: formData,
  });

  if (!response.ok) {
    let message = "Email o contrasena incorrectos.";
    try {
      const body = await response.json() as { message?: string };
      message = body.message || message;
    } catch {
      // Keep the generic login message when the server response is not JSON.
    }
    throw new ApiError("Login failed", response.status, message);
  }

  const body = await response.json() as { redirect?: string };
  return normalizeRedirectTarget(body.redirect || "/admin/dashboard");
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/logout`, {
    method: "POST",
    credentials: "include",
  });
}
