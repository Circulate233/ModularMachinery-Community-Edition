package github.kasuminova.mmce.common.util.concurrent;

import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;

@ZenRegister
@ZenClass("mods.modularmachinery.Action")
@FunctionalInterface
public interface Action extends Runnable {
    void doAction();

    default void run() {
        doAction();
    }
}
