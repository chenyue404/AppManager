// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.content.Context;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.BatteryOptimizationRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskHideRule;
import io.github.muntashirakon.AppManager.rules.struct.NetPolicyRule;
import io.github.muntashirakon.AppManager.rules.struct.NotificationListenerRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.rules.struct.SsaidRule;
import io.github.muntashirakon.AppManager.rules.struct.UriGrantRule;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.ProxyFileReader;
import io.github.muntashirakon.io.ProxyOutputStream;

public class RulesStorageManager implements Closeable {
    public enum Type {
        ACTIVITY,
        PROVIDER,
        RECEIVER,
        SERVICE,
        APP_OP,
        PERMISSION,
        MAGISK_HIDE,
        BATTERY_OPT,
        NET_POLICY,
        NOTIFICATION,
        URI_GRANT,
        SSAID,
        ;

        public static final String[] names = new String[values().length];

        static {
            Type[] values = values();
            for (int i = 0; i < values.length; ++i) names[i] = values[i].name();
        }
    }

    @NonNull
    protected final Context context;
    @NonNull
    private final ArrayList<RuleEntry> entries;
    @GuardedBy("entries")
    @NonNull
    protected String packageName;
    @GuardedBy("entries")
    protected boolean readOnly = true;
    protected int userHandle;

    protected RulesStorageManager(@NonNull Context context, @NonNull String packageName, int userHandle) {
        this.context = context;
        this.packageName = packageName;
        this.userHandle = userHandle;
        this.entries = new ArrayList<>();
        try {
            loadEntries(getDesiredFile(), false);
        } catch (Throwable ignored) {
        }
    }

    public void setReadOnly() {
        this.readOnly = true;
    }

    public void setMutable() {
        this.readOnly = false;
    }

    @Override
    public void close() {
        if (!readOnly) commit();
    }

    @GuardedBy("entries")
    protected RuleEntry get(String name) {
        synchronized (entries) {
            for (RuleEntry entry : entries) if (entry.name.equals(name)) return entry;
            return null;
        }
    }

    @GuardedBy("entries")
    public <T extends RuleEntry> List<T> getAll(Class<T> type) {
        synchronized (entries) {
            List<T> newEntries = new ArrayList<>();
            for (RuleEntry entry : entries) if (type.isInstance(entry)) newEntries.add(type.cast(entry));
            return newEntries;
        }
    }

    @GuardedBy("entries")
    public List<ComponentRule> getAllComponents() {
        return getAll(ComponentRule.class);
    }

    @GuardedBy("entries")
    public List<RuleEntry> getAll() {
        synchronized (entries) {
            return entries;
        }
    }

    /**
     * Check if the given component exists in the rules. It does not necessarily mean that the
     * component is being blocked.
     *
     * @param componentName The component name to check
     * @return {@code true} if exists, {@code false} otherwise
     * @see ComponentsBlocker#isComponentBlocked(String)
     */
    @GuardedBy("entries")
    public boolean hasComponentName(String componentName) {
        synchronized (entries) {
            for (RuleEntry entry : getAll(ComponentRule.class)) if (entry.name.equals(componentName)) return true;
            return false;
        }
    }

    @GuardedBy("entries")
    public int entryCount() {
        synchronized (entries) {
            return entries.size();
        }
    }

    @GuardedBy("entries")
    public void removeEntry(RuleEntry entry) {
        synchronized (entries) {
            entries.remove(entry);
        }
    }

    @GuardedBy("entries")
    protected void removeEntries(String name, Type type) {
        synchronized (entries) {
            Iterator<RuleEntry> entryIterator = entries.iterator();
            RuleEntry entry;
            while (entryIterator.hasNext()) {
                entry = entryIterator.next();
                if (entry.name.equals(name) && entry.type.equals(type)) {
                    entryIterator.remove();
                }
            }
        }
    }

    protected void setComponent(String name, Type componentType, @ComponentRule.ComponentStatus String componentStatus) {
        addUniqueEntry(new ComponentRule(packageName, name, componentType, componentStatus));
    }

    public void setAppOp(int op, @AppOpsManager.Mode int mode) {
        addUniqueEntry(new AppOpRule(packageName, op, mode));
    }

    public void setPermission(String name, boolean isGranted) {
        addUniqueEntry(new PermissionRule(packageName, name, isGranted, 0));
    }

    public void setNotificationListener(String name, boolean isGranted) {
        addUniqueEntry(new NotificationListenerRule(packageName, name, isGranted));
    }

    public void setMagiskHide(boolean isHide) {
        addEntryInternal(new MagiskHideRule(packageName, isHide));
    }

    public void setBatteryOptimization(boolean willOptimize) {
        addEntryInternal(new BatteryOptimizationRule(packageName, willOptimize));
    }

    public void setNetPolicy(@NetworkPolicyManagerCompat.NetPolicy int netPolicy) {
        addEntryInternal(new NetPolicyRule(packageName, netPolicy));
    }

    public void setUriGrant(@NonNull UriManager.UriGrant uriGrant) {
        addEntryInternal(new UriGrantRule(packageName, uriGrant));
    }

    public void setSsaid(@NonNull String ssaid) {
        addEntryInternal(new SsaidRule(packageName, ssaid));
    }

    /**
     * Add entry, remove old entries depending on entry {@link Type}.
     */
    @GuardedBy("entries")
    public void addEntry(@NonNull RuleEntry entry) {
        synchronized (entries) {
            switch (entry.type) {
                case ACTIVITY:
                case PROVIDER:
                case RECEIVER:
                case SERVICE:
                case PERMISSION:
                case APP_OP:
                case NOTIFICATION:
                    addUniqueEntry(entry);
                    break;
                case SSAID:
                case URI_GRANT:
                case NET_POLICY:
                case BATTERY_OPT:
                case MAGISK_HIDE:
                default:
                    addEntryInternal(entry);
            }
        }
    }

    /**
     * Remove the exact entry if exists before adding it.
     */
    @GuardedBy("entries")
    private void addEntryInternal(@NonNull RuleEntry entry) {
        synchronized (entries) {
            removeEntry(entry);
            entries.add(entry);
        }
    }

    /**
     * Remove all entries of the given name and type before adding the entry.
     */
    @GuardedBy("entries")
    private void addUniqueEntry(@NonNull RuleEntry entry) {
        // TODO: 19/5/21 Test uniqueness of the rules
        synchronized (entries) {
            removeEntries(entry.name, entry.type);
            entries.add(entry);
        }
    }

    public void applyAppOpsAndPerms(boolean apply) {
        int uid = PackageUtils.getAppUid(new UserPackagePair(packageName, userHandle));
        AppOpsService appOpsService = new AppOpsService();
        if (apply) {
            // Apply all app ops
            for (AppOpRule appOp : getAll(AppOpRule.class)) {
                try {
                    appOpsService.setMode(appOp.getOp(), uid, packageName, appOp.getMode());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            // Apply all permissions
            for (PermissionRule permission : getAll(PermissionRule.class)) {
                try {
                    if (permission.isGranted()) {
                        // grant permission
                        PermissionCompat.grantPermission(packageName, permission.name, userHandle);
                    } else {
                        PermissionCompat.revokePermission(packageName, permission.name, userHandle);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // Reset all app ops
            try {
                appOpsService.resetAllModes(userHandle, packageName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            // Revoke all permissions
            for (PermissionRule permission : getAll(PermissionRule.class)) {
                try {
                    PermissionCompat.revokePermission(packageName, permission.name, userHandle);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @GuardedBy("entries")
    protected void loadEntries(File file, boolean isExternal) throws IOException, RemoteException {
        String dataRow;
        try (BufferedReader TSVFile = new BufferedReader(new ProxyFileReader(file))) {
            while ((dataRow = TSVFile.readLine()) != null) {
                RuleEntry entry = RuleEntry.unflattenFromString(packageName, dataRow, isExternal);
                synchronized (entries) {
                    entries.add(entry);
                }
            }
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    public void commit() {
        try {
            saveEntries(getDesiredFile(), false);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    public void commitExternal(File tsvRulesFile) {
        try {
            saveEntries(tsvRulesFile, true);
        } catch (IOException | RemoteException ex) {
            ex.printStackTrace();
        }
    }

    @WorkerThread
    @GuardedBy("entries")
    protected void saveEntries(File tsvRulesFile, boolean isExternal) throws IOException, RemoteException {
        synchronized (entries) {
            if (entries.size() == 0) {
                //noinspection ResultOfMethodCallIgnored
                tsvRulesFile.delete();
                return;
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (RuleEntry entry : entries) {
                stringBuilder.append(entry.flattenToString(isExternal)).append("\n");
            }
            try (OutputStream TSVFile = new ProxyOutputStream(tsvRulesFile)) {
                TSVFile.write(stringBuilder.toString().getBytes());
            }
        }
    }

    @NonNull
    public static File getConfDir() {
        return new File(AppManager.getContext().getFilesDir(), "conf");
    }

    @NonNull
    protected File getDesiredFile() throws FileNotFoundException {
        File confDir = getConfDir();
        if (!confDir.exists() && !confDir.mkdirs()) {
            throw new FileNotFoundException("Can not get correct path to save ifw rules");
        }
        return new File(confDir, packageName + ".tsv");
    }

}
