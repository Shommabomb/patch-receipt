package dev.patchreceipt.engine;

import dev.patchreceipt.domain.StageResult;

@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = stage -> {
    };

    void stageCompleted(StageResult stage);
}
