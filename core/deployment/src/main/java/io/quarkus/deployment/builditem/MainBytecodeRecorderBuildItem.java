package io.quarkus.deployment.builditem;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.recording.BytecodeRecorderImpl;

public final class MainBytecodeRecorderBuildItem extends MultiBuildItem {

    private final BytecodeRecorderImpl bytecodeRecorder;
    private final String generatedStartupContextClassName;
    private final boolean canWarmup;

    public MainBytecodeRecorderBuildItem(BytecodeRecorderImpl bytecodeRecorder, boolean canWarmup) {
        this.bytecodeRecorder = bytecodeRecorder;
        this.generatedStartupContextClassName = null;
        this.canWarmup = canWarmup;
    }

    public MainBytecodeRecorderBuildItem(String generatedStartupContextClassName, boolean canWarmup) {
        this.generatedStartupContextClassName = generatedStartupContextClassName;
        this.bytecodeRecorder = null;
        this.canWarmup = canWarmup;
    }

    public boolean canWarmup() {
        return canWarmup;
    }

    public BytecodeRecorderImpl getBytecodeRecorder() {
        return bytecodeRecorder;
    }

    public String getGeneratedStartupContextClassName() {
        return generatedStartupContextClassName;
    }
}
