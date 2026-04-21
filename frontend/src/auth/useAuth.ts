import { useCallback } from "react";
import { useDispatch, useSelector } from "react-redux";
import type { RootState, AppDispatch } from "../store";
import { loginSucceeded, loggedOut } from "./authSlice";
import { useLoginMutation, useRegisterMutation } from "../api/authApi";
import { tokenStorage } from "./tokenStorage";

export function useAuth() {
  const dispatch = useDispatch<AppDispatch>();
  const { accessToken, user } = useSelector((s: RootState) => s.auth);
  const [loginMut] = useLoginMutation();
  const [registerMut] = useRegisterMutation();

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await loginMut({ email, password }).unwrap();
      tokenStorage.setAccess(res.accessToken);
      tokenStorage.setRefresh(res.refreshToken);
      dispatch(loginSucceeded({ accessToken: res.accessToken, user: res.user }));
      return res;
    },
    [loginMut, dispatch],
  );

  const register = useCallback(
    async (email: string, password: string, displayName: string) => {
      return await registerMut({ email, password, displayName }).unwrap();
    },
    [registerMut],
  );

  const logout = useCallback(() => {
    tokenStorage.clear();
    dispatch(loggedOut());
  }, [dispatch]);

  return {
    isAuthenticated: accessToken !== null,
    user,
    accessToken,
    login,
    register,
    logout,
  };
}
