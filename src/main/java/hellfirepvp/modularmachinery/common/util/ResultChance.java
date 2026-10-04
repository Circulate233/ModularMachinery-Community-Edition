/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package hellfirepvp.modularmachinery.common.util;

import java.util.Random;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: ResultChance
 * Created by HellFirePvP
 * Date: 28.06.2017 / 12:58
 */
public class ResultChance {

    public static final ResultChance GUARANTEED = new ResultChance(0) {
        @Override
        public boolean canProduce(float chance) {
            return true;
        }

        public boolean canWork(float canWork) {
            return true;
        }
    };

    // 大多数实例只用于 chance >= 1.0 的快速路径（或 GUARANTEED 单例），Random 懒初始化以避免每次构造的分配。
    private final long seed;
    private Random rand;

    public ResultChance(long seed) {
        this.seed = seed;
    }

    private Random rand() {
        if (this.rand == null) {
            this.rand = new Random(this.seed);
        }
        return this.rand;
    }

    /**
     * <p>Wtf, why canProduce() return the true is not the *consume / produce*?</p>
     * <p>TODO: Remove this.</p>
     *
     * @deprecated Use {@link ResultChance#canWork(float)}
     */
    @Deprecated
    public boolean canProduce(float chance) {
        return chance <= rand().nextFloat();
    }

    public boolean canWork(float chance) {
        if (chance >= 1.0F) {
            return true;
        }
        if (chance <= 0.0F) {
            return false;
        }

        return chance > rand().nextFloat();
    }

}
