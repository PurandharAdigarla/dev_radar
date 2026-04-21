const ACCESS_KEY = "devradar.accessToken";
const REFRESH_KEY = "devradar.refreshToken";

function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // QuotaExceededError or storage disabled — silently no-op
  }
}

function safeRemove(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    // ignore
  }
}

export const tokenStorage = {
  getAccess: () => safeGet(ACCESS_KEY),
  setAccess: (v: string) => safeSet(ACCESS_KEY, v),
  getRefresh: () => safeGet(REFRESH_KEY),
  setRefresh: (v: string) => safeSet(REFRESH_KEY, v),
  clear: () => {
    safeRemove(ACCESS_KEY);
    safeRemove(REFRESH_KEY);
  },
};
