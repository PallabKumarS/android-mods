const CONFIG_PATH = "/data/adb/modules/aod_dimmer_hyperos/config.json";

const defaultSettings = {
    enabled: true,
    minNits: 2.0,      // stock is ~5.0
    maxNits: 60,       // stock is 60
    delayMs: 1000,     // stock is 1000
    extraDimPercent: 60
};

let currentSettings = { ...defaultSettings };

// DOM Elements
const masterSwitch = document.getElementById("masterSwitch");
const minNitsSlider = document.getElementById("minNitsSlider");
const minNitsValue = document.getElementById("minNitsValue");
const maxNitsSlider = document.getElementById("maxNitsSlider");
const maxNitsValue = document.getElementById("maxNitsValue");
const delaySlider = document.getElementById("delaySlider");
const delayValue = document.getElementById("delayValue");
const extraDimSlider = document.getElementById("extraDimSlider");
const extraDimValue = document.getElementById("extraDimValue");
const btnApply = document.getElementById("btnApply");
const btnReset = document.getElementById("btnReset");
const toast = document.getElementById("toast");

// Root Execution helper via KernelSU
async function execRoot(cmd) {
    if (typeof ksu !== "undefined" && ksu.exec) {
        return await ksu.exec(cmd);
    }
    console.log("[Mock Root Exec]:", cmd);
    return { errno: 0, stdout: "", stderr: "" };
}

// Load config on startup
async function loadConfig() {
    try {
        const res = await execRoot(`cat "${CONFIG_PATH}" 2>/dev/null`);
        if (res.errno === 0 && res.stdout.trim().length > 0) {
            const parsed = JSON.parse(res.stdout);
            currentSettings = { ...defaultSettings, ...parsed };
        }
    } catch (e) {
        console.warn("Could not read config from disk, using defaults", e);
    }
    updateUIFromSettings();
}

function updateUIFromSettings() {
    masterSwitch.checked = currentSettings.enabled;
    minNitsSlider.value = currentSettings.minNits;
    minNitsValue.textContent = currentSettings.minNits.toFixed(1) + " nits";
    
    maxNitsSlider.value = currentSettings.maxNits;
    maxNitsValue.textContent = currentSettings.maxNits + " nits";

    delaySlider.value = currentSettings.delayMs;
    delayValue.textContent = currentSettings.delayMs + " ms";

    extraDimSlider.value = currentSettings.extraDimPercent;
    extraDimValue.textContent = currentSettings.extraDimPercent + "%";
}

// Sliders event listeners
minNitsSlider.addEventListener("input", (e) => {
    const val = parseFloat(e.target.value);
    currentSettings.minNits = val;
    minNitsValue.textContent = val.toFixed(1) + " nits";
});

maxNitsSlider.addEventListener("input", (e) => {
    const val = parseInt(e.target.value);
    currentSettings.maxNits = val;
    maxNitsValue.textContent = val + " nits";
});

delaySlider.addEventListener("input", (e) => {
    const val = parseInt(e.target.value);
    currentSettings.delayMs = val;
    delayValue.textContent = val + " ms";
});

extraDimSlider.addEventListener("input", (e) => {
    const val = parseInt(e.target.value);
    currentSettings.extraDimPercent = val;
    extraDimValue.textContent = val + "%";
});

masterSwitch.addEventListener("change", (e) => {
    currentSettings.enabled = e.target.checked;
});

// Save and apply settings
btnApply.addEventListener("click", async () => {
    const jsonStr = JSON.stringify(currentSettings, null, 2);
    
    // 1. Write config.json
    await execRoot(`mkdir -p /data/adb/modules/aod_dimmer_hyperos && cat << 'EOF' > "${CONFIG_PATH}"\n${jsonStr}\nEOF`);

    // 2. Apply runtime settings dynamically
    if (currentSettings.enabled) {
        await execRoot(`settings put secure reduce_bright_colors_activated 1`);
        await execRoot(`settings put secure reduce_bright_colors_level ${currentSettings.extraDimPercent}`);
        await execRoot(`resetprop -n persist.doze.auto.brightness true`);
    } else {
        await execRoot(`settings put secure reduce_bright_colors_activated 0`);
    }

    showToast("Settings applied successfully!");
});

// Reset defaults
btnReset.addEventListener("click", () => {
    currentSettings = { ...defaultSettings };
    updateUIFromSettings();
    showToast("Defaults restored. Tap Save to apply.");
});

function showToast(msg) {
    if (typeof ksu !== "undefined" && ksu.toast) {
        ksu.toast(msg);
        return;
    }
    toast.textContent = msg;
    toast.classList.add("show");
    setTimeout(() => {
        toast.classList.remove("show");
    }, 2500);
}

// Initialize on page load
window.addEventListener("DOMContentLoaded", loadConfig);
