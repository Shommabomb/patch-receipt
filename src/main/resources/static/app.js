const state = {
    caseData: null,
    selectedPatch: null,
    runId: null,
    pollTimer: null,
    runStartedAt: null
};

const byId = (id) => document.getElementById(id);

async function loadCase() {
    const response = await fetch("/api/v1/cases");
    if (!response.ok) throw new Error("The bundled case could not be loaded.");
    const cases = await response.json();
    state.caseData = cases[0];
    renderBugReport(state.caseData.bugReport);
    const options = byId("patch-options");
    state.caseData.patches.forEach((patch, index) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "patch-option";
        button.dataset.patchId = patch.patchId;
        button.innerHTML = `<strong>${escapeHtml(patch.title)}</strong><span>${escapeHtml(patch.description)}</span>`;
        button.addEventListener("click", () => selectPatch(patch.patchId));
        options.appendChild(button);
        if (index === 0) selectPatch(patch.patchId);
    });
    byId("loading").hidden = true;
    byId("case-content").hidden = false;
}

function selectPatch(patchId) {
    if (state.runId) return;
    state.selectedPatch = state.caseData.patches.find((patch) => patch.patchId === patchId);
    document.querySelectorAll(".patch-option").forEach((button) => {
        const selected = button.dataset.patchId === patchId;
        button.classList.toggle("selected", selected);
        button.setAttribute("aria-pressed", String(selected));
    });
    byId("patch-diff").textContent = state.selectedPatch.unifiedDiff;
}

async function startRun() {
    if (!state.selectedPatch || state.runId) return;
    byId("run-button").disabled = true;
    byId("idle-state").hidden = true;
    byId("run-state").hidden = false;
    byId("reset-button").hidden = true;
    byId("verdict-copy").innerHTML = "";
    byId("evidence-metrics").innerHTML = "";
    byId("evidence-findings").innerHTML = "";
    byId("evidence-limitations").innerHTML = "";
    byId("receipt-links").innerHTML = "";
    document.querySelector(".receipt-panel").setAttribute("aria-busy", "true");
    renderPending("Queued", "Waiting for the verifier", []);
    if (window.matchMedia("(max-width: 1050px)").matches) {
        document.querySelector(".receipt-panel").scrollIntoView({
            behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
            block: "start"
        });
    }

    try {
        const response = await fetch("/api/v1/runs", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                caseId: state.caseData.caseId,
                patchId: state.selectedPatch.patchId
            })
        });
        if (!response.ok) {
            const problem = await response.json();
            renderError(problem.detail || "The run could not be started.");
            return;
        }
        const run = await response.json();
        state.runId = run.runId;
        state.runStartedAt = Date.now();
        renderRun(run);
        if (state.runId) {
            state.pollTimer = window.setInterval(pollRun, 900);
        }
    } catch (error) {
        renderError("The run could not be started. Check your connection and try again.");
    }
}

async function pollRun() {
    if (state.runStartedAt && Date.now() - state.runStartedAt > 180_000) {
        window.clearInterval(state.pollTimer);
        renderError("Verification exceeded the three-minute dashboard limit. Please try again.");
        return;
    }
    const response = await fetch(`/api/v1/runs/${state.runId}`, {cache: "no-store"});
    if (!response.ok) {
        window.clearInterval(state.pollTimer);
        renderError("The run could not be refreshed.");
        return;
    }
    const run = await response.json();
    renderRun(run);
    if (run.state === "FAILED") {
        window.clearInterval(state.pollTimer);
        renderError(run.failureMessage || "The verification worker stopped unexpectedly.");
        return;
    }
    if (run.state === "COMPLETED") {
        window.clearInterval(state.pollTimer);
        document.querySelector(".receipt-panel").setAttribute("aria-busy", "false");
        byId("reset-button").hidden = false;
    }
}

function renderRun(run) {
    if (run.state === "FAILED") {
        renderError(run.failureMessage || "The verification worker stopped unexpectedly.");
        return;
    }
    if (run.state !== "COMPLETED") {
        renderPending(run.state === "QUEUED" ? "Queued" : "Verifying", run.currentStage, run.stages);
        return;
    }
    if (!run.verdict || !run.receipts) {
        renderError("The verifier completed without a usable receipt. Please run it again.");
        return;
    }
    const badge = byId("verdict-badge");
    badge.className = `verdict ${run.verdict.toLowerCase().replace("_", "-")}`;
    badge.textContent = run.verdict.replace("_", " ");
    byId("current-stage").textContent = `${run.stages.length} evidence stages complete`;
    renderStages(run.stages);
    byId("verdict-copy").innerHTML = `
        <p class="plain-summary">${escapeHtml(run.plainSummary)}</p>
        <details class="technical-summary">
            <summary>Technical summary</summary>
            <p>${escapeHtml(run.summary)}</p>
        </details>`;
    renderEvidence(run.evidence);
    renderLimitations(run.limitations);
    byId("receipt-links").innerHTML = [
        ["HTML", run.receipts.html],
        ["Markdown", run.receipts.markdown],
        ["JSON", run.receipts.json]
    ].map(([label, href]) =>
        `<a href="${escapeHtml(href)}" target="_blank" rel="noreferrer">${label}</a>`
    ).join("");
}

function renderEvidence(evidence) {
    if (!evidence) return;
    byId("evidence-metrics").innerHTML = evidence.metrics.map((metric) => `
        <div>
            <span>${escapeHtml(metric.label)}</span>
            <strong>${escapeHtml(metric.value)}</strong>
            <small>${escapeHtml(metric.description)}</small>
        </div>`).join("");
    byId("evidence-findings").innerHTML = `
        <strong>Decisive findings</strong>
        <ul>${evidence.findings.map((finding) => `<li>${escapeHtml(finding)}</li>`).join("")}</ul>`;
}

function renderLimitations(limitations) {
    if (!limitations || limitations.length === 0) return;
    byId("evidence-limitations").innerHTML = `
        <details open>
            <summary>What this run did not prove</summary>
            <ul>${limitations.map((limitation) =>
                `<li>${escapeHtml(limitation)}</li>`).join("")}</ul>
        </details>`;
}

function renderPending(label, stage, stages) {
    const badge = byId("verdict-badge");
    badge.className = "verdict pending";
    badge.textContent = label;
    byId("current-stage").textContent = stage;
    renderStages(stages);
}

function renderStages(stages) {
    byId("stage-list").innerHTML = stages.map((stage) => `
        <li class="${escapeHtml(stage.status.toLowerCase())}">
            <span class="stage-dot" aria-hidden="true"></span>
            <div><strong>${escapeHtml(stage.title)}</strong><small>${escapeHtml(stage.summary)}</small></div>
            <time>${formatDuration(stage.durationMs)}</time>
        </li>`).join("");
}

function renderError(message) {
    byId("verdict-badge").className = "verdict rejected";
    byId("verdict-badge").textContent = "Error";
    byId("current-stage").textContent = message;
    document.querySelector(".receipt-panel").setAttribute("aria-busy", "false");
    byId("run-button").disabled = false;
    if (state.pollTimer) window.clearInterval(state.pollTimer);
    state.runId = null;
    state.pollTimer = null;
    state.runStartedAt = null;
}

function resetRun() {
    if (state.pollTimer) window.clearInterval(state.pollTimer);
    state.runId = null;
    state.pollTimer = null;
    state.runStartedAt = null;
    byId("run-button").disabled = false;
    byId("run-state").hidden = true;
    byId("idle-state").hidden = false;
    byId("reset-button").hidden = true;
    byId("stage-list").innerHTML = "";
    byId("verdict-copy").innerHTML = "";
    byId("evidence-metrics").innerHTML = "";
    byId("evidence-findings").innerHTML = "";
    byId("evidence-limitations").innerHTML = "";
    byId("receipt-links").innerHTML = "";
}

function renderBugReport(markdown) {
    const lines = String(markdown).trim().split(/\r?\n/);
    if (lines[0]?.startsWith("# ")) {
        byId("bug-title").textContent = lines.shift().slice(2);
    }
    byId("bug-report").textContent = lines.join("\n").trim();
}

function formatDuration(milliseconds) {
    if (!milliseconds) return "shared";
    return milliseconds < 1000 ? `${milliseconds} ms` : `${(milliseconds / 1000).toFixed(1)} s`;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

byId("run-button").addEventListener("click", startRun);
byId("reset-button").addEventListener("click", resetRun);
loadCase().catch((error) => {
    byId("loading").textContent = error.message;
});
