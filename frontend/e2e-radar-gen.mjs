import { chromium } from "playwright";

const BASE = "http://localhost:8081";

function log(msg) { console.log(`  ${msg}`); }
function pass(msg) { console.log(`  ✓ ${msg}`); }
function fail(msg) { console.log(`  ✗ ${msg}`); }

async function run() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Collect console messages for debugging
  page.on("console", (msg) => {
    if (msg.type() === "error" && !msg.text().includes("favicon")) {
      log(`[console.error] ${msg.text().substring(0, 120)}`);
    }
  });

  // ── 1. Login ──
  console.log("\n[Login]");
  await page.goto(`${BASE}/login`);
  await page.waitForLoadState("networkidle");
  await page.fill("input[type='email'], input[name='email']", "testuser@devradar.io");
  await page.fill("input[type='password'], input[name='password']", "TestPass123!");
  await page.click("button[type='submit']");
  await page.waitForURL("**/app/**", { timeout: 10000 }).catch(() => {});
  const url = page.url();
  if (url.includes("/app")) pass(`Logged in → ${url}`);
  else { fail(`Login failed, at: ${url}`); await browser.close(); process.exit(1); }

  // Extract auth token from localStorage
  const token = await page.evaluate(() => localStorage.getItem("devradar.accessToken"));

  // ── 2. Check topics exist ──
  console.log("\n[Topics]");
  await page.goto(`${BASE}/app/topics`);
  await page.waitForLoadState("networkidle");
  const chipCount = await page.locator("[class*='Chip']").count();
  if (chipCount > 0) pass(`${chipCount} topics configured`);
  else { fail("No topics configured — can't generate radar"); await browser.close(); process.exit(1); }

  // ── 3. Navigate to radars and generate ──
  console.log("\n[Generate Radar]");
  await page.goto(`${BASE}/app/radars`);
  await page.waitForLoadState("networkidle");

  const genBtn = page.locator("text=Generate new radar");
  if (await genBtn.count() === 0) { fail("No 'Generate new radar' button"); await browser.close(); process.exit(1); }

  await genBtn.click();
  pass("Clicked 'Generate new radar'");

  // Wait for navigation to radar detail page
  await page.waitForURL("**/app/radars/*", { timeout: 10000 }).catch(() => {});
  const radarUrl = page.url();
  const radarIdMatch = radarUrl.match(/\/radars\/(\d+)/);
  if (radarIdMatch) pass(`Navigated to radar detail: ${radarUrl}`);
  else { fail(`Not on radar detail page: ${radarUrl}`); await browser.close(); process.exit(1); }

  const radarId = radarIdMatch[1];

  // ── 4. Monitor SSE stream for progress events ──
  console.log("\n[SSE Stream Monitoring]");
  log("Connecting to SSE stream...");
  log(`Token found: ${token ? "yes (" + token.substring(0, 20) + "...)" : "no"}`);

  const sseUrl = `${BASE}/api/radars/${radarId}/stream${token ? `?token=${encodeURIComponent(token)}` : ""}`;

  const sseResults = await page.evaluate(async (url) => {
    const collected = {
      started: false,
      progressEvents: [],
      themeEvents: [],
      complete: null,
      failed: null,
      errors: [],
    };

    return new Promise((res) => {
      const es = new EventSource(url);
      const timeout = setTimeout(() => {
        collected.errors.push("timeout after 3 minutes");
        es.close();
        res(collected);
      }, 180000);

      es.addEventListener("radar.started", () => {
        collected.started = true;
      });

      es.addEventListener("agent.progress", (ev) => {
        const data = JSON.parse(ev.data);
        collected.progressEvents.push({
          agent: data.agent,
          phase: data.phase,
          queryCount: (data.searchQueries || []).length,
          resultCount: (data.searchResults || []).length,
          firstQuery: (data.searchQueries || [])[0] || "",
          sampleResults: (data.searchResults || []).slice(0, 3).map(r => ({
            title: r.title, domain: r.domain
          })),
        });
      });

      es.addEventListener("theme.complete", (ev) => {
        const data = JSON.parse(ev.data);
        collected.themeEvents.push({ title: data.title });
      });

      es.addEventListener("radar.complete", (ev) => {
        const data = JSON.parse(ev.data);
        collected.complete = { generationMs: data.generationMs, tokenCount: data.tokenCount };
        clearTimeout(timeout);
        es.close();
        res(collected);
      });

      es.addEventListener("radar.failed", (ev) => {
        const data = JSON.parse(ev.data);
        collected.failed = { errorCode: data.errorCode, message: data.message || data.errorMessage };
        clearTimeout(timeout);
        es.close();
        res(collected);
      });

      es.onerror = () => {
        if (es.readyState === EventSource.CLOSED) {
          collected.errors.push("EventSource closed unexpectedly");
          clearTimeout(timeout);
          res(collected);
        }
      };
    });
  }, sseUrl);

  // ── 5. Report SSE results ──
  console.log("\n[SSE Results]");

  if (sseResults.started) pass("radar.started event received");
  else log("radar.started not received (may have been missed)");

  const progressCount = sseResults.progressEvents.length;
  if (progressCount > 0) {
    pass(`${progressCount} agent.progress events received`);

    const researchEvents = sseResults.progressEvents.filter(e => e.phase === "research");
    const repoEvents = sseResults.progressEvents.filter(e => e.phase === "repo_discovery");

    if (researchEvents.length > 0) pass(`  ${researchEvents.length} research progress events`);
    if (repoEvents.length > 0) pass(`  ${repoEvents.length} repo_discovery progress events`);

    // Show sample queries
    const withQueries = sseResults.progressEvents.filter(e => e.queryCount > 0);
    if (withQueries.length > 0) {
      pass(`  ${withQueries.length} events had search queries`);
      console.log(`    Sample: "${withQueries[0].firstQuery}"`);
    } else {
      fail("  No events had search queries");
    }

    // Show sample results
    const withResults = sseResults.progressEvents.filter(e => e.resultCount > 0);
    if (withResults.length > 0) {
      pass(`  ${withResults.length} events had search results`);
      const sample = withResults[0].sampleResults[0];
      if (sample) console.log(`    Sample: "${sample.title}" (${sample.domain})`);
    } else {
      fail("  No events had search results (grounding data may be empty)");
    }
  } else {
    fail("No agent.progress events received");
  }

  if (sseResults.complete) {
    pass(`radar.complete: ${(sseResults.complete.generationMs / 1000).toFixed(1)}s, ${sseResults.complete.tokenCount} tokens`);
  } else if (sseResults.failed) {
    fail(`radar.failed: ${sseResults.failed.errorCode} — ${sseResults.failed.message}`);
  } else {
    fail("Neither complete nor failed event received (timeout?)");
  }

  // ── 6. Verify radar detail page after generation ──
  console.log("\n[Radar Detail Page]");
  await page.reload();
  await page.waitForLoadState("networkidle");

  const themeCards = await page.locator("[class*='ThemeCard'], [data-testid='theme-card']").count();
  // Also try by structure - look for theme titles
  const themeHeadings = await page.locator("h3, h2").count();
  const pageText = await page.textContent("body");

  const hasThemes = pageText && (pageText.includes("themes") || pageText.includes("theme"));
  if (hasThemes) pass("Radar detail page shows theme content");

  const hasTokenInfo = pageText && pageText.includes("tokens");
  if (hasTokenInfo) pass("Shows token count");

  const hasTimeInfo = pageText && /\d+\.\d+s/.test(pageText);
  if (hasTimeInfo) pass("Shows generation time");

  // ── Summary ──
  console.log("\n" + "=".repeat(50));
  if (sseResults.complete && progressCount > 0) {
    console.log("RADAR GENERATION TEST PASSED");
    console.log(`  Progress events: ${progressCount}`);
    console.log(`  Generation time: ${(sseResults.complete.generationMs / 1000).toFixed(1)}s`);
    console.log(`  Tokens used: ${sseResults.complete.tokenCount}`);
  } else if (sseResults.complete) {
    console.log("RADAR GENERATED (no progress events — grounding metadata may be empty)");
  } else {
    console.log("RADAR GENERATION FAILED OR TIMED OUT");
  }

  await browser.close();
  process.exit(sseResults.failed ? 1 : 0);
}

run().catch((e) => { console.error(e); process.exit(1); });
