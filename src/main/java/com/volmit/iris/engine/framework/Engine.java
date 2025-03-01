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

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.gui.components.Renderer;
import com.volmit.iris.engine.cache.Cache;
import com.volmit.iris.engine.data.B;
import com.volmit.iris.engine.data.DataProvider;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.parallax.ParallaxAccess;
import com.volmit.iris.engine.parallel.MultiBurst;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

public interface Engine extends DataProvider, Fallible, GeneratorAccess, LootProvider, BlockUpdater, Renderer, Hotloadable {
    void close();

    double getMaxBiomeObjectDensity();

    double getMaxBiomeDecoratorDensity();

    double getMaxBiomeLayerDensity();

    boolean isClosed();

    EngineWorldManager getWorldManager();

    void setParallelism(int parallelism);

    int getParallelism();

    EngineTarget getTarget();

    EngineFramework getFramework();

    void setMinHeight(int min);

    void recycle();

    int getIndex();

    int getMinHeight();

    double modifyX(double x);

    double modifyZ(double z);

    void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes);

    EngineMetrics getMetrics();

    default void save() {
        getParallax().saveAll();
    }

    default void saveNow() {
        getParallax().saveAllNOW();
    }

    default String getName() {
        return getDimension().getName();
    }

    default int getHeight() {
        return getTarget().getHeight();
    }

    default IrisDataManager getData() {
        return getTarget().getData();
    }

    default IrisWorld getWorld() {
        return getTarget().getWorld();
    }

    default IrisDimension getDimension() {
        return getTarget().getDimension();
    }

    default ParallaxAccess getParallax() {
        return getTarget().getParallaxWorld();
    }

    default Color draw(double x, double z) {
        IrisRegion region = getRegion((int) x, (int) z);
        IrisBiome biome = getSurfaceBiome((int) x, (int) z);
        int height = getHeight((int) x, (int) z);
        double heightFactor = M.lerpInverse(0, getHeight(), height);
        Color irc = region.getColor(this.getFramework().getComplex(), RenderType.BIOME);
        Color ibc = biome.getColor(this, RenderType.BIOME);
        Color rc = irc != null ? irc : Color.GREEN.darker();
        Color bc = ibc != null ? ibc : biome.isAquatic() ? Color.BLUE : Color.YELLOW;
        Color f = IrisColor.blend(rc, bc, bc, Color.getHSBColor(0, 0, (float) heightFactor));

        return IrisColor.blend(rc, bc, bc, Color.getHSBColor(0, 0, (float) heightFactor));
    }

    @Override
    default IrisRegion getRegion(int x, int z) {
        return getFramework().getComplex().getRegionStream().get(x, z);
    }

    @Override
    default ParallaxAccess getParallaxAccess() {
        return getParallax();
    }

    @Override
    default IrisBiome getCaveBiome(int x, int z) {
        return getFramework().getComplex().getCaveBiomeStream().get(x, z);
    }

    @Override
    default IrisBiome getSurfaceBiome(int x, int z) {
        return getFramework().getComplex().getTrueBiomeStream().get(x, z);
    }

    @Override
    default int getHeight(int x, int z) {
        return getFramework().getEngineParallax().getHighest(x, z, true);
    }

    @Override
    default void catchBlockUpdates(int x, int y, int z, BlockData data) {
        if (data == null) {
            return;
        }

        if (B.isUpdatable(data)) {
            getParallax().updateBlock(x, y, z);
            getParallax().getMetaRW(x >> 4, z >> 4).setUpdates(true);
        }
    }

    default void placeTiles(Chunk c) {

    }

    @Override
    default void updateChunk(Chunk c) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (getParallax().getMetaR(c.getX(), c.getZ()).isUpdates()) {
            Hunk<Boolean> b = getParallax().getUpdatesR(c.getX(), c.getZ());

            b.iterateSync((x, y, z, v) -> {

                if (v != null && v) {
                    int vx = x & 15;
                    int vz = z & 15;
                    update(x, y, z, c, new RNG(Cache.key(c.getX(), c.getZ())));

                    if (vx > 0 && vx < 15 && vz > 0 && vz < 15) {
                        updateLighting(x, y, z, c);
                    }
                }
            });
        }

        getMetrics().getUpdates().put(p.getMilliseconds());
    }

    default void updateLighting(int x, int y, int z, Chunk c) {
        Block block = c.getBlock(x, y, z);
        BlockData data = block.getBlockData();

        if (B.isLit(data)) {
            try {
                block.setType(Material.AIR, false);
                block.setBlockData(data, true);
            } catch (Exception e) {
                Iris.reportError(e);
                // Issue when adding block data. Suppress massive warnings and stack-traces to console.
            }
        }
    }

    @Override
    default void update(int x, int y, int z, Chunk c, RNG rf) {
        Block block = c.getBlock(x, y, z);
        BlockData data = block.getBlockData();

        if (B.isStorage(data)) {
            RNG rx = rf.nextParallelRNG(BlockPosition.toLong(x, y, z));
            InventorySlotType slot = null;

            if (B.isStorageChest(data)) {
                slot = InventorySlotType.STORAGE;
            }

            if (slot != null) {
                KList<IrisLootTable> tables = getLootTables(rx, block);

                try {
                    InventoryHolder m = (InventoryHolder) block.getState();
                    addItems(false, m.getInventory(), rx, tables, slot, x, y, z, 15);
                } catch (Throwable e) {
                    Iris.reportError(e);
                }
            }
        }
    }

    @Override
    default void scramble(Inventory inventory, RNG rng) {
        org.bukkit.inventory.ItemStack[] items = inventory.getContents();
        org.bukkit.inventory.ItemStack[] nitems = new org.bukkit.inventory.ItemStack[inventory.getSize()];
        System.arraycopy(items, 0, nitems, 0, items.length);
        boolean packedFull = false;

        splitting:
        for (int i = 0; i < nitems.length; i++) {
            ItemStack is = nitems[i];

            if (is != null && is.getAmount() > 1 && !packedFull) {
                for (int j = 0; j < nitems.length; j++) {
                    if (nitems[j] == null) {
                        int take = rng.nextInt(is.getAmount());
                        take = take == 0 ? 1 : take;
                        is.setAmount(is.getAmount() - take);
                        nitems[j] = is.clone();
                        nitems[j].setAmount(take);
                        continue splitting;
                    }
                }

                packedFull = true;
            }
        }

        for (int i = 0; i < 4; i++) {
            try {
                Arrays.parallelSort(nitems, (a, b) -> rng.nextInt());
                break;
            } catch (Throwable e) {
                Iris.reportError(e);

            }
        }

        inventory.setContents(nitems);
    }

    @Override
    default void injectTables(KList<IrisLootTable> list, IrisLootReference r) {
        if (r.getMode().equals(LootMode.CLEAR) || r.getMode().equals(LootMode.REPLACE)) {
            list.clear();
        }

        list.addAll(r.getLootTables(getFramework().getComplex()));
    }

    @Override
    default KList<IrisLootTable> getLootTables(RNG rng, Block b) {
        int rx = b.getX();
        int rz = b.getZ();
        double he = getFramework().getComplex().getHeightStream().get(rx, rz);
        PlacedObject po = getFramework().getEngine().getObjectPlacement(rx, b.getY(), rz);
        if (po != null && po.getPlacement() != null) {

            if (B.isStorageChest(b.getBlockData())) {
                IrisLootTable table = po.getPlacement().getTable(b.getBlockData(), getData());
                if (table != null) {
                    return new KList<>(table);
                }
            }
        }
        IrisRegion region = getFramework().getComplex().getRegionStream().get(rx, rz);
        IrisBiome biomeSurface = getFramework().getComplex().getTrueBiomeStream().get(rx, rz);
        IrisBiome biomeUnder = b.getY() < he ? getFramework().getComplex().getCaveBiomeStream().get(rx, rz) : biomeSurface;
        KList<IrisLootTable> tables = new KList<>();
        double multiplier = 1D * getDimension().getLoot().getMultiplier() * region.getLoot().getMultiplier() * biomeSurface.getLoot().getMultiplier() * biomeUnder.getLoot().getMultiplier();
        injectTables(tables, getDimension().getLoot());
        injectTables(tables, region.getLoot());
        injectTables(tables, biomeSurface.getLoot());
        injectTables(tables, biomeUnder.getLoot());

        if (tables.isNotEmpty()) {
            int target = (int) Math.round(tables.size() * multiplier);

            while (tables.size() < target && tables.isNotEmpty()) {
                tables.add(tables.get(rng.i(tables.size() - 1)));
            }

            while (tables.size() > target && tables.isNotEmpty()) {
                tables.remove(rng.i(tables.size() - 1));
            }
        }

        return tables;
    }

    @Override
    default void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, int x, int y, int z, int mgf) {
        KList<ItemStack> items = new KList<>();

        int b = 4;
        for (IrisLootTable i : tables) {
            b++;
            items.addAll(i.getLoot(debug, items.isEmpty(), rng, slot, x, y, z, b + b, mgf + b));
        }

        for (ItemStack i : items) {
            inv.addItem(i);
        }

        scramble(inv, rng);
    }

    default int getMaxHeight() {
        return getHeight() + getMinHeight();
    }

    EngineEffects getEffects();

    EngineCompound getCompound();

    default boolean isStudio() {
        return getCompound().isStudio();
    }

    default MultiBurst burst() {
        return getTarget().getBurster();
    }

    default void clean() {
        burst().lazy(() -> getParallax().cleanup());
    }

    default IrisBiome getBiome(Location l) {
        return getBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    default IrisRegion getRegion(Location l) {
        return getRegion(l.getBlockX(), l.getBlockZ());
    }

    default boolean contains(Location l) {
        return l.getBlockY() >= getMinHeight() && l.getBlockY() <= getMaxHeight();
    }

    IrisBiome getFocus();

    void hotloading();
}
