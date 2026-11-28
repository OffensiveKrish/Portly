/* Portly Product Website — Interactive Controls */
(function () {
  "use strict";

  // 1. Theme Switcher (dark -> light -> amoled)
  const THEME_ORDER = ["dark", "light", "amoled"];
  const THEME_LABELS = { dark: "Dark", light: "Light", amoled: "AMOLED" };
  const themeBtn = document.getElementById("theme-toggle");
  const themeLabel = document.getElementById("theme-label");

  function applyTheme(theme) {
    const valid = THEME_ORDER.includes(theme) ? theme : "dark";
    document.documentElement.setAttribute("data-theme", valid);
    if (themeLabel) themeLabel.textContent = THEME_LABELS[valid];
    try {
      localStorage.setItem("portly_site_theme", valid);
    } catch (_) {}
  }

  let initialTheme = "dark";
  try {
    const saved = localStorage.getItem("portly_site_theme");
    if (saved && THEME_ORDER.includes(saved)) {
      initialTheme = saved;
    } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches) {
      initialTheme = "light";
    }
  } catch (_) {}
  applyTheme(initialTheme);

  if (themeBtn) {
    themeBtn.addEventListener("click", () => {
      const current = document.documentElement.getAttribute("data-theme") || "dark";
      const next = THEME_ORDER[(THEME_ORDER.indexOf(current) + 1) % THEME_ORDER.length];
      applyTheme(next);
    });
  }

  // 2. Mobile Navigation Menu
  const menuToggle = document.getElementById("menu-toggle");
  const navLinks = document.getElementById("nav-links");

  if (menuToggle && navLinks) {
    menuToggle.addEventListener("click", () => {
      const isOpen = navLinks.classList.toggle("open");
      menuToggle.setAttribute("aria-expanded", String(isOpen));
    });

    navLinks.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", () => {
        navLinks.classList.remove("open");
        menuToggle.setAttribute("aria-expanded", "false");
      });
    });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && navLinks.classList.contains("open")) {
        navLinks.classList.remove("open");
        menuToggle.setAttribute("aria-expanded", "false");
        menuToggle.focus();
      }
    });
  }

  // 3. Interactive Phone Preview Tabs
  const PHONE_SCREENS = {
    home: `
      <div class="phone-card">
        <div class="phone-card-title">LAN &amp; Connectivity</div>
        <div class="phone-card-sub">Wi-Fi Connected &bull; 192.168.1.42</div>
      </div>
      <div class="phone-card">
        <div class="phone-card-title">Quick Connect URI</div>
        <div class="phone-row"><span>sftp://nas.local:22/media</span><span class="badge-chip">SFTP</span></div>
      </div>
      <div class="phone-card">
        <div class="phone-card-title">Active Services</div>
        <div class="phone-row"><span>HTTPS Web Server (:8443)</span><span class="badge-chip badge-green">RUNNING</span></div>
        <div class="phone-row"><span>FTPS Server (:2122)</span><span class="badge-chip badge-green">TLS 1.3</span></div>
      </div>
    `,
    servers: `
      <div class="phone-card">
        <div class="phone-card-title">Embedded Server Suite</div>
        <div class="phone-row"><span>HTTPS Server &bull; :8443</span><span class="badge-chip badge-green">ACTIVE</span></div>
        <div class="phone-row"><span>FTPS Server &bull; :2122</span><span class="badge-chip badge-green">ACTIVE</span></div>
        <div class="phone-row"><span>HTTP Server &bull; :8080</span><span class="badge-chip">STOPPED</span></div>
        <div class="phone-row"><span>FTP Server &bull; :2121</span><span class="badge-chip">STOPPED</span></div>
      </div>
      <div class="phone-card">
        <div class="phone-card-title">Peer Pairing &amp; Clients</div>
        <div class="phone-card-sub">LAN-only bind &bull; 192-bit 60s QR token ready</div>
      </div>
    `,
    files: `
      <div class="phone-card">
        <div class="phone-card-title">Dual-Pane File Browser</div>
        <div class="phone-row"><span>Internal / Documents</span><span class="badge-chip">LOCAL</span></div>
        <div class="phone-row"><span>Home NAS / Backups</span><span class="badge-chip">SMB3</span></div>
      </div>
      <div class="phone-card">
        <div class="phone-card-title">Offline Viewers &amp; ZIP</div>
        <div class="phone-row"><span>architecture-spec.pdf</span><span class="badge-chip">PDF</span></div>
        <div class="phone-row"><span>release-bundle.zip</span><span class="badge-chip">ZIP</span></div>
        <div class="phone-row"><span>demo-recording.mp4</span><span class="badge-chip">MEDIA3</span></div>
      </div>
    `,
    connect: `
      <div class="phone-card">
        <div class="phone-card-title">Saved Storage Profiles</div>
        <div class="phone-row"><span>Production SFTP</span><span class="badge-chip">SSHv2</span></div>
        <div class="phone-row"><span>Synology WebDAV</span><span class="badge-chip">HTTPS</span></div>
        <div class="phone-row"><span>Office SMB Share</span><span class="badge-chip">SMB 3.1.1</span></div>
        <div class="phone-row"><span>Backup FTPS</span><span class="badge-chip">TOFU PIN</span></div>
      </div>
      <div class="phone-card">
        <div class="phone-card-title">Credential Vault</div>
        <div class="phone-card-sub">AES-256-GCM hardware-backed AndroidKeyStore</div>
      </div>
    `,
    transfers: `
      <div class="phone-card">
        <div class="phone-card-title">64KB Streaming Queue</div>
        <div class="phone-row"><span>backup-archive.zip</span><span class="badge-chip badge-green">78%</span></div>
        <div class="phone-card-sub">42.4 MB / s &bull; .part resume &bull; SHA-256 verified</div>
      </div>
      <div class="phone-card">
        <div class="phone-card-title">Completed &amp; Audited</div>
        <div class="phone-row"><span>photos-2026.tar</span><span class="badge-chip badge-green">VERIFIED</span></div>
        <div class="phone-row"><span>notes.md</span><span class="badge-chip badge-green">DONE</span></div>
      </div>
    `,
  };

  const phoneBody = document.getElementById("phone-screen-body");
  const phoneNavBtns = document.querySelectorAll(".phone-nav-btn");

  phoneNavBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const target = btn.getAttribute("data-screen");
      if (!target || !PHONE_SCREENS[target] || !phoneBody) return;
      phoneNavBtns.forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      phoneBody.innerHTML = PHONE_SCREENS[target];
    });
  });

  // 4. Module Showcase Tabs
  const tabBtns = document.querySelectorAll(".tab-btn[role='tab']");
  const tabPanels = document.querySelectorAll(".module-panel[role='tabpanel']");

  tabBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const targetId = btn.getAttribute("aria-controls");
      tabBtns.forEach((b) => b.setAttribute("aria-selected", "false"));
      tabPanels.forEach((p) => {
        p.hidden = true;
      });
      btn.setAttribute("aria-selected", "true");
      const panel = targetId ? document.getElementById(targetId) : null;
      if (panel) panel.hidden = false;
    });
  });

  // 5. FAQ Accordion
  document.querySelectorAll(".faq-trigger").forEach((trigger) => {
    trigger.addEventListener("click", () => {
      const expanded = trigger.getAttribute("aria-expanded") === "true";
      const panelId = trigger.getAttribute("aria-controls");
      const panel = panelId ? document.getElementById(panelId) : null;
      trigger.setAttribute("aria-expanded", String(!expanded));
      if (panel) panel.hidden = expanded;
      const icon = trigger.querySelector(".faq-icon");
      if (icon) icon.textContent = expanded ? "+" : "−";
    });
  });

  // 6. Copy Code Buttons
  document.querySelectorAll(".copy-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const targetId = btn.getAttribute("data-copy");
      const codeEl = targetId ? document.getElementById(targetId) : null;
      if (!codeEl) return;
      try {
        await navigator.clipboard.writeText(codeEl.textContent.trim());
        const prev = btn.textContent;
        btn.textContent = "Copied";
        setTimeout(() => {
          btn.textContent = prev;
        }, 1600);
      } catch (_) {}
    });
  });
})();
