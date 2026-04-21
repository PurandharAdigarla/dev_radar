// Dev Radar — main app. Theme tokens, Tweaks panel, and the design canvas
// that surfaces the 4 screens side-by-side plus a live interactive artboard.

// ─── Tokens (tweakable) ─────────────────────────────────────────────────
const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "#c15f3c",
  "accentHover": "#a84e2f",
  "background": "#faf9f7",
  "serifAccent": false,
  "defaultScreen": "landing"
}/*EDITMODE-END*/;

// Static tokens (not tweakable — we want the brief's palette locked except
// for the ones the designer explicitly wants to try).
const DR_TOKENS = {
  surface: '#ffffff',
  textPrimary: '#2d2a26',
  textSecondary: '#6b655e',
  divider: '#e8e4df',
  error: '#b3261e',
  success: '#2d7a3e',
};

function applyTokens(tweaks) {
  const r = document.documentElement;
  r.style.setProperty('--dr-background', tweaks.background);
  r.style.setProperty('--dr-accent', tweaks.accent);
  r.style.setProperty('--dr-accent-hover', tweaks.accentHover);
  r.style.setProperty('--dr-surface', DR_TOKENS.surface);
  r.style.setProperty('--dr-text-primary', DR_TOKENS.textPrimary);
  r.style.setProperty('--dr-text-secondary', DR_TOKENS.textSecondary);
  r.style.setProperty('--dr-divider', DR_TOKENS.divider);
  r.style.setProperty('--dr-error', DR_TOKENS.error);
  r.style.setProperty('--dr-success', DR_TOKENS.success);
}

// ─── Edit-mode bridge ───────────────────────────────────────────────────
function useTweaks() {
  const [tweaks, setTweaks] = React.useState(TWEAK_DEFAULTS);
  const [editMode, setEditMode] = React.useState(false);

  React.useEffect(() => {
    applyTokens(tweaks);
  }, [tweaks]);

  React.useEffect(() => {
    const onMsg = (e) => {
      const d = e.data || {};
      if (d.type === '__activate_edit_mode') setEditMode(true);
      else if (d.type === '__deactivate_edit_mode') setEditMode(false);
    };
    window.addEventListener('message', onMsg);
    // announce AFTER listener registered
    window.parent.postMessage({ type: '__edit_mode_available' }, '*');
    return () => window.removeEventListener('message', onMsg);
  }, []);

  const patch = React.useCallback((partial) => {
    setTweaks((t) => {
      const next = { ...t, ...partial };
      window.parent.postMessage({ type: '__edit_mode_set_keys', edits: partial }, '*');
      return next;
    });
  }, []);

  return { tweaks, editMode, patch };
}

// ─── Tweaks panel ───────────────────────────────────────────────────────
function TweaksPanel({ tweaks, patch, onClose }) {
  const swatches = [
    { label: 'Terracotta', accent: '#c15f3c', accentHover: '#a84e2f' },
    { label: 'Ember',      accent: '#b85632', accentHover: '#96421f' },
    { label: 'Ink',        accent: '#2d2a26', accentHover: '#000000' },
    { label: 'Moss',       accent: '#5a7a4a', accentHover: '#47613a' },
    { label: 'Indigo',     accent: '#4a5a8a', accentHover: '#36456f' },
  ];
  const bgs = [
    { label: 'Warm', value: '#faf9f7' },
    { label: 'Paper', value: '#f6f2ea' },
    { label: 'Pure', value: '#ffffff' },
  ];

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 20, right: 20,
        zIndex: 1000,
        width: 280,
        background: '#ffffff',
        border: '1px solid #e8e4df',
        borderRadius: 12,
        boxShadow: '0 4px 24px rgba(45,42,38,0.12), 0 1px 2px rgba(45,42,38,0.04)',
        padding: 16,
        fontFamily: "'Inter', -apple-system, system-ui, sans-serif",
        color: '#2d2a26',
        fontSize: 13,
      }}
      onClick={(e) => e.stopPropagation()}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        <div style={{ fontWeight: 600, fontSize: 13, letterSpacing: 0 }}>Tweaks</div>
        <button onClick={onClose} style={{
          background: 'transparent', border: 'none', cursor: 'pointer',
          color: '#6b655e', fontSize: 18, lineHeight: 1, padding: 0, width: 20, height: 20,
        }}>×</button>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6b655e', marginBottom: 8 }}>Accent</div>
          <div style={{ display: 'flex', gap: 6 }}>
            {swatches.map((s) => {
              const active = tweaks.accent === s.accent;
              return (
                <button key={s.label} title={s.label}
                  onClick={() => patch({ accent: s.accent, accentHover: s.accentHover })}
                  style={{
                    width: 28, height: 28, borderRadius: 999,
                    background: s.accent,
                    border: active ? '2px solid #2d2a26' : '2px solid transparent',
                    cursor: 'pointer',
                    padding: 0,
                    boxShadow: active ? 'none' : 'inset 0 0 0 1px rgba(0,0,0,0.06)',
                  }} />
              );
            })}
          </div>
        </div>

        <div>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6b655e', marginBottom: 8 }}>Background</div>
          <div style={{ display: 'flex', gap: 6 }}>
            {bgs.map((b) => {
              const active = tweaks.background === b.value;
              return (
                <button key={b.label}
                  onClick={() => patch({ background: b.value })}
                  style={{
                    padding: '6px 10px', fontSize: 12,
                    background: active ? '#2d2a26' : 'transparent',
                    color: active ? '#fff' : '#2d2a26',
                    border: '1px solid ' + (active ? '#2d2a26' : '#e8e4df'),
                    borderRadius: 6, cursor: 'pointer',
                    fontFamily: 'inherit',
                  }}>{b.label}</button>
              );
            })}
          </div>
        </div>

        <div>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6b655e', marginBottom: 8 }}>Landing headline</div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
            <input type="checkbox"
              checked={tweaks.serifAccent}
              onChange={(e) => patch({ serifAccent: e.target.checked })}
              style={{ accentColor: tweaks.accent }} />
            <span>Italic serif accent on "what you care about"</span>
          </label>
        </div>

        <div>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.08em', color: '#6b655e', marginBottom: 8 }}>Flow starts on</div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {['landing', 'register', 'login', 'app'].map((k) => {
              const active = tweaks.defaultScreen === k;
              return (
                <button key={k}
                  onClick={() => patch({ defaultScreen: k })}
                  style={{
                    padding: '6px 10px', fontSize: 12,
                    background: active ? '#2d2a26' : 'transparent',
                    color: active ? '#fff' : '#2d2a26',
                    border: '1px solid ' + (active ? '#2d2a26' : '#e8e4df'),
                    borderRadius: 6, cursor: 'pointer',
                    fontFamily: 'inherit', textTransform: 'capitalize',
                  }}>{k}</button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Interactive flow (one screen at a time, with real nav) ─────────────
function InteractiveFlow({ tweaks, initial = 'landing', compact = false }) {
  const [screen, setScreen] = React.useState(initial);
  const [user, setUser] = React.useState({ name: 'Alice', email: 'alice@devradar.app' });

  React.useEffect(() => { setScreen(initial); }, [initial]);

  const navigate = (key, data) => {
    if (data && data.name) setUser({ ...user, name: data.name, email: (data.name.toLowerCase() + '@devradar.app') });
    setScreen(key);
  };

  const shared = {
    onNavigate: navigate,
    accent: tweaks.accent,
    accentHover: tweaks.accentHover,
    serifAccent: tweaks.serifAccent,
  };

  let content;
  if (screen === 'landing') content = <LandingScreen {...shared} />;
  else if (screen === 'register') content = <RegisterScreen {...shared} />;
  else if (screen === 'login') content = <LoginScreen {...shared} />;
  else content = <AppShellScreen {...shared} userName={user.name} userEmail={user.email} compact={compact} />;

  return (
    <div data-screen-label={`live-${screen}`}
      style={{ width: '100%', height: '100%', overflow: 'auto', background: 'var(--dr-background)' }}>
      {content}
    </div>
  );
}

// ─── Full-bleed top bar for interactive mode ────────────────────────────
function ModeBar({ mode, setMode, editMode, onToggleTweaks, showTweaksPanel }) {
  return (
    <div style={{
      position: 'fixed', top: 12, left: '50%', transform: 'translateX(-50%)',
      zIndex: 800,
      background: 'rgba(255,255,255,0.94)',
      backdropFilter: 'blur(12px)',
      border: '1px solid #e8e4df',
      borderRadius: 999,
      padding: 4,
      display: 'flex', gap: 2, alignItems: 'center',
      boxShadow: '0 1px 2px rgba(45,42,38,0.04), 0 1px 1px rgba(45,42,38,0.03)',
      fontFamily: "'Inter', system-ui, sans-serif",
      fontSize: 13,
    }}>
      {['canvas', 'flow'].map((m) => (
        <button key={m}
          onClick={() => setMode(m)}
          style={{
            padding: '6px 14px',
            borderRadius: 999,
            border: 'none',
            background: mode === m ? '#2d2a26' : 'transparent',
            color: mode === m ? '#fff' : '#2d2a26',
            cursor: 'pointer',
            fontFamily: 'inherit',
            fontSize: 13,
            fontWeight: 500,
            textTransform: 'capitalize',
          }}>{m === 'canvas' ? 'All screens' : 'Interactive flow'}</button>
      ))}
      {editMode && (
        <>
          <div style={{ width: 1, height: 16, background: '#e8e4df', margin: '0 4px' }} />
          <button
            onClick={onToggleTweaks}
            style={{
              padding: '6px 14px',
              borderRadius: 999,
              border: 'none',
              background: showTweaksPanel ? '#2d2a26' : 'transparent',
              color: showTweaksPanel ? '#fff' : '#2d2a26',
              cursor: 'pointer',
              fontFamily: 'inherit',
              fontSize: 13,
              fontWeight: 500,
            }}>Tweaks</button>
        </>
      )}
    </div>
  );
}

// ─── Canvas of 4 screens ────────────────────────────────────────────────
function ScreensCanvas({ tweaks }) {
  const shared = {
    accent: tweaks.accent,
    accentHover: tweaks.accentHover,
    serifAccent: tweaks.serifAccent,
  };

  // Artboard sizes chosen to feel like real device frames:
  // - Desktop screens: 1280×800
  // - Auth screens: 720×800 (narrower, centered forms live happily here)
  const DESKTOP = { w: 1280, h: 800 };
  const AUTH = { w: 720, h: 800 };

  return (
    <DesignCanvas>
      <DCSection id="public" title="Public" subtitle="Unauthenticated. Shared token set, same warm off-white.">
        <DCArtboard id="landing" label="Landing" width={DESKTOP.w} height={DESKTOP.h}>
          <div data-screen-label="01 Landing" style={{ width: '100%', height: '100%', overflow: 'hidden' }}>
            <LandingScreen {...shared} onNavigate={() => {}} />
          </div>
        </DCArtboard>
        <DCArtboard id="register" label="Register" width={AUTH.w} height={AUTH.h}>
          <div data-screen-label="02 Register" style={{ width: '100%', height: '100%', overflow: 'hidden' }}>
            <RegisterScreen {...shared} onNavigate={() => {}} />
          </div>
        </DCArtboard>
        <DCArtboard id="register-error" label="Register · error" width={AUTH.w} height={AUTH.h}>
          <div data-screen-label="02b Register error" style={{ width: '100%', height: '100%', overflow: 'hidden' }}>
            <RegisterScreen {...shared} onNavigate={() => {}}
              showError="email-taken"
              initialValues={{ email: 'taken@example.com', name: 'Alice Chen', pass: 'hunter2hunter2' }} />
          </div>
        </DCArtboard>
        <DCArtboard id="login" label="Login" width={AUTH.w} height={AUTH.h}>
          <div data-screen-label="03 Login" style={{ width: '100%', height: '100%', overflow: 'hidden' }}>
            <LoginScreen {...shared} onNavigate={() => {}} />
          </div>
        </DCArtboard>
      </DCSection>

      <DCSection id="app" title="App" subtitle="Post-auth. Sidebar is primary nav; content column stays 720px.">
        <DCArtboard id="appshell" label="AppShell · Welcome" width={DESKTOP.w} height={DESKTOP.h}>
          <div data-screen-label="04 AppShell" style={{ width: '100%', height: '100%', overflow: 'hidden' }}>
            <AppShellScreen {...shared} compact userName="Alice" userEmail="alice@devradar.app" onNavigate={() => {}} />
          </div>
        </DCArtboard>
      </DCSection>

      <DCPostIt top={120} left={60} rotate={-2} width={210}>
        Four screens, one token set. Try toggling accent + serif headline in the Tweaks panel (bottom right when Tweaks mode is on in the toolbar).
      </DCPostIt>

      <DCPostIt top={940} left={60} rotate={1.5} width={220}>
        AppShell is deliberately empty — real content (radars, proposals) lands in Plan 8. The emptiness is the design.
      </DCPostIt>
    </DesignCanvas>
  );
}

// ─── Root ───────────────────────────────────────────────────────────────
function App() {
  const { tweaks, editMode, patch } = useTweaks();
  const [mode, setMode] = React.useState(() => localStorage.getItem('dr-mode') || 'canvas');
  const [showTweaksPanel, setShowTweaksPanel] = React.useState(true);

  React.useEffect(() => { localStorage.setItem('dr-mode', mode); }, [mode]);

  return (
    <>
      <ModeBar
        mode={mode}
        setMode={setMode}
        editMode={editMode}
        onToggleTweaks={() => setShowTweaksPanel((v) => !v)}
        showTweaksPanel={showTweaksPanel}
      />
      {mode === 'canvas'
        ? <ScreensCanvas tweaks={tweaks} />
        : <InteractiveFlow tweaks={tweaks} initial={tweaks.defaultScreen} />
      }
      {editMode && showTweaksPanel && (
        <TweaksPanel tweaks={tweaks} patch={patch} onClose={() => setShowTweaksPanel(false)} />
      )}
    </>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
