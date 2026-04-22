export interface User {
  id: number;
  email: string;
  displayName: string;
  active?: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}
