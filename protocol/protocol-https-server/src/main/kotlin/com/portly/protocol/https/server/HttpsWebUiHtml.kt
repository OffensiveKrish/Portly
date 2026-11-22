package com.portly.protocol.https.server

import java.util.Base64

internal object HttpsWebUiHtml {

    const val LOGO_PNG_BASE64: String =
        "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"

    val LOGO_PNG_BYTES: ByteArray by lazy {
        Base64.getDecoder().decode(LOGO_PNG_BASE64)
    }

    const val FORMS_CSS: String = """/* Portly Glassmorphism Stylesheet */"""

    fun loginPage(errorMessage: String? = null, lockedOutSeconds: Long = 0L): String = """
<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Portly — Browser Access Login</title>
    <link rel="icon" type="image/png" href="/assets/logo.png">
    <style>
        :root, [data-theme="dark"] {
            --bg-base: #080B11;
            --bg-glass-card: rgba(18, 26, 43, 0.68);
            --border-glass: rgba(255, 255, 255, 0.08);
            --border-glass-highlight: rgba(255, 255, 255, 0.16);
            --shadow-glass: 0 24px 48px -12px rgba(0, 0, 0, 0.6), inset 0 1px 1px 0 rgba(255, 255, 255, 0.12);
            --text-primary: #F8FAFC;
            --text-secondary: #CBD5E1;
            --text-muted: #94A3B8;
            --brand-primary: #FF5722;
            --brand-grad: linear-gradient(135deg, #FF6B4A 0%, #FF3D00 100%);
            --accent-teal: #00B4D8;
            --input-bg: rgba(15, 23, 42, 0.65);
            --input-border: rgba(255, 255, 255, 0.12);
            --input-focus: #FF6B4A;
            --glow-coral: rgba(255, 87, 34, 0.18);
            --glow-teal: rgba(0, 180, 216, 0.14);
            --glow-indigo: rgba(99, 102, 241, 0.10);
            --danger: #EF4444;
            --danger-bg: rgba(239, 68, 68, 0.16);
            --warning-bg: rgba(245, 158, 11, 0.16);
            --warning-border: rgba(245, 158, 11, 0.3);
            --warning-text: #FBBF24;
        }
        [data-theme="light"] {
            --bg-base: #F1F5F9;
            --bg-glass-card: rgba(255, 255, 255, 0.78);
            --border-glass: rgba(255, 255, 255, 0.85);
            --border-glass-highlight: rgba(255, 255, 255, 0.95);
            --shadow-glass: 0 20px 40px -10px rgba(15, 23, 42, 0.08), inset 0 1px 1px 0 rgba(255, 255, 255, 0.95);
            --text-primary: #0F172A;
            --text-secondary: #334155;
            --text-muted: #64748B;
            --input-bg: rgba(255, 255, 255, 0.9);
            --input-border: rgba(203, 213, 225, 0.8);
            --glow-coral: rgba(255, 107, 74, 0.12);
            --glow-teal: rgba(0, 180, 216, 0.10);
            --glow-indigo: rgba(99, 102, 241, 0.06);
            --danger-bg: rgba(239, 68, 68, 0.1);
            --warning-bg: rgba(245, 158, 11, 0.1);
            --warning-text: #D97706;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: var(--bg-base);
            background-image: 
                radial-gradient(circle at 85% 15%, var(--glow-coral) 0%, transparent 45%),
                radial-gradient(circle at 15% 85%, var(--glow-teal) 0%, transparent 45%),
                radial-gradient(circle at 50% 50%, var(--glow-indigo) 0%, transparent 55%);
            background-attachment: fixed;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
            color: var(--text-primary);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 24px;
        }
        .login-card {
            width: 100%;
            max-width: 440px;
            background: var(--bg-glass-card);
            backdrop-filter: blur(28px) saturate(180%);
            -webkit-backdrop-filter: blur(28px) saturate(180%);
            border: 1px solid var(--border-glass);
            border-radius: 24px;
            padding: 40px 36px;
            box-shadow: var(--shadow-glass);
            text-align: center;
            position: relative;
            overflow: hidden;
            transition: transform 0.25s ease, box-shadow 0.25s ease;
        }
        .login-card::before {
            content: "";
            position: absolute;
            top: 0; left: 0; right: 0; height: 1px;
            background: linear-gradient(90deg, transparent, var(--border-glass-highlight), transparent);
        }
        .logo-wrap {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 80px;
            height: 80px;
            border-radius: 22px;
            background: var(--brand-grad);
            box-shadow: 0 12px 28px rgba(255, 61, 0, 0.35);
            margin-bottom: 22px;
            position: relative;
            transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
        }
        .logo-wrap:hover {
            transform: scale(1.06) rotate(-2deg);
        }
        .brand-title {
            font-size: 26px;
            font-weight: 800;
            letter-spacing: -0.6px;
            margin-bottom: 6px;
            background: var(--brand-grad);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .brand-subtitle {
            font-size: 13px;
            color: var(--text-muted);
            margin-bottom: 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }
        .protocol-pill {
            font-size: 11px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.6px;
            padding: 3px 10px;
            border-radius: 20px;
            background: rgba(16, 185, 129, 0.15);
            border: 1px solid rgba(16, 185, 129, 0.3);
            color: #10B981;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .pulse-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #10B981;
            animation: pulseDot 2s infinite ease-in-out;
        }
        @keyframes pulseDot {
            0%, 100% { opacity: 1; transform: scale(1); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.6); }
            50% { opacity: 0.7; transform: scale(1.2); box-shadow: 0 0 0 5px rgba(16, 185, 129, 0); }
        }
        .alert-box {
            padding: 12px 16px;
            border-radius: 12px;
            font-size: 13px;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 10px;
            text-align: left;
            backdrop-filter: blur(12px);
        }
        .alert-error {
            background: var(--danger-bg);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #F87171;
        }
        .alert-warn {
            background: var(--warning-bg);
            border: 1px solid var(--warning-border);
            color: var(--warning-text);
        }
        .input-group {
            margin-bottom: 18px;
            text-align: left;
        }
        .input-label {
            display: block;
            font-size: 12px;
            font-weight: 600;
            margin-bottom: 7px;
            color: var(--text-secondary);
            letter-spacing: 0.2px;
        }
        .input-wrapper {
            position: relative;
            display: flex;
            align-items: center;
        }
        .input-field {
            width: 100%;
            padding: 12px 16px;
            background: var(--input-bg);
            border: 1px solid var(--input-border);
            border-radius: 12px;
            font-size: 14px;
            color: var(--text-primary);
            outline: none;
            transition: all 0.2s ease;
            backdrop-filter: blur(12px);
        }
        .input-field:focus {
            border-color: var(--input-focus);
            box-shadow: 0 0 0 3px rgba(255, 107, 74, 0.25);
        }
        .pw-toggle {
            position: absolute;
            right: 12px;
            background: none;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            font-size: 16px;
            padding: 4px;
            transition: color 0.15s;
        }
        .pw-toggle:hover { color: var(--text-primary); }
        .remember-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 24px;
            font-size: 12px;
            color: var(--text-secondary);
        }
        .remember-label {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            user-select: none;
        }
        .remember-label input[type="checkbox"] {
            accent-color: var(--brand-primary);
            width: 15px;
            height: 15px;
            cursor: pointer;
        }
        .submit-btn {
            width: 100%;
            padding: 13px 20px;
            background: var(--brand-grad);
            border: none;
            border-radius: 12px;
            color: #FFFFFF;
            font-size: 14px;
            font-weight: 700;
            letter-spacing: 0.3px;
            cursor: pointer;
            box-shadow: 0 6px 20px rgba(255, 61, 0, 0.35);
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }
        .submit-btn:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 10px 24px rgba(255, 61, 0, 0.45);
        }
        .submit-btn:active:not(:disabled) {
            transform: translateY(0);
        }
        .submit-btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
            transform: none;
        }
        .footer-note {
            margin-top: 26px;
            font-size: 11px;
            color: var(--text-muted);
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
        }
        .theme-pill-btn {
            position: absolute;
            top: 18px;
            right: 18px;
            background: var(--input-bg);
            border: 1px solid var(--border-glass);
            border-radius: 20px;
            padding: 5px 10px;
            font-size: 11px;
            color: var(--text-secondary);
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 4px;
            transition: all 0.15s;
        }
        .theme-pill-btn:hover {
            color: var(--text-primary);
            border-color: var(--brand-primary);
        }
        @supports not (backdrop-filter: blur(10px)) {
            .login-card { background: #131B2E !important; }
        }
        @media (prefers-reduced-motion: reduce) {
            * { animation-duration: 0.001ms !important; transition-duration: 0.001ms !important; }
        }
    </style>
</head>
<body>
    <div class="login-card">
        <button type="button" class="theme-pill-btn" id="themeToggleBtn" onclick="cycleTheme()" title="Switch Color Theme">
            <span id="themeToggleIcon">🌙</span> <span id="themeToggleText">Dark</span>
        </button>

        <div class="logo-wrap">
            <svg width="44" height="44" viewBox="0 0 100 100" fill="none">
                <path d="M72 63 C78 63 83 58.5 83 52.5 C83 47 78.5 42.5 73 42 C71.5 32 63 25 52.5 25 C43 25 35 30.5 32.5 39 C31 38.5 29.5 38 28 38 C19.5 38 12.5 45 12.5 53.5 C12.5 62 19.5 69 28 69 L72 69" stroke="#ffffff" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
                <path d="M30 46 L43 46 L49 52 L72 52 C74.2 52 76 53.8 76 56 L76 74 C76 76.2 74.2 78 72 78 L30 78 C27.8 78 26 76.2 26 74 L26 50 C26 47.8 27.8 46 30 46 Z" fill="#ffffff" stroke="#ffffff" stroke-width="2"/>
                <path d="M48 60 L56 66 L48 72" stroke="#FF451A" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
        </div>
        <div class="brand-title">Portly</div>
        <div class="brand-subtitle">
            <span>Browser Access Console</span>
            <span class="protocol-pill"><span class="pulse-dot"></span>● TLS 1.3 Active</span>
        </div>

        ${if (!errorMessage.isNullOrBlank()) """
        <div class="alert-box alert-error">
            <span>⚠️</span>
            <span>$errorMessage</span>
        </div>
        """ else ""}

        ${if (lockedOutSeconds > 0) """
        <div class="alert-box alert-warn" id="lockoutBanner">
            <span>⏱️</span>
            <span>Too many failed attempts. Retry in <strong id="countdown">$lockedOutSeconds</strong>s.</span>
        </div>
        """ else ""}


        <form method="post" action="/login" id="loginForm">
            <div class="input-group">
                <label class="input-label" for="username">Username</label>
                <div class="input-wrapper">
                    <input type="text" id="username" name="username" class="input-field" placeholder="Enter username" required autofocus autocomplete="username"/>
                </div>
            </div>

            <div class="input-group">
                <label class="input-label" for="password">Password</label>
                <div class="input-wrapper">
                    <input type="password" id="password" name="password" class="input-field" placeholder="Enter password" required autocomplete="current-password"/>
                    <button type="button" class="pw-toggle" id="togglePw" title="Show / Hide Password">👁️</button>
                </div>
            </div>

            <div class="remember-row">
                <label class="remember-label">
                    <input type="checkbox" id="rememberMe"/>
                    <span>Remember username</span>
                </label>
                <span style="font-size: 11px; color: var(--text-muted);">Direct device auth</span>
            </div>

            <button type="submit" class="submit-btn" id="submitBtn">
                <span id="btnLabel">Sign In to Portly</span>
            </button>
        </form>

        <div class="footer-note">
            <span>🔒 Direct TLS 1.3 Encrypted Storage Access</span>
        </div>
    </div>

    <script>
        const form = document.getElementById('loginForm');
        const submitBtn = document.getElementById('submitBtn');
        const btnLabel = document.getElementById('btnLabel');
        const pwInput = document.getElementById('password');
        const userInput = document.getElementById('username');
        const toggleBtn = document.getElementById('togglePw');
        const rememberCheckbox = document.getElementById('rememberMe');

        function getStoredTheme() {
            return localStorage.getItem('portly_theme') || 'system';
        }
        function applyTheme(theme) {
            let eff = theme;
            if (theme === 'system') {
                eff = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
            }
            document.documentElement.setAttribute('data-theme', eff);
            const icon = document.getElementById('themeToggleIcon');
            const txt = document.getElementById('themeToggleText');
            if (theme === 'dark') { if (icon) icon.textContent = '🌙'; if (txt) txt.textContent = 'Dark'; }
            else if (theme === 'light') { if (icon) icon.textContent = '☀️'; if (txt) txt.textContent = 'Light'; }
            else { if (icon) icon.textContent = '💻'; if (txt) txt.textContent = 'Auto'; }
        }
        function cycleTheme() {
            const cur = getStoredTheme();
            const next = cur === 'dark' ? 'light' : (cur === 'light' ? 'system' : 'dark');
            localStorage.setItem('portly_theme', next);
            applyTheme(next);
        }
        applyTheme(getStoredTheme());
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
                if (getStoredTheme() === 'system') applyTheme('system');
            });
        }

        try {
            const saved = localStorage.getItem('portly_saved_user');
            if (saved) {
                userInput.value = saved;
                rememberCheckbox.checked = true;
                pwInput.focus();
            }
        } catch(e) {}

        if (toggleBtn) {
            toggleBtn.addEventListener('click', function() {
                const isPw = pwInput.type === 'password';
                pwInput.type = isPw ? 'text' : 'password';
                toggleBtn.textContent = isPw ? '🙈' : '👁️';
            });
        }

        let lockout = ${lockedOutSeconds};
        if (lockout > 0) {{
            submitBtn.disabled = true;
            const timerEl = document.getElementById('countdown');
            const iv = setInterval(function() {{
                lockout--;
                if (timerEl) timerEl.textContent = lockout;
                if (lockout <= 0) {{
                    clearInterval(iv);
                    submitBtn.disabled = false;
                    const b = document.getElementById('lockoutBanner');
                    if (b) b.style.display = 'none';
                }}
            }}, 1000);
        }}

        form.addEventListener('submit', function() {{
            if (rememberCheckbox.checked) {{
                try {{ localStorage.setItem('portly_saved_user', userInput.value.trim()); }} catch(e) {{}}
            }} else {{
                try {{ localStorage.removeItem('portly_saved_user'); }} catch(e) {{}}
            }}
            submitBtn.disabled = true;
            btnLabel.textContent = 'Authenticating...';
        }});
    </script>
</body>
</html>

""".trimIndent()

    fun fileBrowserPage(
        currentPath: String,
        readOnly: Boolean,
        requireAuth: Boolean,
        currentUser: String = "Authorized User",
    ): String = """
<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Portly — Browser Access</title>
    <link rel="icon" type="image/png" href="/assets/logo.png">
    <style>
        :root, [data-theme="dark"] {
            --bg-base: #080B11;
            --bg-glass-nav: rgba(14, 20, 34, 0.75);
            --bg-glass-card: rgba(18, 26, 44, 0.65);
            --bg-glass-card-hover: rgba(26, 38, 64, 0.8);
            --bg-glass-input: rgba(12, 18, 30, 0.7);
            --border-glass: rgba(255, 255, 255, 0.08);
            --border-glass-highlight: rgba(255, 255, 255, 0.16);
            --border-glass-subtle: rgba(255, 255, 255, 0.04);
            --shadow-glass: 0 16px 40px -8px rgba(0, 0, 0, 0.5), inset 0 1px 1px 0 rgba(255, 255, 255, 0.1);
            --shadow-glass-sm: 0 8px 24px -4px rgba(0, 0, 0, 0.4), inset 0 1px 1px 0 rgba(255, 255, 255, 0.08);
            --text-primary: #F8FAFC;
            --text-secondary: #CBD5E1;
            --text-muted: #94A3B8;
            --brand-primary: #FF5722;
            --brand-grad: linear-gradient(135deg, #FF6B4A 0%, #FF3D00 100%);
            --accent-teal: #00B4D8;
            --accent-grad: linear-gradient(135deg, #00C9A7 0%, #00897B 100%);
            --glow-coral: rgba(255, 87, 34, 0.16);
            --glow-teal: rgba(0, 180, 216, 0.14);
            --glow-indigo: rgba(99, 102, 241, 0.10);
            --danger: #EF4444;
            --danger-bg: rgba(239, 68, 68, 0.16);
            --success: #10B981;
            --success-bg: rgba(16, 185, 129, 0.16);
            --header-h: 70px;
            --radius-sm: 10px;
            --radius-md: 16px;
            --radius-lg: 22px;
            --radius-pill: 9999px;
        }
        [data-theme="light"] {
            --bg-base: #F1F5F9;
            --bg-glass-nav: rgba(255, 255, 255, 0.82);
            --bg-glass-card: rgba(255, 255, 255, 0.72);
            --bg-glass-card-hover: rgba(255, 255, 255, 0.9);
            --bg-glass-input: rgba(255, 255, 255, 0.9);
            --border-glass: rgba(255, 255, 255, 0.85);
            --border-glass-highlight: rgba(255, 255, 255, 0.95);
            --border-glass-subtle: rgba(0, 0, 0, 0.05);
            --shadow-glass: 0 16px 36px -8px rgba(15, 23, 42, 0.07), inset 0 1px 1px 0 rgba(255, 255, 255, 0.95);
            --shadow-glass-sm: 0 8px 20px -4px rgba(15, 23, 42, 0.05), inset 0 1px 1px 0 rgba(255, 255, 255, 0.95);
            --text-primary: #0F172A;
            --text-secondary: #334155;
            --text-muted: #64748B;
            --glow-coral: rgba(255, 107, 74, 0.10);
            --glow-teal: rgba(0, 180, 216, 0.08);
            --glow-indigo: rgba(99, 102, 241, 0.05);
            --danger-bg: rgba(239, 68, 68, 0.10);
            --success-bg: rgba(16, 185, 129, 0.12);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: var(--bg-base);
            background-image: 
                radial-gradient(circle at 90% 12%, var(--glow-coral) 0%, transparent 40%),
                radial-gradient(circle at 10% 88%, var(--glow-teal) 0%, transparent 45%),
                radial-gradient(circle at 50% 50%, var(--glow-indigo) 0%, transparent 55%);
            background-attachment: fixed;
            color: var(--text-primary);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
            font-size: 13px;
            line-height: 1.5;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }
        a { color: inherit; text-decoration: none; }

        .portly-header {
            height: var(--header-h);
            background: var(--bg-glass-nav);
            backdrop-filter: blur(24px) saturate(180%);
            -webkit-backdrop-filter: blur(24px) saturate(180%);
            border-bottom: 1px solid var(--border-glass);
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 28px;
            position: sticky;
            top: 0;
            z-index: 100;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
        }
        .portly-header::before {
            content: "";
            position: absolute;
            top: 0; left: 0; right: 0; height: 1px;
            background: linear-gradient(90deg, transparent, var(--border-glass-highlight), transparent);
        }
        .header-left {
            display: flex;
            align-items: center;
            gap: 16px;
        }
        .brand-link {
            display: flex;
            align-items: center;
            gap: 12px;
            cursor: pointer;
        }
        .brand-logo-wrap {
            width: 44px;
            height: 44px;
            border-radius: 12px;
            background: var(--brand-grad);
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 6px 16px rgba(255, 61, 0, 0.35);
            transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
        }
        .brand-logo-wrap:hover {
            transform: scale(1.08) rotate(-2deg);
        }
        .brand-text-col {
            display: flex;
            flex-direction: column;
        }
        .brand-title {
            font-size: 19px;
            font-weight: 800;
            letter-spacing: -0.4px;
            background: var(--brand-grad);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            line-height: 1.15;
        }
        .brand-subtitle-badge {
            font-size: 10px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.6px;
            color: var(--accent-teal);
        }
        .connection-status-pill {
            display: inline-flex;
            align-items: center;
            gap: 7px;
            background: rgba(16, 185, 129, 0.12);
            border: 1px solid rgba(16, 185, 129, 0.25);
            padding: 5px 12px;
            border-radius: var(--radius-pill);
            font-size: 11px;
            font-weight: 700;
            color: #10B981;
            letter-spacing: 0.3px;
        }
        .pulse-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: #10B981;
            animation: pulseGlow 2s infinite ease-in-out;
        }
        @keyframes pulseGlow {
            0%, 100% { opacity: 1; transform: scale(1); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            50% { opacity: 0.7; transform: scale(1.25); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
        }

        .header-center {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .telemetry-capsule {
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass);
            border-radius: var(--radius-pill);
            padding: 6px 18px;
            display: flex;
            align-items: center;
            gap: 14px;
            font-size: 12px;
            box-shadow: var(--shadow-glass-sm);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
        }
        .device-label {
            display: flex;
            align-items: center;
            gap: 7px;
            font-weight: 600;
            color: var(--text-primary);
        }
        .storage-track {
            width: 110px;
            height: 7px;
            background: var(--border-glass);
            border-radius: 4px;
            overflow: hidden;
            position: relative;
        }
        .storage-fill {
            height: 100%;
            background: var(--brand-grad);
            width: 0%;
            border-radius: 4px;
            transition: width 0.4s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .storage-text {
            font-size: 11px;
            color: var(--text-muted);
            white-space: nowrap;
        }

        .header-right {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .url-copy-pill {
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass);
            border-radius: var(--radius-pill);
            padding: 6px 14px;
            font-size: 11px;
            font-family: monospace;
            color: var(--text-secondary);
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 8px;
            transition: all 0.2s;
        }
        .url-copy-pill:hover {
            color: var(--text-primary);
            border-color: var(--brand-primary);
            background: var(--bg-glass-card-hover);
        }
        .header-glass-btn {
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass);
            color: var(--text-primary);
            padding: 7px 14px;
            border-radius: var(--radius-sm);
            cursor: pointer;
            font-size: 12px;
            font-weight: 600;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
        }
        .header-glass-btn:hover {
            background: var(--bg-glass-card-hover);
            border-color: var(--border-glass-highlight);
            transform: translateY(-1px);
        }
        .header-glass-btn:active {
            transform: translateY(0);
        }
        .user-capsule {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 6px 14px;
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass);
            border-radius: var(--radius-pill);
            font-size: 12px;
            font-weight: 600;
            color: var(--text-primary);
        }

        .workspace-container {
            flex: 1;
            max-width: 1440px;
            width: 100%;
            margin: 0 auto;
            padding: 24px 28px 72px;
            display: flex;
            flex-direction: column;
            gap: 18px;
        }

        .glass-panel {
            background: var(--bg-glass-card);
            backdrop-filter: blur(24px) saturate(180%);
            -webkit-backdrop-filter: blur(24px) saturate(180%);
            border: 1px solid var(--border-glass);
            border-radius: var(--radius-md);
            box-shadow: var(--shadow-glass);
            position: relative;
            overflow: hidden;
            transition: all 0.25s ease;
        }
        .glass-panel::before {
            content: "";
            position: absolute;
            top: 0; left: 0; right: 0; height: 1px;
            background: linear-gradient(90deg, transparent, var(--border-glass-highlight), transparent);
        }

        .nav-hero-card {
            padding: 14px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            flex-wrap: wrap;
        }
        .breadcrumbs-row {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 13px;
            font-weight: 500;
            flex: 1;
            min-width: 240px;
            overflow-x: auto;
            padding: 2px 0;
        }
        .crumb-item {
            color: var(--accent-teal);
            cursor: pointer;
            padding: 4px 8px;
            border-radius: 6px;
            transition: all 0.15s;
            display: inline-flex;
            align-items: center;
            gap: 5px;
            font-weight: 600;
        }
        .crumb-item:hover {
            background: var(--bg-glass-card-hover);
            color: #FFFFFF;
        }
        .crumb-sep {
            color: var(--text-muted);
            font-size: 11px;
        }
        .crumb-current {
            color: var(--text-primary);
            font-weight: 700;
            padding: 4px 8px;
        }
        .shortcuts-row {
            display: flex;
            align-items: center;
            gap: 7px;
            flex-wrap: wrap;
        }
        .shortcut-chip {
            background: var(--bg-glass-input);
            border: 1px solid var(--border-glass);
            color: var(--text-secondary);
            font-size: 11px;
            font-weight: 600;
            padding: 5px 12px;
            border-radius: var(--radius-pill);
            cursor: pointer;
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
            display: inline-flex;
            align-items: center;
            gap: 5px;
        }
        .shortcut-chip:hover {
            background: var(--brand-primary);
            color: #FFFFFF;
            border-color: var(--brand-primary);
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(255, 87, 34, 0.3);
        }

        .toolbar-panel {
            padding: 14px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            flex-wrap: wrap;
        }
        .toolbar-actions-left {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }
        .toolbar-actions-right {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .btn-action {
            display: inline-flex;
            align-items: center;
            gap: 7px;
            padding: 9px 16px;
            font-size: 12px;
            font-weight: 700;
            border-radius: var(--radius-sm);
            border: 1px solid var(--border-glass);
            background: var(--bg-glass-input);
            color: var(--text-primary);
            cursor: pointer;
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .btn-action:hover {
            background: var(--bg-glass-card-hover);
            border-color: var(--border-glass-highlight);
            transform: translateY(-1px);
        }
        .btn-action:active {
            transform: translateY(0);
        }
        .btn-primary-action {
            background: var(--brand-grad);
            border-color: transparent;
            color: #FFFFFF;
            box-shadow: 0 6px 18px rgba(255, 61, 0, 0.35);
        }
        .btn-primary-action:hover {
            box-shadow: 0 8px 24px rgba(255, 61, 0, 0.5);
            transform: translateY(-1.5px);
        }
        .search-glass-wrap {
            position: relative;
            width: 260px;
        }
        .search-glass-input {
            width: 100%;
            padding: 9px 14px 9px 36px;
            background: var(--bg-glass-input);
            border: 1px solid var(--border-glass);
            border-radius: var(--radius-sm);
            color: var(--text-primary);
            font-size: 12px;
            outline: none;
            transition: all 0.2s;
        }
        .search-glass-input:focus {
            border-color: var(--brand-primary);
            box-shadow: 0 0 0 3px rgba(255, 87, 34, 0.2);
        }
        .search-glass-icon {
            position: absolute;
            left: 12px;
            top: 50%;
            transform: translateY(-50%);
            color: var(--text-muted);
            pointer-events: none;
            font-size: 14px;
        }
        .view-toggle-group {
            display: flex;
            border: 1px solid var(--border-glass);
            border-radius: var(--radius-sm);
            overflow: hidden;
            background: var(--bg-glass-input);
        }
        .view-toggle-btn {
            background: transparent;
            border: none;
            padding: 7px 12px;
            color: var(--text-muted);
            cursor: pointer;
            font-size: 14px;
            transition: all 0.15s;
        }
        .view-toggle-btn.active {
            background: var(--bg-glass-card-hover);
            color: var(--brand-primary);
        }

        .category-pills-row {
            display: flex;
            align-items: center;
            gap: 8px;
            overflow-x: auto;
            padding-bottom: 4px;
        }
        .category-filter-pill {
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass);
            color: var(--text-secondary);
            font-size: 11px;
            font-weight: 600;
            padding: 6px 14px;
            border-radius: var(--radius-pill);
            cursor: pointer;
            white-space: nowrap;
            transition: all 0.2s;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .category-filter-pill:hover {
            color: var(--text-primary);
            border-color: var(--border-glass-highlight);
            transform: translateY(-1px);
        }
        .category-filter-pill.active {
            background: var(--bg-glass-card-hover);
            border-color: var(--brand-primary);
            color: var(--brand-primary);
            box-shadow: 0 4px 14px rgba(255, 87, 34, 0.2);
        }
        .pill-count {
            background: var(--border-glass);
            padding: 1px 6px;
            border-radius: 10px;
            font-size: 10px;
            font-weight: 700;
        }

        .dropzone-hero {
            border: 2px dashed var(--border-glass-highlight);
            background: var(--bg-glass-card);
            border-radius: var(--radius-md);
            padding: 32px 24px;
            text-align: center;
            cursor: pointer;
            transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
            position: relative;
        }
        .dropzone-hero:hover, .dropzone-hero.dragover {
            border-color: var(--brand-primary);
            background: var(--bg-glass-card-hover);
            box-shadow: 0 12px 32px rgba(255, 87, 34, 0.2);
            transform: translateY(-2px);
        }
        .dropzone-icon {
            font-size: 38px;
            margin-bottom: 10px;
            display: inline-block;
            transition: transform 0.2s ease;
        }
        .dropzone-hero:hover .dropzone-icon {
            transform: translateY(-4px) scale(1.1);
        }
        .dropzone-title {
            font-size: 15px;
            font-weight: 700;
            color: var(--text-primary);
            margin-bottom: 5px;
        }
        .dropzone-subtitle {
            font-size: 12px;
            color: var(--text-muted);
        }

        .table-responsive {
            width: 100%;
            overflow-x: auto;
        }
        .file-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }
        .file-table th {
            text-align: left;
            padding: 14px 16px;
            color: var(--text-muted);
            font-size: 11px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.6px;
            border-bottom: 1px solid var(--border-glass);
            cursor: pointer;
            user-select: none;
            white-space: nowrap;
        }
        .file-table th:hover {
            color: var(--text-primary);
        }
        .file-table td {
            padding: 13px 16px;
            border-bottom: 1px solid var(--border-glass-subtle);
            color: var(--text-secondary);
            vertical-align: middle;
        }
        .file-table tr {
            transition: background 0.15s ease;
        }
        .file-table tbody tr:hover {
            background: var(--bg-glass-card-hover);
        }
        .file-name-cell {
            display: flex;
            align-items: center;
            gap: 10px;
            color: var(--text-primary);
            font-weight: 600;
        }
        .file-icon {
            font-size: 18px;
            width: 24px;
            text-align: center;
        }
        .file-link {
            cursor: pointer;
            transition: color 0.15s;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            max-width: 480px;
        }
        .file-link:hover {
            color: var(--brand-primary);
            text-decoration: underline;
        }
        .format-badge {
            font-size: 10px;
            font-weight: 700;
            text-transform: uppercase;
            padding: 3px 8px;
            border-radius: 6px;
            background: var(--border-glass);
            color: var(--text-muted);
        }

        .grid-view-container {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
            gap: 16px;
            padding: 8px 0;
        }
        .grid-card {
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass);
            border-radius: var(--radius-md);
            padding: 18px 14px;
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
            cursor: pointer;
            position: relative;
            transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
            box-shadow: var(--shadow-glass-sm);
        }
        .grid-card:hover {
            background: var(--bg-glass-card-hover);
            border-color: var(--border-glass-highlight);
            transform: translateY(-4px);
            box-shadow: var(--shadow-glass);
        }
        .grid-card-check {
            position: absolute;
            top: 10px;
            right: 10px;
            accent-color: var(--brand-primary);
            width: 16px;
            height: 16px;
            cursor: pointer;
        }
        .grid-card-icon {
            font-size: 42px;
            margin: 12px 0 10px;
        }
        .grid-card-name {
            font-size: 12px;
            font-weight: 600;
            color: var(--text-primary);
            width: 100%;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            margin-bottom: 4px;
        }
        .grid-card-meta {
            font-size: 11px;
            color: var(--text-muted);
        }

        .batch-floating-bar {
            position: fixed;
            bottom: 24px;
            left: 50%;
            transform: translateX(-50%);
            background: var(--bg-glass-nav);
            backdrop-filter: blur(28px) saturate(180%);
            -webkit-backdrop-filter: blur(28px) saturate(180%);
            border: 1px solid var(--border-glass-highlight);
            border-radius: var(--radius-pill);
            padding: 10px 24px;
            display: none;
            align-items: center;
            gap: 16px;
            box-shadow: 0 20px 48px rgba(0, 0, 0, 0.5);
            z-index: 900;
            animation: slideUp 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
        }
        @keyframes slideUp {
            from { transform: translate(-50%, 20px); opacity: 0; }
            to { transform: translate(-50%, 0); opacity: 1; }
        }

        .modal-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0, 0, 0, 0.65);
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            padding: 20px;
        }
        .modal-box {
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass-highlight);
            border-radius: 20px;
            padding: 28px 24px;
            width: 100%;
            max-width: 460px;
            box-shadow: 0 24px 56px rgba(0, 0, 0, 0.6);
            position: relative;
        }
        .modal-large {
            max-width: 880px;
            max-height: 90vh;
            overflow-y: auto;
        }

        .upload-drawer {
            position: fixed;
            bottom: 24px;
            right: 24px;
            width: 360px;
            background: var(--bg-glass-nav);
            border: 1px solid var(--border-glass-highlight);
            border-radius: var(--radius-md);
            padding: 18px 20px;
            box-shadow: 0 20px 48px rgba(0, 0, 0, 0.5);
            z-index: 850;
            display: none;
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
        }
        .upload-progress-track {
            height: 6px;
            background: var(--border-glass);
            border-radius: 3px;
            overflow: hidden;
            margin: 10px 0;
        }
        .upload-progress-fill {
            height: 100%;
            background: var(--brand-grad);
            width: 0%;
            transition: width 0.2s ease;
        }

        .toast-container {
            position: fixed;
            bottom: 24px;
            left: 24px;
            z-index: 1100;
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .toast-item {
            background: var(--bg-glass-card);
            border: 1px solid var(--border-glass-highlight);
            border-radius: 12px;
            padding: 12px 18px;
            font-size: 13px;
            font-weight: 600;
            box-shadow: 0 10px 28px rgba(0, 0, 0, 0.4);
            display: flex;
            align-items: center;
            gap: 10px;
            animation: fadeIn 0.2s ease;
        }
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }

        @supports not (backdrop-filter: blur(10px)) {
            .glass-panel, .portly-header, .modal-box, .telemetry-capsule {
                background: #111827 !important;
            }
        }
        @media (max-width: 900px) {
            .header-center { display: none; }
            .portly-header { padding: 0 16px; }
            .workspace-container { padding: 16px 16px 60px; }
        }
        @media (prefers-reduced-motion: reduce) {
            * { animation-duration: 0.001ms !important; transition-duration: 0.001ms !important; }
        }
    </style>
</head>
<body>
    <header class="portly-header">
        <div class="header-left">
            <div class="brand-link" onclick="navigateTo('/')">
                <div class="brand-logo-wrap">
                    <svg width="26" height="26" viewBox="0 0 100 100" fill="none">
                        <path d="M72 63 C78 63 83 58.5 83 52.5 C83 47 78.5 42.5 73 42 C71.5 32 63 25 52.5 25 C43 25 35 30.5 32.5 39 C31 38.5 29.5 38 28 38 C19.5 38 12.5 45 12.5 53.5 C12.5 62 19.5 69 28 69 L72 69" stroke="#ffffff" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
                        <path d="M30 46 L43 46 L49 52 L72 52 C74.2 52 76 53.8 76 56 L76 74 C76 76.2 74.2 78 72 78 L30 78 C27.8 78 26 76.2 26 74 L26 50 C26 47.8 27.8 46 30 46 Z" fill="#ffffff" stroke="#ffffff" stroke-width="2"/>
                        <path d="M48 60 L56 66 L48 72" stroke="#FF451A" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                </div>
                <div class="brand-text-col">
                    <span class="brand-title">Portly</span>
                    <span class="brand-subtitle-badge">Browser Access</span>
                </div>
            </div>
            <div class="connection-status-pill" title="Connected to Portly Android Server">
                <span class="pulse-dot"></span>
                <span>● TLS 1.3 Active</span>
            </div>
        </div>

        <div class="header-center">
            <div class="telemetry-capsule">
                <div class="device-label">
                    <span>📱</span>
                    <span id="deviceModelText">Android Device</span>
                </div>
                <div class="storage-track">
                    <div class="storage-fill" id="storageBarFill"></div>
                </div>
                <span class="storage-text" id="storageSummaryText">-- free of --</span>
            </div>
        </div>

        <div class="header-right">
            <button type="button" class="url-copy-pill" id="copyUrlBtn" onclick="copyServerUrl()" title="Click to copy Web Console address">
                <span id="urlDisplay">https://...</span>
                <span id="copyIcon">📋</span>
            </button>
            <button type="button" class="header-glass-btn" onclick="cycleTheme()" id="themeBtn" title="Theme Mode">
                <span id="themeIcon">🌙</span>
            </button>
            <div class="user-capsule">
                <span>👤</span>
                <span>${currentUser}</span>
            </div>
            <a href="/logout" class="header-glass-btn" title="Sign out of Web Console">
                <span>🚪</span>
                <span>Logout</span>
            </a>
        </div>
    </header>

    <main class="workspace-container">
        <div id="permissionNotice" class="glass-panel" style="display:none; padding:14px 20px; border-color:rgba(245,158,11,0.4); background:rgba(245,158,11,0.1); color:#FBBF24; align-items:center; gap:12px;">
            <span style="font-size:20px;">⚠️</span>
            <div style="flex:1;">
                <strong>Limited Storage Access:</strong>
                <span>All Files Access is required on Android 11+ to view and transfer files in root storage. Please grant permission in Portly app settings.</span>
            </div>
        </div>

        <div class="glass-panel nav-hero-card">
            <div class="breadcrumbs-row" id="breadcrumbBar">
                <span class="crumb-item" onclick="navigateTo('/')">🏠 Root</span>
            </div>
            <div class="shortcuts-row">
                <span class="shortcut-chip" onclick="navigateTo('/')">🏠 Root</span>
                <span class="shortcut-chip" onclick="navigateTo('/Download')">📥 Downloads</span>
                <span class="shortcut-chip" onclick="navigateTo('/DCIM')">📷 DCIM</span>
                <span class="shortcut-chip" onclick="navigateTo('/Documents')">📑 Documents</span>
                <span class="shortcut-chip" onclick="navigateTo('/Music')">🎵 Music</span>
                <span class="shortcut-chip" onclick="navigateTo('/Movies')">🎬 Movies</span>
                <span class="shortcut-chip" onclick="navigateTo('/Pictures')">🖼️ Pictures</span>
            </div>
        </div>

        <div class="glass-panel toolbar-panel">
            <div class="toolbar-actions-left">
                <input type="file" id="fileInput" multiple style="display:none;" onchange="handleFileSelect(event)" />
                <input type="file" id="folderInput" webkitdirectory directory multiple style="display:none;" onchange="handleFileSelect(event)" />

                <button type="button" class="btn-action btn-primary-action" onclick="document.getElementById('fileInput').click()">
                    <span>⬆️</span>
                    <span>Upload Files</span>
                </button>
                <button type="button" class="btn-action" onclick="document.getElementById('folderInput').click()">
                    <span>📁</span>
                    <span>Upload Folder</span>
                </button>
                <button type="button" class="btn-action" onclick="openNewFolderModal()">
                    <span>➕</span>
                    <span>New Folder</span>
                </button>
                <button type="button" class="btn-action" onclick="refreshList()">
                    <span id="refreshIcon">🔄</span>
                    <span>Refresh</span>
                </button>
            </div>

            <div class="toolbar-actions-right">
                <div class="search-glass-wrap">
                    <span class="search-glass-icon">🔍</span>
                    <input type="text" id="searchInput" class="search-glass-input" placeholder="Search in directory..." oninput="handleSearch(this.value)" />
                </div>
                <div class="view-toggle-group">
                    <button type="button" class="view-toggle-btn active" id="listViewBtn" onclick="setViewMode('list')" title="List View">☰</button>
                    <button type="button" class="view-toggle-btn" id="gridViewBtn" onclick="setViewMode('grid')" title="Grid View">⊞</button>
                </div>
            </div>
        </div>

        <div class="category-pills-row">
            <div class="category-filter-pill active" onclick="setCategoryFilter('ALL', this)">
                <span>All Files</span>
                <span class="pill-count" id="countAll">0</span>
            </div>
            <div class="category-filter-pill" onclick="setCategoryFilter('DIR', this)">
                <span>📁 Folders</span>
                <span class="pill-count" id="countFolders">0</span>
            </div>
            <div class="category-filter-pill" onclick="setCategoryFilter('IMG', this)">
                <span>🖼️ Images</span>
                <span class="pill-count" id="countImages">0</span>
            </div>
            <div class="category-filter-pill" onclick="setCategoryFilter('VID', this)">
                <span>🎬 Videos</span>
                <span class="pill-count" id="countVideos">0</span>
            </div>
            <div class="category-filter-pill" onclick="setCategoryFilter('AUD', this)">
                <span>🎵 Audio</span>
                <span class="pill-count" id="countAudio">0</span>
            </div>
            <div class="category-filter-pill" onclick="setCategoryFilter('DOC', this)">
                <span>📑 Documents</span>
                <span class="pill-count" id="countDocs">0</span>
            </div>
            <div class="category-filter-pill" onclick="setCategoryFilter('ARCHIVE', this)">
                <span>📦 Archives</span>
                <span class="pill-count" id="countArchives">0</span>
            </div>
        </div>

        <div class="dropzone-hero" id="dropzone" onclick="document.getElementById('fileInput').click()">
            <div class="dropzone-icon">☁️</div>
            <div class="dropzone-title">Drag & Drop files or folders here to upload</div>
            <div class="dropzone-subtitle">Batch transfer directly to connected Android storage • Real-time streaming</div>
        </div>

        <div class="glass-panel" style="padding: 16px 20px;">
            <div class="table-responsive" id="listViewContainer">
                <table class="file-table">
                    <thead>
                        <tr>
                            <th style="width: 40px; text-align: center;">
                                <input type="checkbox" id="selectAllCheckbox" onchange="toggleSelectAll(this.checked)" />
                            </th>
                            <th onclick="sortTable('name')">Name ⬍</th>
                            <th onclick="sortTable('size')" style="width: 120px;">Size ⬍</th>
                            <th onclick="sortTable('modified')" style="width: 160px;">Modified ⬍</th>
                            <th style="width: 100px;">Format</th>
                            <th style="width: 160px; text-align: right;">Actions</th>
                        </tr>
                    </thead>
                    <tbody id="tableBody">
                        <tr>
                            <td colspan="6" style="text-align:center; padding: 40px; color: var(--text-muted);">
                                Loading device storage...
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div class="grid-view-container" id="gridViewContainer" style="display:none;"></div>
        </div>
    </main>

    <div class="batch-floating-bar" id="batchBar">
        <span style="font-weight: 700; color: var(--text-primary);" id="batchCountText">0 selected</span>
        <button type="button" class="btn-action btn-primary-action" onclick="downloadSelectedZip()">
            <span>⬇️</span>
            <span>Download ZIP</span>
        </button>
        <button type="button" class="btn-action" style="color:var(--danger); border-color:rgba(239,68,68,0.3);" onclick="deleteSelectedBatch()">
            <span>🗑️</span>
            <span>Delete Selected</span>
        </button>
        <button type="button" class="btn-action" onclick="clearSelection()">
            <span>✕</span>
            <span>Clear</span>
        </button>
    </div>

    <div class="upload-drawer" id="uploadDrawer">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
            <strong id="uploadDrawerTitle" style="font-size: 13px; color: var(--text-primary);">Uploading...</strong>
            <button type="button" class="header-glass-btn" style="padding: 2px 8px; font-size: 11px;" onclick="cancelUpload()">Cancel</button>
        </div>
        <div class="upload-progress-track">
            <div class="upload-progress-fill" id="uploadProgressFill"></div>
        </div>
        <div style="display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted);">
            <span id="uploadBytesText">0 / 0</span>
            <span id="uploadSpeedText">-- MB/s</span>
        </div>
    </div>

    <!-- Modals -->
    <div class="modal-overlay" id="newFolderModal">
        <div class="modal-box">
            <h3 style="margin-bottom: 16px; color: var(--text-primary);">Create New Directory</h3>
            <input type="text" id="newFolderName" class="search-glass-input" style="width: 100%; margin-bottom: 18px;" placeholder="e.g. Project Assets" />
            <div style="display: flex; justify-content: flex-end; gap: 10px;">
                <button type="button" class="btn-action" onclick="closeModal('newFolderModal')">Cancel</button>
                <button type="button" class="btn-action btn-primary-action" onclick="submitCreateFolder()">Create</button>
            </div>
        </div>
    </div>

    <div class="modal-overlay" id="renameModal">
        <div class="modal-box">
            <h3 style="margin-bottom: 16px; color: var(--text-primary);">Rename Item</h3>
            <input type="text" id="renameNewName" class="search-glass-input" style="width: 100%; margin-bottom: 18px;" />
            <div style="display: flex; justify-content: flex-end; gap: 10px;">
                <button type="button" class="btn-action" onclick="closeModal('renameModal')">Cancel</button>
                <button type="button" class="btn-action btn-primary-action" onclick="submitRename()">Rename</button>
            </div>
        </div>
    </div>

    <div class="modal-overlay" id="previewModal">
        <div class="modal-box modal-large">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; border-bottom: 1px solid var(--border-glass); padding-bottom: 12px;">
                <strong id="previewTitle" style="font-size: 15px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 600px;">Preview</strong>
                <div style="display: flex; gap: 8px;">
                    <button type="button" class="btn-action" id="previewRotateBtn" onclick="rotatePreviewImage()" style="display:none;">🔄 Rotate</button>
                    <a id="previewDownloadBtn" class="btn-action btn-primary-action" download>⬇️ Download</a>
                    <button type="button" class="btn-action" onclick="closeModal('previewModal')">✕ Close</button>
                </div>
            </div>
            <div id="previewContent" style="min-height: 280px; display: flex; align-items: center; justify-content: center;"></div>
        </div>
    </div>

    <div class="toast-container" id="toastContainer"></div>

    <script>
        let currentPath = '$currentPath' || '/';
        let rawEntries = [];
        let filteredEntries = [];
        let selectedPaths = new Set();
        let viewMode = localStorage.getItem('portly_view_mode') || 'list';
        let sortKey = 'name';
        let sortAsc = true;
        let searchQuery = '';
        let categoryFilter = 'ALL';
        let activeXhr = null;
        let renameTargetPath = null;
        let previewRotation = 0;

        const isReadOnly = ${readOnly};
        const currentUser = "$currentUser";

        try {
            document.getElementById('urlDisplay').textContent = window.location.origin;
        } catch(e) {}

        function copyServerUrl() {
            const url = window.location.origin;
            navigator.clipboard.writeText(url).then(() => {
                showToast('URL copied to clipboard: ' + url);
                const ic = document.getElementById('copyIcon');
                if (ic) { ic.textContent = '✅'; setTimeout(() => { ic.textContent = '📋'; }, 2000); }
            }).catch(() => {
                showToast('Failed to copy URL', 'error');
            });
        }

        function getStoredTheme() {
            return localStorage.getItem('portly_theme') || 'system';
        }
        function applyTheme(theme) {
            let eff = theme;
            if (theme === 'system') {
                eff = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
            }
            document.documentElement.setAttribute('data-theme', eff);
            const icon = document.getElementById('themeIcon');
            if (icon) {
                if (theme === 'dark') icon.textContent = '🌙';
                else if (theme === 'light') icon.textContent = '☀️';
                else icon.textContent = '💻';
            }
        }
        function cycleTheme() {
            const cur = getStoredTheme();
            const next = cur === 'dark' ? 'light' : (cur === 'light' ? 'system' : 'dark');
            localStorage.setItem('portly_theme', next);
            applyTheme(next);
            showToast('Theme set to ' + (next.charAt(0).toUpperCase() + next.slice(1)));
        }
        applyTheme(getStoredTheme());
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
                if (getStoredTheme() === 'system') applyTheme('system');
            });
        }

        async function loadDeviceInfo() {
            try {
                const res = await fetch('/api/device-info');
                if (res.ok) {
                    const info = await res.json();
                    document.getElementById('deviceModelText').textContent = info.deviceName || 'Android Storage';
                    document.getElementById('storageBarFill').style.width = (info.usedPercent || 0) + '%';
                    document.getElementById('storageSummaryText').textContent = (info.freeFormatted || '0 B') + ' free of ' + (info.totalFormatted || '0 B');
                    const warn = document.getElementById('permissionNotice');
                    if (warn) {
                        warn.style.display = (info.hasStorageAccess === false) ? 'flex' : 'none';
                    }
                }
            } catch(e) {}
        }
        loadDeviceInfo();

        async function loadDirectory(path) {
            currentPath = path || '/';
            selectedPaths.clear();
            updateBatchBar();
            updateBreadcrumbs();
            const tb = document.getElementById('tableBody');
            tb.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 40px; color: var(--text-muted);"><div style="font-size:24px; margin-bottom:8px;">⏳</div>Loading device storage...</td></tr>';
            try {
                const res = await fetch('/api/list?path=' + encodeURIComponent(currentPath));
                if (res.status === 401) {
                    window.location.href = '/login';
                    return;
                }
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const data = await res.json();
                rawEntries = data.entries || [];
                updateCategoryCounts();
                applyFiltersAndRender();
            } catch(e) {
                tb.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 32px; color: var(--danger);"><div style="font-size:24px; margin-bottom:8px;">⚠️</div>Failed to load directory: ' + escapeHtml(e.message) + '</td></tr>';
            }
        }

        function updateBreadcrumbs() {
            const bar = document.getElementById('breadcrumbBar');
            const parts = currentPath.split('/').filter(p => p.length > 0);
            let html = '<span class="crumb-item" onclick="navigateTo(\'/\')">🏠 Root</span>';
            let acc = '';
            for (let i = 0; i < parts.length; i++) {
                acc += '/' + parts[i];
                const p = acc;
                const isLast = (i === parts.length - 1);
                html += '<span class="crumb-sep">/</span>';
                if (isLast) {
                    html += '<span class="crumb-current">' + escapeHtml(parts[i]) + '</span>';
                } else {
                    html += '<span class="crumb-item" onclick="navigateTo(\'' + escapeHtml(p) + '\')">' + escapeHtml(parts[i]) + '</span>';
                }
            }
            bar.innerHTML = html;
        }

        function navigateTo(path) {
            loadDirectory(path);
        }
        function refreshList() {
            const icon = document.getElementById('refreshIcon');
            if (icon) { icon.style.display = 'inline-block'; icon.style.transition = 'transform 0.5s ease'; icon.style.transform = 'rotate(360deg)'; setTimeout(() => { icon.style.transform = 'rotate(0deg)'; }, 500); }
            loadDirectory(currentPath);
            loadDeviceInfo();
            showToast('Refreshed directory contents');
        }

        function updateCategoryCounts() {
            let folders = 0, images = 0, videos = 0, audios = 0, docs = 0, archives = 0;
            for (const e of rawEntries) {
                if (e.isDirectory) { folders++; continue; }
                const ext = getExt(e.name);
                if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) images++;
                else if (['mp4', 'mkv', 'webm', 'mov', 'avi', '3gp'].includes(ext)) videos++;
                else if (['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac'].includes(ext)) audios++;
                else if (['pdf', 'doc', 'docx', 'txt', 'rtf', 'xlsx', 'xls', 'csv', 'ppt', 'pptx'].includes(ext)) docs++;
                else if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'apk'].includes(ext)) archives++;
            }
            document.getElementById('countAll').textContent = rawEntries.length;
            document.getElementById('countFolders').textContent = folders;
            document.getElementById('countImages').textContent = images;
            document.getElementById('countVideos').textContent = videos;
            document.getElementById('countAudio').textContent = audios;
            document.getElementById('countDocs').textContent = docs;
            document.getElementById('countArchives').textContent = archives;
        }

        function handleSearch(q) {
            searchQuery = (q || '').trim().toLowerCase();
            applyFiltersAndRender();
        }
        function setCategoryFilter(cat, btn) {
            categoryFilter = cat;
            document.querySelectorAll('.category-filter-pill').forEach(el => el.classList.remove('active'));
            if (btn) btn.classList.add('active');
            applyFiltersAndRender();
        }
        function sortTable(key) {
            if (sortKey === key) sortAsc = !sortAsc;
            else { sortKey = key; sortAsc = true; }
            applyFiltersAndRender();
        }

        function applyFiltersAndRender() {
            filteredEntries = rawEntries.filter(entry => {
                if (searchQuery && !entry.name.toLowerCase().includes(searchQuery)) return false;
                if (categoryFilter === 'DIR' && !entry.isDirectory) return false;
                if (categoryFilter === 'IMG') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return false;
                }
                if (categoryFilter === 'VID') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['mp4', 'mkv', 'webm', 'mov', 'avi', '3gp'].includes(ext)) return false;
                }
                if (categoryFilter === 'AUD') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac'].includes(ext)) return false;
                }
                if (categoryFilter === 'DOC') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['pdf', 'doc', 'docx', 'txt', 'rtf', 'xlsx', 'xls', 'csv', 'ppt', 'pptx'].includes(ext)) return false;
                }
                if (categoryFilter === 'ARCHIVE') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'apk'].includes(ext)) return false;
                }
                return true;
            });

            filteredEntries.sort((a, b) => {
                if (a.isDirectory && !b.isDirectory) return -1;
                if (!a.isDirectory && b.isDirectory) return 1;
                let valA, valB;
                if (sortKey === 'size') { valA = a.sizeBytes || 0; valB = b.sizeBytes || 0; }
                else if (sortKey === 'modified') { valA = a.modifiedMillis || 0; valB = b.modifiedMillis || 0; }
                else { valA = a.name.toLowerCase(); valB = b.name.toLowerCase(); }
                if (valA < valB) return sortAsc ? -1 : 1;
                if (valA > valB) return sortAsc ? 1 : -1;
                return 0;
            });

            renderView();
        }

        function renderView() {
            if (viewMode === 'list') renderTableView();
            else renderGridView();
        }

        function setViewMode(mode) {
            viewMode = mode;
            localStorage.setItem('portly_view_mode', mode);
            document.getElementById('listViewBtn').classList.toggle('active', mode === 'list');
            document.getElementById('gridViewBtn').classList.toggle('active', mode === 'grid');
            document.getElementById('listViewContainer').style.display = mode === 'list' ? 'block' : 'none';
            document.getElementById('gridViewContainer').style.display = mode === 'grid' ? 'grid' : 'none';
            renderView();
        }

        function renderTableView() {
            const tb = document.getElementById('tableBody');
            if (filteredEntries.length === 0) {
                const emptyMsg = (rawEntries.length === 0)
                    ? 'Directory is empty. Drag files here or click Upload to get started.'
                    : 'No matching files found for current filter.';
                tb.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 48px; color: var(--text-muted);"><div style="font-size:32px; margin-bottom:10px;">📂</div>' + emptyMsg + '</td></tr>';
                return;
            }
            let html = '';
            for (const item of filteredEntries) {
                const isChecked = selectedPaths.has(item.path);
                const icon = item.isDirectory ? '📁' : getFileIcon(item.name);
                const ext = getExt(item.name) || (item.isDirectory ? 'FOLDER' : 'FILE');
                html += '<tr>' +
                    '<td style="text-align:center;"><input type="checkbox" ' + (isChecked ? 'checked' : '') + ' onchange="toggleSelect(\'' + escapeHtml(item.path) + '\', this.checked)" /></td>' +
                    '<td><div class="file-name-cell"><span class="file-icon">' + icon + '</span>' +
                        (item.isDirectory
                            ? '<span class="file-link" onclick="navigateTo(\'' + escapeHtml(item.path) + '\')">' + escapeHtml(item.name) + '</span>'
                            : '<span class="file-link" onclick="openPreview(\'' + escapeHtml(item.path) + '\', \'' + escapeHtml(item.name) + '\')">' + escapeHtml(item.name) + '</span>'
                        ) +
                    '</div></td>' +
                    '<td>' + (item.isDirectory ? '--' : (item.sizeFormatted || formatBytes(item.sizeBytes))) + '</td>' +
                    '<td>' + (item.modifiedFormatted || formatDate(item.modifiedMillis)) + '</td>' +
                    '<td><span class="format-badge">' + ext + '</span></td>' +
                    '<td style="text-align:right; white-space:nowrap;">' +
                        (item.isDirectory
                            ? '<button type="button" class="btn-action" style="padding:5px 10px;" onclick="navigateTo(\'' + escapeHtml(item.path) + '\')">Open</button> '
                            : '<button type="button" class="btn-action" style="padding:5px 10px;" onclick="openPreview(\'' + escapeHtml(item.path) + '\', \'' + escapeHtml(item.name) + '\')" title="Preview">👁️</button> ' +
                              '<a href="/download?path=' + encodeURIComponent(item.path) + '" class="btn-action" style="padding:5px 10px;" download title="Download">⬇️</a> '
                        ) +
                        (!isReadOnly ? '<button type="button" class="btn-action" style="padding:5px 10px;" onclick="openRenameModal(\'' + escapeHtml(item.path) + '\', \'' + escapeHtml(item.name) + '\')" title="Rename">✏️</button> ' : '') +
                        (!isReadOnly ? '<button type="button" class="btn-action" style="padding:5px 10px; color:var(--danger);" onclick="deleteItemSingle(\'' + escapeHtml(item.path) + '\')" title="Delete">🗑️</button>' : '') +
                    '</td>' +
                '</tr>';
            }
            tb.innerHTML = html;
        }

        function renderGridView() {
            const g = document.getElementById('gridViewContainer');
            if (filteredEntries.length === 0) {
                const emptyMsg = (rawEntries.length === 0)
                    ? 'Directory is empty. Drag files here or click Upload to get started.'
                    : 'No matching files found for current filter.';
                g.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 56px; color: var(--text-muted);"><div style="font-size:36px; margin-bottom:12px;">📂</div>' + emptyMsg + '</div>';
                return;
            }
            let html = '';
            for (const item of filteredEntries) {
                const isChecked = selectedPaths.has(item.path);
                const icon = item.isDirectory ? '📁' : getFileIcon(item.name);
                html += '<div class="grid-card" onclick="handleCardClick(event, \'' + escapeHtml(item.path) + '\', ' + item.isDirectory + ', \'' + escapeHtml(item.name) + '\')">' +
                    '<input type="checkbox" class="grid-card-check" ' + (isChecked ? 'checked' : '') + ' onclick="event.stopPropagation(); toggleSelect(\'' + escapeHtml(item.path) + '\', this.checked)" />' +
                    '<div class="grid-card-icon">' + icon + '</div>' +
                    '<div class="grid-card-name" title="' + escapeHtml(item.name) + '">' + escapeHtml(item.name) + '</div>' +
                    '<div class="grid-card-meta">' + (item.isDirectory ? 'Directory' : (item.sizeFormatted || formatBytes(item.sizeBytes))) + '</div>' +
                '</div>';
            }
            g.innerHTML = html;
        }

        function handleCardClick(e, path, isDir, name) {
            if (isDir) navigateTo(path);
            else openPreview(path, name);
        }

        function toggleSelect(path, checked) {
            if (checked) selectedPaths.add(path);
            else selectedPaths.delete(path);
            updateBatchBar();
        }
        function toggleSelectAll(checked) {
            if (checked) {
                filteredEntries.forEach(e => selectedPaths.add(e.path));
            } else {
                selectedPaths.clear();
            }
            renderView();
            updateBatchBar();
        }
        function clearSelection() {
            selectedPaths.clear();
            const el = document.getElementById('selectAllCheckbox');
            if (el) el.checked = false;
            renderView();
            updateBatchBar();
        }
        function updateBatchBar() {
            const bar = document.getElementById('batchBar');
            const cnt = selectedPaths.size;
            if (cnt > 0) {
                bar.style.display = 'flex';
                document.getElementById('batchCountText').textContent = cnt + ' selected';
            } else {
                bar.style.display = 'none';
            }
        }

        function downloadSelectedZip() {
            if (selectedPaths.size === 0) return;
            const p = Array.from(selectedPaths).join(',');
            window.location.href = '/api/zip-download?paths=' + encodeURIComponent(p);
            showToast('Preparing ZIP download archive...');
        }

        async function deleteSelectedBatch() {
            if (selectedPaths.size === 0) return;
            if (!confirm('Permanently delete ' + selectedPaths.size + ' selected item(s) from device storage?')) return;
            try {
                const res = await fetch('/api/batch-delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ paths: Array.from(selectedPaths) })
                });
                if (res.ok) {
                    const d = await res.json();
                    showToast('Deleted ' + d.successCount + ' item(s)');
                    clearSelection();
                    refreshList();
                } else {
                    showToast('Batch delete failed: ' + await res.text(), 'error');
                }
            } catch(e) {
                showToast('Network error: ' + e.message, 'error');
            }
        }

        function openPreview(path, name) {
            previewRotation = 0;
            const ext = getExt(name);
            const content = document.getElementById('previewContent');
            const title = document.getElementById('previewTitle');
            const dl = document.getElementById('previewDownloadBtn');
            const rotBtn = document.getElementById('previewRotateBtn');
            title.textContent = name;
            dl.href = '/download?path=' + encodeURIComponent(path);
            rotBtn.style.display = 'none';

            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) {
                rotBtn.style.display = 'inline-flex';
                content.innerHTML = '<img id="previewImageEl" src="/preview?path=' + encodeURIComponent(path) + '" style="max-width:100%; max-height:75vh; border-radius:12px; object-fit:contain; transition: transform 0.25s ease;" />';
            } else if (['mp4', 'webm', 'mkv', 'mov'].includes(ext)) {
                content.innerHTML = '<video controls autoplay style="max-width:100%; max-height:75vh; border-radius:12px;"><source src="/preview?path=' + encodeURIComponent(path) + '"></video>';
            } else if (['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac'].includes(ext)) {
                content.innerHTML = '<div style="padding:32px; width:100%; text-align:center;"><div style="font-size:56px; margin-bottom:16px;">🎵</div><audio controls autoplay style="width:100%;"><source src="/preview?path=' + encodeURIComponent(path) + '"></audio></div>';
            } else if (['pdf'].includes(ext)) {
                content.innerHTML = '<iframe src="/preview?path=' + encodeURIComponent(path) + '" style="width:100%; height:75vh; border:none; border-radius:12px;"></iframe>';
            } else if (['txt', 'log', 'json', 'xml', 'md', 'py', 'kt', 'java', 'html', 'css', 'js', 'sh', 'sql', 'yaml', 'yml'].includes(ext)) {
                content.innerHTML = '<div style="padding:28px;">Loading text content...</div>';
                fetch('/preview?path=' + encodeURIComponent(path))
                    .then(r => r.text())
                    .then(txt => {
                        content.innerHTML = '<div style="width:100%; text-align:left;"><button type="button" class="btn-action" style="margin-bottom:10px;" onclick="copyPreviewText()">📋 Copy Text</button><pre id="previewTextContent" style="background:var(--bg-glass-input); padding:18px; border-radius:12px; overflow:auto; max-height:68vh; font-family:monospace; font-size:12px; border:1px solid var(--border-glass);"><code>' + escapeHtml(txt) + '</code></pre></div>';
                    })
                    .catch(err => { content.innerHTML = '<div style="color:var(--danger); padding:24px;">Error reading file: ' + escapeHtml(err.message) + '</div>'; });
            } else {
                content.innerHTML = '<div style="padding:36px; text-align:center;"><div style="font-size:52px; margin-bottom:14px;">📄</div><p style="color:var(--text-secondary); margin-bottom:16px;">Inline preview is not supported for this file type.</p><a href="/download?path=' + encodeURIComponent(path) + '" class="btn-action btn-primary-action" download>Download File</a></div>';
            }
            document.getElementById('previewModal').style.display = 'flex';
        }

        function rotatePreviewImage() {
            previewRotation = (previewRotation + 90) % 360;
            const el = document.getElementById('previewImageEl');
            if (el) el.style.transform = 'rotate(' + previewRotation + 'deg)';
        }

        function copyPreviewText() {
            const el = document.getElementById('previewTextContent');
            if (el) {
                navigator.clipboard.writeText(el.textContent).then(() => {
                    showToast('File content copied to clipboard');
                });
            }
        }

        function handleFileSelect(e) {
            const files = Array.from(e.target.files || []);
            if (files.length === 0) return;
            startUpload(files);
            e.target.value = '';
        }

        function startUpload(files) {
            const drawer = document.getElementById('uploadDrawer');
            const title = document.getElementById('uploadDrawerTitle');
            const fill = document.getElementById('uploadProgressFill');
            const bytesText = document.getElementById('uploadBytesText');
            const speedText = document.getElementById('uploadSpeedText');

            drawer.style.display = 'block';
            title.textContent = 'Uploading ' + files.length + ' file(s)...';
            fill.style.width = '0%';

            const fd = new FormData();
            files.forEach(f => fd.append('file', f, f.webkitRelativePath || f.name));

            const start = Date.now();
            activeXhr = new XMLHttpRequest();
            activeXhr.open('POST', '/api/upload?path=' + encodeURIComponent(currentPath));

            activeXhr.upload.onprogress = function(e) {
                if (e.lengthComputable) {
                    const pct = Math.round((e.loaded / e.total) * 100);
                    fill.style.width = pct + '%';
                    bytesText.textContent = formatBytes(e.loaded) + ' / ' + formatBytes(e.total);
                    const elapsed = (Date.now() - start) / 1000;
                    if (elapsed > 0.3) {
                        const speed = (e.loaded / (1024 * 1024 * elapsed)).toFixed(2);
                        speedText.textContent = speed + ' MB/s';
                    }
                }
            };

            activeXhr.onload = function() {
                activeXhr = null;
                if (this.status >= 200 && this.status < 300) {
                    fill.style.width = '100%';
                    title.textContent = 'Upload complete!';
                    showToast('Uploaded ' + files.length + ' file(s) successfully');
                    setTimeout(() => { drawer.style.display = 'none'; }, 1600);
                    refreshList();
                } else {
                    title.textContent = 'Upload failed!';
                    showToast('Upload error: ' + this.responseText, 'error');
                }
            };

            activeXhr.onerror = function() {
                activeXhr = null;
                title.textContent = 'Upload interrupted!';
                showToast('Network error during upload', 'error');
            };

            activeXhr.send(fd);
        }

        function cancelUpload() {
            if (activeXhr) {
                activeXhr.abort();
                activeXhr = null;
                document.getElementById('uploadDrawer').style.display = 'none';
                showToast('Upload cancelled');
            }
        }

        const dz = document.getElementById('dropzone');
        if (dz) {
            ['dragenter', 'dragover'].forEach(n => {
                window.addEventListener(n, e => { e.preventDefault(); e.stopPropagation(); dz.classList.add('dragover'); });
            });
            ['dragleave', 'drop'].forEach(n => {
                window.addEventListener(n, e => { e.preventDefault(); e.stopPropagation(); dz.classList.remove('dragover'); });
            });
            window.addEventListener('drop', e => {
                const dt = e.dataTransfer;
                if (dt && dt.files && dt.files.length > 0) {
                    startUpload(Array.from(dt.files));
                }
            });
        }

        function openNewFolderModal() {
            document.getElementById('newFolderName').value = '';
            document.getElementById('newFolderModal').style.display = 'flex';
            setTimeout(() => document.getElementById('newFolderName').focus(), 50);
        }
        async function submitCreateFolder() {
            const name = document.getElementById('newFolderName').value.trim();
            if (!name) return;
            closeModal('newFolderModal');
            try {
                const res = await fetch('/api/mkdir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ parentPath: currentPath, name: name })
                });
                if (res.ok) {
                    showToast('Directory created: ' + name);
                    refreshList();
                } else {
                    showToast('Failed to create folder: ' + await res.text(), 'error');
                }
            } catch(e) { showToast('Network error: ' + e.message, 'error'); }
        }

        function openRenameModal(path, oldName) {
            renameTargetPath = path;
            const input = document.getElementById('renameNewName');
            input.value = oldName;
            document.getElementById('renameModal').style.display = 'flex';
            setTimeout(() => input.focus(), 50);
        }
        async function submitRename() {
            const newName = document.getElementById('renameNewName').value.trim();
            if (!newName || !renameTargetPath) return;
            closeModal('renameModal');
            try {
                const res = await fetch('/api/rename', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: renameTargetPath, newName: newName })
                });
                if (res.ok) {
                    showToast('Renamed successfully');
                    refreshList();
                } else {
                    showToast('Rename failed: ' + await res.text(), 'error');
                }
            } catch(e) { showToast('Network error: ' + e.message, 'error'); }
        }

        async function deleteItemSingle(path) {
            if (!confirm('Permanently delete "' + path + '" from device storage?')) return;
            try {
                const res = await fetch('/api/delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: path })
                });
                if (res.ok) {
                    showToast('Deleted item');
                    refreshList();
                } else {
                    showToast('Delete failed: ' + await res.text(), 'error');
                }
            } catch(e) { showToast('Network error: ' + e.message, 'error'); }
        }

        function closeModal(id) {
            document.getElementById(id).style.display = 'none';
        }

        function formatBytes(b) {
            if (!b || b === 0) return '0 B';
            const k = 1024, s = ['B', 'KB', 'MB', 'GB', 'TB'];
            const i = Math.floor(Math.log(b) / Math.log(k));
            return parseFloat((b / Math.pow(k, i)).toFixed(1)) + ' ' + s[i];
        }
        function formatDate(ms) {
            if (!ms) return '--';
            const d = new Date(ms);
            return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0') + ' ' + String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0');
        }
        function getExt(n) {
            const idx = n.lastIndexOf('.');
            return idx > 0 ? n.substring(idx + 1).toLowerCase() : '';
        }
        function getFileIcon(n) {
            const ext = getExt(n);
            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return '🖼️';
            if (['mp4', 'mkv', 'webm', 'mov', 'avi', '3gp'].includes(ext)) return '🎬';
            if (['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac'].includes(ext)) return '🎵';
            if (['pdf'].includes(ext)) return '📕';
            if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(ext)) return '📦';
            if (['apk'].includes(ext)) return '🤖';
            if (['doc', 'docx'].includes(ext)) return '📄';
            if (['xls', 'xlsx', 'csv'].includes(ext)) return '📊';
            if (['ppt', 'pptx'].includes(ext)) return '📑';
            if (['txt', 'log', 'md'].includes(ext)) return '📝';
            if (['py', 'kt', 'java', 'html', 'css', 'js', 'sh', 'sql', 'json', 'xml'].includes(ext)) return '💻';
            return '📄';
        }
        function escapeHtml(s) {
            if (!s) return '';
            return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
        }
        function showToast(msg, type) {
            const tc = document.getElementById('toastContainer');
            const el = document.createElement('div');
            el.className = 'toast-item';
            el.innerHTML = (type === 'error' ? '⚠️ ' : '✅ ') + escapeHtml(msg);
            tc.appendChild(el);
            setTimeout(() => { el.remove(); }, 3400);
        }

        window.addEventListener('keydown', e => {
            if (e.key === 'Escape') {
                closeModal('previewModal');
                closeModal('newFolderModal');
                closeModal('renameModal');
            }
        });

        loadDirectory(currentPath);
    </script>
</body>
</html>

""".trimIndent()
}