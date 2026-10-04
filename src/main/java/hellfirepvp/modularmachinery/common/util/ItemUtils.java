/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package hellfirepvp.modularmachinery.common.util;

import github.kasuminova.mmce.common.helper.AdvancedItemChecker;
import github.kasuminova.mmce.common.util.OredictCache;
import hellfirepvp.modularmachinery.common.crafting.helper.ProcessingComponent;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.nbt.NBTMatchingHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: ItemUtils
 * Created by HellFirePvP
 * Date: 28.06.2017 / 12:42
 */
public class ItemUtils {

    public static void decrStackInInventory(ItemStackHandler handler, int slot) {
        if (slot < 0 || slot >= handler.getSlots()) {
            return;
        }
        ItemStack st = handler.getStackInSlot(slot);
        if (st.isEmpty()) {
            return;
        }
        st.setCount(st.getCount() - 1);
        if (st.getCount() <= 0) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    //Negative amount: overhead fuel burnt
    //Positive amount: Failure/couldn't find enough fuel
    public static int consumeFromInventoryFuel(IItemHandlerModifiable handler, int fuelAmtToConsume, boolean simulate, @Nullable NBTTagCompound matchNBTTag) {
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventoryFuel(handler, matchNBTTag, contents);
            if (contents.isEmpty()) {
                return fuelAmtToConsume;
            }

            for (int slot : contents.keySet()) {
                ItemStack inSlot = contents.get(slot);
                if (inSlot.getItem().hasContainerItem(inSlot)) {
                    if (inSlot.getCount() > 1) {
                        continue; //uh... rip. we won't consume 16 buckets at once.
                    }
                    ItemStack stack = ForgeHooks.getContainerItem(inSlot);
                    fuelAmtToConsume -= TileEntityFurnace.getItemBurnTime(inSlot);
                    if (!simulate) {
                        handler.setStackInSlot(slot, stack.copy());
                    }
                    if (fuelAmtToConsume <= 0) {
                        break;
                    }
                }
                int fuelPer = TileEntityFurnace.getItemBurnTime(inSlot);
                int toConsumeDiv = fuelAmtToConsume / fuelPer;
                int fuelMod = fuelAmtToConsume % fuelPer;

                int toConsume = toConsumeDiv + (fuelMod > 0 ? 1 : 0);
                int toRemove = Math.min(toConsume, inSlot.getCount());

                fuelAmtToConsume -= toRemove * fuelPer;
                if (!simulate) {
                    handler.setStackInSlot(slot, copyStackWithSize(inSlot, inSlot.getCount() - toRemove));
                }
                if (fuelAmtToConsume <= 0) {
                    break;
                }
            }
            return fuelAmtToConsume;
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static boolean consumeFromInventory(IItemHandlerModifiable handler, ItemStack toConsume, boolean simulate, @Nullable NBTTagCompound matchNBTTag) {
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventory(handler, toConsume, false, matchNBTTag, contents);
            if (contents.isEmpty()) {
                return false;
            }

            int cAmt = toConsume.getCount();
            for (int slot : contents.keySet()) {
                ItemStack inSlot = contents.get(slot);
                if (inSlot.getItem().hasContainerItem(inSlot)) {
                    if (inSlot.getCount() > 1) {
                        continue; //uh... rip. we won't consume 16 buckets at once.
                    }
                    ItemStack stack = ForgeHooks.getContainerItem(inSlot);
                    cAmt--;
                    if (!simulate) {
                        handler.setStackInSlot(slot, stack.copy());
                    }
                    if (cAmt <= 0) {
                        break;
                    }
                }
                int toRemove = Math.min(cAmt, inSlot.getCount());
                cAmt -= toRemove;
                if (!simulate) {
                    handler.setStackInSlot(slot, copyStackWithSize(inSlot, inSlot.getCount() - toRemove));
                }
                if (cAmt <= 0) {
                    break;
                }
            }
            return cAmt <= 0;
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static boolean consumeFromInventory(IItemHandlerModifiable handler, ItemStack toConsume, boolean simulate, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller) {
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventory(handler, toConsume, false, itemChecker, controller, contents);
            if (contents.isEmpty()) {
                return false;
            }

            int cAmt = toConsume.getCount();
            for (int slot : contents.keySet()) {
                ItemStack inSlot = contents.get(slot);
                if (inSlot.getItem().hasContainerItem(inSlot)) {
                    if (inSlot.getCount() > 1) {
                        continue; //uh... rip. we won't consume 16 buckets at once.
                    }
                    ItemStack stack = ForgeHooks.getContainerItem(inSlot);
                    cAmt--;
                    if (!simulate) {
                        handler.setStackInSlot(slot, stack.copy());
                    }
                    if (cAmt <= 0) {
                        break;
                    }
                }
                int toRemove = Math.min(cAmt, inSlot.getCount());
                cAmt -= toRemove;
                if (!simulate) {
                    handler.setStackInSlot(slot, copyStackWithSize(inSlot, inSlot.getCount() - toRemove));
                }
                if (cAmt <= 0) {
                    break;
                }
            }
            return cAmt <= 0;
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int consumeAll(IItemHandlerModifiable handler, ItemStack toConsume, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller) {
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventory(handler, toConsume, false, itemChecker, controller, contents);
            if (toConsume.getCount() <= 0 || contents.isEmpty()) {
                return 0;
            }
            return consumeAllInternal(handler, contents, toConsume.getCount());
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int consumeAll(IItemHandlerModifiable handler, ItemStack toConsume, @Nullable NBTTagCompound matchNBTTag) {
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventory(handler, toConsume, false, matchNBTTag, contents);
            if (toConsume.getCount() <= 0 || contents.isEmpty()) {
                return 0;
            }
            return consumeAllInternal(handler, contents, toConsume.getCount());
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int consumeAll(IItemHandlerModifiable handler, String oreName, int amount, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller) {
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventoryOreDict(handler, oreName, itemChecker, controller, contents);
            if (amount <= 0 || contents.isEmpty()) {
                return 0;
            }
            return consumeAllInternal(handler, contents, amount);
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int consumeAll(IItemHandlerModifiable handler, String oreName, int amount, @Nullable NBTTagCompound matchNBTTag) {
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventoryOreDict(handler, oreName, matchNBTTag, contents);
            if (amount <= 0 || contents.isEmpty()) {
                return 0;
            }
            return consumeAllInternal(handler, contents, amount);
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int damageAll(IItemHandlerModifiable handler, ItemStack toDamage, int amount, int damagePerUse, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller) {
        if (amount <= 0 || damagePerUse <= 0) {
            return 0;
        }
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventory(handler, toDamage, false, itemChecker, controller, contents);
            if (contents.isEmpty()) {
                return 0;
            }
            return damageAllInternal(handler, contents, amount, damagePerUse);
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int damageAll(IItemHandlerModifiable handler, ItemStack toDamage, int amount, int damagePerUse, @Nullable NBTTagCompound matchNBTTag) {
        if (amount <= 0 || damagePerUse <= 0) {
            return 0;
        }
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventory(handler, toDamage, false, matchNBTTag, contents);
            if (contents.isEmpty()) {
                return 0;
            }
            return damageAllInternal(handler, contents, amount, damagePerUse);
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int damageAll(IItemHandlerModifiable handler, String oreName, int amount, int damagePerUse, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller) {
        if (amount <= 0 || damagePerUse <= 0) {
            return 0;
        }
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventoryOreDict(handler, oreName, itemChecker, controller, contents);
            if (contents.isEmpty()) {
                return 0;
            }
            return damageAllInternal(handler, contents, amount, damagePerUse);
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static int damageAll(IItemHandlerModifiable handler, String oreName, int amount, int damagePerUse, @Nullable NBTTagCompound matchNBTTag) {
        if (amount <= 0 || damagePerUse <= 0) {
            return 0;
        }
        Int2ObjectMap<ItemStack> contents = borrowIndexedScratch(handler.getSlots() * 2);
        try {
            fillItemsIndexedInInventoryOreDict(handler, oreName, matchNBTTag, contents);
            if (contents.isEmpty()) {
                return 0;
            }
            return damageAllInternal(handler, contents, amount, damagePerUse);
        } finally {
            returnIndexedScratch(contents);
        }
    }

    public static boolean hasDamageableEntry(final String oreDictName) {
        if (oreDictName == null || oreDictName.isEmpty()) {
            return false;
        }
        NonNullList<ItemStack> entries = OreDictionary.getOres(oreDictName);
        for (ItemStack entry : entries) {
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.isItemStackDamageable()) {
                return true;
            }
            if (entry.getItemDamage() == OreDictionary.WILDCARD_VALUE && entry.getItem().getCreativeTab() != null) {
                NonNullList<ItemStack> subItems = NonNullList.create();
                entry.getItem().getSubItems(entry.getItem().getCreativeTab(), subItems);
                for (ItemStack subEntry : subItems) {
                    if (!subEntry.isEmpty() && subEntry.isItemStackDamageable()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int insertAll(@Nonnull ItemStack stack, IItemHandlerModifiable handler, int maxInsert) {
        if (stack.getCount() <= 0) {
            return 0;
        }

        int inserted = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            int maxStackSize = handler.getSlotLimit(i);
            if (maxStackSize <= 64) {
                maxStackSize = Math.min(maxStackSize, stack.getMaxStackSize());
            }
            ItemStack in = handler.getStackInSlot(i);
            int count = in.getCount();
            if (count >= maxStackSize) {
                continue;
            }

            if (in.isEmpty()) {
                int toInsert = Math.min(maxInsert - inserted, maxStackSize);
                handler.setStackInSlot(i, copyStackWithSize(stack, toInsert));
                inserted += toInsert;
            } else {
                if (stackEqualsNonNBT(stack, in) && matchTags(stack, in)) {
                    int toInsert = Math.min(maxInsert - inserted, maxStackSize - count);
                    handler.setStackInSlot(i, copyStackWithSize(stack, toInsert + count));
                    inserted += toInsert;
                }
            }

            if (inserted >= maxInsert) {
                break;
            }
        }

        return inserted;
    }

    private static int consumeAllInternal(IItemHandlerModifiable handler, Int2ObjectMap<ItemStack> contents, int maxConsume) {
        int cAmt = 0;
        for (final Int2ObjectMap.Entry<ItemStack> content : contents.int2ObjectEntrySet()) {
            int slot = content.getIntKey();
            ItemStack stack = content.getValue();
            int count = stack.getCount();
            if (count > 1) {
                if (stack.getItem().hasContainerItem(stack)) {
                    continue; //uh... rip. we won't consume 16 buckets at once.
                }
            }

            int toConsume = Math.min(maxConsume - cAmt, count);
            handler.setStackInSlot(slot, copyStackWithSize(stack, count - toConsume));
            cAmt += toConsume;

            if (cAmt >= maxConsume) {
                break;
            }
        }

        return cAmt;
    }

    private static int damageAllInternal(IItemHandlerModifiable handler, Int2ObjectMap<ItemStack> contents, int maxOperations, int damagePerUse) {
        int operations = 0;
        if (damagePerUse <= 0) {
            return 0;
        }
        for (final Int2ObjectMap.Entry<ItemStack> content : contents.int2ObjectEntrySet()) {
            int slot = content.getIntKey();
            ItemStack stack = content.getValue();
            if (stack.isEmpty() || !stack.isItemStackDamageable() || stack.getMaxDamage() <= 0) {
                continue;
            }

            while (operations < maxOperations && !stack.isEmpty()) {
                int newDamage = stack.getItemDamage() + damagePerUse;
                if (newDamage >= stack.getMaxDamage()) {
                    stack.shrink(1);
                    if (!stack.isEmpty()) {
                        stack.setItemDamage(0);
                    }
                } else {
                    stack.setItemDamage(newDamage);
                }
                operations++;
            }

            handler.setStackInSlot(slot, stack);

            if (operations >= maxOperations) {
                break;
            }
        }

        return operations;
    }

    public static boolean stackEqualsNonNBT(@Nonnull ItemStack stack, @Nonnull ItemStack other) {
        if (stack.isEmpty() && other.isEmpty()) {
            return true;
        }
        if (stack.isEmpty() || other.isEmpty()) {
            return false;
        }
        Item sItem = stack.getItem();
        Item oItem = other.getItem();
        if (sItem.getHasSubtypes() || oItem.getHasSubtypes()) {
            return sItem.equals(other.getItem()) &&
                (stack.getItemDamage() == other.getItemDamage() ||
                    stack.getItemDamage() == OreDictionary.WILDCARD_VALUE ||
                    other.getItemDamage() == OreDictionary.WILDCARD_VALUE);
        } else {
            return sItem.equals(other.getItem());
        }
    }

    public static boolean matchTags(@Nonnull ItemStack stack, @Nonnull ItemStack other) {
        return ItemStack.areItemStackTagsEqual(stack, other);
    }

    @Nonnull
    public static ItemStack copyStackWithSize(@Nonnull ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack s = stack.copy();
        s.setCount(amount);
        return s;
    }

    /**
     * 向指定容器插入指定的物品，返回未插入的物品。
     *
     * @param external       容器
     * @param willBeInserted 要插入的物品
     * @return 未被插入的物品，如果全部插入，返回空物品
     */
    public static ItemStack insertItemStackToContainer(IItemHandler external, ItemStack willBeInserted) {
        ItemStack beInserted = willBeInserted;
        for (int externalSlotId = 0; externalSlotId < external.getSlots(); externalSlotId++) {
            ItemStack stackInSlot = external.getStackInSlot(externalSlotId);

            if (stackInSlot.isEmpty()) {
                ItemStack notInserted = external.insertItem(externalSlotId, beInserted, false);
                if (notInserted.isEmpty()) {
                    return ItemStack.EMPTY;
                } else {
                    beInserted = notInserted;
                    continue;
                }
            }

            if (matchStacks(stackInSlot, willBeInserted)) {
                ItemStack notInserted = external.insertItem(externalSlotId, beInserted, false);
                if (notInserted.isEmpty()) {
                    return ItemStack.EMPTY;
                } else {
                    beInserted = notInserted;
                }
            }
        }

        return beInserted;
    }

    public static Int2ObjectMap<ItemStack> findItemsIndexedInInventoryFuel(IItemHandlerModifiable handler, @Nullable NBTTagCompound matchNBTTag) {
        Int2ObjectMap<ItemStack> stacksOut = new Int2ObjectOpenHashMap<>(handler.getSlots() * 2);
        fillItemsIndexedInInventoryFuel(handler, matchNBTTag, stacksOut);
        return stacksOut;
    }

    private static void fillItemsIndexedInInventoryFuel(IItemHandlerModifiable handler, @Nullable NBTTagCompound matchNBTTag, Int2ObjectMap<ItemStack> out) {
        for (int j = 0; j < handler.getSlots(); j++) {
            ItemStack s = handler.getStackInSlot(j);
            if (TileEntityFurnace.getItemBurnTime(s) > 0 && NBTMatchingHelper.matchNBTCompound(matchNBTTag, s.getTagCompound())) {
                out.put(j, s);
            }
        }
    }

    public static Int2ObjectMap<ItemStack> findItemsIndexedInInventoryOreDict(IItemHandlerModifiable handler, String oreDict, @Nullable NBTTagCompound matchNBTTag) {
        Int2ObjectMap<ItemStack> stacksOut = new Int2ObjectOpenHashMap<>(handler.getSlots() * 2);
        fillItemsIndexedInInventoryOreDict(handler, oreDict, matchNBTTag, stacksOut);
        return stacksOut;
    }

    private static void fillItemsIndexedInInventoryOreDict(IItemHandlerModifiable handler, String oreDict, @Nullable NBTTagCompound matchNBTTag, Int2ObjectMap<ItemStack> out) {
        for (int j = 0; j < handler.getSlots(); j++) {
            ItemStack s = handler.getStackInSlot(j);
            if (s.isEmpty()) {
                continue;
            }
            int[] ids = OredictCache.getOreIDsFast(s);
            for (int id : ids) {
                if (OreDictionary.getOreName(id).equals(oreDict) && NBTMatchingHelper.matchNBTCompound(matchNBTTag, s.getTagCompound())) {
                    out.put(j, s);
                    break;
                }
            }
        }
    }

    public static Int2ObjectMap<ItemStack> findItemsIndexedInInventoryOreDict(IItemHandlerModifiable handler, String oreDict, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller) {
        Int2ObjectMap<ItemStack> stacksOut = new Int2ObjectOpenHashMap<>(handler.getSlots() * 2);
        fillItemsIndexedInInventoryOreDict(handler, oreDict, itemChecker, controller, stacksOut);
        return stacksOut;
    }

    private static void fillItemsIndexedInInventoryOreDict(IItemHandlerModifiable handler, String oreDict, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller, Int2ObjectMap<ItemStack> out) {
        for (int j = 0; j < handler.getSlots(); j++) {
            ItemStack s = handler.getStackInSlot(j);
            if (s.isEmpty()) {
                continue;
            }
            int[] ids = OredictCache.getOreIDsFast(s);
            for (int id : ids) {
                if (OreDictionary.getOreName(id).equals(oreDict) && itemChecker.isMatch(controller, s)) {
                    out.put(j, s);
                    break;
                }
            }
        }
    }

    public static Int2ObjectMap<ItemStack> findItemsIndexedInInventory(IItemHandlerModifiable handler, ItemStack match, boolean strict, @Nullable NBTTagCompound matchNBTTag) {
        Int2ObjectMap<ItemStack> stacksOut = new Int2ObjectOpenHashMap<>(handler.getSlots() * 2);
        fillItemsIndexedInInventory(handler, match, strict, matchNBTTag, stacksOut);
        return stacksOut;
    }

    private static void fillItemsIndexedInInventory(IItemHandlerModifiable handler, ItemStack match, boolean strict, @Nullable NBTTagCompound matchNBTTag, Int2ObjectMap<ItemStack> out) {
        for (int j = 0; j < handler.getSlots(); j++) {
            ItemStack s = handler.getStackInSlot(j);
            if ((strict ? matchStacks(s, match) : matchStackLoosely(s, match)) && NBTMatchingHelper.matchNBTCompound(matchNBTTag, s.getTagCompound())) {
                out.put(j, s);
            }
        }
    }

    public static Int2ObjectMap<ItemStack> findItemsIndexedInInventory(IItemHandlerModifiable handler, ItemStack match, boolean strict, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller) {
        Int2ObjectMap<ItemStack> stacksOut = new Int2ObjectOpenHashMap<>(handler.getSlots() * 2);
        fillItemsIndexedInInventory(handler, match, strict, itemChecker, controller, stacksOut);
        return stacksOut;
    }

    private static void fillItemsIndexedInInventory(IItemHandlerModifiable handler, ItemStack match, boolean strict, AdvancedItemChecker itemChecker, TileMultiblockMachineController controller, Int2ObjectMap<ItemStack> out) {
        for (int j = 0; j < handler.getSlots(); j++) {
            ItemStack s = handler.getStackInSlot(j);
            if ((strict ? matchStacks(s, match) : matchStackLoosely(s, match)) && itemChecker.isMatch(controller, s)) {
                out.put(j, s);
            }
        }
    }

    /**
     * <p>借用线程本地的槽位索引 scratch map。索引 map 仅在 {@code consumeAll} / {@code damageAll} 等
     * 方法内部同步使用、不逃逸出本类，因此可以安全复用。</p>
     *
     * <p>深度计数防止重入（例如特殊 handler 在写入时再次触发本类方法）导致内容被覆盖；
     * 重入时退回临时分配。</p>
     */
    private static Int2ObjectMap<ItemStack> borrowIndexedScratch(int expectedSize) {
        IndexedScratch scratch = INDEXED_SCRATCH.get();
        scratch.depth++;
        if (scratch.depth > 1) {
            return new Int2ObjectOpenHashMap<>(expectedSize);
        }
        Int2ObjectOpenHashMap<ItemStack> map = scratch.map;
        map.clear();
        return map;
    }

    private static void returnIndexedScratch(Int2ObjectMap<ItemStack> borrowed) {
        INDEXED_SCRATCH.get().depth--;
    }

    private static final class IndexedScratch {
        final Int2ObjectOpenHashMap<ItemStack> map = new Int2ObjectOpenHashMap<>();
        int depth = 0;
    }

    private static final ThreadLocal<IndexedScratch> INDEXED_SCRATCH = ThreadLocal.withInitial(IndexedScratch::new);

    public static boolean matchStacks(@Nonnull ItemStack stack, @Nonnull ItemStack other) {
        if (!ItemStack.areItemsEqual(stack, other)) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(stack, other);
    }

    public static boolean matchStackLoosely(@Nonnull ItemStack stack, @Nonnull ItemStack other) {
        if (stack.isEmpty()) {
            return other.isEmpty();
        }
        return OreDictionary.itemMatches(other, stack, false);
    }

    public static boolean stackNotInList(final List<ItemStack> list, final ItemStack stackFromBlockState) {
        for (final ItemStack stack : list) {
            if (matchStacks(stackFromBlockState, stack)) {
                return false;
            }
        }
        return true;
    }

    public static ItemStack getOredictItem(final RecipeCraftingContext context, final String oreDictName, final NBTTagCompound tag) {
        ItemStack stack = ItemStack.EMPTY;
        for (ItemStack oreInstance : OreDictionary.getOres(oreDictName)) {
            if (oreInstance.isEmpty()) {
                continue;
            }
            stack = copyStackWithSize(oreInstance, 1);

            if (!stack.isEmpty()) { //Try all options first..
                break;
            }
        }

        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Unknown ItemStack: Cannot find an item in oredict '" + oreDictName + "'!");
        }

        if (tag != null) {
            stack.setTagCompound(tag.copy());
        }
        return stack;
    }

    @Nonnull
    public static List<ProcessingComponent<?>> copyItemHandlerComponents(final List<ProcessingComponent<?>> components) {
        CopyShellPool pool = COPY_SHELL_POOL.get();
        boolean pooled = pool.canBorrow();

        List<ProcessingComponent<?>> list = new ArrayList<>(components.size());
        for (ProcessingComponent<?> component : components) {
            Object provided = component.getProvidedComponent();
            IItemHandlerImpl handler = null;

            if (provided instanceof IItemHandlerImpl handlerMM) {
                if (pooled) {
                    handler = pool.borrow().copyFrom(handlerMM);
                } else {
                    handler = handlerMM.copy();
                }
            } else if (provided instanceof IItemHandlerModifiable handlerDefault) {
                handler = new IItemHandlerImpl(handlerDefault);
            }

            if (handler != null) {
                list.add(new ProcessingComponent<>(
                    (MachineComponent<Object>) component.component(),
                    handler,
                    component.getTag())
                );
            }
        }
        return list;
    }

    /**
     * <p>开启一个 handler 拷贝会话。会话期间 {@link #copyItemHandlerComponents(List)} 生成的
     * {@link IItemHandlerImpl} 副本会复用线程本地的空壳对象（仅刷新内容，不重建结构），
     * 会话结束后空壳归还池中。</p>
     *
     * <p><strong>注意：会话结束后，会话期间生成的副本不得再被引用或修改。</strong></p>
     * <p>会话必须以 try/finally 配对，异常路径也应调用 {@link #endHandlerCopySession()}。</p>
     */
    public static void beginHandlerCopySession() {
        COPY_SHELL_POOL.get().enter();
    }

    /**
     * <p>结束当前线程的 handler 拷贝会话，归还本会话借出的全部空壳。</p>
     *
     * @see #beginHandlerCopySession()
     */
    public static void endHandlerCopySession() {
        COPY_SHELL_POOL.get().exit();
    }

    private static final int COPY_SHELL_POOL_LIMIT = 64;
    private static final ThreadLocal<CopyShellPool> COPY_SHELL_POOL = ThreadLocal.withInitial(CopyShellPool::new);

    private static final class CopyShellPool {
        private final ArrayDeque<IItemHandlerImpl> free = new ArrayDeque<>();
        private final List<IItemHandlerImpl> leased = new ArrayList<>();
        private int depth = 0;

        void enter() {
            this.depth++;
        }

        boolean canBorrow() {
            return this.depth > 0;
        }

        IItemHandlerImpl borrow() {
            IItemHandlerImpl shell = this.free.pollFirst();
            if (shell == null) {
                shell = new IItemHandlerImpl();
            }
            this.leased.add(shell);
            return shell;
        }

        void exit() {
            if (this.depth > 0) {
                this.depth--;
            }
            if (this.depth != 0) {
                return;
            }
            for (IItemHandlerImpl shell : this.leased) {
                if (this.free.size() >= COPY_SHELL_POOL_LIMIT) {
                    break;
                }
                this.free.offerLast(shell);
            }
            this.leased.clear();
        }
    }

    @Nonnull
    public static List<ProcessingComponent<?>> fastCopyItemHandlerComponents(final List<ProcessingComponent<?>> components) {
        List<ProcessingComponent<?>> list = new ArrayList<>();
        for (ProcessingComponent<?> component : components) {
            ProcessingComponent<Object> objectProcessingComponent = new ProcessingComponent<>(
                (MachineComponent<Object>) component.component(),
                ((IItemHandlerImpl) component.getProvidedComponent()).fastCopy(),
                component.getTag());
            list.add(objectProcessingComponent);
        }
        return list;
    }
}
