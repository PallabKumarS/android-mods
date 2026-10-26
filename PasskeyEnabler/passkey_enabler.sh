#!/system/bin/sh
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done
sleep 3

ACTIVE_PKG=$(getprop persist.passkey.provider)
ACTIVE_SVC=$(getprop persist.passkey.service)

if [ -z "$ACTIVE_PKG" ]; then
    ACTIVE_PKG="com.x8bit.bitwarden"
    ACTIVE_SVC="com.x8bit.bitwarden/com.x8bit.bitwarden.Autofill.CredentialProviderService"
fi

if [ "$ACTIVE_PKG" = "com.google.android.gms" ]; then
    settings put secure credential_service com.google.android.gms/com.google.android.gms.auth.api.credentials.credman.service.PasswordAndPasskeyService
    settings put secure credential_service_primary com.google.android.gms/com.google.android.gms.auth.api.credentials.credman.service.PasswordAndPasskeyService
    settings put secure autofill_service com.google.android.gms/com.google.android.gms.autofill.service.AutofillService
else
    settings put secure credential_service "$ACTIVE_SVC":com.google.android.gms/com.google.android.gms.auth.api.credentials.credman.service.PasswordAndPasskeyService
    settings put secure credential_service_primary "$ACTIVE_SVC"
    settings put secure autofill_service com.x8bit.bitwarden/com.x8bit.bitwarden.Autofill.AutofillService

    CHROME_PREFS="/data/data/com.android.chrome/shared_prefs/com.android.chrome_preferences.xml"
    if [ -f "$CHROME_PREFS" ]; then
        CHROME_UID=$(stat -c '%u:%g' /data/data/com.android.chrome)
        sed -i 's/name="Chrome.AutofillThirdPartyMode.State" value="false"/name="Chrome.AutofillThirdPartyMode.State" value="true"/g' "$CHROME_PREFS"
        if [ -n "$CHROME_UID" ]; then
            chown "$CHROME_UID" "$CHROME_PREFS"
        fi
    fi
fi
