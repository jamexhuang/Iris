/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.actuator;

import com.volmit.iris.engine.decorator.*;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedActuator;
import com.volmit.iris.engine.framework.EngineDecorator;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.function.Predicate;

public class IrisDecorantActuator extends EngineAssignedActuator<BlockData> {
    private static final Predicate<BlockData> PREDICATE_SOLID = (b) -> b != null && !b.getMaterial().isAir() && !b.getMaterial().equals(Material.WATER) && !b.getMaterial().equals(Material.LAVA);
    private final RNG rng;
    @Getter
    private final EngineDecorator surfaceDecorator;
    @Getter
    private final EngineDecorator ceilingDecorator;
    @Getter
    private final EngineDecorator seaSurfaceDecorator;
    @Getter
    private final EngineDecorator seaFloorDecorator;
    @Getter
    private final EngineDecorator shoreLineDecorator;
    private final boolean shouldRay;

    public IrisDecorantActuator(Engine engine) {
        super(engine, "Decorant");
        shouldRay = shouldRayDecorate();
        this.rng = new RNG(engine.getTarget().getWorld().seed());
        surfaceDecorator = new IrisSurfaceDecorator(getEngine());
        ceilingDecorator = new IrisCeilingDecorator(getEngine());
        seaSurfaceDecorator = new IrisSeaSurfaceDecorator(getEngine());
        shoreLineDecorator = new IrisShoreLineDecorator(getEngine());
        seaFloorDecorator = new IrisSeaFloorDecorator(getEngine());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<BlockData> output) {
        if (!getEngine().getDimension().isDecorate()) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();

        int j, realX, realZ, height;
        IrisBiome biome, cave;

        for (int i = 0; i < output.getWidth(); i++) {
            for (j = 0; j < output.getDepth(); j++) {
                boolean solid;
                int emptyFor = 0;
                int lastSolid = 0;
                realX = (int) Math.round(modX(x + i));
                realZ = (int) Math.round(modZ(z + j));
                height = (int) Math.round(getComplex().getHeightStream().get(realX, realZ));
                biome = getComplex().getTrueBiomeStream().get(realX, realZ);
                cave = shouldRay ? getComplex().getCaveBiomeStream().get(realX, realZ) : null;

                if (biome.getDecorators().isEmpty() && (cave == null || cave.getDecorators().isEmpty())) {
                    continue;
                }

                if (height == getDimension().getFluidHeight()) {
                    getShoreLineDecorator().decorate(i, j,
                            realX, (int) Math.round(modX(x + i + 1)), (int) Math.round(modX(x + i - 1)),
                            realZ, (int) Math.round(modZ(z + j + 1)), (int) Math.round(modZ(z + j - 1)),
                            output, biome, height, getEngine().getHeight() - height);
                } else if (height == getDimension().getFluidHeight() + 1) {
                    getSeaSurfaceDecorator().decorate(i, j,
                            realX, (int) Math.round(modX(x + i + 1)), (int) Math.round(modX(x + i - 1)),
                            realZ, (int) Math.round(modZ(z + j + 1)), (int) Math.round(modZ(z + j - 1)),
                            output, biome, height, getEngine().getHeight() - getDimension().getFluidHeight());
                } else if (height < getDimension().getFluidHeight()) {
                    getSeaFloorDecorator().decorate(i, j, realX, realZ, output, biome, height + 1, getDimension().getFluidHeight());
                }

                getSurfaceDecorator().decorate(i, j, realX, realZ, output, biome, height, getEngine().getHeight() - height);

                if (cave != null && cave.getDecorators().isNotEmpty()) {
                    for (int k = height; k > 0; k--) {
                        solid = PREDICATE_SOLID.test(output.get(i, k, j));

                        if (solid) {
                            if (emptyFor > 0) {
                                getSurfaceDecorator().decorate(i, j, realX, realZ, output, cave, k, emptyFor);
                                getCeilingDecorator().decorate(i, j, realX, realZ, output, cave, lastSolid - 1, emptyFor);
                                emptyFor = 0;
                            }
                            lastSolid = k;
                        } else {
                            emptyFor++;
                        }
                    }
                }
            }
        }

        getEngine().getMetrics().getDecoration().put(p.getMilliseconds());
    }

    private boolean shouldRayDecorate() {
        return getEngine().getDimension().isCarving() || getEngine().getDimension().isCaves() || getEngine().getDimension().isRavines();
    }
}
