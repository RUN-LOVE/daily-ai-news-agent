const TOPICS = [
    "大模型",
    "AI Agent",
    "多模态",
    "开源模型",
    "AI 基础设施",
    "行业落地",
    "AI 安全",
    "融资与商业"
];

const state = {
    sources: [],
    currentDate: null,
    running: false
};

const $ = (id) => document.getElementById(id);

function todayString() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");
    return `${now.getFullYear()}-${month}-${day}`;
}

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });

    if (!response.ok) {
        let message = `请求失败（${response.status}）`;
        try {
            const body = await response.json();
            if (body && body.error) {
                message = body.error;
            }
        } catch (ignored) {
            // Keep the HTTP status message when the response is not JSON.
        }
        throw new Error(message);
    }

    if (response.status === 204) {
        return null;
    }
    return response.json();
}

function splitList(value) {
    return [...new Set(
        value.split(/[,，]/)
            .map((item) => item.trim())
            .filter(Boolean)
    )];
}

function showGlobalError(message) {
    const element = $("global-error");
    element.textContent = message;
    element.classList.remove("hidden");
}

function hideGlobalError() {
    $("global-error").classList.add("hidden");
}

function setFormMessage(message, type = "") {
    const element = $("form-message");
    element.textContent = message;
    element.className = "form-message" + (type ? ` ${type}` : "");
}

function setSaveState(message) {
    $("save-state").textContent = message;
}

function renderTopics(selectedTopics) {
    const container = $("topics");
    container.replaceChildren();

    const allTopics = [...new Set([...TOPICS, ...selectedTopics])];
    allTopics.forEach((topic) => {
        const label = document.createElement("label");
        label.className = "chip";

        const input = document.createElement("input");
        input.type = "checkbox";
        input.value = topic;
        input.checked = selectedTopics.includes(topic);

        const text = document.createElement("span");
        text.textContent = topic;

        label.append(input, text);
        container.append(label);
    });
}

function renderSources(selectedSourceIds) {
    const container = $("sources");
    container.replaceChildren();

    if (state.sources.length === 0) {
        const hint = document.createElement("p");
        hint.className = "hint";
        hint.textContent = "没有读取到新闻源，请检查 workspace/sources/feeds.json。";
        container.append(hint);
        return;
    }

    state.sources.forEach((source) => {
        const label = document.createElement("label");
        label.className = "source-card";

        const input = document.createElement("input");
        input.type = "checkbox";
        input.value = source.id;
        input.checked = selectedSourceIds.includes(source.id);

        const body = document.createElement("span");
        body.className = "source-body";

        const name = document.createElement("strong");
        name.textContent = source.name;

        const meta = document.createElement("span");
        const language = source.language === "zh" ? "中文" : "英文";
        meta.textContent = `${language} · ${source.focus || source.type || "RSS"}`;

        body.append(name, meta);
        label.append(input, body);
        container.append(label);
    });
}

function updateEmailHint() {
    const useEmail = $("pushChannel").value === "EMAIL";
    $("email").required = useEmail;
    $("email-hint").textContent = useEmail
        ? "已选择邮件推送，邮箱必填。"
        : "选择邮件推送时，邮箱必填。";
    $("email-hint").classList.toggle("required-hint", useEmail);
}

function fillForm(profile) {
    $("displayName").value = profile.displayName || "";
    $("role").value = profile.role || "";
    $("email").value = profile.email || "";
    $("keywords").value = (profile.keywords || []).join(", ");
    $("excludeKeywords").value = (profile.excludeKeywords || []).join(", ");
    $("maxItems").value = profile.maxItems ?? 8;
    $("deliverTime").value = profile.deliverTime || "08:30";
    $("pushChannel").value = profile.pushChannel || "INBOX";
    $("language").value = profile.language || "zh";

    renderTopics(profile.topics || []);
    renderSources(profile.sources || []);
    updateEmailHint();
}

function collectProfile() {
    const topics = [...$("topics").querySelectorAll("input:checked")]
        .map((input) => input.value);
    const sources = [...$("sources").querySelectorAll("input:checked")]
        .map((input) => input.value);

    return {
        displayName: $("displayName").value.trim(),
        role: $("role").value.trim(),
        email: $("email").value.trim(),
        topics,
        keywords: splitList($("keywords").value),
        excludeKeywords: splitList($("excludeKeywords").value),
        sources,
        language: $("language").value,
        maxItems: Number($("maxItems").value || 8),
        deliverTime: $("deliverTime").value || "08:30",
        pushChannel: $("pushChannel").value
    };
}

async function loadProfile() {
    try {
        const [profile, sources] = await Promise.all([
            api("/api/profile"),
            api("/api/sources")
        ]);
        state.sources = sources;
        fillForm(profile);
        setSaveState("已载入");
        setFormMessage("");
    } catch (error) {
        showGlobalError(`加载失败：${error.message}`);
        setSaveState("加载失败");
    }
}

async function saveProfile(event) {
    event.preventDefault();
    hideGlobalError();

    const payload = collectProfile();
    if (!payload.displayName) {
        setFormMessage("请填写称呼。", "error");
        $("displayName").focus();
        return;
    }
    if (payload.pushChannel === "EMAIL" && !payload.email) {
        setFormMessage("选择邮件推送时必须填写邮箱。", "error");
        $("email").focus();
        return;
    }

    const button = $("save-button");
    button.disabled = true;
    setFormMessage("正在保存…");

    try {
        const saved = await api("/api/profile", {
            method: "PUT",
            body: JSON.stringify(payload)
        });
        fillForm(saved);
        setSaveState(`已保存 · ${new Date().toLocaleTimeString("zh-CN", {
            hour: "2-digit",
            minute: "2-digit"
        })}`);
        setFormMessage("偏好已保存。", "success");
    } catch (error) {
        setFormMessage(error.message, "error");
        setSaveState("保存失败");
    } finally {
        button.disabled = false;
    }
}

function renderMeta(briefing) {
    const container = $("briefing-meta");
    container.replaceChildren();

    const status = briefing.stopReason === "MAX_STEPS" ? "达到步数上限" : "已完成";
    const chips = [
        briefing.date,
        `工具调用 ${briefing.steps.length} 次`,
        `模型轮次 ${briefing.llmCalls} 次`,
        status
    ];
    chips.forEach((text) => {
        const chip = document.createElement("span");
        chip.textContent = text;
        container.append(chip);
    });
}

function appendInline(parent, text) {
    const linkPattern = /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g;
    let lastIndex = 0;
    let match;

    while ((match = linkPattern.exec(text)) !== null) {
        appendBold(parent, text.slice(lastIndex, match.index));
        const anchor = document.createElement("a");
        anchor.href = match[2];
        anchor.textContent = match[1];
        anchor.target = "_blank";
        anchor.rel = "noopener noreferrer";
        parent.append(anchor);
        lastIndex = match.index + match[0].length;
    }
    appendBold(parent, text.slice(lastIndex));
}

function appendBold(parent, text) {
    if (!text) {
        return;
    }
    text.split("**").forEach((part, index) => {
        if (!part) {
            return;
        }
        if (index % 2 === 1) {
            const strong = document.createElement("strong");
            strong.textContent = part;
            parent.append(strong);
        } else {
            parent.append(document.createTextNode(part));
        }
    });
}

function renderBriefingContent(content) {
    const container = $("briefing-content");
    container.replaceChildren();
    let currentList = null;

    (content || "").split(/\r?\n/).forEach((rawLine) => {
        const line = rawLine.trim();
        if (!line) {
            currentList = null;
            return;
        }

        const heading = line.match(/^(#{1,6})\s+(.*)$/);
        if (heading) {
            const level = Math.min(heading[1].length + 2, 5);
            const element = document.createElement(`h${level}`);
            appendInline(element, heading[2]);
            container.append(element);
            currentList = null;
            return;
        }

        const bullet = line.match(/^[-*]\s+(.*)$/);
        if (bullet) {
            if (!currentList) {
                currentList = document.createElement("ul");
                container.append(currentList);
            }
            const item = document.createElement("li");
            appendInline(item, bullet[1]);
            currentList.append(item);
            return;
        }

        const ordered = line.match(/^\d+[.)]\s+(.*)$/);
        if (ordered) {
            if (!currentList) {
                currentList = document.createElement("ol");
                container.append(currentList);
            }
            const item = document.createElement("li");
            appendInline(item, ordered[1]);
            currentList.append(item);
            return;
        }

        if (line.startsWith(">")) {
            const quote = document.createElement("p");
            quote.className = "briefing-quote";
            appendInline(quote, line.replace(/^>\s?/, ""));
            container.append(quote);
            currentList = null;
            return;
        }

        const paragraph = document.createElement("p");
        appendInline(paragraph, line);
        container.append(paragraph);
        currentList = null;
    });
}

function truncateText(value, maxLength) {
    if (!value) {
        return "";
    }
    return value.length <= maxLength ? value : value.slice(0, maxLength) + "…（界面仅显示部分）";
}

function prettyJson(value) {
    try {
        return JSON.stringify(JSON.parse(value), null, 2);
    } catch (ignored) {
        return value;
    }
}

function renderTrace(briefing) {
    const list = $("trace-list");
    list.replaceChildren();
    $("trace-summary").textContent =
        `Agent 执行轨迹 · ${briefing.steps.length} 次工具调用 · ${briefing.llmCalls} 次模型调用`;

    if (!briefing.steps.length) {
        const empty = document.createElement("p");
        empty.className = "trace-empty";
        empty.textContent = "本次运行模型没有调用工具。";
        list.append(empty);
        return;
    }

    briefing.steps.forEach((step) => {
        const item = document.createElement("div");
        item.className = "trace-step";

        const head = document.createElement("div");
        head.className = "trace-head";

        const name = document.createElement("strong");
        name.textContent = `#${step.index} ${step.toolName}`;

        const outcome = document.createElement("span");
        outcome.className = step.success ? "trace-ok" : "trace-fail";
        outcome.textContent = step.success ? "成功" : "失败";

        const duration = document.createElement("span");
        duration.className = "trace-duration";
        duration.textContent = `${step.durationMs} ms`;

        head.append(name, outcome, duration);

        const body = document.createElement("details");
        body.className = "trace-body";
        const summary = document.createElement("summary");
        summary.textContent = "参数与结果";
        body.append(summary);

        if (step.assistantContent) {
            const thought = document.createElement("p");
            thought.className = "trace-thought";
            thought.textContent = truncateText(step.assistantContent, 300);
            body.append(thought);
        }

        const argsLabel = document.createElement("span");
        argsLabel.className = "trace-label";
        argsLabel.textContent = "参数";
        const args = document.createElement("pre");
        args.textContent = truncateText(prettyJson(step.toolArguments), 800);

        const outputLabel = document.createElement("span");
        outputLabel.className = "trace-label";
        outputLabel.textContent = "返回";
        const output = document.createElement("pre");
        output.textContent = truncateText(prettyJson(step.toolOutput), 1000);

        body.append(argsLabel, args, outputLabel, output);
        item.append(head, body);
        list.append(item);
    });
}

function renderEmptyBriefing() {
    state.currentDate = null;
    $("briefing-view").classList.add("hidden");
    $("briefing-empty").classList.remove("hidden");
    $("briefing-eyebrow").textContent = "TODAY";
    $("briefing-heading").textContent = "今日简报";
    $("run-button").textContent = "生成今日简报";
    $("back-button").classList.add("hidden");
    highlightHistory(null);
}

function renderBriefing(briefing) {
    state.currentDate = briefing.date;
    $("briefing-empty").classList.add("hidden");
    $("briefing-view").classList.remove("hidden");

    const isToday = briefing.date === todayString();
    $("briefing-eyebrow").textContent = isToday ? "TODAY" : "ARCHIVE";
    $("briefing-heading").textContent = isToday ? "今日简报" : "历史简报";
    $("run-button").textContent = isToday ? "重新生成今日简报" : "生成今日简报";
    $("back-button").classList.toggle("hidden", isToday);

    renderMeta(briefing);
    renderBriefingContent(briefing.content);
    renderTrace(briefing);
    highlightHistory(briefing.date);
}

function highlightHistory(date) {
    document.querySelectorAll(".history-item").forEach((button) => {
        button.classList.toggle("active", button.dataset.date === date);
    });
}

function renderHistory(items) {
    const list = $("history-list");
    list.replaceChildren();
    $("history-empty").classList.toggle("hidden", items.length > 0);

    items.forEach((item) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "history-item";
        button.dataset.date = item.date;

        const row = document.createElement("span");
        row.className = "history-row";

        const date = document.createElement("strong");
        date.textContent = item.date;

        const stateText = document.createElement("span");
        stateText.className = item.stopReason === "COMPLETED" ? "history-ok" : "history-warn";
        stateText.textContent = item.stopReason === "COMPLETED" ? "已完成" : "未完成";
        row.append(date, stateText);

        const title = document.createElement("span");
        title.className = "history-title";
        title.textContent = item.title;

        const meta = document.createElement("span");
        meta.className = "history-meta";
        meta.textContent = `${item.llmCalls} 轮模型 · ${new Date(item.createdAt).toLocaleString("zh-CN")}`;

        button.append(row, title, meta);
        button.addEventListener("click", () => loadBriefing(item.date));
        list.append(button);
    });

    highlightHistory(state.currentDate);
}

async function loadBriefing(date) {
    try {
        const briefing = await api(`/api/briefings/${encodeURIComponent(date)}`);
        renderBriefing(briefing);
    } catch (error) {
        showGlobalError(`加载简报失败：${error.message}`);
    }
}

async function refreshHistory() {
    const items = await api("/api/briefings");
    renderHistory(items);
    return items;
}

async function loadBriefings(preferredDate = null) {
    try {
        const items = await refreshHistory();
        if (preferredDate) {
            await loadBriefing(preferredDate);
        } else if (!state.currentDate && items.length > 0) {
            await loadBriefing(items[0].date);
        } else if (items.length === 0) {
            renderEmptyBriefing();
        }
    } catch (error) {
        showGlobalError(`加载简报失败：${error.message}`);
    }
}

async function openLatest() {
    state.currentDate = null;
    await loadBriefings();
}

function setRunning(running) {
    state.running = running;
    $("run-button").disabled = running;
    $("run-status").classList.toggle("hidden", !running);
    if (running) {
        $("run-status").textContent = "Agent 正在抓取新闻、筛选内容并生成简报，请稍候…";
    }
}

async function runBriefing() {
    if (state.running) {
        return;
    }
    hideGlobalError();
    setRunning(true);

    try {
        const briefing = await api("/api/agent/run", { method: "POST" });
        renderBriefing(briefing);
        await refreshHistory();
        setFormMessage("简报已生成并保存。", "success");
    } catch (error) {
        showGlobalError(`生成失败：${error.message}`);
    } finally {
        setRunning(false);
    }
}

async function bootstrap() {
    await loadProfile();
    await loadBriefings();
}

function init() {
    $("profile-form").addEventListener("submit", saveProfile);
    $("pushChannel").addEventListener("change", updateEmailHint);
    $("run-button").addEventListener("click", runBriefing);
    $("back-button").addEventListener("click", openLatest);
    bootstrap();
}

document.addEventListener("DOMContentLoaded", init);
