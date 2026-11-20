/**
 * Admin Privacy Screen Lock (No Password)
 * Blurs and censors all content on admin pages with 1-click Lock / Unlock
 * and Alt+L keyboard shortcut. Synced across admin pages via localStorage.
 */
(function () {
    const STORAGE_KEY = 'admin_privacy_locked';
    const OVERLAY_ID = 'adminPrivacyLockOverlay';
    const STYLE_ID = 'adminPrivacyLockStyles';
    const BTN_ID = 'adminPrivacyLockBtn';

    function injectStyles() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = `
            body.admin-privacy-locked {
                overflow: hidden !important;
            }
            body.admin-privacy-locked > *:not(#${OVERLAY_ID}) {
                filter: blur(20px) saturate(0.35) brightness(0.92) !important;
                pointer-events: none !important;
                user-select: none !important;
                transition: filter 0.2s ease !important;
            }
            #${OVERLAY_ID} {
                position: fixed;
                inset: 0;
                z-index: 99999;
                display: none;
                align-items: center;
                justify-content: center;
                background: rgba(15, 23, 42, 0.32);
                backdrop-filter: blur(8px);
                -webkit-backdrop-filter: blur(8px);
                padding: 20px;
            }
            body.admin-privacy-locked #${OVERLAY_ID} {
                display: flex;
            }
            .admin-privacy-lock-card {
                background: rgba(255, 255, 255, 0.96);
                border: 1px solid rgba(255, 255, 255, 0.7);
                border-radius: 22px;
                box-shadow: 0 24px 60px rgba(15, 23, 42, 0.28);
                padding: 36px 32px;
                max-width: 400px;
                width: 100%;
                text-align: center;
            }
            .admin-privacy-lock-icon {
                width: 68px;
                height: 68px;
                border-radius: 50%;
                background: linear-gradient(135deg, #e91e63, #c2185b);
                color: #ffffff;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                font-size: 1.9rem;
                margin-bottom: 16px;
                box-shadow: 0 10px 24px rgba(233, 30, 99, 0.35);
            }
            .admin-privacy-unlock-btn {
                background: linear-gradient(135deg, #e91e63, #c2185b);
                color: #ffffff;
                border: none;
                border-radius: 999px;
                padding: 12px 28px;
                font-weight: 700;
                font-size: 0.98rem;
                cursor: pointer;
                box-shadow: 0 8px 20px rgba(233, 30, 99, 0.32);
                transition: transform 0.15s ease, box-shadow 0.15s ease;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                gap: 8px;
            }
            .admin-privacy-unlock-btn:hover {
                transform: translateY(-1px);
                box-shadow: 0 12px 26px rgba(233, 30, 99, 0.42);
            }
            #${BTN_ID} {
                display: inline-flex;
                align-items: center;
                gap: 6px;
                padding: 6px 14px;
                border-radius: 999px;
                border: 1.5px solid #cbd5e1;
                background: #ffffff;
                color: #1e293b;
                font-size: 0.82rem;
                font-weight: 700;
                cursor: pointer;
                transition: all 0.15s ease;
                white-space: nowrap;
            }
            #${BTN_ID}:hover {
                border-color: #e91e63;
                color: #e91e63;
                background: #fdf2f8;
            }
            #${BTN_ID}.floating-fallback {
                position: fixed;
                bottom: 24px;
                left: 24px;
                z-index: 1055;
                box-shadow: 0 8px 22px rgba(15, 23, 42, 0.16);
                padding: 9px 16px;
            }
        `;
        document.head.appendChild(style);
    }

    function ensureOverlay() {
        if (document.getElementById(OVERLAY_ID)) return;
        const overlay = document.createElement('div');
        overlay.id = OVERLAY_ID;
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');
        overlay.setAttribute('aria-label', 'Admin Privacy Screen Locked');
        overlay.innerHTML = `
            <div class="admin-privacy-lock-card">
                <div class="admin-privacy-lock-icon">
                    <i class="bi bi-shield-lock-fill"></i>
                </div>
                <h4 style="font-weight: 800; color: #0f172a; margin-bottom: 8px;">Screen Locked</h4>
                <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 24px;">
                    Page content is blurred and censored for privacy. Click below to unlock.
                </p>
                <button type="button" class="admin-privacy-unlock-btn" id="adminPrivacyUnlockBtn">
                    <i class="bi bi-unlock-fill"></i>
                    <span>Unlock Page</span>
                </button>
                <div style="margin-top: 14px; font-size: 0.75rem; color: #94a3b8;">
                    Shortcut: <strong>Alt + L</strong>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);

        const unlockBtn = overlay.querySelector('#adminPrivacyUnlockBtn');
        if (unlockBtn) {
            unlockBtn.addEventListener('click', function () {
                setLocked(false);
            });
        }
    }

    function setLocked(locked) {
        try {
            localStorage.setItem(STORAGE_KEY, locked ? 'true' : 'false');
        } catch (_) {}
        if (locked) {
            document.body.classList.add('admin-privacy-locked');
        } else {
            document.body.classList.remove('admin-privacy-locked');
        }
    }

    function isLocked() {
        try {
            return localStorage.getItem(STORAGE_KEY) === 'true';
        } catch (_) {
            return false;
        }
    }

    function injectLockButton() {
        if (document.getElementById(BTN_ID)) return;

        const btn = document.createElement('button');
        btn.id = BTN_ID;
        btn.type = 'button';
        btn.title = 'Blur & censor page for privacy (Alt + L)';
        btn.innerHTML = '<i class="bi bi-lock-fill"></i><span>Lock Page</span>';
        btn.addEventListener('click', function () {
            setLocked(true);
        });

        // Try placing cleanly inside the admin navbar right-side container
        const navContainer =
            document.querySelector('nav .navbar-nav.ms-auto') ||
            document.querySelector('nav .d-flex.align-items-center.gap-2') ||
            document.querySelector('nav .container-fluid > .d-flex:last-child') ||
            document.querySelector('nav .container > .d-flex:last-child') ||
            document.querySelector('header .d-flex:last-child');

        if (navContainer) {
            navContainer.insertBefore(btn, navContainer.firstChild);
        } else {
            btn.classList.add('floating-fallback');
            document.body.appendChild(btn);
        }
    }

    function init() {
        injectStyles();
        ensureOverlay();
        injectLockButton();
        if (isLocked()) {
            document.body.classList.add('admin-privacy-locked');
        }

        // Sync lock/unlock state across open admin tabs
        window.addEventListener('storage', function (e) {
            if (e.key === STORAGE_KEY) {
                if (e.newValue === 'true') {
                    document.body.classList.add('admin-privacy-locked');
                } else {
                    document.body.classList.remove('admin-privacy-locked');
                }
            }
        });

        // Alt + L keyboard shortcut to toggle lock/unlock
        window.addEventListener('keydown', function (e) {
            if (e.altKey && !e.ctrlKey && !e.metaKey && String(e.key || '').toLowerCase() === 'l') {
                e.preventDefault();
                setLocked(!document.body.classList.contains('admin-privacy-locked'));
            }
        });
    }

    window.toggleAdminPrivacyLock = function (forceState) {
        const next = typeof forceState === 'boolean' ? forceState : !document.body.classList.contains('admin-privacy-locked');
        setLocked(next);
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
