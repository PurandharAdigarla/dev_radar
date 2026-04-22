import { useEffect, useMemo, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";
import { TextField } from "../components/TextField";
import { Alert } from "../components/Alert";
import { PageHeader } from "../components/PageHeader";
import { TagChip } from "../components/TagChip";
import {
  useGetMyInterestsQuery,
  useListTagsQuery,
  useSetMyInterestsMutation,
} from "../api/interestApi";
import type { InterestCategory, InterestTag } from "../api/types";

const CATEGORY_ORDER: InterestCategory[] = [
  "language",
  "framework",
  "database",
  "devops",
  "security",
  "other",
];

const CATEGORY_LABEL: Record<InterestCategory, string> = {
  language: "Languages",
  framework: "Frameworks",
  database: "Databases",
  devops: "DevOps & cloud",
  security: "Security",
  other: "Other",
};

export function InterestPickerPage() {
  const { data: tagsPage, isLoading: tagsLoading } = useListTagsQuery({});
  const { data: myInterests } = useGetMyInterestsQuery();
  const [setMyInterests, saveState] = useSetMyInterestsMutation();

  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    if (myInterests && !hydrated) {
      setSelected(new Set(myInterests.map((t) => t.slug)));
      setHydrated(true);
    }
  }, [myInterests, hydrated]);

  const tags = tagsPage?.content ?? [];
  const filteredTags = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return tags;
    return tags.filter(
      (t) => t.displayName.toLowerCase().includes(q) || t.slug.includes(q),
    );
  }, [tags, query]);

  const byCategory = useMemo(() => {
    const map = new Map<string, InterestTag[]>();
    for (const t of filteredTags) {
      const key = t.category ?? "other";
      const arr = map.get(key) ?? [];
      arr.push(t);
      map.set(key, arr);
    }
    return map;
  }, [filteredTags]);

  const serverSlugs = useMemo(
    () => new Set((myInterests ?? []).map((t) => t.slug)),
    [myInterests],
  );
  const dirty = useMemo(() => {
    if (selected.size !== serverSlugs.size) return true;
    for (const s of selected) if (!serverSlugs.has(s)) return true;
    return false;
  }, [selected, serverSlugs]);

  function toggle(slug: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(slug)) next.delete(slug);
      else next.add(slug);
      return next;
    });
  }

  async function onSave() {
    await setMyInterests({ tagSlugs: Array.from(selected) }).unwrap().catch(() => {
      /* error shown via saveState.isError */
    });
  }

  // Spec §5.4 — warn the browser before unloading when there are unsaved changes.
  // React Router v7 in-SPA navigation blocking is deferred; beforeunload covers
  // the common case (tab close, hard reload, external navigation).
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      // Modern browsers ignore the returnValue text but require it to be set.
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader title="Interests" sub="Pick topics your weekly radar should cover." />

      <Box
        sx={{
          display: "flex",
          gap: 2,
          alignItems: "center",
          mb: 5,
          flexWrap: "wrap",
        }}
      >
        <Box sx={{ flex: 1, minWidth: 280, maxWidth: 420 }}>
          <TextField
            label="Search"
            placeholder="Search tags…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </Box>
        <Typography
          sx={{
            fontSize: 14,
            color: "text.secondary",
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {`${selected.size} selected`}
        </Typography>
        <Box sx={{ flex: 1 }} />
        <Button onClick={onSave} disabled={!dirty || saveState.isLoading}>
          {saveState.isLoading ? "Saving…" : "Save"}
        </Button>
      </Box>

      {saveState.isError && (
        <Box sx={{ mb: 4 }}>
          <Alert severity="error">Couldn't save your interests. Try again.</Alert>
        </Box>
      )}

      {tagsLoading && (
        <Typography variant="body2" color="text.secondary">Loading tags…</Typography>
      )}

      {!tagsLoading && filteredTags.length === 0 && (
        <Box sx={{ py: 5, textAlign: "center", color: "text.secondary", fontSize: 14 }}>
          No tags match “{query}”.
        </Box>
      )}

      {CATEGORY_ORDER.map((cat) => {
        const catTags = byCategory.get(cat);
        if (!catTags || catTags.length === 0) return null;
        return (
          <Box key={cat} sx={{ mb: 5 }}>
            <Typography
              variant="overline"
              color="text.secondary"
              sx={{ display: "block", mb: 2 }}
            >
              {CATEGORY_LABEL[cat]}
            </Typography>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              {catTags.map((t) => (
                <TagChip
                  key={t.slug}
                  label={t.displayName}
                  selected={selected.has(t.slug)}
                  onToggle={() => toggle(t.slug)}
                />
              ))}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
