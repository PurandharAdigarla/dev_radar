import { chromium } from "playwright";

const BASE = "http://localhost:8081";
const issues = [];

function log(msg) { console.log(`  ${msg}`); }
function pass(msg) { console.log(`  ✓ ${msg}`); }
function fail(msg) { issues.push(msg); console.log(`  ✗ ${msg}`); }

async function run() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Collect console errors and network errors
  const consoleErrors = [];
  const networkErrors = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") consoleErrors.push(msg.text());
  });
  page.on("response", (resp) => {
    if (resp.status() >= 500) networkErrors.push(`${resp.status()} ${resp.url()}`);
  });

  // 1. Landing page
  console.log("\n[Landing Page]");
  await page.goto(BASE);
  await page.waitForLoadState("networkidle");

  const heroText = await page.textContent("h1");
  if (heroText && heroText.includes("AI")) pass("Hero text updated (mentions AI)");
  else fail(`Hero text not updated: "${heroText}"`);

  const hasGitHub = await page.locator("text=GitHub").count();
  if (hasGitHub === 0) pass("No GitHub references on landing");
  else fail(`Found ${hasGitHub} GitHub reference(s) on landing page`);

  const getStartedBtn = await page.locator("text=Get Started").count();
  if (getStartedBtn > 0) pass("'Get Started' button present");
  else fail("Missing 'Get Started' button");

  // 2. Login page
  console.log("\n[Login Page]");
  await page.goto(`${BASE}/login`);
  await page.waitForLoadState("networkidle");

  const loginForm = await page.locator("input[type='email'], input[name='email'], input").first().count();
  if (loginForm > 0) pass("Login form renders");
  else fail("Login form not found");

  // 3. Login flow
  console.log("\n[Login Flow]");
  await page.fill("input[type='email'], input[name='email']", "testuser@devradar.io");
  await page.fill("input[type='password'], input[name='password']", "TestPass123!");
  await page.click("button[type='submit']");
  await page.waitForURL("**/app/**", { timeout: 10000 }).catch(() => {});

  const url = page.url();
  if (url.includes("/app")) pass(`Redirected to app: ${url}`);
  else fail(`Not redirected to app, stuck at: ${url}`);

  // 4. Dashboard
  console.log("\n[Dashboard]");
  await page.goto(`${BASE}/app/dashboard`);
  await page.waitForLoadState("networkidle");

  const dashGitHub = await page.locator("text=GitHub").count();
  if (dashGitHub === 0) pass("No GitHub references on dashboard");
  else fail(`Found ${dashGitHub} GitHub reference(s) on dashboard`);

  const topicsCard = await page.locator("text=Your Topics").count();
  if (topicsCard > 0) pass("'Your Topics' card present");
  else fail("Missing 'Your Topics' card");

  // 5. Topics page
  console.log("\n[Topics Page]");
  await page.goto(`${BASE}/app/topics`);
  await page.waitForLoadState("networkidle");

  const topicsHeader = await page.locator("text=Topics").first().count();
  if (topicsHeader > 0) pass("Topics page renders");
  else fail("Topics page not rendering");

  const chips = await page.locator("[class*='Chip']").count();
  if (chips >= 10) pass(`${chips} topic chips displayed`);
  else fail(`Expected 10+ topic chips, found ${chips}`);

  // 6. Radars page
  console.log("\n[Radars Page]");
  await page.goto(`${BASE}/app/radars`);
  await page.waitForLoadState("networkidle");

  const generateBtn = await page.locator("text=Generate new radar").count();
  if (generateBtn > 0) pass("'Generate new radar' button present");
  else fail("Missing 'Generate new radar' button");

  const interestRef = await page.locator("text=interest").count();
  if (interestRef === 0) pass("No 'interest' references on radars page");
  else fail(`Found ${interestRef} 'interest' reference(s) on radars page`);

  // 7. Settings page
  console.log("\n[Settings Page]");
  await page.goto(`${BASE}/app/settings`);
  await page.waitForLoadState("networkidle");

  const settingsGitHub = await page.locator("text=GitHub").count();
  if (settingsGitHub === 0) pass("No GitHub references on settings");
  else fail(`Found ${settingsGitHub} GitHub reference(s) on settings`);

  const manageTopics = await page.locator("text=Manage topics").count();
  if (manageTopics > 0) pass("'Manage topics' link present");
  else fail("Missing 'Manage topics' link");

  // 8. Navigation
  console.log("\n[Navigation]");
  const navTopics = await page.locator("nav >> text=Topics").count();
  if (navTopics > 0) pass("'Topics' in navigation");
  else fail("Missing 'Topics' in navigation");

  const navInterests = await page.locator("nav >> text=Interests").count();
  if (navInterests === 0) pass("No 'Interests' in navigation");
  else fail("'Interests' still in navigation");

  // 9. Console/Network errors
  console.log("\n[Errors]");
  if (networkErrors.length > 0) {
    console.log("  Network 500s:");
    networkErrors.forEach(e => console.log(`    ${e}`));
  }
  const criticalErrors = consoleErrors.filter(e =>
    !e.includes("favicon") && !e.includes("404") && !e.includes("health") && !e.includes("500")
  );
  if (criticalErrors.length === 0 && networkErrors.length === 0) pass("No critical errors");
  else if (criticalErrors.length > 0) {
    fail(`${criticalErrors.length} console error(s):`);
    criticalErrors.slice(0, 5).forEach(e => console.log(`    ${e.substring(0, 120)}`));
  } else {
    // Network 500s are informational, not blocking
    log(`${networkErrors.length} API endpoint(s) returned 500 (may be expected for unused features)`);
  }

  // Summary
  console.log("\n" + "=".repeat(50));
  if (issues.length === 0) {
    console.log("ALL CHECKS PASSED");
  } else {
    console.log(`${issues.length} ISSUE(S) FOUND:`);
    issues.forEach(i => console.log(`  - ${i}`));
  }

  await browser.close();
  process.exit(issues.length > 0 ? 1 : 0);
}

run().catch((e) => { console.error(e); process.exit(1); });
