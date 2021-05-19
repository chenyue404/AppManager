// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;

public class AppOpRule extends RuleEntry {
    private final int op;
    @AppOpsManager.Mode
    private int mode;

    public AppOpRule(@NonNull String packageName, int op, @AppOpsManager.Mode int mode) {
        super(packageName, String.valueOf(op), RulesStorageManager.Type.APP_OP);
        this.op = op;
        this.mode = mode;
    }

    public AppOpRule(@NonNull String packageName, String opInt, @NonNull StringTokenizer tokenizer)
            throws RuntimeException {
        super(packageName, opInt, RulesStorageManager.Type.APP_OP);
        this.op = Integer.parseInt(opInt);
        if (tokenizer.hasMoreElements()) {
            mode = Integer.parseInt(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: mode not found");
    }

    public int getOp() {
        return op;
    }

    @AppOpsManager.Mode
    public int getMode() {
        return mode;
    }

    public void setMode(@AppOpsManager.Mode int mode) {
        this.mode = mode;
    }

    @NonNull
    @Override
    public String toString() {
        return "AppOpRule{" +
                "packageName='" + packageName + '\'' +
                ", op=" + op +
                ", mode=" + mode +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + op + "\t" + type.name() + "\t" + mode;
    }
}
