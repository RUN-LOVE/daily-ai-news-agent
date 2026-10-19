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
    sources: []
};

const $ = (id) => document.getElementById(id);

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

function init() {
    $("profile-form").addEventListener("submit", saveProfile);
    $("pushChannel").addEventListener("change", updateEmailHint);
    loadProfile();
}

document.addEventListener("DOMContentLoaded", init);
