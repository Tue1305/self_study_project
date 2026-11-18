/**
 * End-of-Day (EOD) Register Closing, Daily Z-Report, and Technician Commission & Tip Payouts Module
 * Jenny's Nail Studio & Luxe Nail Spa Sanctuary
 */

(function () {
    // Inject Styles for Thermal Receipts and Print Modes
    const styleEl = document.createElement('style');
    styleEl.id = 'eodRegisterStyles';
    styleEl.innerHTML = `
        .eod-kpi-card {
            border-radius: 12px;
            padding: 16px;
            background: #ffffff;
            border: 1px solid #e2e8f0;
            box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
            transition: transform 0.15s ease;
        }
        .eod-kpi-card:hover {
            transform: translateY(-2px);
        }
        .eod-receipt-slip {
            background-color: #fff;
            color: #1a202c;
            font-family: 'Courier New', Courier, monospace;
            padding: 24px;
            border: 1px dashed #cbd5e1;
            border-radius: 8px;
            max-width: 440px;
            margin: 0 auto;
            box-shadow: 0 4px 12px rgba(0,0,0,0.05);
        }
        .eod-receipt-divider {
            border-top: 1px dashed #94a3b8;
            margin: 10px 0;
        }
        .eod-receipt-double-divider {
            border-top: 2px dashed #475569;
            margin: 12px 0;
        }

        @media print {
            body * {
                visibility: hidden !important;
            }
            #eodPrintSection, #eodPrintSection * {
                visibility: visible !important;
            }
            #eodPrintSection {
                position: absolute !important;
                left: 0 !important;
                top: 0 !important;
                width: 100% !important;
                display: block !important;
                margin: 0 !important;
                padding: 10px !important;
            }
            .modal-backdrop, .modal {
                display: none !important;
            }
            .no-print {
                display: none !important;
            }
        }
    `;
    document.head.appendChild(styleEl);

    // Inject Modals into DOM once loaded
    function injectEodModals() {
        if (document.getElementById('eodRegisterModal')) return;

        const container = document.createElement('div');
        container.innerHTML = `
        <!-- Owner Security Password Verification Modal -->
        <div class="modal fade" id="eodPasswordModal" tabindex="-1" data-bs-backdrop="static" aria-hidden="true">
            <div class="modal-dialog modal-dialog-centered" style="max-width: 420px;">
                <div class="modal-content border-0 shadow-lg rounded-4">
                    <div class="modal-header border-bottom py-3 px-4 bg-light">
                        <div class="d-flex align-items-center gap-2">
                            <span class="p-2 rounded-circle bg-warning bg-opacity-10 text-warning">
                                <i class="bi bi-shield-lock-fill fs-5"></i>
                            </span>
                            <div>
                                <h6 class="modal-title fw-bold text-dark mb-0">Owner Security Gate</h6>
                                <small class="text-muted">Register Close & Staff Payroll</small>
                            </div>
                        </div>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body p-4">
                        <p class="text-secondary small mb-3">
                            Please enter the <strong>Salon Owner / Administrator password</strong> to access financial register closing and technician commission earnings.
                        </p>
                        <form id="eodPasswordForm" onsubmit="event.preventDefault(); submitEodOwnerPassword();">
                            <div class="mb-3">
                                <label class="form-label small fw-semibold text-dark">Owner / Admin Password</label>
                                <div class="input-group">
                                    <span class="input-group-text bg-white"><i class="bi bi-key text-muted"></i></span>
                                    <input type="password" id="eodOwnerPasswordInput" class="form-control text-center fw-bold" placeholder="Enter owner password (e.g. admin123)" required autocomplete="current-password">
                                    <button class="btn btn-outline-secondary" type="button" onclick="toggleEodPasswordVisibility()">
                                        <i id="eodPwToggleIcon" class="bi bi-eye"></i>
                                    </button>
                                </div>
                            </div>
                            <div id="eodPasswordAlert" class="alert alert-danger py-2 small d-none mb-3"></div>
                            <div class="d-flex gap-2">
                                <button type="button" class="btn btn-light w-50 fw-semibold" data-bs-dismiss="modal">Cancel</button>
                                <button type="submit" id="btnUnlockEod" class="btn btn-dark w-50 fw-semibold d-flex align-items-center justify-content-center gap-2">
                                    <span>Unlock</span>
                                    <i class="bi bi-unlock-fill"></i>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>

        <!-- Main EOD Register Closing & Staff Payroll Modal -->
        <div class="modal fade" id="eodRegisterModal" tabindex="-1" data-bs-backdrop="static" aria-hidden="true">
            <div class="modal-dialog modal-xl modal-dialog-scrollable">
                <div class="modal-content border-0 shadow-lg rounded-4">
                    <!-- Modal Header -->
                    <div class="modal-header border-bottom py-3 px-4 bg-white sticky-top">
                        <div class="d-flex align-items-center gap-3">
                            <span class="p-2 rounded-circle bg-dark text-white">
                                <i class="bi bi-cash-stack fs-5"></i>
                            </span>
                            <div>
                                <h5 class="modal-title fw-bold text-dark mb-0 d-flex align-items-center gap-2">
                                    <span>Daily Register Close & Staff Payroll</span>
                                    <span id="eodStatusPill" class="badge bg-secondary fs-6">OPEN</span>
                                </h5>
                                <div class="d-flex align-items-center gap-2 mt-1">
                                    <span id="eodTenantNameBadge" class="badge bg-light text-dark border">Jenny's Nail Studio</span>
                                    <span id="eodVerifiedOwnerBadge" class="badge bg-light text-muted border">
                                        <i class="bi bi-person-check-fill text-success me-1"></i>Owner Mode
                                    </span>
                                </div>
                            </div>
                        </div>

                        <!-- Date Navigation Controls & Lock Button -->
                        <div class="d-flex align-items-center gap-2 ms-auto">
                            <div class="btn-group btn-group-sm">
                                <button class="btn btn-outline-secondary" onclick="changeEodDate(-1)" title="Previous Day">
                                    <i class="bi bi-chevron-left"></i>
                                </button>
                                <button class="btn btn-outline-secondary" onclick="setEodDateToday()">Today</button>
                                <button class="btn btn-outline-secondary" onclick="changeEodDate(1)" title="Next Day">
                                    <i class="bi bi-chevron-right"></i>
                                </button>
                            </div>
                            <input type="date" id="eodDatePicker" class="form-control form-control-sm fw-semibold" style="width: 145px;" onchange="loadEodSummary(this.value)">
                            <button type="button" class="btn btn-outline-danger btn-sm fw-semibold ms-1" onclick="lockEodRegister()" title="Lock financial screen">
                                <i class="bi bi-lock-fill"></i> Lock
                            </button>
                            <button type="button" class="btn-close ms-2" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                    </div>

                    <!-- Nav Tabs -->
                    <div class="px-4 pt-3 bg-light border-bottom">
                        <ul class="nav nav-tabs border-0" id="eodTabs" role="tablist">
                            <li class="nav-item">
                                <button class="nav-link active fw-bold text-dark d-flex align-items-center gap-2" id="zreport-tab" data-bs-toggle="tab" data-bs-target="#zreportPane" type="button">
                                    <i class="bi bi-receipt"></i> Daily Z-Report & Cash Drawer
                                </button>
                            </li>
                            <li class="nav-item">
                                <button class="nav-link fw-bold text-dark d-flex align-items-center gap-2" id="staff-payroll-tab" data-bs-toggle="tab" data-bs-target="#staffPayrollPane" type="button">
                                    <i class="bi bi-people-fill text-primary"></i> Staff Commission & Tip Payouts
                                </button>
                            </li>
                            <li class="nav-item">
                                <button class="nav-link fw-bold text-dark d-flex align-items-center gap-2" id="print-slips-tab" data-bs-toggle="tab" data-bs-target="#printSlipsPane" type="button">
                                    <i class="bi bi-printer-fill text-success"></i> Printable Thermal Slips
                                </button>
                            </li>
                        </ul>
                    </div>

                    <!-- Modal Body -->
                    <div class="modal-body p-4 bg-light">
                        <div class="tab-content" id="eodTabsContent">
                            <!-- Tab 1: Daily Z-Report & Cash Drawer Balancing -->
                            <div class="tab-pane fade show active" id="zreportPane" role="tabpanel">
                                <!-- Top Row: 5 Key Financial KPI Cards -->
                                <div class="row g-3 mb-4">
                                    <div class="col-6 col-md-4 col-xl-2">
                                        <div class="eod-kpi-card text-center">
                                            <div class="text-muted small fw-semibold">Gross Revenue</div>
                                            <div id="eodKpiGross" class="fs-4 fw-bold text-dark mt-1">$0.00</div>
                                            <small class="text-muted"><span id="eodKpiTxCount">0</span> sales</small>
                                        </div>
                                    </div>
                                    <div class="col-6 col-md-4 col-xl-2">
                                        <div class="eod-kpi-card text-center">
                                            <div class="text-muted small fw-semibold">Service Sales</div>
                                            <div id="eodKpiServices" class="fs-4 fw-bold text-primary mt-1">$0.00</div>
                                            <small class="text-muted">Net subtotal</small>
                                        </div>
                                    </div>
                                    <div class="col-6 col-md-4 col-xl-2">
                                        <div class="eod-kpi-card text-center">
                                            <div class="text-muted small fw-semibold">Tax Collected</div>
                                            <div id="eodKpiTax" class="fs-4 fw-bold text-secondary mt-1">$0.00</div>
                                            <small class="text-muted">Sales tax</small>
                                        </div>
                                    </div>
                                    <div class="col-6 col-md-4 col-xl-3">
                                        <div class="eod-kpi-card text-center">
                                            <div class="text-muted small fw-semibold">Total Tips Collected</div>
                                            <div id="eodKpiTips" class="fs-4 fw-bold text-success mt-1">$0.00</div>
                                            <small class="text-muted">Card: <span id="eodKpiCardTips">$0.00</span> | Cash: <span id="eodKpiCashTips">$0.00</span></small>
                                        </div>
                                    </div>
                                    <div class="col-12 col-md-4 col-xl-3">
                                        <div class="eod-kpi-card text-center">
                                            <div class="text-muted small fw-semibold">Gift Cards / Discounts</div>
                                            <div id="eodKpiDiscounts" class="fs-4 fw-bold text-danger mt-1">$0.00</div>
                                            <small class="text-muted">Redeemed value</small>
                                        </div>
                                    </div>
                                </div>

                                <!-- Dual Column Breakdown: Payment Tenders & Cash Drawer Reconciliation -->
                                <div class="row g-4">
                                    <!-- Left Column: Payment Tender Settlement -->
                                    <div class="col-lg-6">
                                        <div class="card border-0 shadow-sm rounded-4 h-100">
                                            <div class="card-header bg-white py-3 px-4 border-bottom">
                                                <h6 class="fw-bold mb-0 text-dark d-flex align-items-center gap-2">
                                                    <i class="bi bi-credit-card-2-front-fill text-primary"></i>
                                                    <span>Payment Tender Breakdown</span>
                                                </h6>
                                            </div>
                                            <div class="card-body p-4">
                                                <div class="table-responsive">
                                                    <table class="table table-sm align-middle mb-0">
                                                        <thead class="table-light">
                                                            <tr>
                                                                <th>Payment Method</th>
                                                                <th class="text-center">Count</th>
                                                                <th class="text-end">Batch Total</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody id="eodPaymentBreakdownBody">
                                                            <!-- Injected via JS -->
                                                        </tbody>
                                                    </table>
                                                </div>

                                                <div class="mt-4 p-3 bg-light rounded-3 border">
                                                    <div class="d-flex justify-content-between align-items-center">
                                                        <span class="fw-semibold text-secondary small">Credit / Apple Pay Terminal Batch:</span>
                                                        <span id="eodCardBatchDisplay" class="fw-bold fs-6 text-dark">$0.00</span>
                                                    </div>
                                                    <div class="d-flex justify-content-between align-items-center mt-1">
                                                        <span class="fw-semibold text-secondary small">Gift Card Redemptions:</span>
                                                        <span id="eodGiftCardDisplay" class="fw-bold text-muted">$0.00</span>
                                                    </div>
                                                    <div class="d-flex justify-content-between align-items-center mt-1">
                                                        <span class="fw-semibold text-secondary small">Total Cash Collected:</span>
                                                        <span id="eodCashCollectedSummary" class="fw-bold text-success">$0.00</span>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Right Column: Cash Drawer Reconciliation & Register Close -->
                                    <div class="col-lg-6">
                                        <div class="card border-0 shadow-sm rounded-4 h-100">
                                            <div class="card-header bg-white py-3 px-4 border-bottom d-flex justify-content-between align-items-center">
                                                <h6 class="fw-bold mb-0 text-dark d-flex align-items-center gap-2">
                                                    <i class="bi bi-cash-coin text-warning"></i>
                                                    <span>Cash Drawer Reconciliation</span>
                                                </h6>
                                                <span id="eodCloseTimestamp" class="small text-muted"></span>
                                            </div>
                                            <div class="card-body p-4">
                                                <div class="row g-3 mb-3">
                                                    <div class="col-6">
                                                        <label class="form-label small fw-semibold text-muted mb-1">Opening Cash Float ($)</label>
                                                        <input type="number" id="eodOpeningFloat" class="form-control form-control-sm fw-semibold" value="200.00" step="0.01" oninput="calculateEodVariance()">
                                                        <small class="text-muted">Morning drawer start</small>
                                                    </div>
                                                    <div class="col-6">
                                                        <label class="form-label small fw-semibold text-muted mb-1">Cash Sales & Tips ($)</label>
                                                        <input type="text" id="eodCashSalesDisplay" class="form-control form-control-sm bg-light fw-bold text-success" readonly value="$0.00">
                                                        <small class="text-muted">Recorded by POS</small>
                                                    </div>
                                                </div>

                                                <div class="p-3 bg-light rounded-3 border mb-3">
                                                    <div class="d-flex justify-content-between align-items-center">
                                                        <span class="fw-bold text-dark">Expected Cash in Drawer:</span>
                                                        <span id="eodExpectedCashDisplay" class="fs-4 fw-bold text-primary">$200.00</span>
                                                    </div>
                                                    <small class="text-muted">Opening Float + Cash Sales / Tips</small>
                                                </div>

                                                <div class="mb-3">
                                                    <label class="form-label small fw-bold text-dark mb-1">
                                                        Physical Cash Counted ($) <span class="text-danger">*</span>
                                                    </label>
                                                    <div class="input-group">
                                                        <span class="input-group-text bg-white"><i class="bi bi-currency-dollar"></i></span>
                                                        <input type="number" id="eodActualCashInput" step="0.01" class="form-control form-control-lg fw-bold text-end" placeholder="0.00" oninput="calculateEodVariance()">
                                                    </div>
                                                </div>

                                                <!-- Live Over / Short Variance Display -->
                                                <div id="eodVarianceBanner" class="p-2 rounded text-center fw-bold small mb-3 bg-light text-muted border">
                                                    Enter counted cash to calculate variance
                                                </div>

                                                <div class="mb-3">
                                                    <label class="form-label small fw-semibold text-muted mb-1">Manager Closing Notes</label>
                                                    <textarea id="eodClosingNotes" class="form-control form-control-sm" rows="2" placeholder="e.g., Register balanced perfectly, drawer drop of $300 deposited into safe..."></textarea>
                                                </div>

                                                <button id="btnFinalizeRegisterClose" class="btn btn-success w-100 fw-bold py-2 shadow-sm d-flex align-items-center justify-content-center gap-2" onclick="submitEodRegisterClose()">
                                                    <i class="bi bi-check-circle-fill"></i>
                                                    <span>Finalize & Close Register</span>
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Tab 2: Staff Commission & Tip Payouts -->
                            <div class="tab-pane fade" id="staffPayrollPane" role="tabpanel">
                                <div class="card border-0 shadow-sm rounded-4">
                                    <div class="card-header bg-white py-3 px-4 border-bottom d-flex justify-content-between align-items-center">
                                        <div>
                                            <h6 class="fw-bold mb-0 text-dark">Technician Commission & Gratuity Settlement</h6>
                                            <small class="text-muted">Automatic calculations based on individual staff commission rates & tips collected</small>
                                        </div>
                                        <button class="btn btn-outline-dark btn-sm fw-semibold" onclick="printEodReport('STAFF_SLIPS')">
                                            <i class="bi bi-printer me-1"></i> Print Slips
                                        </button>
                                    </div>
                                    <div class="card-body p-0">
                                        <div class="table-responsive">
                                            <table class="table table-hover align-middle mb-0">
                                                <thead class="table-light">
                                                    <tr>
                                                        <th class="ps-4">Technician</th>
                                                        <th class="text-center">Commission Rate</th>
                                                        <th class="text-center">Appts</th>
                                                        <th class="text-end">Service Sales</th>
                                                        <th class="text-end">Commission Earned</th>
                                                        <th class="text-end">Card Tips</th>
                                                        <th class="text-end">Cash Tips</th>
                                                        <th class="text-end">Total Tips</th>
                                                        <th class="text-end">Total Take-Home</th>
                                                        <th class="text-end pe-4">Salon House Cut</th>
                                                    </tr>
                                                </thead>
                                                <tbody id="eodStaffPayoutsBody">
                                                    <!-- Injected via JS -->
                                                </tbody>
                                                <tfoot class="table-secondary fw-bold">
                                                    <tr id="eodStaffPayoutsFooter">
                                                        <!-- Totals injected via JS -->
                                                    </tr>
                                                </tfoot>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Tab 3: Printable Thermal Slips Preview -->
                            <div class="tab-pane fade" id="printSlipsPane" role="tabpanel">
                                <div class="d-flex justify-content-between align-items-center mb-4 bg-white p-3 rounded-4 shadow-sm">
                                    <div class="d-flex gap-2">
                                        <button class="btn btn-dark fw-bold btn-sm d-flex align-items-center gap-1" onclick="printEodReport('Z_REPORT')">
                                            <i class="bi bi-printer-fill"></i> Print Daily Z-Report
                                        </button>
                                        <button class="btn btn-primary fw-bold btn-sm d-flex align-items-center gap-1" onclick="printEodReport('STAFF_SLIPS')">
                                            <i class="bi bi-receipt"></i> Print Staff Payout Slips
                                        </button>
                                        <button class="btn btn-outline-secondary fw-bold btn-sm d-flex align-items-center gap-1" onclick="printEodReport('ALL')">
                                            <i class="bi bi-files"></i> Print Full Package
                                        </button>
                                    </div>
                                    <small class="text-muted"><i class="bi bi-info-circle me-1"></i>Optimized for standard 80mm thermal receipt printers & letter sheets.</small>
                                </div>

                                <div class="row g-4 justify-content-center">
                                    <div class="col-lg-6">
                                        <div class="text-center mb-2 fw-semibold text-muted small">Daily Z-Report Thermal Preview</div>
                                        <div id="eodZReportPreviewContainer" class="eod-receipt-slip">
                                            <!-- Injected via JS -->
                                        </div>
                                    </div>
                                    <div class="col-lg-6">
                                        <div class="text-center mb-2 fw-semibold text-muted small">Technician Payout Slips Preview</div>
                                        <div id="eodStaffSlipsPreviewContainer" class="d-flex flex-column gap-3">
                                            <!-- Injected via JS -->
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Hidden Container for Print Execution -->
        <div id="eodPrintSection" style="display: none;"></div>
        `;

        document.body.appendChild(container);
    }

    // State Variables
    let currentEodData = null;
    let selectedEodDate = new Date().toISOString().split('T')[0];

    function getAuthToken() {
        return localStorage.getItem('token') || localStorage.getItem('jwt_token') || '';
    }

    function getActiveTenant() {
        if (window.TenantTheme && typeof window.TenantTheme.resolveActiveTenantId === 'function') {
            return window.TenantTheme.resolveActiveTenantId();
        }
        return window.activeTenantId || 'default';
    }

    function getSalonName() {
        const brandTitle = document.getElementById('portalBrandTitle') || document.getElementById('brandLogoText');
        return brandTitle ? brandTitle.textContent.replace('Admin Portal', '').trim() || "Jenny's Nail Studio" : "Jenny's Nail Studio";
    }

    // Public method: Open EOD Register Modal (with owner password security gate)
    window.openEodRegisterModal = function () {
        injectEodModals();

        const isVerified = sessionStorage.getItem('eod_owner_verified') === 'true';
        if (!isVerified) {
            // Prompt for Owner Password
            const pwModal = new bootstrap.Modal(document.getElementById('eodPasswordModal'));
            document.getElementById('eodOwnerPasswordInput').value = '';
            document.getElementById('eodPasswordAlert').classList.add('d-none');
            pwModal.show();
            setTimeout(() => {
                const input = document.getElementById('eodOwnerPasswordInput');
                if (input) input.focus();
            }, 400);
        } else {
            // Already verified in this session
            showEodRegisterModal();
        }
    };

    window.toggleEodPasswordVisibility = function () {
        const input = document.getElementById('eodOwnerPasswordInput');
        const icon = document.getElementById('eodPwToggleIcon');
        if (input.type === 'password') {
            input.type = 'text';
            icon.classList.replace('bi-eye', 'bi-eye-slash');
        } else {
            input.type = 'password';
            icon.classList.replace('bi-eye-slash', 'bi-eye');
        }
    };

    window.submitEodOwnerPassword = async function () {
        const passwordInput = document.getElementById('eodOwnerPasswordInput');
        const alertBox = document.getElementById('eodPasswordAlert');
        const btn = document.getElementById('btnUnlockEod');

        const password = passwordInput.value;
        if (!password) return;

        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Verifying...';
        alertBox.classList.add('d-none');

        try {
            const token = getAuthToken();
            const headers = {
                'Content-Type': 'application/json',
                'X-Tenant-ID': getActiveTenant()
            };
            if (token) headers['Authorization'] = 'Bearer ' + token;

            const res = await fetch('/api/pos/verify-owner-password', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ password: password })
            });

            const data = await res.json();
            if (res.ok && data.success) {
                sessionStorage.setItem('eod_owner_verified', 'true');
                sessionStorage.setItem('eod_owner_name', data.ownerName || 'Store Owner');

                // Close Password Modal
                const pwModalEl = document.getElementById('eodPasswordModal');
                const pwModal = bootstrap.Modal.getInstance(pwModalEl);
                if (pwModal) pwModal.hide();

                // Open Main Register Modal
                showEodRegisterModal();
            } else {
                alertBox.textContent = data.message || 'Incorrect owner password. Access denied.';
                alertBox.classList.remove('d-none');
                passwordInput.select();
            }
        } catch (err) {
            alertBox.textContent = 'Network or server error verifying password.';
            alertBox.classList.remove('d-none');
        } finally {
            btn.disabled = false;
            btn.innerHTML = '<span>Unlock</span> <i class="bi bi-unlock-fill"></i>';
        }
    };

    window.lockEodRegister = function () {
        sessionStorage.removeItem('eod_owner_verified');
        sessionStorage.removeItem('eod_owner_name');

        const regModalEl = document.getElementById('eodRegisterModal');
        const regModal = bootstrap.Modal.getInstance(regModalEl);
        if (regModal) regModal.hide();
    };

    function showEodRegisterModal() {
        injectEodModals();

        const datePicker = document.getElementById('eodDatePicker');
        if (!datePicker.value) {
            datePicker.value = selectedEodDate;
        }

        const ownerName = sessionStorage.getItem('eod_owner_name') || 'Store Owner';
        const ownerBadge = document.getElementById('eodVerifiedOwnerBadge');
        if (ownerBadge) {
            ownerBadge.innerHTML = `<i class="bi bi-person-check-fill text-success me-1"></i>Verified: ${escapeHtml(ownerName)}`;
        }

        const tenantBadge = document.getElementById('eodTenantNameBadge');
        if (tenantBadge) {
            tenantBadge.textContent = getSalonName();
        }

        const regModal = new bootstrap.Modal(document.getElementById('eodRegisterModal'));
        regModal.show();

        loadEodSummary(datePicker.value || selectedEodDate);
    }

    window.setEodDateToday = function () {
        const today = new Date().toISOString().split('T')[0];
        document.getElementById('eodDatePicker').value = today;
        loadEodSummary(today);
    };

    window.changeEodDate = function (deltaDays) {
        const datePicker = document.getElementById('eodDatePicker');
        const current = new Date(datePicker.value || new Date());
        current.setDate(current.getDate() + deltaDays);
        const nextDateStr = current.toISOString().split('T')[0];
        datePicker.value = nextDateStr;
        loadEodSummary(nextDateStr);
    };

    window.loadEodSummary = async function (dateStr) {
        selectedEodDate = dateStr;
        const token = getAuthToken();
        const headers = { 'X-Tenant-ID': getActiveTenant() };
        if (token) headers['Authorization'] = 'Bearer ' + token;

        try {
            const res = await fetch(`/api/pos/daily-summary?date=${encodeURIComponent(dateStr)}`, {
                headers: headers
            });

            if (!res.ok) throw new Error('Failed to load daily financial summary.');
            const data = await res.json();
            currentEodData = data;
            renderEodSummary(data);
        } catch (err) {
            console.error('Error loading EOD summary:', err);
        }
    };

    function renderEodSummary(data) {
        const isClosed = data.isClosed === true;
        const statusPill = document.getElementById('eodStatusPill');
        if (statusPill) {
            if (isClosed) {
                statusPill.className = 'badge bg-success fs-6';
                statusPill.innerHTML = '<i class="bi bi-check2-circle me-1"></i>REGISTER CLOSED';
            } else {
                statusPill.className = 'badge bg-warning text-dark fs-6';
                statusPill.innerHTML = '<i class="bi bi-clock-history me-1"></i>OPEN / IN-PROGRESS';
            }
        }

        // 1. KPI Cards
        document.getElementById('eodKpiGross').textContent = formatCurrency(data.totalGrossRevenue);
        document.getElementById('eodKpiTxCount').textContent = data.transactionCount || 0;
        document.getElementById('eodKpiServices').textContent = formatCurrency(data.totalServiceSales);
        document.getElementById('eodKpiTax').textContent = formatCurrency(data.totalTaxCollected);
        document.getElementById('eodKpiTips').textContent = formatCurrency(data.totalTipsCollected);

        let cardTips = 0;
        let cashTips = 0;
        if (data.staffPayouts && data.staffPayouts.length > 0) {
            data.staffPayouts.forEach(s => {
                cardTips += Number(s.cardTips || 0);
                cashTips += Number(s.cashTips || 0);
            });
        }
        document.getElementById('eodKpiCardTips').textContent = formatCurrency(cardTips);
        document.getElementById('eodKpiCashTips').textContent = formatCurrency(cashTips);

        const discountsTotal = Number(data.totalDiscountsGiven || 0) + Number(data.giftCardRedemptions || 0);
        document.getElementById('eodKpiDiscounts').textContent = formatCurrency(discountsTotal);

        // 2. Payment Tender Table
        const breakdownTbody = document.getElementById('eodPaymentBreakdownBody');
        breakdownTbody.innerHTML = '';
        if (data.paymentBreakdown) {
            for (const [method, item] of Object.entries(data.paymentBreakdown)) {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td class="fw-semibold">${formatPaymentLabel(method)}</td>
                    <td class="text-center"><span class="badge bg-light text-dark border">${item.count || 0}</span></td>
                    <td class="text-end fw-bold">${formatCurrency(item.total)}</td>
                `;
                breakdownTbody.appendChild(tr);
            }
        }

        // Tender Summary Cards
        document.getElementById('eodCardBatchDisplay').textContent = formatCurrency(data.cardBatchTotal);
        document.getElementById('eodGiftCardDisplay').textContent = formatCurrency(data.giftCardRedemptions);
        document.getElementById('eodCashCollectedSummary').textContent = formatCurrency(data.cashCollected);
        document.getElementById('eodCashSalesDisplay').value = formatCurrency(data.cashCollected);

        // 3. Cash Drawer Inputs & Calculations
        const openingFloatInput = document.getElementById('eodOpeningFloat');
        const actualCashInput = document.getElementById('eodActualCashInput');
        const notesInput = document.getElementById('eodClosingNotes');
        const finalizeBtn = document.getElementById('btnFinalizeRegisterClose');
        const closeTimestamp = document.getElementById('eodCloseTimestamp');

        if (isClosed && data.closingRecord) {
            const cr = data.closingRecord;
            openingFloatInput.value = Number(cr.openingCashFloat || 200).toFixed(2);
            actualCashInput.value = Number(cr.actualCashCounted || 0).toFixed(2);
            notesInput.value = cr.notes || '';
            finalizeBtn.innerHTML = '<i class="bi bi-arrow-repeat me-1"></i> Update Register Closing';
            finalizeBtn.className = 'btn btn-outline-secondary w-100 fw-bold py-2';
            closeTimestamp.textContent = `Closed by ${cr.closedBy || 'Owner'} at ${formatTime(cr.closedAt)}`;
        } else {
            if (!openingFloatInput.value) openingFloatInput.value = '200.00';
            actualCashInput.value = '';
            notesInput.value = '';
            finalizeBtn.innerHTML = '<i class="bi bi-check-circle-fill me-1"></i> Finalize & Close Register';
            finalizeBtn.className = 'btn btn-success w-100 fw-bold py-2 shadow-sm';
            closeTimestamp.textContent = '';
        }

        calculateEodVariance();

        // 4. Staff Commission & Tip Payouts Table
        renderStaffPayouts(data);

        // 5. Thermal Receipt Previews
        renderZReportPreview(data);
        renderStaffSlipsPreview(data);
    }

    window.calculateEodVariance = function () {
        if (!currentEodData) return;

        const openingFloat = Number(document.getElementById('eodOpeningFloat').value || 0);
        const cashCollected = Number(currentEodData.cashCollected || 0);
        const expectedCash = openingFloat + cashCollected;

        document.getElementById('eodExpectedCashDisplay').textContent = formatCurrency(expectedCash);

        const actualCashVal = document.getElementById('eodActualCashInput').value;
        const banner = document.getElementById('eodVarianceBanner');

        if (actualCashVal === '' || isNaN(Number(actualCashVal))) {
            banner.className = 'p-2 rounded text-center fw-semibold small mb-3 bg-light text-muted border';
            banner.innerHTML = '<i class="bi bi-calculator me-1"></i> Count drawer cash to verify balance';
            return;
        }

        const actualCash = Number(actualCashVal);
        const variance = actualCash - expectedCash;

        if (Math.abs(variance) < 0.01) {
            banner.className = 'p-2 rounded text-center fw-bold small mb-3 bg-success bg-opacity-10 text-success border border-success';
            banner.innerHTML = '<i class="bi bi-check-circle-fill me-1"></i> Exact Cash Balance: $0.00 Variance';
        } else if (variance < 0) {
            banner.className = 'p-2 rounded text-center fw-bold small mb-3 bg-danger bg-opacity-10 text-danger border border-danger';
            banner.innerHTML = `<i class="bi bi-exclamation-triangle-fill me-1"></i> Cash Shortage: -$${Math.abs(variance).toFixed(2)} Below Expected`;
        } else {
            banner.className = 'p-2 rounded text-center fw-bold small mb-3 bg-primary bg-opacity-10 text-primary border border-primary';
            banner.innerHTML = `<i class="bi bi-info-circle-fill me-1"></i> Cash Overage: +$${variance.toFixed(2)} Above Expected`;
        }
    };

    function renderStaffPayouts(data) {
        const tbody = document.getElementById('eodStaffPayoutsBody');
        const tfoot = document.getElementById('eodStaffPayoutsFooter');
        tbody.innerHTML = '';
        tfoot.innerHTML = '';

        const staffList = data.staffPayouts || [];
        if (staffList.length === 0) {
            tbody.innerHTML = '<tr><td colspan="10" class="text-center py-4 text-muted small">No technician appointments recorded for this date.</td></tr>';
            return;
        }

        let totalAppts = 0;
        let totalSales = 0;
        let totalComm = 0;
        let totalCardTips = 0;
        let totalCashTips = 0;
        let totalTips = 0;
        let totalTakeHome = 0;
        let totalHouse = 0;

        staffList.forEach(s => {
            const appts = Number(s.appointmentsCount || 0);
            const sales = Number(s.serviceSales || 0);
            const comm = Number(s.commissionEarned || 0);
            const cardTips = Number(s.cardTips || 0);
            const cashTips = Number(s.cashTips || 0);
            const tips = Number(s.totalTips || 0);
            const takeHome = Number(s.totalStaffPayout || 0);
            const house = Number(s.salonHouseShare || 0);

            totalAppts += appts;
            totalSales += sales;
            totalComm += comm;
            totalCardTips += cardTips;
            totalCashTips += cashTips;
            totalTips += tips;
            totalTakeHome += takeHome;
            totalHouse += house;

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td class="ps-4 fw-bold text-dark d-flex align-items-center gap-2">
                    <span class="p-1 rounded bg-light border text-pink"><i class="bi bi-person-fill"></i></span>
                    <span>${escapeHtml(s.nailTechName || 'Technician')}</span>
                </td>
                <td class="text-center"><span class="badge bg-light text-dark border">${Number(s.commissionRate || 60).toFixed(0)}%</span></td>
                <td class="text-center fw-semibold">${appts}</td>
                <td class="text-end fw-semibold">${formatCurrency(sales)}</td>
                <td class="text-end fw-bold text-primary">${formatCurrency(comm)}</td>
                <td class="text-end text-muted">${formatCurrency(cardTips)}</td>
                <td class="text-end text-muted">${formatCurrency(cashTips)}</td>
                <td class="text-end fw-bold text-success">${formatCurrency(tips)}</td>
                <td class="text-end fw-bold text-dark fs-6 bg-light">${formatCurrency(takeHome)}</td>
                <td class="text-end pe-4 fw-semibold text-secondary">${formatCurrency(house)}</td>
            `;
            tbody.appendChild(tr);
        });

        tfoot.innerHTML = `
            <td class="ps-4">TOTALS (${staffList.length} Technicians)</td>
            <td class="text-center">--</td>
            <td class="text-center">${totalAppts}</td>
            <td class="text-end">${formatCurrency(totalSales)}</td>
            <td class="text-end text-primary">${formatCurrency(totalComm)}</td>
            <td class="text-end">${formatCurrency(totalCardTips)}</td>
            <td class="text-end">${formatCurrency(totalCashTips)}</td>
            <td class="text-end text-success">${formatCurrency(totalTips)}</td>
            <td class="text-end text-dark fs-6">${formatCurrency(totalTakeHome)}</td>
            <td class="text-end pe-4">${formatCurrency(totalHouse)}</td>
        `;
    }

    window.submitEodRegisterClose = async function () {
        if (!currentEodData) return;

        const openingFloat = Number(document.getElementById('eodOpeningFloat').value || 200);
        const actualCashVal = document.getElementById('eodActualCashInput').value;
        const notes = document.getElementById('eodClosingNotes').value;
        const ownerName = sessionStorage.getItem('eod_owner_name') || 'Store Owner';

        const btn = document.getElementById('btnFinalizeRegisterClose');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Finalizing Register Close...';

        try {
            const token = getAuthToken();
            const headers = {
                'Content-Type': 'application/json',
                'X-Tenant-ID': getActiveTenant()
            };
            if (token) headers['Authorization'] = 'Bearer ' + token;

            const payload = {
                closingDate: selectedEodDate,
                openingFloat: openingFloat,
                actualCash: actualCashVal !== '' ? Number(actualCashVal) : (openingFloat + Number(currentEodData.cashCollected || 0)),
                notes: notes,
                closedBy: ownerName
            };

            const res = await fetch('/api/pos/register-close', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(payload)
            });

            if (!res.ok) throw new Error('Failed to finalize register closing.');
            const data = await res.json();

            // Refresh current view
            await loadEodSummary(selectedEodDate);

            alert('Register closing finalized successfully! The daily financial snapshot and staff payouts have been saved.');
        } catch (err) {
            alert('Error closing register: ' + err.message);
        } finally {
            btn.disabled = false;
        }
    };

    function renderZReportPreview(data) {
        const container = document.getElementById('eodZReportPreviewContainer');
        if (!container) return;

        const salon = getSalonName();
        const date = data.date || selectedEodDate;
        const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        const openingFloat = Number(data.openingCashFloat || 200).toFixed(2);
        const cashCollected = Number(data.cashCollected || 0).toFixed(2);
        const expectedCash = Number(data.expectedCash || 0).toFixed(2);
        const actualCash = Number(data.actualCash || expectedCash).toFixed(2);
        const variance = Number(data.cashVariance || 0).toFixed(2);

        let paymentRows = '';
        if (data.paymentBreakdown) {
            for (const [method, item] of Object.entries(data.paymentBreakdown)) {
                if (Number(item.total || 0) > 0 || item.count > 0) {
                    paymentRows += `
                        <div class="d-flex justify-content-between">
                            <span>${padRight(formatPaymentLabel(method), 18)} (${item.count}):</span>
                            <span>$${Number(item.total).toFixed(2)}</span>
                        </div>
                    `;
                }
            }
        }

        container.innerHTML = `
            <div class="text-center">
                <div class="fw-bold fs-5">${escapeHtml(salon)}</div>
                <div>*** DAILY Z-REPORT (CLOSE) ***</div>
                <div class="small">Date: ${date}  Time: ${time}</div>
                <div class="small">Register ID: REG-01  Tenant: ${data.tenantId || 'default'}</div>
            </div>
            <div class="eod-receipt-divider"></div>
            <div class="d-flex justify-content-between">
                <span>Total Invoices / Appts:</span>
                <span class="fw-bold">${data.transactionCount || 0}</span>
            </div>
            <div class="d-flex justify-content-between">
                <span>Net Service Sales:</span>
                <span class="fw-bold">$${Number(data.totalServiceSales || 0).toFixed(2)}</span>
            </div>
            <div class="d-flex justify-content-between">
                <span>Sales Tax Collected:</span>
                <span>$${Number(data.totalTaxCollected || 0).toFixed(2)}</span>
            </div>
            <div class="d-flex justify-content-between">
                <span>Total Tips Collected:</span>
                <span class="fw-bold">$${Number(data.totalTipsCollected || 0).toFixed(2)}</span>
            </div>
            <div class="d-flex justify-content-between">
                <span>Discounts Given:</span>
                <span>-$${Number(data.totalDiscountsGiven || 0).toFixed(2)}</span>
            </div>
            <div class="eod-receipt-double-divider"></div>
            <div class="d-flex justify-content-between fs-6 fw-bold">
                <span>GROSS REVENUE:</span>
                <span>$${Number(data.totalGrossRevenue || 0).toFixed(2)}</span>
            </div>
            <div class="eod-receipt-divider"></div>
            <div class="fw-bold small mb-1">PAYMENT TENDER BREAKDOWN:</div>
            ${paymentRows}
            <div class="eod-receipt-divider"></div>
            <div class="fw-bold small mb-1">CASH DRAWER RECONCILIATION:</div>
            <div class="d-flex justify-content-between">
                <span>Opening Cash Float:</span>
                <span>$${openingFloat}</span>
            </div>
            <div class="d-flex justify-content-between">
                <span>Cash Sales Collected:</span>
                <span>+$${cashCollected}</span>
            </div>
            <div class="d-flex justify-content-between fw-bold">
                <span>Expected Drawer Total:</span>
                <span>$${expectedCash}</span>
            </div>
            <div class="d-flex justify-content-between">
                <span>Actual Cash Counted:</span>
                <span class="fw-bold">$${actualCash}</span>
            </div>
            <div class="d-flex justify-content-between fw-bold">
                <span>Over / Short Variance:</span>
                <span>${variance >= 0 ? '+' : ''}$${variance}</span>
            </div>
            <div class="eod-receipt-divider"></div>
            <div class="fw-bold small mb-1">STAFF COMMISSION SUMMARY:</div>
            <div class="d-flex justify-content-between">
                <span>Total Technician Earnings:</span>
                <span>$${Number(data.totalCommissionPaid || 0).toFixed(2)}</span>
            </div>
            <div class="d-flex justify-content-between">
                <span>Total Tips Distributed:</span>
                <span>$${Number(data.totalTipsCollected || 0).toFixed(2)}</span>
            </div>
            <div class="d-flex justify-content-between fw-bold">
                <span>Salon House Retained Cut:</span>
                <span>$${Number(data.totalSalonHouseShare || 0).toFixed(2)}</span>
            </div>
            <div class="eod-receipt-double-divider"></div>
            <div class="mt-4 pt-2">
                <div class="small">Manager Signature: _______________________</div>
                <div class="small mt-2">Print Name: ______________________________</div>
            </div>
        `;
    }

    function renderStaffSlipsPreview(data) {
        const container = document.getElementById('eodStaffSlipsPreviewContainer');
        if (!container) return;
        container.innerHTML = '';

        const staffList = data.staffPayouts || [];
        if (staffList.length === 0) {
            container.innerHTML = '<div class="text-muted small text-center py-4">No staff appointments found for this date.</div>';
            return;
        }

        const salon = getSalonName();
        const date = data.date || selectedEodDate;

        staffList.forEach(s => {
            const card = document.createElement('div');
            card.className = 'eod-receipt-slip';
            card.innerHTML = `
                <div class="text-center">
                    <div class="fw-bold">${escapeHtml(salon)}</div>
                    <div>*** TECHNICIAN PAYOUT SLIP ***</div>
                    <div class="small">Date: ${date}</div>
                </div>
                <div class="eod-receipt-divider"></div>
                <div class="d-flex justify-content-between">
                    <span>Technician:</span>
                    <span class="fw-bold">${escapeHtml(s.nailTechName || 'Nail Tech')}</span>
                </div>
                <div class="d-flex justify-content-between">
                    <span>Commission Rate:</span>
                    <span>${Number(s.commissionRate || 60).toFixed(0)}%</span>
                </div>
                <div class="d-flex justify-content-between">
                    <span>Completed Appointments:</span>
                    <span>${s.appointmentsCount || 0}</span>
                </div>
                <div class="d-flex justify-content-between">
                    <span>Total Service Sales:</span>
                    <span>$${Number(s.serviceSales || 0).toFixed(2)}</span>
                </div>
                <div class="eod-receipt-divider"></div>
                <div class="d-flex justify-content-between">
                    <span>Commission Earned:</span>
                    <span class="fw-bold">$${Number(s.commissionEarned || 0).toFixed(2)}</span>
                </div>
                <div class="d-flex justify-content-between">
                    <span>Card Tips (From Salon):</span>
                    <span>$${Number(s.cardTips || 0).toFixed(2)}</span>
                </div>
                <div class="d-flex justify-content-between">
                    <span>Cash Tips:</span>
                    <span>$${Number(s.cashTips || 0).toFixed(2)}</span>
                </div>
                <div class="d-flex justify-content-between">
                    <span>Total Tips:</span>
                    <span>$${Number(s.totalTips || 0).toFixed(2)}</span>
                </div>
                <div class="eod-receipt-double-divider"></div>
                <div class="d-flex justify-content-between fs-6 fw-bold">
                    <span>TOTAL PAYOUT TAKE-HOME:</span>
                    <span>$${Number(s.totalStaffPayout || 0).toFixed(2)}</span>
                </div>
                <div class="eod-receipt-divider"></div>
                <div class="mt-4 pt-1">
                    <div class="small" style="font-size: 11px;">I confirm that the above services and tip totals are correct:</div>
                    <div class="small mt-3">Technician Signature: _____________________</div>
                    <div class="small mt-2">Manager Approval:    _____________________</div>
                </div>
            `;
            container.appendChild(card);
        });
    }

    window.printEodReport = function (mode) {
        if (!currentEodData) return;

        const printSection = document.getElementById('eodPrintSection');
        printSection.innerHTML = '';

        if (mode === 'Z_REPORT' || mode === 'ALL') {
            const zReportHtml = document.getElementById('eodZReportPreviewContainer').outerHTML;
            const wrap = document.createElement('div');
            wrap.style.pageBreakAfter = 'always';
            wrap.innerHTML = zReportHtml;
            printSection.appendChild(wrap);
        }

        if (mode === 'STAFF_SLIPS' || mode === 'ALL') {
            const slips = document.querySelectorAll('#eodStaffSlipsPreviewContainer .eod-receipt-slip');
            slips.forEach(slip => {
                const wrap = document.createElement('div');
                wrap.style.pageBreakAfter = 'always';
                wrap.innerHTML = slip.outerHTML;
                printSection.appendChild(wrap);
            });
        }

        printSection.style.display = 'block';
        window.print();
        setTimeout(() => {
            printSection.style.display = 'none';
        }, 1000);
    };

    // Helper functions
    function formatCurrency(val) {
        const num = Number(val || 0);
        return '$' + num.toFixed(2);
    }

    function formatPaymentLabel(method) {
        const m = (method || '').toUpperCase();
        if (m === 'CARD') return 'Credit / Debit Card';
        if (m === 'CASH') return 'Cash';
        if (m === 'APPLE_PAY') return 'Apple Pay / NFC';
        if (m === 'GIFT_CARD') return 'Gift Card';
        if (m === 'SPLIT') return 'Split Tender';
        return m;
    }

    function formatTime(isoStr) {
        if (!isoStr) return '';
        try {
            return new Date(isoStr).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch (e) {
            return isoStr;
        }
    }

    function padRight(str, len) {
        let s = String(str || '');
        while (s.length < len) s += ' ';
        return s;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Auto-inject on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', injectEodModals);
    } else {
        injectEodModals();
    }
})();
