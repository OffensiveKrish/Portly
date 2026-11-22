package com.portly.protocol.http.server

import java.util.Base64

internal object HttpWebUiHtml {

    const val LOGO_PNG_BASE64: String =
        "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起動/d2ZanwQ7bA96f8l42I75C/bL/W2iK+q67X9v8mFh11n7n4v2f8w8eXf7V/j/gPzCg1s13YfR5qE90Tf9k1w3wR2730z4e7f8x1/rL3j27j3z/f1k+3k/w/8k73P0g7z8x1XbF+vN3j/d3l/zL3f82/3/xIff1n468f7n0Ym/f1k6f/X7d/j/x9s3/4Xj//f1p6v4g7z/3/x/j5n7L7/8D8142I75C/bL/W2iK+q67X9v8mFh11n7n4v2f8w8eXf7V/j/gPzCg1s13YfR5qE90Tf9k1w3wR2730z4e7f8x1/rL3j27j3z/f1k+3k/w/8k73P0g7z8x1XbF+vN3j/d3l/zL3f82/3/xIff1n468f7n0Ym/f1k6f/X7d/j/x9s3/4Xj//f1p6v4g7z/3/x/j5n7L7/8D"

    val LOGO_PNG_BYTES: ByteArray by lazy {
        Base64.getDecoder().decode(LOGO_PNG_BASE64)
    }

    // Exact Bandhan Bank MFT / Globalscape EFT Styling
    const val EFT_CSS: String = """
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
    background-color: #ffffff;
    color: #333333;
    font-family: Arial, Helvetica, "Segoe UI", sans-serif;
    font-size: 13px;
    line-height: 1.5;
    -webkit-font-smoothing: antialiased;
}

/* Links */
a {
    color: #0066cc;
    text-decoration: none;
}
a:hover {
    text-decoration: underline;
}

/* Classic EFT Buttons */
.eft-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: #efefef;
    background: linear-gradient(to bottom, #fbfbfb 0%, #e6e6e6 100%);
    border: 1px solid #767676;
    border-radius: 2px;
    padding: 3px 18px;
    font-size: 13px;
    font-family: Arial, Helvetica, sans-serif;
    color: #000000;
    cursor: pointer;
    outline: none;
    user-select: none;
    white-space: nowrap;
    text-shadow: 0 1px 0 rgba(255,255,255,0.8);
}
.eft-btn:hover {
    background: linear-gradient(to bottom, #f0f0f0 0%, #dadada 100%);
    border-color: #4f4f4f;
}
.eft-btn:active {
    background: #d6d6d6;
    border-color: #333333;
}
.eft-btn-primary {
    background: linear-gradient(to bottom, #00a4e4 0%, #0082c9 100%);
    border: 1px solid #0068a3;
    color: #ffffff;
    font-weight: bold;
    text-shadow: 0 -1px 0 rgba(0,0,0,0.3);
}
.eft-btn-primary:hover {
    background: linear-gradient(to bottom, #0093cf 0%, #0072b2 100%);
    border-color: #005080;
}
.eft-btn-sm {
    padding: 2px 10px;
    font-size: 12px;
}
.eft-btn:disabled {
    opacity: 0.55;
    cursor: not-allowed;
}

/* Classic Form Inputs */
.eft-input {
    font-family: Arial, Helvetica, sans-serif;
    font-size: 13px;
    padding: 3px 6px;
    border: 1px solid #7f9db9;
    border-radius: 1px;
    background: #ffffff;
    color: #111111;
    outline: none;
    transition: border-color 0.15s;
}
.eft-input:focus {
    border-color: #009bd9;
    box-shadow: 0 0 3px rgba(0, 155, 217, 0.4);
}
.eft-select {
    font-family: Arial, Helvetica, sans-serif;
    font-size: 12px;
    padding: 3px 6px;
    border: 1px solid #7f9db9;
    background: #ffffff;
    color: #111111;
    border-radius: 1px;
}

/* Signature Cyan Fieldset Box */
.eft-box {
    border: 2px solid #009bd9;
    background: #ffffff;
    margin: 0 auto;
    padding: 20px;
}
.eft-box legend {
    color: #333333;
    font-family: Arial, Helvetica, sans-serif;
    font-size: 13px;
    font-weight: normal;
    padding: 0 5px;
}

/* Spinner */
.eft-spinner {
    display: inline-block;
    width: 13px;
    height: 13px;
    border: 2px solid rgba(0,0,0,0.2);
    border-top-color: #000000;
    border-radius: 50%;
    animation: eftSpin 0.7s linear infinite;
    margin-left: 6px;
    vertical-align: middle;
}
@keyframes eftSpin {
    to { transform: rotate(360deg); }
}
"""

    const val FORMS_CSS: String = EFT_CSS

    fun loginPage(errorMessage: String? = null, lockedOutSeconds: Long = 0L): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MFT - Log In</title>
    <link rel="icon" type="image/png" href="/Web/Shared/images/gs-atom-logo.png">
    <style>
${EFT_CSS}

html, body {
    height: 100%;
}
body {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    background-color: #ffffff;
}

/* The exact MFT Login fieldset as in screenshot */
.login-fieldset {
    width: 390px;
    max-width: 90vw;
    border: 2px solid #009bd9;
    background: #ffffff;
    padding: 26px 36px 30px 36px;
    margin: 0 auto;
}
.login-legend {
    color: #333333;
    font-family: Arial, Helvetica, sans-serif;
    font-size: 13px;
    font-weight: normal;
    padding: 0 5px;
    margin-left: -2px;
}

.logo-wrap {
    text-align: center;
    margin-bottom: 24px;
}
.logo-img {
    width: 125px;
    height: auto;
    display: inline-block;
}

/* Form table layout for exact label/input horizontal alignment */
.form-table {
    width: 100%;
    border-collapse: collapse;
}
.form-table td {
    padding: 3px 0;
    vertical-align: middle;
}
.label-col {
    width: 80px;
    text-align: right;
    padding-right: 8px !important;
    font-size: 13px;
    color: #333333;
    white-space: nowrap;
}
.input-col {
    text-align: left;
}
.input-col .eft-input {
    width: 100%;
    height: 22px;
}

.sublink-row td {
    padding: 1px 0 8px 0;
}
.sublink-text {
    font-size: 11px;
    text-align: right;
}

.pw-wrapper {
    position: relative;
    display: flex;
    align-items: center;
}
.pw-toggle-btn {
    position: absolute;
    right: 4px;
    background: transparent;
    border: none;
    cursor: pointer;
    color: #666666;
    padding: 2px;
    display: flex;
    align-items: center;
    justify-content: center;
}
.pw-toggle-btn:hover {
    color: #000000;
}

.btn-center-row {
    text-align: center;
    margin-top: 18px;
}
.login-submit-btn {
    min-width: 80px;
    padding: 3px 18px;
    font-size: 13px;
}

/* Alerts */
.msg-banner {
    padding: 8px 12px;
    margin-bottom: 16px;
    font-size: 12px;
    line-height: 1.4;
    border-radius: 2px;
}
.msg-banner.error {
    background-color: #fff1f0;
    border: 1px solid #ffccc7;
    color: #cf1322;
}
.msg-banner.lockout {
    background-color: #fffbe6;
    border: 1px solid #ffe58f;
    color: #d46b08;
}

.security-notice {
    margin-top: 24px;
    font-size: 11px;
    color: #777777;
    text-align: center;
    max-width: 480px;
}
    </style>
</head>
<body>

    <fieldset class="login-fieldset">
        <legend class="login-legend">Log In</legend>

        <div class="logo-wrap">
            <img src="/Web/Shared/images/gs-atom-logo.png" alt="MFT Logo" class="logo-img" />
        </div>

        ${if (!errorMessage.isNullOrBlank()) """
        <div class="msg-banner error" role="alert">
            <strong>Authentication Failed:</strong> $errorMessage
        </div>
        """ else ""}

        ${if (lockedOutSeconds > 0) """
        <div class="msg-banner lockout" id="lockoutBanner">
            Account access temporarily restricted. Retry in <strong id="countdown">$lockedOutSeconds</strong>s.
        </div>
        """ else ""}

        <form method="post" action="/Web/Account/Login.htm" id="loginForm" autocomplete="on">
            <table class="form-table">
                <tr>
                    <td class="label-col"><label for="username">Username:</label></td>
                    <td class="input-col">
                        <input type="text" id="username" name="username" class="eft-input" required autocomplete="username" autofocus />
                    </td>
                </tr>
                <tr class="sublink-row">
                    <td></td>
                    <td class="sublink-text">
                        <a href="javascript:void(0)" onclick="alert('Please contact your System Administrator or Bank Helpdesk for corporate username recovery.')">Forgot Username</a>
                    </td>
                </tr>
                <tr>
                    <td class="label-col"><label for="password">Password:</label></td>
                    <td class="input-col">
                        <div class="pw-wrapper">
                            <input type="password" id="password" name="password" class="eft-input" required autocomplete="current-password" />
                            <button type="button" class="pw-toggle-btn" id="togglePw" title="Show / Hide Password" tabindex="-1">
                                <svg id="eyeIcon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                                    <circle cx="12" cy="12" r="3"></circle>
                                </svg>
                            </button>
                        </div>
                    </td>
                </tr>
                <tr class="sublink-row">
                    <td></td>
                    <td class="sublink-text">
                        <a href="javascript:void(0)" onclick="alert('Self-service password reset requires enterprise directory validation. Contact your IT administrator.')">Forgot Password</a>
                    </td>
                </tr>
            </table>

            <div style="margin: 8px 0 0 80px; font-size: 12px; color: #555555;">
                <label style="cursor: pointer; display: inline-flex; align-items: center; gap: 4px;">
                    <input type="checkbox" id="rememberMe" style="cursor: pointer;" />
                    <span>Remember username</span>
                </label>
            </div>

            <div class="btn-center-row">
                <button type="submit" id="submitBtn" class="eft-btn login-submit-btn">
                    <span id="btnLabel">Log in</span>
                    <span id="btnSpinner" class="eft-spinner" style="display:none;"></span>
                </button>
            </div>
        </form>
    </fieldset>

    <div class="security-notice">
        Notice: Authorized enterprise personnel only. All access, transfers, and sessions are logged and audited in accordance with BFSI security standards.
    </div>

    <script>
        const form = document.getElementById('loginForm');
        const submitBtn = document.getElementById('submitBtn');
        const btnLabel = document.getElementById('btnLabel');
        const btnSpinner = document.getElementById('btnSpinner');
        const pwInput = document.getElementById('password');
        const userInput = document.getElementById('username');
        const toggleBtn = document.getElementById('togglePw');
        const rememberCheckbox = document.getElementById('rememberMe');

        // Remember Me username restoration
        try {
            const savedUser = localStorage.getItem('mft_saved_username');
            if (savedUser) {
                userInput.value = savedUser;
                rememberCheckbox.checked = true;
                pwInput.focus();
            }
        } catch(e) {}

        // Password Show/Hide Toggle
        if (toggleBtn) {
            toggleBtn.addEventListener('click', function() {
                const isPw = pwInput.type === 'password';
                pwInput.type = isPw ? 'text' : 'password';
                toggleBtn.innerHTML = isPw 
                    ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>'
                    : '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>';
            });
        }

        // Lockout countdown timer
        let lockoutRemaining = $lockedOutSeconds;
        if (lockoutRemaining > 0) {
            submitBtn.disabled = true;
            const timerEl = document.getElementById('countdown');
            const interval = setInterval(function() {
                lockoutRemaining--;
                if (timerEl) timerEl.textContent = lockoutRemaining;
                if (lockoutRemaining <= 0) {
                    clearInterval(interval);
                    submitBtn.disabled = false;
                    const banner = document.getElementById('lockoutBanner');
                    if (banner) banner.style.display = 'none';
                }
            }, 1000);
        }

        // Submit loading state and storage
        form.addEventListener('submit', function() {
            if (rememberCheckbox.checked) {
                try { localStorage.setItem('mft_saved_username', userInput.value.trim()); } catch(e) {}
            } else {
                try { localStorage.removeItem('mft_saved_username'); } catch(e) {}
            }
            submitBtn.disabled = true;
            btnSpinner.style.display = 'inline-block';
            btnLabel.textContent = 'Logging in...';
        });
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
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MFT - Web Transfer Client</title>
    <link rel="icon" type="image/png" href="/Web/Shared/images/gs-atom-logo.png">
    <style>
${EFT_CSS}

/* Application Layout */
.app-header {
    background: #ffffff;
    border-bottom: 2px solid #009bd9;
    padding: 10px 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.header-brand {
    display: flex;
    align-items: center;
    gap: 12px;
}
.header-logo {
    height: 38px;
    width: auto;
}
.header-title-box {
    display: flex;
    flex-direction: column;
}
.header-title {
    font-size: 16px;
    font-weight: bold;
    color: #231f20;
    letter-spacing: -0.2px;
}
.header-subtitle {
    font-size: 11px;
    color: #009bd9;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.header-user-section {
    display: flex;
    align-items: center;
    gap: 16px;
    font-size: 12px;
    color: #444444;
}
.user-badge {
    background: #f0f7fb;
    border: 1px solid #bce2f5;
    padding: 3px 8px;
    border-radius: 2px;
    color: #0072a3;
    font-weight: 600;
}

.main-content {
    max-width: 1380px;
    margin: 16px auto;
    padding: 0 16px 40px 16px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

/* Nav & Breadcrumbs Toolbar */
.toolbar-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #f8fafc;
    border: 1px solid #d1d5db;
    padding: 8px 14px;
    border-radius: 2px;
    flex-wrap: wrap;
    gap: 10px;
}
.breadcrumbs-container {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #333333;
    flex: 1;
    min-width: 250px;
}
.breadcrumb-crumb {
    color: #0066cc;
    cursor: pointer;
    text-decoration: none;
}
.breadcrumb-crumb:hover {
    text-decoration: underline;
}
.breadcrumb-separator {
    color: #999999;
}
.breadcrumb-current {
    font-weight: bold;
    color: #111111;
}

.toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
}

/* The signature EFT fieldsets for sections */
.section-fieldset {
    border: 2px solid #009bd9;
    background: #ffffff;
    padding: 16px 20px;
    margin: 0;
}
.section-legend {
    color: #333333;
    font-family: Arial, Helvetica, sans-serif;
    font-size: 13px;
    font-weight: bold;
    padding: 0 6px;
}

/* Upload Area Grid */
.upload-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
    align-items: start;
}
@media (max-width: 960px) {
    .upload-grid {
        grid-template-columns: 1fr;
    }
}

/* Dropzone */
.dropzone {
    border: 2px dashed #009bd9;
    background: #fcfdfd;
    border-radius: 2px;
    padding: 24px 16px;
    text-align: center;
    cursor: pointer;
    transition: all 0.2s ease;
}
.dropzone:hover, .dropzone.dragover {
    background: #eef8fc;
    border-color: #0077aa;
}
.dropzone-icon {
    margin-bottom: 8px;
    color: #009bd9;
}
.dropzone-title {
    font-size: 14px;
    font-weight: bold;
    color: #111111;
    margin-bottom: 4px;
}
.dropzone-subtitle {
    font-size: 12px;
    color: #666666;
    margin-bottom: 12px;
}
.dropzone-meta {
    font-size: 11px;
    color: #888888;
}

/* Staged Files Queue */
.staged-box {
    margin-top: 12px;
    max-height: 140px;
    overflow-y: auto;
    border: 1px solid #d1d5db;
    background: #ffffff;
}
.staged-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 10px;
    border-bottom: 1px solid #eeeeee;
    font-size: 12px;
}
.staged-item:last-child {
    border-bottom: none;
}
.staged-info {
    display: flex;
    align-items: center;
    gap: 8px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.file-badge {
    background: #e6f4fb;
    color: #0077aa;
    font-size: 10px;
    font-weight: bold;
    padding: 2px 6px;
    border-radius: 2px;
    text-transform: uppercase;
}
.staged-remove {
    background: transparent;
    border: none;
    color: #cc0000;
    cursor: pointer;
    font-size: 14px;
    font-weight: bold;
    padding: 0 4px;
}

/* Transfer Details Form */
.transfer-form-grid {
    display: grid;
    grid-template-columns: 100px 1fr;
    gap: 10px 12px;
    align-items: center;
    font-size: 12px;
}
.transfer-label {
    text-align: right;
    color: #333333;
    font-weight: bold;
}
.transfer-input {
    width: 100%;
}
.transfer-actions {
    grid-column: 2;
    margin-top: 6px;
    display: flex;
    align-items: center;
    gap: 10px;
}

/* Upload Progress */
.progress-card {
    margin-top: 14px;
    border: 1px solid #bce2f5;
    background: #f5faff;
    padding: 12px 16px;
    border-radius: 2px;
    display: none;
}
.progress-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
    font-size: 12px;
    font-weight: bold;
    color: #0072a3;
}
.progress-track {
    background: #e2e8f0;
    height: 12px;
    border-radius: 2px;
    overflow: hidden;
    position: relative;
}
.progress-fill {
    background: linear-gradient(90deg, #009bd9, #00c0f7);
    height: 100%;
    width: 0%;
    transition: width 0.15s ease-out;
}
.progress-meta {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: #555555;
    margin-top: 6px;
}

/* Repository Table */
.table-filter-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    gap: 12px;
    flex-wrap: wrap;
}
.search-wrapper {
    position: relative;
    display: flex;
    align-items: center;
}
.search-input {
    width: 260px;
    padding-left: 24px;
}
.search-icon {
    position: absolute;
    left: 7px;
    color: #777777;
    pointer-events: none;
}

.eft-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
    text-align: left;
    border: 1px solid #d1d5db;
}
.eft-table th {
    background: #f1f5f9;
    color: #334155;
    font-weight: bold;
    padding: 8px 10px;
    border-bottom: 2px solid #009bd9;
    border-right: 1px solid #e2e8f0;
    cursor: pointer;
    user-select: none;
    white-space: nowrap;
}
.eft-table th:last-child {
    border-right: none;
}
.eft-table th:hover {
    background: #e2e8f0;
}
.eft-table td {
    padding: 7px 10px;
    border-bottom: 1px solid #e2e8f0;
    border-right: 1px solid #f1f5f9;
    color: #222222;
}
.eft-table td:last-child {
    border-right: none;
}
.eft-table tr:hover td {
    background: #f8fafc;
}
.item-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 500;
}
.item-link {
    color: #0066cc;
    cursor: pointer;
    text-decoration: none;
}
.item-link:hover {
    text-decoration: underline;
}

/* Status Badges */
.status-pill {
    display: inline-block;
    padding: 2px 6px;
    font-size: 10px;
    font-weight: bold;
    border-radius: 2px;
    text-transform: uppercase;
}
.status-active { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }
.status-pending { background: #fef9c3; color: #a16207; border: 1px solid #fef08a; }
.status-failed { background: #fee2e2; color: #b91c1c; border: 1px solid #fecaca; }

/* Pagination */
.pagination-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 10px;
    font-size: 12px;
    color: #666666;
}

/* Modal Dialog */
.eft-modal-backdrop {
    position: fixed;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.45);
    display: none;
    align-items: center;
    justify-content: center;
    z-index: 1000;
}
.eft-modal {
    width: 360px;
    max-width: 90vw;
    border: 2px solid #009bd9;
    background: #ffffff;
    padding: 20px 24px;
}
.modal-title {
    font-size: 14px;
    font-weight: bold;
    color: #231f20;
    margin-bottom: 12px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}
    </style>
</head>
<body>

    <!-- MFT Header Bar -->
    <header class="app-header">
        <div class="header-brand">
            <img src="/Web/Shared/images/gs-atom-logo.png" alt="MFT Logo" class="header-logo" />
            <div class="header-title-box">
                <span class="header-title">Bandhan Bank MFT Portal</span>
                <span class="header-subtitle">Globalscape EFT™ - Web Transfer Client</span>
            </div>
        </div>
        <div class="header-user-section">
            <span>Logged in as: <span class="user-badge" id="currentUserDisplay">$currentUser</span></span>
            ${if (requireAuth) """
            <form method="post" action="/logout" style="display:inline;">
                <button type="submit" class="eft-btn eft-btn-sm" title="End authenticated session">Log Out</button>
            </form>
            """ else ""}
        </div>
    </header>

    <main class="main-content">

        <!-- Breadcrumbs & Directory Actions -->
        <div class="toolbar-bar">
            <div class="breadcrumbs-container" id="breadcrumbBar">
                <span class="breadcrumb-crumb" onclick="navigateTo('/')">📁 Root</span>
            </div>
            <div class="toolbar-actions">
                ${if (!readOnly) """
                <button type="button" class="eft-btn eft-btn-sm" onclick="openNewFolderModal()">
                    <span>➕ New Folder</span>
                </button>
                """ else ""}
                <button type="button" class="eft-btn eft-btn-sm" onclick="refreshList()">
                    <span>🔄 Refresh</span>
                </button>
            </div>
        </div>

        <!-- File Upload & Transfer Section -->
        ${if (!readOnly) """
        <fieldset class="section-fieldset">
            <legend class="section-legend">File Upload & Transfer</legend>

            <div class="upload-grid">
                <!-- Dropzone Area -->
                <div>
                    <div class="dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
                        <input type="file" id="fileInput" multiple style="display:none;" onchange="handleFileSelect(event)" />
                        <div class="dropzone-icon">
                            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                                <polyline points="17 8 12 3 7 8"></polyline>
                                <line x1="12" y1="3" x2="12" y2="15"></line>
                            </svg>
                        </div>
                        <div class="dropzone-title">Drag & Drop Files Here or Click to Browse</div>
                        <div class="dropzone-subtitle">Select multiple files for enterprise secure upload</div>
                        <div class="dropzone-meta">Maximum transfer payload: 2 GB • Permitted: All corporate formats</div>
                    </div>

                    <!-- Staged Queue -->
                    <div id="stagedContainer" style="display:none;">
                        <div style="font-size: 11px; font-weight: bold; margin-top: 8px; color: #444444;">
                            Selected Files (<span id="stagedCount">0</span>):
                        </div>
                        <div class="staged-box" id="stagedList"></div>
                    </div>
                </div>

                <!-- Transfer Details Form -->
                <div>
                    <form id="transferDetailsForm" onsubmit="event.preventDefault(); startBatchUpload();">
                        <div class="transfer-form-grid">
                            <label class="transfer-label">Sender:</label>
                            <div>
                                <input type="text" class="eft-input transfer-input" value="$currentUser" readonly style="background:#f1f5f9; color:#475569;" />
                            </div>

                            <label class="transfer-label">Recipient: <span style="color:#cf1322;">*</span></label>
                            <div>
                                <select id="recipientSelect" class="eft-select transfer-input" required>
                                    <option value="Bandhan Bank Central MFT Repository">Bandhan Bank Central MFT Repository</option>
                                    <option value="IT Operations & Infrastructure">IT Operations & Infrastructure</option>
                                    <option value="Audit & Compliance Wing">Audit & Compliance Wing</option>
                                    <option value="Treasury & Financial Settlements">Treasury & Financial Settlements</option>
                                    <option value="Risk Management Division">Risk Management Division</option>
                                    <option value="External Partner Transfer Node">External Partner Transfer Node</option>
                                </select>
                            </div>

                            <label class="transfer-label">Purpose: <span style="color:#cf1322;">*</span></label>
                            <div>
                                <select id="categorySelect" class="eft-select transfer-input" required>
                                    <option value="General Batch File Transfer">General Batch File Transfer</option>
                                    <option value="Periodic Audit Logs">Periodic Audit Logs</option>
                                    <option value="Daily Financial Reconciliation">Daily Financial Reconciliation</option>
                                    <option value="Regulatory KYC Documentation">Regulatory KYC Documentation</option>
                                    <option value="Scheduled MIS Reporting">Scheduled MIS Reporting</option>
                                </select>
                            </div>

                            <label class="transfer-label">Remarks:</label>
                            <div>
                                <input type="text" id="remarksInput" class="eft-input transfer-input" placeholder="Optional transfer notes or reference ticket" />
                            </div>

                            <div></div>
                            <div class="transfer-actions">
                                <button type="submit" id="startUploadBtn" class="eft-btn eft-btn-primary" disabled>
                                    <span>Upload Selected Files</span>
                                </button>
                                <button type="button" class="eft-btn" onclick="clearStagedFiles()">
                                    <span>Clear</span>
                                </button>
                            </div>
                        </div>
                    </form>

                    <!-- Real-time Upload Progress Bar -->
                    <div class="progress-card" id="progressCard">
                        <div class="progress-header">
                            <span id="progressFileTitle">Uploading...</span>
                            <span id="progressPercent">0%</span>
                        </div>
                        <div class="progress-track">
                            <div class="progress-fill" id="progressFill"></div>
                        </div>
                        <div class="progress-meta">
                            <span id="progressBytes">0 KB / 0 KB</span>
                            <span id="progressSpeed">-- MB/s</span>
                            <button type="button" class="eft-btn eft-btn-sm" onclick="cancelActiveUpload()" style="margin-left: 8px;">Cancel</button>
                        </div>
                    </div>
                </div>
            </div>
        </fieldset>
        """ else ""}

        <!-- Transfer Repository / File Table -->
        <fieldset class="section-fieldset">
            <legend class="section-legend">Transfer Repository & Files</legend>

            <div class="table-filter-bar">
                <div class="search-wrapper">
                    <svg class="search-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="11" cy="11" r="8"></circle>
                        <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                    <input type="text" id="tableSearchInput" class="eft-input search-input" placeholder="Search files by name..." oninput="handleSearch(this.value)" />
                </div>
                <div style="display: flex; align-items: center; gap: 8px;">
                    <label style="font-size: 11px; color: #555555; font-weight: bold;">Filter Type:</label>
                    <select id="typeFilter" class="eft-select" onchange="handleTypeFilter(this.value)">
                        <option value="ALL">All Files & Folders</option>
                        <option value="DIR">Folders Only</option>
                        <option value="DOC">Documents (PDF, Office, Text)</option>
                        <option value="IMG">Images (PNG, JPG, SVG)</option>
                        <option value="ARCHIVE">Archives (ZIP, TAR, GZ)</option>
                    </select>
                </div>
            </div>

            <table class="eft-table">
                <thead>
                    <tr>
                        <th onclick="sortTable('name')">Name ⬍</th>
                        <th onclick="sortTable('size')">Size ⬍</th>
                        <th onclick="sortTable('modified')">Last Modified ⬍</th>
                        <th>Sender</th>
                        <th>Recipient</th>
                        <th>Status</th>
                        <th style="text-align: right;">Actions</th>
                    </tr>
                </thead>
                <tbody id="fileTableBody">
                    <tr><td colspan="7" style="text-align:center; padding: 24px; color: #888888;">Loading repository entries...</td></tr>
                </tbody>
            </table>

            <div class="pagination-bar">
                <div id="paginationSummary">Showing 0 of 0 entries</div>
                <div style="display: flex; gap: 6px;">
                    <button type="button" id="prevPageBtn" class="eft-btn eft-btn-sm" onclick="changePage(-1)" disabled>Previous</button>
                    <button type="button" id="nextPageBtn" class="eft-btn eft-btn-sm" onclick="changePage(1)" disabled>Next</button>
                </div>
            </div>
        </fieldset>

    </main>

    <!-- New Folder Modal -->
    <div class="eft-modal-backdrop" id="newFolderModal">
        <div class="eft-modal">
            <div class="modal-title">
                <span>Create New Directory</span>
                <a href="javascript:void(0)" onclick="closeNewFolderModal()" style="color:#666666; font-size: 16px; font-weight: bold;">✕</a>
            </div>
            <form onsubmit="event.preventDefault(); submitCreateFolder();">
                <div style="margin-bottom: 14px;">
                    <label style="display: block; font-size: 12px; font-weight: bold; margin-bottom: 4px;">Folder Name:</label>
                    <input type="text" id="newFolderNameInput" class="eft-input" style="width: 100%;" required placeholder="e.g. Q4_Reports" />
                </div>
                <div style="text-align: right; display: flex; justify-content: flex-end; gap: 8px;">
                    <button type="button" class="eft-btn" onclick="closeNewFolderModal()">Cancel</button>
                    <button type="submit" class="eft-btn eft-btn-primary">Create</button>
                </div>
            </form>
        </div>
    </div>

    <script>
        let currentPath = '$currentPath' || '/';
        let rawEntries = [];
        let filteredEntries = [];
        let currentPage = 1;
        const pageSize = 15;
        let stagedFiles = [];
        let activeXhr = null;
        let sortKey = 'name';
        let sortAsc = true;
        let activeSearchQuery = '';
        let activeTypeFilter = 'ALL';

        const currentUser = "$currentUser";
        const isReadOnly = ${readOnly};

        async function loadDirectory(path) {
            currentPath = path || '/';
            updateBreadcrumbs();
            const tableBody = document.getElementById('fileTableBody');
            tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 24px; color: #888888;">Loading repository entries...</td></tr>';
            try {
                const response = await fetch('/api/list?path=' + encodeURIComponent(currentPath));
                if (response.status === 401) {
                    window.location.href = '/Web/Account/Login.htm';
                    return;
                }
                if (!response.ok) throw new Error('HTTP ' + response.status);
                const data = await response.json();
                rawEntries = data.entries || [];
                applyFiltersAndRender();
            } catch(e) {
                tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 20px; color: #cf1322;">Failed to load directory entries: ' + e.message + '</td></tr>';
            }
        }

        function updateBreadcrumbs() {
            const bar = document.getElementById('breadcrumbBar');
            const parts = currentPath.split('/').filter(p => p.length > 0);
            let html = '<span class="breadcrumb-crumb" onclick="navigateTo(\'/\')">📁 Root</span>';
            let accumulated = '';
            for (let i = 0; i < parts.length; i++) {
                accumulated += '/' + parts[i];
                const p = accumulated;
                const isLast = (i === parts.length - 1);
                html += ' <span class="breadcrumb-separator">/</span> ';
                if (isLast) {
                    html += '<span class="breadcrumb-current">' + escapeHtml(parts[i]) + '</span>';
                } else {
                    html += '<span class="breadcrumb-crumb" onclick="navigateTo(\'' + escapeHtml(p) + '\')">' + escapeHtml(parts[i]) + '</span>';
                }
            }
            bar.innerHTML = html;
        }

        function navigateTo(path) {
            loadDirectory(path);
        }

        function refreshList() {
            loadDirectory(currentPath);
        }

        function handleSearch(q) {
            activeSearchQuery = (q || '').trim().toLowerCase();
            currentPage = 1;
            applyFiltersAndRender();
        }

        function handleTypeFilter(type) {
            activeTypeFilter = type;
            currentPage = 1;
            applyFiltersAndRender();
        }

        function sortTable(key) {
            if (sortKey === key) {
                sortAsc = !sortAsc;
            } else {
                sortKey = key;
                sortAsc = true;
            }
            applyFiltersAndRender();
        }

        function applyFiltersAndRender() {
            filteredEntries = rawEntries.filter(entry => {
                // Search filter
                if (activeSearchQuery && !entry.name.toLowerCase().includes(activeSearchQuery)) {
                    return false;
                }
                // Type filter
                if (activeTypeFilter === 'DIR' && !entry.isDirectory) return false;
                if (activeTypeFilter === 'DOC') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['pdf', 'doc', 'docx', 'xls', 'xlsx', 'txt', 'csv', 'ppt', 'pptx'].includes(ext)) return false;
                }
                if (activeTypeFilter === 'IMG') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp'].includes(ext)) return false;
                }
                if (activeTypeFilter === 'ARCHIVE') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['zip', 'tar', 'gz', '7z', 'rar', 'bz2'].includes(ext)) return false;
                }
                return true;
            });

            // Sorting
            filteredEntries.sort((a, b) => {
                // Directories first
                if (a.isDirectory && !b.isDirectory) return -1;
                if (!a.isDirectory && b.isDirectory) return 1;

                let valA, valB;
                if (sortKey === 'size') {
                    valA = a.sizeBytes || 0;
                    valB = b.sizeBytes || 0;
                } else if (sortKey === 'modified') {
                    valA = a.modifiedMillis || 0;
                    valB = b.modifiedMillis || 0;
                } else {
                    valA = a.name.toLowerCase();
                    valB = b.name.toLowerCase();
                }

                if (valA < valB) return sortAsc ? -1 : 1;
                if (valA > valB) return sortAsc ? 1 : -1;
                return 0;
            });

            renderTablePage();
        }

        function renderTablePage() {
            const tableBody = document.getElementById('fileTableBody');
            const total = filteredEntries.length;
            const start = (currentPage - 1) * pageSize;
            const end = Math.min(start + pageSize, total);
            const pageItems = filteredEntries.slice(start, end);

            if (total === 0) {
                tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 24px; color: #888888;">No files or directories found in this repository location.</td></tr>';
            } else {
                let html = '';
                for (const item of pageItems) {
                    const icon = item.isDirectory ? '📁' : getFileIcon(item.name);
                    const formattedSize = item.isDirectory ? '--' : (item.sizeFormatted || formatBytes(item.sizeBytes));
                    const formattedDate = item.modifiedFormatted || formatDate(item.modifiedMillis);

                    html += '<tr>' +
                        '<td>' +
                            '<div class="item-name-cell">' +
                                '<span>' + icon + '</span>' +
                                (item.isDirectory 
                                    ? '<span class="item-link" onclick="navigateTo(\'' + escapeHtml(item.path) + '\')">' + escapeHtml(item.name) + '</span>'
                                    : '<span>' + escapeHtml(item.name) + '</span>'
                                ) +
                            '</div>' +
                        '</td>' +
                        '<td>' + formattedSize + '</td>' +
                        '<td>' + formattedDate + '</td>' +
                        '<td><span style="color:#555555;">' + escapeHtml(currentUser) + '</span></td>' +
                        '<td><span style="color:#555555;">MFT Repository</span></td>' +
                        '<td><span class="status-pill status-active">Verified</span></td>' +
                        '<td style="text-align: right; white-space: nowrap;">' +
                            (item.isDirectory
                                ? '<button type="button" class="eft-btn eft-btn-sm" onclick="navigateTo(\'' + escapeHtml(item.path) + '\')">Open</button> '
                                : '<a href="/download?path=' + encodeURIComponent(item.path) + '" class="eft-btn eft-btn-sm" download>Download</a> '
                            ) +
                            (!isReadOnly ? '<button type="button" class="eft-btn eft-btn-sm" onclick="promptRename(\'' + escapeHtml(item.path) + '\', \'' + escapeHtml(item.name) + '\')">Rename</button> ' : '') +
                            (!isReadOnly ? '<button type="button" class="eft-btn eft-btn-sm" style="color:#cf1322;" onclick="confirmDelete(\'' + escapeHtml(item.path) + '\')">Delete</button>' : '') +
                        '</td>' +
                    '</tr>';
                }
                tableBody.innerHTML = html;
            }

            // Pagination update
            document.getElementById('paginationSummary').textContent = total === 0 ? 'Showing 0 of 0 entries' : 'Showing ' + (start + 1) + '-' + end + ' of ' + total + ' entries';
            document.getElementById('prevPageBtn').disabled = (currentPage <= 1);
            document.getElementById('nextPageBtn').disabled = (end >= total);
        }

        function changePage(delta) {
            currentPage += delta;
            renderTablePage();
        }

        // Staged Files Selection
        function handleFileSelect(e) {
            const files = Array.from(e.target.files || []);
            addFilesToStage(files);
            e.target.value = '';
        }

        function addFilesToStage(newFiles) {
            for (const f of newFiles) {
                stagedFiles.push(f);
            }
            renderStagedList();
        }

        function renderStagedList() {
            const container = document.getElementById('stagedContainer');
            const list = document.getElementById('stagedList');
            const countEl = document.getElementById('stagedCount');
            const submitBtn = document.getElementById('startUploadBtn');

            countEl.textContent = stagedFiles.length;
            if (stagedFiles.length === 0) {
                container.style.display = 'none';
                submitBtn.disabled = true;
                return;
            }

            container.style.display = 'block';
            submitBtn.disabled = false;

            let html = '';
            stagedFiles.forEach((file, idx) => {
                const ext = getExt(file.name) || 'FILE';
                html += '<div class="staged-item">' +
                    '<div class="staged-info">' +
                        '<span class="file-badge">' + ext + '</span>' +
                        '<span title="' + escapeHtml(file.name) + '">' + escapeHtml(file.name) + '</span>' +
                        '<span style="color:#888888; font-size:11px;">(' + formatBytes(file.size) + ')</span>' +
                    '</div>' +
                    '<button type="button" class="staged-remove" onclick="removeStagedFile(' + idx + ')" title="Remove file">✕</button>' +
                '</div>';
            });
            list.innerHTML = html;
        }

        function removeStagedFile(idx) {
            stagedFiles.splice(idx, 1);
            renderStagedList();
        }

        function clearStagedFiles() {
            stagedFiles = [];
            renderStagedList();
        }

        // Drag & Drop
        const dropzone = document.getElementById('dropzone');
        if (dropzone) {
            ['dragenter', 'dragover'].forEach(name => {
                dropzone.addEventListener(name, (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    dropzone.classList.add('dragover');
                });
            });
            ['dragleave', 'drop'].forEach(name => {
                dropzone.addEventListener(name, (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    dropzone.classList.remove('dragover');
                });
            });
            dropzone.addEventListener('drop', (e) => {
                const dt = e.dataTransfer;
                if (dt && dt.files && dt.files.length > 0) {
                    addFilesToStage(Array.from(dt.files));
                }
            });
        }

        // Upload Execution
        function startBatchUpload() {
            if (stagedFiles.length === 0) return;

            const recipient = document.getElementById('recipientSelect').value;
            const category = document.getElementById('categorySelect').value;
            const remarks = document.getElementById('remarksInput').value;

            const progressCard = document.getElementById('progressCard');
            const progressFill = document.getElementById('progressFill');
            const progressPercent = document.getElementById('progressPercent');
            const progressBytes = document.getElementById('progressBytes');
            const progressSpeed = document.getElementById('progressSpeed');
            const progressTitle = document.getElementById('progressFileTitle');
            const uploadBtn = document.getElementById('startUploadBtn');

            progressCard.style.display = 'block';
            uploadBtn.disabled = true;

            const formData = new FormData();
            stagedFiles.forEach(f => formData.append('file', f));
            formData.append('recipient', recipient);
            formData.append('category', category);
            formData.append('remarks', remarks);

            let startTime = Date.now();
            activeXhr = new XMLHttpRequest();
            activeXhr.open('POST', '/api/upload?path=' + encodeURIComponent(currentPath));

            activeXhr.upload.onprogress = function(e) {
                if (e.lengthComputable) {
                    const pct = Math.round((e.loaded / e.total) * 100);
                    progressFill.style.width = pct + '%';
                    progressPercent.textContent = pct + '%';
                    progressBytes.textContent = formatBytes(e.loaded) + ' / ' + formatBytes(e.total);

                    const elapsedSec = (Date.now() - startTime) / 1000;
                    if (elapsedSec > 0.3) {
                        const speedMB = (e.loaded / (1024 * 1024 * elapsedSec)).toFixed(2);
                        progressSpeed.textContent = speedMB + ' MB/s';
                    }
                }
            };

            activeXhr.onload = function() {
                activeXhr = null;
                uploadBtn.disabled = false;
                if (activeXhr && activeXhr.status >= 200 && activeXhr.status < 300 || this.status === 200) {
                    progressPercent.textContent = '100% Completed';
                    progressFill.style.width = '100%';
                    progressTitle.textContent = 'Transfer completed successfully.';
                    clearStagedFiles();
                    setTimeout(() => {
                        progressCard.style.display = 'none';
                        refreshList();
                    }, 1200);
                } else {
                    progressTitle.textContent = 'Upload Failed: ' + (this.responseText || 'Server rejected request');
                    alert('Upload failed: ' + (this.responseText || 'Server error'));
                }
            };

            activeXhr.onerror = function() {
                activeXhr = null;
                uploadBtn.disabled = false;
                progressTitle.textContent = 'Upload interrupted by network error.';
                alert('Transfer error occurred. Please check network connection and retry.');
            };

            progressTitle.textContent = 'Uploading ' + stagedFiles.length + ' file(s)...';
            activeXhr.send(formData);
        }

        function cancelActiveUpload() {
            if (activeXhr) {
                activeXhr.abort();
                activeXhr = null;
                document.getElementById('progressFileTitle').textContent = 'Upload cancelled by user.';
                document.getElementById('startUploadBtn').disabled = false;
            }
        }

        // New Folder Modal
        function openNewFolderModal() {
            document.getElementById('newFolderNameInput').value = '';
            document.getElementById('newFolderModal').style.display = 'flex';
            setTimeout(() => document.getElementById('newFolderNameInput').focus(), 50);
        }
        function closeNewFolderModal() {
            document.getElementById('newFolderModal').style.display = 'none';
        }
        async function submitCreateFolder() {
            const name = document.getElementById('newFolderNameInput').value.trim();
            if (!name) return;
            closeNewFolderModal();
            try {
                const res = await fetch('/api/mkdir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ parentPath: currentPath, name: name })
                });
                if (res.ok) {
                    refreshList();
                } else {
                    alert('Folder creation failed: ' + await res.text());
                }
            } catch(e) {
                alert('Network error: ' + e.message);
            }
        }

        // Rename & Delete
        async function promptRename(path, oldName) {
            const newName = prompt('Enter new name for "' + oldName + '":', oldName);
            if (!newName || newName.trim() === '' || newName.trim() === oldName) return;
            try {
                const res = await fetch('/api/rename', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: path, newName: newName.trim() })
                });
                if (res.ok) {
                    refreshList();
                } else {
                    alert('Rename failed: ' + await res.text());
                }
            } catch(e) {
                alert('Rename error: ' + e.message);
            }
        }

        async function confirmDelete(path) {
            if (!confirm('Are you sure you want to permanently delete "' + path + '"?')) return;
            try {
                const res = await fetch('/api/delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: path })
                });
                if (res.ok) {
                    refreshList();
                } else {
                    alert('Delete failed: ' + await res.text());
                }
            } catch(e) {
                alert('Delete error: ' + e.message);
            }
        }

        // Helper utilities
        function formatBytes(bytes) {
            if (!bytes || bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
        }

        function formatDate(ms) {
            if (!ms) return '--';
            const d = new Date(ms);
            return d.getFullYear() + '-' +
                String(d.getMonth() + 1).padStart(2, '0') + '-' +
                String(d.getDate()).padStart(2, '0') + ' ' +
                String(d.getHours()).padStart(2, '0') + ':' +
                String(d.getMinutes()).padStart(2, '0');
        }

        function getExt(name) {
            const idx = name.lastIndexOf('.');
            return idx > 0 ? name.substring(idx + 1).toLowerCase() : '';
        }

        function getFileIcon(name) {
            const ext = getExt(name);
            if (['pdf'].includes(ext)) return '📕';
            if (['doc', 'docx', 'txt', 'rtf', 'odt'].includes(ext)) return '📄';
            if (['xls', 'xlsx', 'csv'].includes(ext)) return '📊';
            if (['zip', 'tar', 'gz', '7z', 'rar'].includes(ext)) return '📦';
            if (['png', 'jpg', 'jpeg', 'gif', 'svg'].includes(ext)) return '🖼️';
            return '📄';
        }

        function escapeHtml(str) {
            if (!str) return '';
            return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
        }

        // Initialize view
        loadDirectory(currentPath);
    </script>
</body>
</html>
""".trimIndent()
}
