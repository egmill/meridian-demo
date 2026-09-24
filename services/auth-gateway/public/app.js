// Meridian Online Banking - thin client over the four platform services.
const SERVICES = {
  auth: "",
  transactions: "http://localhost:8081",
  pii: "http://localhost:8002",
  audit: "http://localhost:8003",
};

const $ = (id) => document.getElementById(id);

function show(el, message, ok) {
  el.textContent = message;
  el.className = "result " + (ok ? "ok" : "err");
}

async function call(url, options = {}) {
  const res = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  let body = null;
  try {
    body = await res.json();
  } catch {
    body = null;
  }
  return { ok: res.ok, status: res.status, body };
}

function errorText(result) {
  const b = result.body || {};
  return b.error || b.detail || `Request failed (HTTP ${result.status})`;
}

const money = (value) => Number(value).toFixed(2);

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[c]);
}

// ---- Sign in ---------------------------------------------------------------
$("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target));
  const out = $("login-result");
  try {
    const r = await call(`${SERVICES.auth}/auth/login`, { method: "POST", body: JSON.stringify(data) });
    if (r.ok) {
      show(out, `Signed in as ${r.body.displayName}.`, true);
      $("session").textContent = `Signed in: ${r.body.displayName}`;
    } else if (r.body && typeof r.body.remainingAttempts === "number") {
      show(out, `${errorText(r)}. ${r.body.remainingAttempts} attempt(s) remaining.`, false);
    } else {
      show(out, errorText(r), false);
    }
  } catch {
    show(out, "auth-gateway is unreachable.", false);
  }
});

// ---- Accounts & transfers -------------------------------------------------
async function loadAccounts() {
  const body = $("accounts-body");
  try {
    const r = await call(`${SERVICES.transactions}/accounts`);
    if (!r.ok) throw new Error();
    body.innerHTML = r.body.map((a) => `
      <tr><td>${escapeHtml(a.id)}</td><td>${escapeHtml(a.name)}</td>
      <td class="num">$${escapeHtml(money(a.balance))}</td></tr>`).join("");

    const options = r.body.map((a) => `<option value="${escapeHtml(a.id)}">${escapeHtml(a.id)} · ${escapeHtml(a.name)}</option>`).join("");
    for (const id of ["from-select", "to-select"]) {
      const select = $(id);
      const current = select.value;
      select.innerHTML = options;
      if (current) select.value = current;
    }
    if (!$("to-select").dataset.init) {
      $("to-select").selectedIndex = Math.min(2, r.body.length - 1);
      $("to-select").dataset.init = "1";
    }
  } catch {
    body.innerHTML = `<tr><td colspan="3" class="muted">transaction-service is unreachable.</td></tr>`;
  }
}

$("transfer-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target));
  const payload = { ...data, amount: Number(data.amount) };
  const out = $("transfer-result");
  try {
    const r = await call(`${SERVICES.transactions}/transfers`, { method: "POST", body: JSON.stringify(payload) });
    if (r.ok) {
      show(out, `Transfer ${r.body.reference} complete: $${money(r.body.amount)} from ${r.body.fromAccountId} to ${r.body.toAccountId}.`, true);
    } else {
      show(out, errorText(r), false);
    }
  } catch {
    show(out, "transaction-service is unreachable.", false);
  }
  loadAccounts();
  loadAudit();
});

// ---- Customer profile ------------------------------------------------------
async function loadCustomers(selectId) {
  const select = $("customer-select");
  try {
    const r = await call(`${SERVICES.pii}/customers`);
    if (!r.ok) throw new Error();
    select.innerHTML = r.body.map((c) => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.id)} · ${escapeHtml(c.name)}</option>`).join("");
    if (selectId) select.value = selectId;
    loadProfile(select.value);
  } catch {
    $("customer-profile").innerHTML = `<dd class="muted">pii-vault is unreachable.</dd>`;
  }
}

async function loadProfile(id) {
  const dl = $("customer-profile");
  const out = $("customer-result");
  out.textContent = "";
  out.className = "result";
  if (!id) return;
  try {
    const r = await call(`${SERVICES.pii}/customers/${encodeURIComponent(id)}`);
    if (!r.ok) {
      dl.innerHTML = "";
      show(out, r.status >= 500 ? "Something went wrong loading this profile. Please try again later." : errorText(r), false);
      return;
    }
    const p = r.body;
    dl.innerHTML = `
      <dt>Name</dt><dd>${escapeHtml(p.name)}</dd>
      <dt>Email</dt><dd>${escapeHtml(p.email)}</dd>
      <dt>SSN</dt><dd>${escapeHtml(p.ssnMasked)}</dd>
      <dt>Verify (last 4)</dt><dd>${escapeHtml(p.ssnLast4)}</dd>`;
  } catch {
    dl.innerHTML = "";
    show(out, "pii-vault is unreachable.", false);
  }
}

$("customer-select").addEventListener("change", (e) => loadProfile(e.target.value));

$("customer-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target));
  const out = $("customer-result");
  try {
    const r = await call(`${SERVICES.pii}/customers`, { method: "POST", body: JSON.stringify(data) });
    if (r.ok) {
      e.target.reset();
      await loadCustomers(r.body.id);
      show(out, `Created ${r.body.id}.`, true);
    } else {
      show(out, r.status >= 500 ? "Something went wrong creating this customer." : errorText(r), false);
      loadCustomers();
    }
  } catch {
    show(out, "pii-vault is unreachable.", false);
  }
});

// ---- Audit trail -----------------------------------------------------------
async function loadAudit() {
  const body = $("audit-body");
  try {
    const r = await call(`${SERVICES.audit}/events`);
    if (!r.ok) throw new Error();
    body.innerHTML = r.body.length
      ? r.body.map((ev) => `
        <tr><td>${ev.id}</td><td>${escapeHtml(ev.timestamp.replace("T", " ").slice(0, 19))}</td>
        <td>${escapeHtml(ev.actor)}</td><td>${escapeHtml(ev.action)}</td><td>${escapeHtml(ev.details)}</td></tr>`).join("")
      : `<tr><td colspan="5" class="muted">No audit events yet.</td></tr>`;
  } catch {
    body.innerHTML = `<tr><td colspan="5" class="muted">audit-log is unreachable.</td></tr>`;
  }
}

$("audit-refresh").addEventListener("click", loadAudit);

loadAccounts();
loadCustomers();
loadAudit();
