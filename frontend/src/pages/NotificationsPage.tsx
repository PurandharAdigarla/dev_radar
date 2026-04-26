import { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import FormControlLabel from "@mui/material/FormControlLabel";
import Switch from "@mui/material/Switch";
import { PageHeader } from "../components/PageHeader";
import { Button } from "../components/Button";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import {
  useGetNotificationPrefsQuery,
  useUpdateNotificationPrefsMutation,
  useSendTestEmailMutation,
} from "../api/notificationApi";

const DAY_LABELS: Record<number, string> = {
  1: "Monday",
  2: "Tuesday",
  3: "Wednesday",
  4: "Thursday",
  5: "Friday",
  6: "Saturday",
  7: "Sunday",
};

function formatHour(h: number): string {
  if (h === 0) return "12:00 AM UTC";
  if (h < 12) return `${h}:00 AM UTC`;
  if (h === 12) return "12:00 PM UTC";
  return `${h - 12}:00 PM UTC`;
}

export function NotificationsPage() {
  const { data: prefs, isLoading } = useGetNotificationPrefsQuery();
  const [updatePrefs, updateState] = useUpdateNotificationPrefsMutation();
  const [sendTest, testState] = useSendTestEmailMutation();

  const [emailEnabled, setEmailEnabled] = useState(false);
  const [emailAddress, setEmailAddress] = useState("");
  const [dayOfWeek, setDayOfWeek] = useState(1);
  const [hourUtc, setHourUtc] = useState(9);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    if (prefs && !hydrated) {
      setEmailEnabled(prefs.emailEnabled);
      setEmailAddress(prefs.emailAddress ?? "");
      setDayOfWeek(prefs.digestDayOfWeek);
      setHourUtc(prefs.digestHourUtc);
      setHydrated(true);
    }
  }, [prefs, hydrated]);

  async function onSave() {
    await updatePrefs({
      emailEnabled,
      emailAddress: emailAddress || null,
      digestDayOfWeek: dayOfWeek,
      digestHourUtc: hourUtc,
    }).unwrap().catch(() => {});
  }

  async function onSendTest() {
    await sendTest().unwrap().catch(() => {});
  }

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader title="Notifications" sub="Configure how and when you receive your radar digest." />

      {isLoading && (
        <Typography variant="body2" color="text.secondary">Loading preferences...</Typography>
      )}

      {!isLoading && (
        <>
          <Box sx={{ mb: 5 }}>
            <Typography
              component="h2"
              sx={{ fontSize: "1.125rem", fontWeight: 500, color: "text.primary", mb: 2 }}
            >
              Email Digest
            </Typography>
            <Typography sx={{ fontSize: "0.9375rem", color: "text.secondary", mb: 3, lineHeight: "24px" }}>
              Get a weekly email summary of your latest radar, delivered on the day and time you choose.
            </Typography>

            <Box sx={{ mb: 3 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={emailEnabled}
                    onChange={(e) => setEmailEnabled(e.target.checked)}
                  />
                }
                label={
                  <Typography sx={{ fontSize: "0.9375rem", fontWeight: 500, color: "text.primary" }}>
                    Enable email digest
                  </Typography>
                }
              />
            </Box>

            {emailEnabled && (
              <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
                <Box sx={{ maxWidth: 420 }}>
                  <TextField
                    label="Email address"
                    placeholder="Leave blank to use your account email"
                    value={emailAddress}
                    onChange={(e) => setEmailAddress(e.target.value)}
                  />
                </Box>

                <Box sx={{ display: "flex", gap: 3, flexWrap: "wrap" }}>
                  <Box sx={{ minWidth: 200 }}>
                    <Typography
                      sx={{ fontSize: "0.8125rem", fontWeight: 500, color: "text.primary", mb: 1 }}
                    >
                      Day of week
                    </Typography>
                    <Select
                      value={dayOfWeek}
                      onChange={(e) => setDayOfWeek(Number(e.target.value))}
                      size="small"
                      sx={{ minWidth: 180 }}
                    >
                      {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                        <MenuItem key={d} value={d}>{DAY_LABELS[d]}</MenuItem>
                      ))}
                    </Select>
                  </Box>

                  <Box sx={{ minWidth: 200 }}>
                    <Typography
                      sx={{ fontSize: "0.8125rem", fontWeight: 500, color: "text.primary", mb: 1 }}
                    >
                      Time (UTC)
                    </Typography>
                    <Select
                      value={hourUtc}
                      onChange={(e) => setHourUtc(Number(e.target.value))}
                      size="small"
                      sx={{ minWidth: 180 }}
                    >
                      {Array.from({ length: 24 }, (_, i) => i).map((h) => (
                        <MenuItem key={h} value={h}>{formatHour(h)}</MenuItem>
                      ))}
                    </Select>
                  </Box>
                </Box>
              </Box>
            )}
          </Box>

          {updateState.isError && (
            <Box sx={{ mb: 3 }}>
              <Alert severity="error">Failed to save preferences. Please try again.</Alert>
            </Box>
          )}
          {updateState.isSuccess && (
            <Box sx={{ mb: 3 }}>
              <Alert severity="success">Preferences saved.</Alert>
            </Box>
          )}
          {testState.isError && (
            <Box sx={{ mb: 3 }}>
              <Alert severity="error">Failed to send test email. Check your email configuration.</Alert>
            </Box>
          )}
          {testState.isSuccess && (
            <Box sx={{ mb: 3 }}>
              <Alert severity="success">Test email sent! Check your inbox.</Alert>
            </Box>
          )}

          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Button onClick={onSave} disabled={updateState.isLoading}>
              {updateState.isLoading ? "Saving..." : "Save"}
            </Button>
            {emailEnabled && (
              <Button variant="outlined" onClick={onSendTest} disabled={testState.isLoading}>
                {testState.isLoading ? "Sending..." : "Send test email"}
              </Button>
            )}
          </Box>
        </>
      )}
    </Box>
  );
}
