/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package hellfirepvp.modularmachinery.common.integration.crafttweaker;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.data.IData;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.item.IngredientStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.api.oredict.IOreDictEntry;
import crafttweaker.util.IEventHandler;
import github.kasuminova.mmce.common.event.Phase;
import github.kasuminova.mmce.common.event.recipe.*;
import github.kasuminova.mmce.common.util.concurrent.Action;
import hellfirepvp.modularmachinery.common.base.Mods;
import hellfirepvp.modularmachinery.common.crafting.PreparedRecipe;
import hellfirepvp.modularmachinery.common.crafting.RecipeRegistry;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentRequirement;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentSelectorTag;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.helper.AdvancedItemCheckerCT;
import hellfirepvp.modularmachinery.common.integration.crafttweaker.helper.AdvancedItemModifierCT;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineRegistry;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceType;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.crafttweaker.gas.IGasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.*;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: RecipePrimer
 * Created by HellFirePvP
 * Date: 02.01.2018 / 18:18
 */
@ZenRegister
@ZenClass("mods.modularmachinery.RecipePrimer")
public class RecipePrimer implements PreparedRecipe {

    protected final ResourceLocation name, machineName;
    private final int tickTime, priority;
    private final boolean doesVoidPerTick;

    private final List<ComponentRequirement<?, ?>> components = new LinkedList<>();
    private final List<Action> needAfterInitActions = new LinkedList<>();
    private final List<String> toolTipList = new ArrayList<>();
    private final Map<Class<?>, List<IEventHandler<RecipeEvent>>> recipeEventHandlers = new HashMap<>();

    private boolean parallelized = Config.recipeParallelizeEnabledByDefault;
    private boolean loadJEI = true;
    private int maxThreads = -1;
    private String threadName = "";
    private ComponentRequirement<?, ?> lastComponent = null;

    public RecipePrimer(ResourceLocation registryName, ResourceLocation owningMachine, int tickTime, int configuredPriority, boolean doesVoidPerTick) {
        this.name = registryName;
        this.machineName = owningMachine;
        this.tickTime = tickTime;
        this.priority = configuredPriority;
        this.doesVoidPerTick = doesVoidPerTick;
    }

    @ZenMethod
    public RecipePrimer setParallelizeUnaffected(boolean unaffected) {
        if (lastComponent instanceof ComponentRequirement.Parallelizable parallelizable) {
            parallelizable.setParallelizeUnaffected(unaffected);
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] Target " + lastComponent.getClass() + " cannot be parallelized!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setParallelized(boolean isParallelized) {
        this.parallelized = isParallelized;
        return this;
    }

    @ZenMethod
    public RecipePrimer setChance(float chance) {
        if (lastComponent != null) {
            if (lastComponent instanceof ComponentRequirement.ChancedRequirement chancedReq) {
                chancedReq.setChance(chance);
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] Cannot set chance for not-chance-based Component: " + lastComponent.getClass());
            }
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setTag(String selectorTag) {
        if (lastComponent != null) {
            lastComponent.setTag(new ComponentSelectorTag(selectorTag));
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setPreViewNBT(IData nbt) {
        if (lastComponent != null) {
            if (lastComponent instanceof RequirementItem reqItem) {
                reqItem.previewDisplayTag = CraftTweakerMC.getNBTCompound(nbt);
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] setPreViewNBT(IData nbt) only can be applied to `Item`!");
            }
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] setPreViewNBT(IData nbt) only can be applied to `Item`!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setNBTChecker(AdvancedItemCheckerCT checker) {
        if (lastComponent != null) {
            if (lastComponent instanceof RequirementItem reqItem) {
                reqItem.setItemChecker((controller, stack) -> checker.isMatch(controller, CraftTweakerMC.getIItemStack(stack)));
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] setNBTChecker(AdvancedItemNBTChecker checker) only can be applied to `Item`!");
            }
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] setNBTChecker(AdvancedItemNBTChecker checker) only can be applied to `Item`!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addItemModifier(AdvancedItemModifierCT modifier) {
        if (lastComponent != null) {
            if (lastComponent instanceof RequirementItem reqItem) {
                reqItem.addItemModifier((controller, stack) -> CraftTweakerMC.getItemStack(modifier.apply(controller, CraftTweakerMC.getIItemStackMutable(stack))));
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] addItemModifier(AdvancedItemModifier checker) only can be applied to `Item`!");
            }
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] addItemModifier(AdvancedItemModifier checker) only can be applied to `Item`!");
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer setMinMaxAmount(int min, int max) {
        if (lastComponent != null) {
            if (lastComponent instanceof RequirementItem reqItem) {
                if (min < max) {
                    reqItem.minAmount = min;
                    reqItem.maxAmount = max;
                } else {
                    CraftTweakerAPI.logWarning("[ModularMachinery] `min` cannot larger than `max`!");
                }
            } else {
                CraftTweakerAPI.logWarning("[ModularMachinery] setMinMaxOutputAmount(int min, int max) only can be applied to `Item`!");
            }
        } else {
            CraftTweakerAPI.logWarning("[ModularMachinery] setMinMaxOutputAmount(int min, int max) only can be applied to `Item`!");
        }
        return this;
    }

    /**
     * <p>为某个输入或输出设置特定触发时间。</p>
     * <p>注意：如果设置了触发时间，则配方在其他时间不会触发任何消耗或产出动作。</p>
     *
     * @param tickTime 触发的配方时间，实际触发时间受到配方修改器影响
     */
    @ZenMethod
    public RecipePrimer setTriggerTime(int tickTime) {
        if (lastComponent != null) {
            lastComponent.setTriggerTime(tickTime);
        }
        return this;
    }

    /**
     * 使触发时间可以被重复触发。
     *
     * @param repeatable true 为可重复，默认 false。
     */
    @ZenMethod
    public RecipePrimer setTriggerRepeatable(boolean repeatable) {
        if (lastComponent != null) {
            lastComponent.setTriggerRepeatable(repeatable);
        }
        return this;
    }

    /**
     * <p>使一个物品/流体等需求忽略输出检测，对一些大量输出不同种类物品等需求非常有用。</p>
     * <p>警告：如果忽略输出则有时可能会导致输出吞物品行为。</p>
     *
     * @param ignoreOutputCheck true 为忽略，默认为 false 不忽略。
     */
    @ZenMethod
    public RecipePrimer setIgnoreOutputCheck(boolean ignoreOutputCheck) {
        if (lastComponent != null) {
            lastComponent.setIgnoreOutputCheck(ignoreOutputCheck);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addRecipeTooltip(String... tooltips) {
        toolTipList.addAll(Arrays.asList(tooltips));
        return this;
    }

    @ZenMethod
    public RecipePrimer addSmartInterfaceDataInput(String typeStr, float minValue, float maxValue) {
        needAfterInitActions.add(() -> {
            DynamicMachine machine = MachineRegistry.getRegistry().getMachine(machineName);
            if (machine == null) {
                CraftTweakerAPI.logError("Could not find machine `" + machineName.toString() + "`!");
                return;
            }
            SmartInterfaceType type = machine.getSmartInterfaceType(typeStr);
            if (type == null) {
                CraftTweakerAPI.logError("SmartInterfaceType " + typeStr + " Not Found!");
                return;
            }
            appendComponent(new RequirementInterfaceNumInput(type, minValue, maxValue));
        });
        return this;
    }

    @ZenMethod
    public RecipePrimer addSmartInterfaceDataInput(String typeStr, float value) {
        return addSmartInterfaceDataInput(typeStr, value, value);
    }

    /**
     * 设置此配方在工厂中同时运行的数量是否不超过指定数值。
     */
    @ZenMethod
    public RecipePrimer setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        return this;
    }

    /**
     * 设置此配方只能被指定的核心线程执行。
     *
     * @param name 线程名
     */
    @ZenMethod
    public RecipePrimer setThreadName(String name) {
        this.threadName = name == null ? "" : name;
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // EventHandlers
    //----------------------------------------------------------------------------------------------

    @ZenMethod
    public RecipePrimer addPreCheckHandler(IEventHandler<RecipeCheckEvent> handler) {
        addRecipeEventHandler(RecipeCheckEvent.class, event -> {
            if (event.phase != Phase.START) return;
            handler.handle(event);
        });
        return this;
    }

    @ZenMethod
    public RecipePrimer addPostCheckHandler(IEventHandler<RecipeCheckEvent> handler) {
        addRecipeEventHandler(RecipeCheckEvent.class, event -> {
            if (event.phase != Phase.END) return;
            handler.handle(event);
        });
        return this;
    }

    @ZenMethod
    @Deprecated
    public RecipePrimer addCheckHandler(IEventHandler<RecipeCheckEvent> handler) {
        CraftTweakerAPI.logWarning("[ModularMachinery] Deprecated method addCheckHandler()! Consider using addPostCheckHandler()");
        addRecipeEventHandler(RecipeCheckEvent.class, handler);
        return this;
    }

    @ZenMethod
    public RecipePrimer addStartHandler(IEventHandler<RecipeStartEvent> handler) {
        addRecipeEventHandler(RecipeStartEvent.class, handler);
        return this;
    }

    @ZenMethod
    public RecipePrimer addPreTickHandler(IEventHandler<RecipeTickEvent> handler) {
        addRecipeEventHandler(RecipeTickEvent.class, event -> {
            if (event.phase != Phase.START) return;
            handler.handle(event);
        });
        return this;
    }

    @ZenMethod
    public RecipePrimer addPostTickHandler(IEventHandler<RecipeTickEvent> handler) {
        addRecipeEventHandler(RecipeTickEvent.class, event -> {
            if (event.phase != Phase.END) return;
            handler.handle(event);
        });
        return this;
    }

    @ZenMethod
    public RecipePrimer addFailureHandler(IEventHandler<RecipeFailureEvent> handler) {
        addRecipeEventHandler(RecipeFailureEvent.class, handler);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFinishHandler(IEventHandler<RecipeFinishEvent> handler) {
        addRecipeEventHandler(RecipeFinishEvent.class, handler);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryStartHandler(IEventHandler<FactoryRecipeStartEvent> handler) {
        addRecipeEventHandler(FactoryRecipeStartEvent.class, handler);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryPreTickHandler(IEventHandler<FactoryRecipeTickEvent> handler) {
        addRecipeEventHandler(FactoryRecipeTickEvent.class, event -> {
            if (event.phase != Phase.START) {
                return;
            }
            handler.handle(event);
        });
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryPostTickHandler(IEventHandler<FactoryRecipeTickEvent> handler) {
        addRecipeEventHandler(FactoryRecipeTickEvent.class, event -> {
            if (event.phase != Phase.END) {
                return;
            }
            handler.handle(event);
        });
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryFailureHandler(IEventHandler<FactoryRecipeFailureEvent> handler) {
        addRecipeEventHandler(FactoryRecipeFailureEvent.class, handler);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFactoryFinishHandler(IEventHandler<FactoryRecipeFinishEvent> handler) {
        addRecipeEventHandler(FactoryRecipeFinishEvent.class, handler);
        return this;
    }

    private <H extends RecipeEvent> void addRecipeEventHandler(Class<H> hClass, IEventHandler<H> handler) {
        recipeEventHandlers.putIfAbsent(hClass, new ArrayList<>());
        recipeEventHandlers.get(hClass).add((IEventHandler<RecipeEvent>) handler);
    }

    //----------------------------------------------------------------------------------------------
    // General Input & Output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addInput(IIngredient input) {
        if (input instanceof IItemStack ||
                input instanceof IOreDictEntry ||
                input instanceof IngredientStack && input.getInternal() instanceof IOreDictEntry) {
            addItemInput(input);
        } else if (input instanceof ILiquidStack liquidStack) {
            addFluidInput(liquidStack);
        } else if (Mods.MEKANISM.isPresent() && checkIGasStackAndAdd(IOType.INPUT, input)) {
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid input type %s(%s)! Ignored.", input, input.getClass()));
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addInputs(IIngredient... inputs) {
        for (IIngredient input : inputs) {
            addInput(input);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addOutput(IIngredient output) {
        if (output instanceof IItemStack ||
                output instanceof IOreDictEntry ||
                output instanceof IngredientStack && output.getInternal() instanceof IOreDictEntry) {
            addItemOutput(output);
        } else if (output instanceof ILiquidStack) {
            addFluidOutput((ILiquidStack) output);
        } else if (Mods.MEKANISM.isPresent() && checkIGasStackAndAdd(IOType.OUTPUT, output)) {
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid output type %s(%s)! Ignored.", output, output.getClass()));
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addOutputs(IIngredient... outputs) {
        for (IIngredient output : outputs) {
            addOutput(output);
        }
        return this;
    }

    @Optional.Method(modid = "mekanism")
    private boolean checkIGasStackAndAdd(IOType ioType, IIngredient input) {
        if (!(input instanceof IGasStack gasStack)) {
            return false;
        }
        switch (ioType) {
            case INPUT -> addGasInput(gasStack);
            case OUTPUT -> addGasOutput(gasStack);
        }
        return true;
    }

    //----------------------------------------------------------------------------------------------
    // Energy input & output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addEnergyPerTickInput(long perTick) {
        requireEnergy(IOType.INPUT, perTick);
        return this;
    }

    @ZenMethod
    public RecipePrimer addEnergyPerTickOutput(long perTick) {
        requireEnergy(IOType.OUTPUT, perTick);
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // FLUID input & output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addFluidInput(ILiquidStack fluid) {
        requireFluid(IOType.INPUT, fluid, false);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidInputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidInput(fluid);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickInput(ILiquidStack fluid) {
        requireFluid(IOType.INPUT, fluid, true);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickInputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidPerTickInput(fluid);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidOutput(ILiquidStack fluid) {
        requireFluid(IOType.OUTPUT, fluid, false);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidOutputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidOutput(fluid);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickOutput(ILiquidStack fluid) {
        requireFluid(IOType.OUTPUT, fluid, true);
        return this;
    }

    @ZenMethod
    public RecipePrimer addFluidPerTickOutputs(ILiquidStack... fluids) {
        for (ILiquidStack fluid : fluids) {
            addFluidPerTickOutput(fluid);
        }
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // GAS input & output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    @Deprecated
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasInput(String gasName, int amount) {
        requireGas(IOType.INPUT, gasName, amount);
        return this;
    }

    @ZenMethod
    @Deprecated
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasOutput(String gasName, int amount) {
        requireGas(IOType.OUTPUT, gasName, amount);
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasInput(IGasStack gasStack) {
        requireGas(IOType.INPUT, gasStack, false);
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasOutput(IGasStack gasStack) {
        requireGas(IOType.OUTPUT, gasStack, false);
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasInputs(IGasStack... gasStacks) {
        for (final IGasStack gasStack : gasStacks) {
            requireGas(IOType.INPUT, gasStack, false);
        }
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasOutputs(IGasStack... gasStacks) {
        for (final IGasStack gasStack : gasStacks) {
            requireGas(IOType.OUTPUT, gasStack, false);
        }
        return this;
    }

    // Gas Per Tick Input / Output

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasPerTickInput(IGasStack gasStack) {
        requireGas(IOType.INPUT, gasStack, true);
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasPerTickOutput(IGasStack gasStack) {
        requireGas(IOType.OUTPUT, gasStack, true);
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasPerTickInputs(IGasStack... gasStacks) {
        for (final IGasStack gasStack : gasStacks) {
            requireGas(IOType.INPUT, gasStack, true);
        }
        return this;
    }

    @ZenMethod
    @Optional.Method(modid = "mekanism")
    public RecipePrimer addGasPerTickOutputs(IGasStack... gasStacks) {
        for (final IGasStack gasStack : gasStacks) {
            requireGas(IOType.OUTPUT, gasStack, true);
        }
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // ITEM input
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addItemInput(IIngredient input) {
        if (input instanceof IItemStack) {
            requireItem(IOType.INPUT, (IItemStack) input);
        } else if (input instanceof IOreDictEntry) {
            requireItem(IOType.INPUT, ((IOreDictEntry) input).getName(), 1);
        } else if (input instanceof IngredientStack && input.getInternal() instanceof IOreDictEntry) {
            requireItem(IOType.INPUT, ((IOreDictEntry) input.getInternal()).getName(), input.getAmount());
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid input type %s(%s)! Ignored.", input, input.getClass()));
        }

        return this;
    }

    @Deprecated
    @ZenMethod
    public RecipePrimer addItemInput(IOreDictEntry oreDict, int amount) {
        requireItem(IOType.INPUT, oreDict.getName(), amount);
        CraftTweakerAPI.logWarning(String.format("[ModularMachinery] Deprecated method " +
                        "`addItemInput(<ore:%s>, %s)`! Consider using `addItemInput(<ore:%s> * %s)`",
                oreDict.getName(), amount, oreDict.getName(), amount)
        );
        return this;
    }

    @ZenMethod
    public RecipePrimer addItemInputs(IIngredient... inputs) {
        for (IIngredient input : inputs) {
            addItemInput(input);
        }
        return this;
    }

    @ZenMethod
    public RecipePrimer addFuelItemInput(int requiredTotalBurnTime) {
        requireFuel(requiredTotalBurnTime);
        return this;
    }

    @ZenMethod
    public RecipePrimer addIngredientArrayInput(IngredientArrayPrimer ingredientArrayPrimer) {
        appendComponent(new RequirementIngredientArray(ingredientArrayPrimer.getIngredientStackList()));
        return this;
    }

    /**
     * <p>设置为false时不再为JEI注册对应配方</p>
     * <p>默认情况在为true</p>
     */
    @ZenMethod
    public RecipePrimer setLoadJEI(boolean load) {
        this.loadJEI = load;
        return this;
    }

    /**
     * <p>随机选择给定的一组物品中的其中一个输出。</p>
     * <p>虽然都使用 IngredientArrayPrimer 作为参数，但是输出的工作机制要<strong>稍有不同</strong>。</p>
     * <p>每个 Ingredient 的 chance 代表整个物品列表的随机选择权重，权重越高，输出概率越大。</p>
     * <p>同样，setMinMaxAmount() 也能够起作用。</p>
     */
    @ZenMethod
    public RecipePrimer addRandomItemOutput(IngredientArrayPrimer ingredientArrayPrimer) {
        appendComponent(new RequirementIngredientArray(ingredientArrayPrimer.getIngredientStackList(), IOType.OUTPUT));
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // ITEM output
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addItemOutput(IIngredient output) {
        if (output instanceof IItemStack) {
            requireItem(IOType.OUTPUT, (IItemStack) output);
        } else if (output instanceof IOreDictEntry) {
            requireItem(IOType.OUTPUT, ((IOreDictEntry) output).getName(), 1);
        } else if (output instanceof IngredientStack && output.getInternal() instanceof IOreDictEntry) {
            requireItem(IOType.OUTPUT, ((IOreDictEntry) output.getInternal()).getName(), output.getAmount());
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid output type %s(%s)! Ignored.", output, output.getClass()));
        }

        return this;
    }

    @Deprecated
    @ZenMethod
    public RecipePrimer addItemOutput(IOreDictEntry oreDict, int amount) {
        requireItem(IOType.OUTPUT, oreDict.getName(), amount);
        CraftTweakerAPI.logWarning(String.format("[ModularMachinery] Deprecated method " +
                        "`addItemOutput(<ore:%s>, %s)`! Consider using `addItemOutput(<ore:%s> * %s)`",
                oreDict.getName(), amount, oreDict.getName(), amount)
        );
        return this;
    }

    @ZenMethod
    public RecipePrimer addItemOutputs(IIngredient... inputs) {
        for (IIngredient input : inputs) {
            addItemOutput(input);
        }

        return this;
    }

    //----------------------------------------------------------------------------------------------
    // Catalyst
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public RecipePrimer addCatalystInput(IIngredient input, String[] tooltips, RecipeModifier[] modifiers) {
        if (input instanceof IItemStack) {
            requireCatalyst((IItemStack) input, tooltips, modifiers);
        } else if (input instanceof IOreDictEntry) {
            requireCatalyst(((IOreDictEntry) input).getName(), 1, tooltips, modifiers);
        } else if (input instanceof IngredientStack && input.getInternal() instanceof IOreDictEntry) {
            requireCatalyst(((IOreDictEntry) input.getInternal()).getName(), input.getAmount(), tooltips, modifiers);
        } else {
            CraftTweakerAPI.logError(String.format("[ModularMachinery] Invalid input type %s(%s)! Ignored.", input, input.getClass()));
        }

        return this;
    }

    @ZenMethod
    public RecipePrimer addCatalystInput(IngredientArrayPrimer input, String[] tooltips, RecipeModifier[] modifiers) {
        requireCatalyst(input, tooltips, modifiers);
        return this;
    }

    //----------------------------------------------------------------------------------------------
    // Internals
    //----------------------------------------------------------------------------------------------
    private void requireEnergy(IOType ioType, long perTick) {
        appendComponent(new RequirementEnergy(ioType, perTick));
    }

    private void requireFluid(IOType ioType, ILiquidStack stack, boolean isPerTick) {
        FluidStack mcFluid = CraftTweakerMC.getLiquidStack(stack);
        if (mcFluid == null) {
            CraftTweakerAPI.logError("[ModularMachinery] FluidStack not found/unknown fluid: " + stack.toString());
            return;
        }
        if (stack.getTag() != null) {
            mcFluid.tag = CraftTweakerMC.getNBTCompound(stack.getTag());
        }

        if (isPerTick) {
            appendComponent(new RequirementFluidPerTick(ioType, mcFluid));
        } else {
            appendComponent(new RequirementFluid(ioType, mcFluid));
        }
    }

    @Deprecated
    @Optional.Method(modid = "mekanism")
    private void requireGas(IOType ioType, String gasName, int amount) {
        Gas gas = GasRegistry.getGas(gasName);
        if (gas == null) {
            CraftTweakerAPI.logError("[ModularMachinery] GasStack not found/unknown gas: " + gasName);
            return;
        }
        int max = Math.max(0, amount);
        GasStack gasStack = new GasStack(gas, max);
        switch (ioType) {
            case INPUT -> CraftTweakerAPI.logWarning(String.format(
                    "[ModularMachinery] `addGasInput(%s, %d)` is deprecated, consider using `<gas:%s> * %d`!",
                    gasName, amount, gasName, amount
            ));
            case OUTPUT -> CraftTweakerAPI.logWarning(String.format(
                    "[ModularMachinery] `addGasOutput(%s, %d)` is deprecated, consider using `<gas:%s> * %d`!",
                    gasName, amount, gasName, amount
            ));
        }
        RequirementGas req = new RequirementGas(ioType, gasStack);
        appendComponent(req);
    }

    @Optional.Method(modid = "mekanism")
    private void requireGas(IOType ioType, IGasStack gasStack, boolean perTick) {
        if (perTick) {
            RequirementGasPerTick req = new RequirementGasPerTick(ioType, (GasStack) gasStack.getInternal());
            appendComponent(req);
            return;
        }
        RequirementGas req = new RequirementGas(ioType, (GasStack) gasStack.getInternal());
        appendComponent(req);
    }

    private void requireFuel(int requiredTotalBurnTime) {
        appendComponent(new RequirementItem(IOType.INPUT, requiredTotalBurnTime));
    }

    private void requireItem(IOType ioType, IItemStack stack) {
        ItemStack mcStack = CraftTweakerMC.getItemStack(stack);
        if (mcStack.isEmpty()) {
            CraftTweakerAPI.logError("[ModularMachinery] ItemStack not found/unknown item: " + stack.toString());
            return;
        }
        RequirementItem ri = new RequirementItem(ioType, mcStack);
        if (stack.getTag().length() > 0) {
            ri.tag = CraftTweakerMC.getNBTCompound(stack.getTag());
            ri.previewDisplayTag = CraftTweakerMC.getNBTCompound(stack.getTag());
        }
        appendComponent(ri);
    }

    private void requireItem(IOType ioType, String oreDictName, int amount) {
        appendComponent(new RequirementItem(ioType, oreDictName, amount));
    }

    private void requireCatalyst(String oreDictName, int amount, String[] tooltips, RecipeModifier[] modifiers) {
        RequirementCatalyst catalyst = new RequirementCatalyst(oreDictName, amount);
        for (String tooltip : tooltips) {
            catalyst.addTooltip(tooltip);
        }
        for (RecipeModifier modifier : modifiers) {
            if (modifier != null) {
                catalyst.addModifier(modifier);
            }
        }
        appendComponent(catalyst);
    }

    private void requireCatalyst(IItemStack stack, String[] tooltips, RecipeModifier[] modifiers) {
        ItemStack mcStack = CraftTweakerMC.getItemStack(stack);
        if (mcStack.isEmpty()) {
            CraftTweakerAPI.logError("[ModularMachinery] ItemStack not found/unknown item: " + stack.toString());
            return;
        }
        RequirementCatalyst catalyst = new RequirementCatalyst(mcStack);
        for (String tooltip : tooltips) {
            catalyst.addTooltip(tooltip);
        }
        for (RecipeModifier modifier : modifiers) {
            if (modifier != null) {
                catalyst.addModifier(modifier);
            }
        }
        appendComponent(catalyst);
    }

    private void requireCatalyst(IngredientArrayPrimer ingredientArrayPrimer, String[] tooltips, RecipeModifier[] modifiers) {
        RequirementCatalyst catalyst = new RequirementCatalyst(ingredientArrayPrimer.getIngredientStackList());
        for (String tooltip : tooltips) {
            catalyst.addTooltip(tooltip);
        }
        for (RecipeModifier modifier : modifiers) {
            if (modifier != null) {
                catalyst.addModifier(modifier);
            }
        }
        appendComponent(catalyst);
    }

    public void appendComponent(ComponentRequirement<?, ?> component) {
        this.components.add(component);
        this.lastComponent = component;
    }

    //----------------------------------------------------------------------------------------------
    // build
    //----------------------------------------------------------------------------------------------
    @ZenMethod
    public void build() {
        RecipeRegistry.getRegistry().registerRecipeEarly(this);
    }

    //----------------------------------------------------------------------------------------------
    // lingering stats
    //----------------------------------------------------------------------------------------------

    @Override
    public String getFilePath() {
        return "";
    }

    @Override
    public ResourceLocation getRecipeRegistryName() {
        return name;
    }

    @Override
    public ResourceLocation getAssociatedMachineName() {
        return machineName;
    }

    @Override
    public ResourceLocation getParentMachineName() {
        return machineName;
    }

    @Override
    public int getTotalProcessingTickTime() {
        return tickTime;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean voidPerTickFailure() {
        return doesVoidPerTick;
    }

    @Override
    public List<ComponentRequirement<?, ?>> getComponents() {
        return components;
    }

    @Override
    public Map<Class<?>, List<IEventHandler<RecipeEvent>>> getRecipeEventHandlers() {
        return recipeEventHandlers;
    }

    @Override
    public List<String> getTooltipList() {
        return toolTipList;
    }

    @Override
    public boolean isParallelized() {
        return parallelized;
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }

    @Override
    public String getThreadName() {
        return threadName;
    }

    @Override
    public void loadNeedAfterInitActions() {
        for (Action needAfterInitAction : needAfterInitActions) {
            needAfterInitAction.doAction();
        }
    }

    @Override
    public boolean getLoadJEI() {
        return loadJEI;
    }
}
