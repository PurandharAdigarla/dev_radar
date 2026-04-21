// Dev Radar — four screen components.
// Pure presentational. `onNavigate(key)` is optional; when absent (static
// artboard use), clicks are no-ops. Error state is driven by props so the
// design-canvas can show error/clean side by side.

const DR_FONT_UI = "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";
const DR_FONT_MONO = "'JetBrains Mono', ui-monospace, 'SF Mono', Menlo, monospace";

// ─── Primitives ─────────────────────────────────────────────────────────

function DRButton({ variant = 'primary', fullWidth, children, onClick, accent, accentHover, type = 'button' }) {
  const [hover, setHover] = React.useState(false);
  const [focus, setFocus] = React.useState(false);
  const base = {
    fontFamily: DR_FONT_UI,
    fontSize: 15,
    fontWeight: 500,
    lineHeight: 1,
    padding: '12px 20px',
    borderRadius: 999, // pill
    border: variant === 'outlined' ? '1px solid var(--dr-divider)' : '1px solid transparent',
    cursor: 'pointer',
    transition: 'background 120ms ease, color 120ms ease, border-color 120ms ease, box-shadow 120ms ease',
    width: fullWidth ? '100%' : 'auto',
    outline: 'none',
    boxShadow: focus ? `0 0 0 2px ${accent}` : 'none',
    letterSpacing: 0,
  };
  const primary = {
    background: hover ? accentHover : accent,
    color: '#ffffff',
  };
  const outlined = {
    background: hover ? 'rgba(45,42,38,0.04)' : 'transparent',
    color: 'var(--dr-text-primary)',
    borderColor: 'var(--dr-divider)',
  };
  const ghost = {
    background: 'transparent',
    color: 'var(--dr-text-secondary)',
    padding: '6px 8px',
    borderRadius: 6,
    border: '1px solid transparent',
    fontSize: 14,
    boxShadow: 'none',
  };
  const style = {
    ...base,
    ...(variant === 'primary' ? primary : variant === 'outlined' ? outlined : ghost),
  };
  if (variant === 'ghost' && hover) style.color = 'var(--dr-text-primary)';
  return (
    <button
      type={type}
      style={style}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onFocus={() => setFocus(true)}
      onBlur={() => setFocus(false)}
    >
      {children}
    </button>
  );
}

function DRField({ label, value, onChange, type = 'text', autoComplete, helper, id }) {
  const [focus, setFocus] = React.useState(false);
  const fid = id || `f-${label.replace(/\s+/g, '-').toLowerCase()}`;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <label htmlFor={fid}
        style={{
          fontFamily: DR_FONT_UI, fontSize: 13, lineHeight: '20px',
          fontWeight: 500, color: 'var(--dr-text-primary)',
        }}>
        {label}
      </label>
      <input
        id={fid}
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(e) => onChange && onChange(e.target.value)}
        onFocus={() => setFocus(true)}
        onBlur={() => setFocus(false)}
        style={{
          fontFamily: DR_FONT_UI,
          fontSize: 15,
          lineHeight: '24px',
          color: 'var(--dr-text-primary)',
          background: 'var(--dr-surface)',
          border: '1px solid var(--dr-divider)',
          borderRadius: 8,
          padding: '10px 14px',
          outline: 'none',
          boxShadow: focus ? `0 0 0 2px var(--dr-accent)` : 'none',
          borderColor: focus ? 'var(--dr-accent)' : 'var(--dr-divider)',
          transition: 'border-color 120ms, box-shadow 120ms',
          width: '100%',
          boxSizing: 'border-box',
        }}
      />
      {helper && (
        <div style={{ fontFamily: DR_FONT_UI, fontSize: 13, lineHeight: '20px', color: 'var(--dr-text-secondary)' }}>
          {helper}
        </div>
      )}
    </div>
  );
}

function DRAlert({ children }) {
  return (
    <div role="alert" style={{
      fontFamily: DR_FONT_UI, fontSize: 14, lineHeight: '20px',
      color: 'var(--dr-error)',
      background: 'rgba(179,38,30,0.06)',
      border: '1px solid rgba(179,38,30,0.2)',
      borderRadius: 8,
      padding: '10px 14px',
      display: 'flex', gap: 10, alignItems: 'flex-start',
    }}>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: 2 }}>
        <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.4" />
        <path d="M8 4.5v4M8 11v.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
      <span>{children}</span>
    </div>
  );
}

// Overline: 11px uppercase letter-spaced
function DROverline({ children, style }) {
  return (
    <div style={{
      fontFamily: DR_FONT_UI,
      fontSize: 11, lineHeight: '16px',
      fontWeight: 500,
      letterSpacing: '0.08em',
      textTransform: 'uppercase',
      color: 'var(--dr-text-secondary)',
      ...style,
    }}>
      {children}
    </div>
  );
}

// ─── Screen 1: Landing ──────────────────────────────────────────────────

function LandingScreen({ onNavigate, accent, accentHover, serifAccent }) {
  return (
    <div style={{
      minHeight: '100%', width: '100%',
      background: 'var(--dr-background)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: '80px 32px',
      boxSizing: 'border-box',
      fontFamily: DR_FONT_UI,
    }}>
      <div style={{ maxWidth: 640, width: '100%', textAlign: 'left' }}>
        <DROverline style={{ marginBottom: 32 }}>Dev Radar</DROverline>
        <h1 style={{
          margin: 0,
          fontFamily: DR_FONT_UI,
          fontSize: 48,
          lineHeight: 1.15,
          fontWeight: 500,
          letterSpacing: '-0.02em',
          color: 'var(--dr-text-primary)',
          textWrap: 'balance',
        }}>
          A weekly brief for{' '}
          {serifAccent ? (
            <span style={{
              fontFamily: "'Source Serif Pro', 'Source Serif 4', Georgia, serif",
              fontStyle: 'italic',
              fontWeight: 400,
            }}>
              what you care about.
            </span>
          ) : 'what you care about.'}
        </h1>
        <p style={{
          marginTop: 24, marginBottom: 40,
          fontFamily: DR_FONT_UI,
          fontSize: 17, lineHeight: 1.6,
          color: 'var(--dr-text-secondary)',
          maxWidth: 560,
          textWrap: 'pretty',
        }}>
          Personalized radars synthesized from Hacker News, GitHub Trending, and security advisories,
          with citations you can trust.
        </p>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <DRButton variant="primary" accent={accent} accentHover={accentHover}
            onClick={() => onNavigate && onNavigate('register')}>
            Create account
          </DRButton>
          <DRButton variant="outlined" accent={accent} accentHover={accentHover}
            onClick={() => onNavigate && onNavigate('login')}>
            Sign in
          </DRButton>
        </div>
      </div>
    </div>
  );
}

// ─── Shared auth card ───────────────────────────────────────────────────

function AuthCard({ children }) {
  return (
    <div style={{
      minHeight: '100%', width: '100%',
      background: 'var(--dr-background)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: '80px 32px',
      boxSizing: 'border-box',
      fontFamily: DR_FONT_UI,
    }}>
      <div style={{ maxWidth: 400, width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 40 }}>
          <DROverline>Dev Radar</DROverline>
        </div>
        {children}
      </div>
    </div>
  );
}

// ─── Screen 2: Register ─────────────────────────────────────────────────

function RegisterScreen({ onNavigate, accent, accentHover, showError, initialValues }) {
  const [email, setEmail] = React.useState((initialValues && initialValues.email) || '');
  const [name, setName] = React.useState((initialValues && initialValues.name) || '');
  const [pass, setPass] = React.useState((initialValues && initialValues.pass) || '');
  const [error, setError] = React.useState(showError || null);

  const submit = (e) => {
    e && e.preventDefault();
    if (email.trim().toLowerCase() === 'taken@example.com') {
      setError('email-taken');
      return;
    }
    if (!email || !name || !pass) {
      setError('required');
      return;
    }
    setError(null);
    onNavigate && onNavigate('app', { name: name || 'Alice' });
  };

  return (
    <AuthCard>
      <h2 style={{
        margin: 0, marginBottom: 8,
        fontFamily: DR_FONT_UI, fontSize: 24, lineHeight: '32px',
        fontWeight: 500, color: 'var(--dr-text-primary)',
        letterSpacing: '-0.01em',
      }}>Create your account</h2>
      <p style={{
        margin: 0, marginBottom: 32,
        fontFamily: DR_FONT_UI, fontSize: 15, lineHeight: '24px',
        color: 'var(--dr-text-secondary)',
      }}>Start getting your weekly radar.</p>

      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
        {error === 'email-taken' && (
          <DRAlert>That email is already registered. <a
            onClick={(e) => { e.preventDefault(); onNavigate && onNavigate('login'); }}
            href="#signin"
            style={{ color: 'inherit', textDecoration: 'underline', cursor: 'pointer' }}>Sign in instead?</a></DRAlert>
        )}
        {error === 'required' && <DRAlert>Please fill in all fields.</DRAlert>}

        <DRField label="Email" type="email" autoComplete="email"
          value={email} onChange={setEmail} />
        <DRField label="Display name" autoComplete="nickname"
          value={name} onChange={setName}
          helper="Shown on your radar header. You can change this later." />
        <DRField label="Password" type="password" autoComplete="new-password"
          value={pass} onChange={setPass}
          helper="At least 8 characters." />

        <div style={{ marginTop: 4 }}>
          <DRButton variant="primary" fullWidth type="submit"
            accent={accent} accentHover={accentHover}>
            Create account
          </DRButton>
        </div>
      </form>

      <div style={{
        marginTop: 32, textAlign: 'center',
        fontFamily: DR_FONT_UI, fontSize: 14, lineHeight: '20px',
        color: 'var(--dr-text-secondary)',
      }}>
        Have an account?{' '}
        <a
          onClick={(e) => { e.preventDefault(); onNavigate && onNavigate('login'); }}
          href="#signin"
          style={{ color: 'var(--dr-text-primary)', textDecoration: 'underline', textUnderlineOffset: 3, cursor: 'pointer' }}
        >Sign in</a>
      </div>
    </AuthCard>
  );
}

// ─── Screen 3: Login ────────────────────────────────────────────────────

function LoginScreen({ onNavigate, accent, accentHover, showError }) {
  const [email, setEmail] = React.useState('');
  const [pass, setPass] = React.useState('');
  const [error, setError] = React.useState(showError || null);

  const submit = (e) => {
    e && e.preventDefault();
    if (email.trim().toLowerCase() === 'wrong@example.com') {
      setError('bad-credentials');
      return;
    }
    if (!email || !pass) {
      setError('required');
      return;
    }
    setError(null);
    const nameFromEmail = email.split('@')[0] || 'Alice';
    const displayName = nameFromEmail.charAt(0).toUpperCase() + nameFromEmail.slice(1);
    onNavigate && onNavigate('app', { name: displayName });
  };

  return (
    <AuthCard>
      <h2 style={{
        margin: 0, marginBottom: 8,
        fontFamily: DR_FONT_UI, fontSize: 24, lineHeight: '32px',
        fontWeight: 500, color: 'var(--dr-text-primary)',
        letterSpacing: '-0.01em',
      }}>Sign in</h2>
      <p style={{
        margin: 0, marginBottom: 32,
        fontFamily: DR_FONT_UI, fontSize: 15, lineHeight: '24px',
        color: 'var(--dr-text-secondary)',
      }}>Welcome back.</p>

      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
        {error === 'bad-credentials' && <DRAlert>Incorrect email or password.</DRAlert>}
        {error === 'required' && <DRAlert>Please fill in all fields.</DRAlert>}

        <DRField label="Email" type="email" autoComplete="email"
          value={email} onChange={setEmail} />
        <DRField label="Password" type="password" autoComplete="current-password"
          value={pass} onChange={setPass} />

        <div style={{ marginTop: 4 }}>
          <DRButton variant="primary" fullWidth type="submit"
            accent={accent} accentHover={accentHover}>
            Sign in
          </DRButton>
        </div>
      </form>

      <div style={{
        marginTop: 32, textAlign: 'center',
        fontFamily: DR_FONT_UI, fontSize: 14, lineHeight: '20px',
        color: 'var(--dr-text-secondary)',
      }}>
        New here?{' '}
        <a
          onClick={(e) => { e.preventDefault(); onNavigate && onNavigate('register'); }}
          href="#register"
          style={{ color: 'var(--dr-text-primary)', textDecoration: 'underline', textUnderlineOffset: 3, cursor: 'pointer' }}
        >Create an account</a>
      </div>
    </AuthCard>
  );
}

// ─── Screen 4: AppShell ─────────────────────────────────────────────────

function AppShellScreen({ onNavigate, accent, accentHover, userName = 'Alice', userEmail = 'alice@devradar.app', compact }) {
  const sidebarWidth = 240;
  return (
    <div style={{
      minHeight: '100%', width: '100%',
      background: 'var(--dr-background)',
      display: 'flex',
      fontFamily: DR_FONT_UI,
      color: 'var(--dr-text-primary)',
    }}>
      {/* Sidebar */}
      <aside style={{
        width: sidebarWidth,
        flexShrink: 0,
        borderRight: '1px solid var(--dr-divider)',
        padding: '32px 24px',
        display: 'flex', flexDirection: 'column',
        minHeight: compact ? 720 : '100vh',
        boxSizing: 'border-box',
      }}>
        <DROverline style={{ marginBottom: 40 }}>Dev Radar</DROverline>

        <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {[
            { key: 'radars', label: 'Radars' },
            { key: 'proposals', label: 'Proposals' },
            { key: 'settings', label: 'Settings' },
          ].map((item) => (
            <div key={item.key} style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '8px 10px',
              borderRadius: 6,
              color: 'var(--dr-text-secondary)',
              opacity: 0.6,
              cursor: 'not-allowed',
              fontSize: 15,
              lineHeight: '24px',
            }}>
              <span>{item.label}</span>
              <span style={{
                fontSize: 11, lineHeight: '16px',
                letterSpacing: '0.06em',
                textTransform: 'uppercase',
                color: 'var(--dr-text-secondary)',
                opacity: 0.8,
              }}>soon</span>
            </div>
          ))}
        </nav>

        <div style={{ flex: 1 }} />

        {/* User block */}
        <div style={{
          paddingTop: 20,
          borderTop: '1px solid var(--dr-divider)',
        }}>
          <div style={{
            fontSize: 14, lineHeight: '20px', fontWeight: 500,
            color: 'var(--dr-text-primary)',
          }}>{userName}</div>
          <div style={{
            fontSize: 13, lineHeight: '20px',
            color: 'var(--dr-text-secondary)',
            marginBottom: 12,
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>{userEmail}</div>
          <button
            onClick={() => onNavigate && onNavigate('landing')}
            style={{
              background: 'transparent',
              border: 'none',
              padding: 0,
              fontFamily: DR_FONT_UI,
              fontSize: 13, lineHeight: '20px',
              color: 'var(--dr-text-secondary)',
              cursor: 'pointer',
              textDecoration: 'underline',
              textUnderlineOffset: 3,
            }}
            onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--dr-text-primary)')}
            onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--dr-text-secondary)')}
          >Sign out</button>
        </div>
      </aside>

      {/* Main */}
      <main style={{
        flex: 1,
        padding: '80px 48px',
        display: 'flex',
        justifyContent: 'flex-start',
      }}>
        <div style={{ maxWidth: 720, width: '100%' }}>
          <h1 style={{
            margin: 0,
            fontFamily: DR_FONT_UI,
            fontSize: 32, lineHeight: '40px',
            fontWeight: 500,
            letterSpacing: '-0.01em',
            color: 'var(--dr-text-primary)',
          }}>Welcome, {userName}.</h1>
          <p style={{
            marginTop: 16, marginBottom: 0,
            fontFamily: DR_FONT_UI, fontSize: 15, lineHeight: '24px',
            color: 'var(--dr-text-secondary)',
            maxWidth: 560,
          }}>
            Your radars and proposals will appear here soon.
          </p>
        </div>
      </main>
    </div>
  );
}

Object.assign(window, {
  LandingScreen, RegisterScreen, LoginScreen, AppShellScreen,
  DRButton, DRField, DRAlert, DROverline,
  DR_FONT_UI, DR_FONT_MONO,
});
