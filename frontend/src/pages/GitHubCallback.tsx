import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useDispatch } from "react-redux";
import type { AppDispatch } from "../store";
import { tokenStorage } from "../auth/tokenStorage";
import { loginSucceeded } from "../auth/authSlice";
import { authApi } from "../api/authApi";
import { interestApi } from "../api/interestApi";

export function GitHubCallback() {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    const hash = window.location.hash.startsWith("#")
      ? window.location.hash.slice(1)
      : "";
    const params = new URLSearchParams(hash);
    const accessToken = params.get("accessToken");

    if (!accessToken) {
      navigate("/login?error=oauth_missing_token", { replace: true });
      return;
    }

    // Read query params before clearing the URL.
    const isLink = new URLSearchParams(window.location.search).get("from") === "link";

    tokenStorage.setAccess(accessToken);
    // OAuth flow issues access-token-only; no refresh token. Session lives
    // as long as the access token; re-auth via GitHub on expiry.
    tokenStorage.setRefresh("");

    // Clear the hash so the token doesn't linger in the address bar.
    window.history.replaceState({}, "", "/auth/github/complete");

    (async () => {
      try {
        const me = await dispatch(authApi.endpoints.me.initiate()).unwrap();
        dispatch(loginSucceeded({ accessToken, user: me }));
        if (isLink) {
          navigate("/app/settings", { replace: true });
        } else {
          const interests = await dispatch(interestApi.endpoints.getMyInterests.initiate()).unwrap();
          navigate(interests.length === 0 ? "/app/onboarding" : "/app", { replace: true });
        }
      } catch {
        tokenStorage.clear();
        navigate("/login?error=oauth_me_failed", { replace: true });
      }
    })();
  }, [dispatch, navigate]);

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "background.default",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Typography variant="body1" color="text.secondary">
        Signing you in…
      </Typography>
    </Box>
  );
}
