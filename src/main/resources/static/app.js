const state = {
    caseData: null,
    selectedPatch: null,
    runId: null,
    pollTimer: null
};

const byId = (id) => document.getElementById(id);

async function loadCase() {
    const response = await fetch("/api/v1/cases");
    if (!response.ok) throw new Error("The bundled case could not be loaded.");
    const cases = await response.json();
    state.caseData = cases[0];
    byId("bug-report").textContent = state.caseData.bugReport.trim();
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
    renderPending("Queued", "Waiting for the verifier", []);

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
    renderRun(run);
    state.pollTimer = window.setInterval(pollRun, 900);
}

async function pollRun() {
    const response = await fetch(`/api/v1/runs/${state.runId}`);
    if (!response.ok) {
        window.clearInterval(state.pollTimer);
        renderError("The run could not be refreshed.");
        return;
    }
    const run = await response.json();
    renderRun(run);
    if (run.state === "COMPLETED") {
        window.clearInterval(state.pollTimer);
    }
}

function renderRun(run) {
    if (run.state !== "COMPLETED") {
        renderPending(run.state === "QUEUED" ? "Queued" : "Verifying", run.currentStage, run.stages);
        return;
    }
    const badge = byId("verdict-badge");
    badge.className = `verdict ${run.verdict.toLowerCase().replace("_", "-")}`;
    badge.textContent = run.verdict.replace("_", " ");
    byId("current-stage").textContent = `${run.stages.length} evidence stages complete`;
    renderStages(run.stages);
    byId("verdict-copy").innerHTML = `<h3>${escapeHtml(run.summary)}</h3>`;
    byId("receipt-links").innerHTML = [
        ["HTML", run.receipts.html],
        ["Markdown", run.receipts.markdown],
        ["JSON", run.receipts.json]
    ].map(([label, href]) => `<a href="${href}" target="_blank" rel="noreferrer">${label}</a>`).join("");
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
        <li class="${stage.status.toLowerCase()}">
            <span class="stage-dot" aria-hidden="true"></span>
            <div><strong>${escapeHtml(stage.title)}</strong><small>${escapeHtml(stage.summary)}</small></div>
            <time>${formatDuration(stage.durationMs)}</time>
        </li>`).join("");
}

function renderError(message) {
    byId("verdict-badge").className = "verdict rejected";
    byId("verdict-badge").textContent = "Error";
    byId("current-stage").textContent = message;
    byId("run-button").disabled = false;
    state.runId = null;
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
loadCase().catch((error) => {
    byId("loading").textContent = error.message;
});
