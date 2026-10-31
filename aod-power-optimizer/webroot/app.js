let execCount = 0;

function exec(command) {
	return new Promise((resolve) => {
		const callbackName = `ksu_cb_${Date.now()}_${execCount++}`;
		window[callbackName] = (errno, stdout, stderr) => {
			delete window[callbackName];
			resolve({
				errno,
				stdout: stdout ? stdout.trim() : "",
				stderr: stderr ? stderr.trim() : "",
			});
		};
		try {
			if (typeof ksu !== "undefined" && ksu.exec) {
				ksu.exec(command, "{}", callbackName);
			} else {
				delete window[callbackName];
				resolve({ errno: 0, stdout: "", stderr: "Mock mode" });
			}
		} catch (e) {
			delete window[callbackName];
			resolve({ errno: -1, stdout: "", stderr: String(e) });
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

function updateBadge(badgeId, text, isActive) {
	const el = document.getElementById(badgeId);
	if (!el) return;
	el.textContent = text;
	if (isActive) {
		el.classList.add("active");
	} else {
		el.classList.remove("active");
	}
}

async function refreshSystemStatus() {
	const battRes = await exec("dumpsys battery");
	if (battRes.stdout) {
		const levelMatch = battRes.stdout.match(/level:\s*(\d+)/);
		const tempMatch = battRes.stdout.match(/temperature:\s*(\d+)/);
		if (levelMatch) {
			const tempStr = tempMatch
				? ` (${(Number.parseInt(tempMatch[1], 10) / 10).toFixed(1)}C)`
				: "";
			document.getElementById("stat-battery").textContent =
				`${levelMatch[1]}%${tempStr}`;
		}
	}

	const dispRes = await exec(
		'dumpsys display | grep -E "mActiveModeId|mActiveDisplayMode" | head -n 1',
	);
	if (dispRes.stdout) {
		const fpsMatch = dispRes.stdout.match(/(\d+(?:\.\d+)?)fps/i);
		if (fpsMatch) {
			document.getElementById("stat-fps").textContent =
				`${Math.round(Number.parseFloat(fpsMatch[1]))} Hz`;
		}
	}

	const pwrRes = await exec(
		'dumpsys power | grep -E "mWakefulness=" | head -n 1',
	);
	if (pwrRes.stdout) {
		const stateMatch = pwrRes.stdout.match(/mWakefulness=([A-Za-z]+)/);
		if (stateMatch) {
			document.getElementById("stat-screen").textContent = stateMatch[1];
		}
	}
}

async function loadCurrentSettings() {
	const lowBitRes = await exec("settings get global ambient_low_bit_enabled");
	const isLowBit = lowBitRes.stdout === "1";
	document.getElementById("chk-low-bit").checked = isLowBit;
	updateBadge("badge-low-bit", `Global: ${lowBitRes.stdout || "0"}`, isLowBit);

	const proxRes = await exec("settings get secure doze_proximity_sensor_enabled");
	const isProx = proxRes.stdout === "1";
	document.getElementById("chk-proximity").checked = isProx;
	updateBadge("badge-proximity", `Secure: ${proxRes.stdout || "0"}`, isProx);

	const minRateRes = await exec("settings get system min_refresh_rate");
	const minRateVal = minRateRes.stdout || "0";
	const rateSelect = document.getElementById("sel-min-rate");
	if (minRateVal === "60.0" || minRateVal === "60") {
		rateSelect.value = "60.0";
	} else if (minRateVal === "90.0" || minRateVal === "90") {
		rateSelect.value = "90.0";
	} else if (minRateVal === "120.0" || minRateVal === "120") {
		rateSelect.value = "120.0";
	} else {
		rateSelect.value = "0";
	}
	updateBadge(
		"badge-min-rate",
		`System: ${minRateVal === "null" ? "Default" : minRateVal}`,
		minRateVal === "60.0",
	);

	const wifiRes = await exec("settings get global wifi_scan_always_enabled");
	const isWifiThrottled = wifiRes.stdout === "0";
	document.getElementById("chk-wifi-scan").checked = isWifiThrottled;
	updateBadge(
		"badge-wifi-scan",
		`Global: ${wifiRes.stdout || "1"}`,
		isWifiThrottled,
	);

	const bleRes = await exec("settings get global ble_scan_always_enabled");
	const isBleThrottled = bleRes.stdout === "0";
	document.getElementById("chk-ble-scan").checked = isBleThrottled;
	updateBadge(
		"badge-ble-scan",
		`Global: ${bleRes.stdout || "1"}`,
		isBleThrottled,
	);

	const phantomRes = await exec(
		"settings get global settings_enable_monitor_phantom_procs",
	);
	const isPhantomDisabled = phantomRes.stdout === "false";
	document.getElementById("chk-phantom").checked = isPhantomDisabled;
	updateBadge(
		"badge-phantom",
		`Monitor: ${phantomRes.stdout || "true"}`,
		isPhantomDisabled,
	);
}

async function saveAndApplySettings() {
	const lowBit = document.getElementById("chk-low-bit").checked ? "1" : "0";
	const proximity = document.getElementById("chk-proximity").checked
		? "1"
		: "0";
	const minRate = document.getElementById("sel-min-rate").value;
	const wifiThrottled = document.getElementById("chk-wifi-scan").checked
		? "1"
		: "0";
	const bleThrottled = document.getElementById("chk-ble-scan").checked
		? "1"
		: "0";
	const phantomDisabled = document.getElementById("chk-phantom").checked
		? "1"
		: "0";

	const configContent = `LOW_BIT=${lowBit}
PROXIMITY_POCKET=${proximity}
MIN_REFRESH_RATE=${minRate}
WIFI_SCAN_THROTTLE=${wifiThrottled}
BLE_SCAN_THROTTLE=${bleThrottled}
PHANTOM_KILLER_DISABLE=${phantomDisabled}
`;

	await exec(`cat << 'EOF' > /data/adb/modules/aod-power-optimizer/config.sh
${configContent}EOF`);
	await exec("chmod 644 /data/adb/modules/aod-power-optimizer/config.sh");

	await exec(`settings put global ambient_low_bit_enabled ${lowBit}`);
	await exec(`settings put global ambient_low_bit_enabled_dev ${lowBit}`);
	await exec(`settings put secure doze_proximity_sensor_enabled ${proximity}`);

	if (minRate !== "0") {
		await exec(`settings put system min_refresh_rate ${minRate}`);
	} else {
		await exec("settings delete system min_refresh_rate");
	}

	await exec(
		`settings put global wifi_scan_always_enabled ${wifiThrottled === "1" ? "0" : "1"}`,
	);
	await exec(
		`settings put global ble_scan_always_enabled ${bleThrottled === "1" ? "0" : "1"}`,
	);

	if (phantomDisabled === "1") {
		await exec(
			"device_config put activity_manager max_phantom_processes 2147483647",
		);
		await exec(
			"settings put global settings_enable_monitor_phantom_procs false",
		);
	} else {
		await exec("device_config put activity_manager max_phantom_processes 32");
		await exec(
			"settings put global settings_enable_monitor_phantom_procs true",
		);
	}

	showToast("Power optimization settings applied");
	await loadCurrentSettings();
	await refreshSystemStatus();
}

async function restoreDefaultSettings() {
	await exec("settings put global ambient_low_bit_enabled 0");
	await exec("settings put global ambient_low_bit_enabled_dev 0");
	await exec("settings put secure doze_proximity_sensor_enabled 0");
	await exec("settings delete system min_refresh_rate");
	await exec("settings put global wifi_scan_always_enabled 1");
	await exec("settings put global ble_scan_always_enabled 1");
	await exec("device_config put activity_manager max_phantom_processes 32");
	await exec("settings put global settings_enable_monitor_phantom_procs true");

	const defaultConfig = `LOW_BIT=0
PROXIMITY_POCKET=0
MIN_REFRESH_RATE=0
WIFI_SCAN_THROTTLE=0
BLE_SCAN_THROTTLE=0
PHANTOM_KILLER_DISABLE=0
`;
	await exec(`cat << 'EOF' > /data/adb/modules/aod-power-optimizer/config.sh
${defaultConfig}EOF`);

	showToast("Restored Android defaults");
	await loadCurrentSettings();
	await refreshSystemStatus();
}

window.addEventListener("DOMContentLoaded", () => {
	document
		.getElementById("btn-save")
		.addEventListener("click", saveAndApplySettings);
	document
		.getElementById("btn-restore")
		.addEventListener("click", restoreDefaultSettings);
	document
		.getElementById("btn-refresh")
		.addEventListener("click", async () => {
			await loadCurrentSettings();
			await refreshSystemStatus();
			showToast("Status refreshed");
		});

	loadCurrentSettings();
	refreshSystemStatus();
});
