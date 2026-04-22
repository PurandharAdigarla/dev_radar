import { useCallback } from "react";
import { useDispatch, useSelector } from "react-redux";
import type { RootState, AppDispatch } from "../store";
import { loginSucceeded, loggedOut, setUser } from "./authSlice";
import { authApi, useLoginMutation, useRegisterMutation } from "../api/authApi";
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
      // Backend login returns only tokens; fetch the user profile so the shell
      // can render display name / email without a second page load.
      const me = await dispatch(authApi.endpoints.me.initiate()).unwrap();
      dispatch(loginSucceeded({ accessToken: res.accessToken, user: me }));
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
    _setUser: (u: Parameters<typeof setUser>[0]) => dispatch(setUser(u)),
  };
}
