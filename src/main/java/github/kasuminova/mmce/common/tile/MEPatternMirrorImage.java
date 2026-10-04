package github.kasuminova.mmce.common.tile;

import github.kasuminova.mmce.common.tile.base.MachineCombinationComponent;
import github.kasuminova.mmce.common.util.InfItemFluidHandler;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import hellfirepvp.modularmachinery.common.tiles.base.SelectiveUpdateTileEntity;
import hellfirepvp.modularmachinery.common.tiles.base.TileColorableMachineComponent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public class MEPatternMirrorImage extends TileColorableMachineComponent implements SelectiveUpdateTileEntity, MachineComponentTile, MachineCombinationComponent {

    public BlockPos providerPos;
    public InfItemFluidHandler handler;

    public MEPatternMirrorImage() {
        handler = new InfItemFluidHandler();
    }

    @Nullable
    @Override
    public MachineComponent<InfItemFluidHandler> provideComponent() {
        if (!this.world.isRemote && providerPos != null) {
            MEPatternProvider tileEntity = MEPatternProvider.coordinate.getOrDefault(this.world.provider.getDimension(),Collections.emptyMap()).get(providerPos);
            if (tileEntity != null && !tileEntity.isInvalid()) {
                return tileEntity.provideComponent();
            }
        }
        return null;
    }

    @NotNull
    @Override
    public Collection<MachineComponent<?>> provideComponents() {
        if (!this.world.isRemote && providerPos != null) {
            MEPatternProvider tileEntity = MEPatternProvider.coordinate.getOrDefault(this.world.provider.getDimension(),Collections.emptyMap()).get(providerPos);
            if (tileEntity != null && !tileEntity.isInvalid()) {
                return tileEntity.provideComponents();
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);
        if (compound.hasKey("providerPos")) {
            this.providerPos = BlockPos.fromLong(compound.getLong("providerPos"));
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);
        if (providerPos != null) {
            compound.setLong("providerPos", providerPos.toLong());
        }
    }

}
