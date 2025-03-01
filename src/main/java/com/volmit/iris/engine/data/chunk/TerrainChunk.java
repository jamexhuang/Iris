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

package com.volmit.iris.engine.data.chunk;

import com.volmit.iris.core.nms.BiomeBaseInjector;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.NotNull;

public interface TerrainChunk extends BiomeGrid, ChunkData {
    static TerrainChunk create(World world) {
        return new LinkedTerrainChunk(world);
    }

    static TerrainChunk create(World world, BiomeGrid grid) {
        return new LinkedTerrainChunk(world, grid);
    }

    static TerrainChunk create(ChunkData raw, BiomeGrid grid) {
        return new LinkedTerrainChunk(grid, raw);
    }

    BiomeBaseInjector getBiomeBaseInjector();

    void setRaw(ChunkData data);

    /**
     * Get biome at x, z within chunk being generated
     *
     * @param x - 0-15
     * @param z - 0-15
     * @return Biome value
     * @deprecated biomes are now 3-dimensional
     */
    @NotNull
    @Deprecated
    Biome getBiome(int x, int z);

    /**
     * Get biome at x, z within chunk being generated
     *
     * @param x - 0-15
     * @param y - 0-255
     * @param z - 0-15
     * @return Biome value
     */
    @NotNull
    Biome getBiome(int x, int y, int z);

    /**
     * Set biome at x, z within chunk being generated
     *
     * @param x   - 0-15
     * @param z   - 0-15
     * @param bio - Biome value
     * @deprecated biomes are now 3-dimensional
     */
    @Deprecated
    void setBiome(int x, int z, @NotNull Biome bio);

    /**
     * Set biome at x, z within chunk being generated
     *
     * @param x   - 0-15
     * @param y   - 0-255
     * @param z   - 0-15
     * @param bio - Biome value
     */
    void setBiome(int x, int y, int z, @NotNull Biome bio);

    /**
     * Get the maximum height for the chunk.
     * <p>
     * Setting blocks at or above this height will do nothing.
     *
     * @return the maximum height
     */
    int getMaxHeight();

    /**
     * Set the block at x,y,z in the chunk data to material.
     * <p>
     * Setting blocks outside the chunk's bounds does nothing.
     *
     * @param x         the x location in the chunk from 0-15 inclusive
     * @param y         the y location in the chunk from 0 (inclusive) - maxHeight
     *                  (exclusive)
     * @param z         the z location in the chunk from 0-15 inclusive
     * @param blockData the type to set the block to
     */
    void setBlock(int x, int y, int z, @NotNull BlockData blockData);

    /**
     * Get the type and data of the block at x, y, z.
     * <p>
     * Getting blocks outside the chunk's bounds returns air.
     *
     * @param x the x location in the chunk from 0-15 inclusive
     * @param y the y location in the chunk from 0 (inclusive) - maxHeight
     *          (exclusive)
     * @param z the z location in the chunk from 0-15 inclusive
     * @return the data of the block or the BlockData for air if x, y or z are
     * outside the chunk's bounds
     */
    @NotNull
    BlockData getBlockData(int x, int y, int z);

    ChunkData getRaw();

    void inject(BiomeGrid biome);
}
