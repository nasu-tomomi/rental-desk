// すべてのAPIでセッションCookieを送信する。
export async function apiFetch(url, options = {}) {
  const response = await fetch(url, { ...options, credentials: "include" });
  if (response.status === 401 && !new URL(url, window.location.href).pathname.startsWith("/api/auth/")) {
    window.dispatchEvent(new Event("auth-expired"));
  }
  return response;
}
