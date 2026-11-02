const MODDIR = "/data/adb/modules/aod-power-optimizer";
const MI_DOZE_NODE = "/sys/class/mi_display/disp-DSI-0/doze_brightness";

const DEFAULT_CONFIG = {
	HW_AOD_LBM: "1",
	AOD_RAIL_MODE: "lbm",
	AOD_BRIGHTNESS_OVERRIDE: "0",
	AOD_CUSTOM_BRIGHTNESS: "1",
	LOW_BIT: "1",
	PROXIMITY_POCKET: "1",
	SUPPRESS_AOD_PULSE_WAKE: "1",
	SUPPRESS_TILT_WAKE: "1",
	DEEP_DOZE_OPT: "1",
	WIFI_SCAN_THROTTLE: "1",
	BLE_SCAN_THROTTLE: "1",
};

let execSeq = 0;

function exec(command) {
	return new Promise((resolve) => {
		const cbName = `ksu_cb_${Date.now()}_${execSeq++}`;
		window[cbName] = (errno, stdout, stderr) => {
			delete window[cbName];
			resolve({
				errno,
				stdout: stdout ? stdout.trim() : "",
				stderr: stderr ? stderr.trim() : "",
			});
		};
		try {
			if (typeof ksu !== "undefined" && ksu.exec) {
				ksu.exec(command, "{}", cbName);
			} else {
				delete window[cbName];
				resolve({ errno: 0, stdout: "", stderr: "Mock mode" });
			}
		} catch (err) {
			delete window[cbName];
			resolve({ errno: -1, stdout: "", stderr: String(err) });
		}
	});
}

function showToast(message) {
	if (typeof ksu !== "undefined" && ksu.toast) {
		ksu.toast(message);
		return;
	}
	const toast = document.getElementById("toast");
	if (!toast) return;
	toast.textContent = message;
	toast.classList.add("show");
	setTimeout(() => toast.classList.remove("show"), 2500);
}

function updateBadge(id, text, isActive) {
	const el = document.getElementById(id);
	if (!el) return;
	el.textContent = text;
	el.classList.toggle("active", Boolean(isActive));
}

function toggleOverridePanel(enabled) {
	const panel = document.getElementById("panel-aod-slider");
	if (!panel) return;
	panel.classList.toggle("hidden", !enabled);
}

function parseKeyValues(raw) {
	const map = {};
	for (const line of raw.split("\n")) {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith("#")) continue;
		const idx = trimmed.indexOf("=");
		if (idx <= 0) continue;
		const key = trimmed.slice(0, idx).trim();
		const val = trimmed
			.slice(idx + 1)
			.trim()
			.replace(/^["']|["']$/g, "");
		map[key] = val;
	}
	return map;
}

function formatRailLabel(val) {
	if (val === "2") return "LP2 / LBM (2)";
	if (val === "1") return "LP1 / HBM (1)";
	if (val === "0") return "Awake / Normal (0)";
	return "Unsupported";
}

async function refreshSystemStatus() {
	const script = `
		BATT=$(dumpsys battery 2>/dev/null | grep -E "level:|temperature:" | tr '\\n' ' ')
		WAKE=$(dumpsys power 2>/dev/null | grep -m 1 "mWakefulness=" | sed 's/.*mWakefulness=//')
		FPS=$(dumpsys display 2>/dev/null | grep -m 1 -E "mActiveModeId|mActiveDisplayMode|fps=")
		RAIL="na"
		[ -r "${MI_DOZE_NODE}" ] && RAIL=$(cat "${MI_DOZE_NODE}" 2>/dev/null)
		DPID=""
		if [ -f "${MODDIR}/daemon.pid" ]; then
			P=$(cat "${MODDIR}/daemon.pid" 2>/dev/null)
			[ -n "$P" ] && kill -0 "$P" 2>/dev/null && DPID="$P"
		fi
		AOD_BRT=$(settings get secure doze_brightness 2>/dev/null)
		OVR="0"
		[ -f "${MODDIR}/.override_was_active" ] && OVR="1"
		echo "BATT=$BATT"
		echo "WAKE=$WAKE"
		echo "FPS=$FPS"
		echo "RAIL=$RAIL"
		echo "DPID=$DPID"
		echo "AOD_BRT=$AOD_BRT"
		echo "OVR=$OVR"
	`;
	const res = await exec(script);
	const data = parseKeyValues(res.stdout);

	if (data.BATT) {
		const lvl = data.BATT.match(/level:\s*(\d+)/);
		const tmp = data.BATT.match(/temperature:\s*(\d+)/);
		if (lvl) {
			const tempSuffix = tmp
				? ` (${(Number.parseInt(tmp[1], 10) / 10).toFixed(1)}C)`
				: "";
			document.getElementById("stat-battery").textContent =
				`${lvl[1]}%${tempSuffix}`;
		}
	}

	if (data.WAKE) {
		document.getElementById("stat-screen").textContent =
			data.WAKE.split(/\s+/)[0];
	}

	if (data.FPS) {
		const fpsMatch = data.FPS.match(/(\d+(?:\.\d+)?)\s*fps/i);
		if (fpsMatch) {
			document.getElementById("stat-fps").textContent =
				`${Math.round(Number.parseFloat(fpsMatch[1]))} Hz`;
		}
	}

	document.getElementById("stat-rail").textContent = formatRailLabel(data.RAIL);
	document.getElementById("stat-daemon").textContent = data.DPID
		? `Active (${data.DPID})`
		: "Stopped";

	const brtVal =
		data.AOD_BRT && data.AOD_BRT !== "null" ? `${data.AOD_BRT}%` : "Auto";
	const brtMode = data.OVR === "1" ? "Custom" : "System";
	document.getElementById("stat-aod-brt").textContent =
		`${brtVal} (${brtMode})`;
}

async function loadCurrentSettings() {
	const res = await exec(`
		cat "${MODDIR}/config.sh" 2>/dev/null
		echo "---LIVE---"
		echo "SYS_DOZE_BRT=$(settings get secure doze_brightness 2>/dev/null)"
		echo "LOW_BIT_LIVE=$(settings get global ambient_low_bit_enabled 2>/dev/null)"
		echo "PROX_LIVE=$(settings get secure doze_proximity_sensor_enabled 2>/dev/null)"
		echo "DOZE_EN_LIVE=$(settings get secure doze_enabled 2>/dev/null)"
		echo "TILT_GLOBAL_LIVE=$(settings get global ambient_tilt_to_wake 2>/dev/null)"
		echo "SCOPE_ACTIVE=$([ -f "${MODDIR}/.doze_scope_active" ] && echo 1 || echo 0)"
		if [ -r "${MI_DOZE_NODE}" ]; then echo "MI_NODE=supported"; else echo "MI_NODE=missing"; fi
	`);

	const parts = res.stdout.split("---LIVE---");
	const cfg = { ...DEFAULT_CONFIG, ...parseKeyValues(parts[0] || "") };
	const live = parseKeyValues(parts[1] || "");

	const hwLbm = cfg.HW_AOD_LBM === "1";
	const railMode = ["lbm", "auto", "hbm"].includes(cfg.AOD_RAIL_MODE)
		? cfg.AOD_RAIL_MODE
		: "lbm";
	document.getElementById("chk-hw-lbm").checked = hwLbm;
	document.getElementById("sel-rail-mode").value = railMode;
	updateBadge(
		"badge-hw-lbm",
		live.MI_NODE === "supported"
			? `disp-DSI-0: ${hwLbm ? railMode.toUpperCase() : "OFF"}`
			: "Generic Panel",
		hwLbm && live.MI_NODE === "supported",
	);

	const isOverride = cfg.AOD_BRIGHTNESS_OVERRIDE === "1";
	const sysBrt =
		live.SYS_DOZE_BRT && live.SYS_DOZE_BRT !== "null" ? live.SYS_DOZE_BRT : "1";
	const sliderVal = isOverride ? cfg.AOD_CUSTOM_BRIGHTNESS || sysBrt : sysBrt;

	document.getElementById("chk-aod-override").checked = isOverride;
	document.getElementById("rng-aod-brightness").value = sliderVal;
	document.getElementById("lbl-aod-brightness").textContent = `${sliderVal}%`;
	toggleOverridePanel(isOverride);
	updateBadge(
		"badge-aod-override",
		isOverride ? `Overridden: ${sliderVal}%` : `Respecting System (${sysBrt}%)`,
		!isOverride || Boolean(sliderVal),
	);

	populateStandardControls(cfg, live);
}

function populateStandardControls(cfg, live) {
	const isLowBit = cfg.LOW_BIT === "1";
	document.getElementById("chk-low-bit").checked = isLowBit;
	updateBadge(
		"badge-low-bit",
		`Global: ${live.LOW_BIT_LIVE || "0"}`,
		live.LOW_BIT_LIVE === "1",
	);

	const isProx = cfg.PROXIMITY_POCKET === "1";
	document.getElementById("chk-proximity").checked = isProx;
	updateBadge(
		"badge-proximity",
		`Secure: ${live.PROX_LIVE || "0"}`,
		live.PROX_LIVE === "1",
	);

	const pulseOpt = cfg.SUPPRESS_AOD_PULSE_WAKE === "1";
	document.getElementById("chk-suppress-pulse").checked = pulseOpt;
	updateBadge(
		"badge-suppress-pulse",
		pulseOpt
			? "Stays in 60 Hz LP2 AOD"
			: `Wakes Screen (${live.DOZE_EN_LIVE || "1"})`,
		pulseOpt,
	);

	const tiltOpt = cfg.SUPPRESS_TILT_WAKE === "1";
	document.getElementById("chk-suppress-tilt").checked = tiltOpt;
	updateBadge(
		"badge-suppress-tilt",
		tiltOpt
			? "Pickup Wake Suppressed"
			: `Active (${live.TILT_GLOBAL_LIVE || "1"})`,
		tiltOpt,
	);

	const scopeSuffix =
		live.SCOPE_ACTIVE === "1" ? "Active in Doze" : "Armed for AOD";

	const deepDoze = cfg.DEEP_DOZE_OPT === "1";
	document.getElementById("chk-deep-doze").checked = deepDoze;
	updateBadge(
		"badge-deep-doze",
		deepDoze ? `60s Sensorless (${scopeSuffix})` : "Disabled",
		deepDoze,
	);

	const wifiOpt = cfg.WIFI_SCAN_THROTTLE === "1";
	document.getElementById("chk-wifi-scan").checked = wifiOpt;
	updateBadge(
		"badge-wifi-scan",
		wifiOpt ? `Doze-Only (${scopeSuffix})` : "Disabled",
		wifiOpt,
	);

	const bleOpt = cfg.BLE_SCAN_THROTTLE === "1";
	document.getElementById("chk-ble-scan").checked = bleOpt;
	updateBadge(
		"badge-ble-scan",
		bleOpt ? `5% Duty in AOD (${scopeSuffix})` : "Disabled",
		bleOpt,
	);
}

function collectFormConfig() {
	const flag = (id) => (document.getElementById(id).checked ? "1" : "0");
	return {
		HW_AOD_LBM: flag("chk-hw-lbm"),
		AOD_RAIL_MODE: document.getElementById("sel-rail-mode").value,
		AOD_BRIGHTNESS_OVERRIDE: flag("chk-aod-override"),
		AOD_CUSTOM_BRIGHTNESS: document.getElementById("rng-aod-brightness").value,
		LOW_BIT: flag("chk-low-bit"),
		PROXIMITY_POCKET: flag("chk-proximity"),
		SUPPRESS_AOD_PULSE_WAKE: flag("chk-suppress-pulse"),
		SUPPRESS_TILT_WAKE: flag("chk-suppress-tilt"),
		DEEP_DOZE_OPT: flag("chk-deep-doze"),
		WIFI_SCAN_THROTTLE: flag("chk-wifi-scan"),
		BLE_SCAN_THROTTLE: flag("chk-ble-scan"),
	};
}

async function writeAndApplyConfig(cfg, message) {
	const lines = Object.entries(cfg)
		.map(([k, v]) => `${k}=${v}`)
		.join("\n");

	await exec(`cat << 'EOF' > "${MODDIR}/config.sh"
${lines}
EOF
chmod 644 "${MODDIR}/config.sh"
sh "${MODDIR}/apply_settings.sh"
nohup sh "${MODDIR}/doze_daemon.sh" </dev/null >/dev/null 2>&1 &`);

	showToast(message);
	await loadCurrentSettings();
	await refreshSystemStatus();
}

window.addEventListener("DOMContentLoaded", () => {
	const chkOverride = document.getElementById("chk-aod-override");
	const rngBrightness = document.getElementById("rng-aod-brightness");
	const lblBrightness = document.getElementById("lbl-aod-brightness");

	chkOverride.addEventListener("change", () => {
		toggleOverridePanel(chkOverride.checked);
	});

	rngBrightness.addEventListener("input", () => {
		lblBrightness.textContent = `${rngBrightness.value}%`;
	});

	document.getElementById("btn-save").addEventListener("click", async () => {
		await writeAndApplyConfig(
			collectFormConfig(),
			"AOD-only optimizations saved and applied live",
		);
	});

	document.getElementById("btn-restore").addEventListener("click", async () => {
		await writeAndApplyConfig(
			DEFAULT_CONFIG,
			"Restored recommended v2.1.0 AOD-only defaults",
		);
	});

	document.getElementById("btn-refresh").addEventListener("click", async () => {
		await loadCurrentSettings();
		await refreshSystemStatus();
		showToast("Hardware status refreshed");
	});

	loadCurrentSettings();
	refreshSystemStatus();
});
