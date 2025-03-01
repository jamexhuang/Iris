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
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.util.data.IrisBiomeStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public class LinkedTerrainChunk implements TerrainChunk {
    private final IrisBiomeStorage biome3D;
    private ChunkData rawChunkData;
    private final BiomeGrid storage;

    public LinkedTerrainChunk(World world) {
        this(null, Bukkit.createChunkData(world));
    }

    public LinkedTerrainChunk(World world, BiomeGrid storage) {
        this(storage, Bukkit.createChunkData(world));
    }

    public LinkedTerrainChunk(BiomeGrid storage, ChunkData data) {
        this.storage = storage;
        rawChunkData = data;
        biome3D = storage != null ? null : new IrisBiomeStorage();
    }

    @Override
    public BiomeBaseInjector getBiomeBaseInjector() {
        return (x, y, z, bb) -> INMS.get().forceBiomeInto(x, y, z, bb, storage);
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int z) {
        if (storage != null) {
            return storage.getBiome(x, z);
        }

        return biome3D.getBiome(x, 0, z);
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int y, int z) {
        if (storage != null) {
            return storage.getBiome(x, y, z);
        }

        return biome3D.getBiome(x, y, z);
    }

    @Override
    public void setBiome(int x, int z, Biome bio) {
        if (storage != null) {
            storage.setBiome(x, z, bio);
            return;
        }

        biome3D.setBiome(x, 0, z, bio);
    }

    public BiomeGrid getRawBiome() {
        return storage;
    }

    @Override
    public void setBiome(int x, int y, int z, Biome bio) {
        if (storage != null) {
            storage.setBiome(x, y, z, bio);
            return;
        }

        biome3D.setBiome(x, y, z, bio);
    }

    @Override
    public int getMinHeight() {
        return rawChunkData.getMinHeight();
    }

    @Override
    public int getMaxHeight() {
        return rawChunkData.getMaxHeight();
    }

    @Override
    public void setBlock(int x, int y, int z, BlockData blockData) {
        rawChunkData.setBlock(x, y, z, blockData);
    }

    @NotNull
    @Override
    public BlockData getBlockData(int x, int y, int z) {
        return rawChunkData.getBlockData(x, y, z);
    }

    @Deprecated
    @Override
    public void setBlock(int x, int y, int z, @NotNull Material material) {
        rawChunkData.setBlock(x, y, z, material);
    }

    @Deprecated
    @Override
    public void setBlock(int x, int y, int z, @NotNull MaterialData material) {
        rawChunkData.setBlock(x, y, z, material);
    }

    @Deprecated
    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull Material material) {
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material);
    }

    @Deprecated
    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull MaterialData material) {
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material);
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @NotNull BlockData blockData) {
        rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, blockData);
    }

    @NotNull
    @Deprecated
    @Override
    public Material getType(int x, int y, int z) {
        return rawChunkData.getType(x, y, z);
    }

    @NotNull
    @Deprecated
    @Override
    public MaterialData getTypeAndData(int x, int y, int z) {
        return rawChunkData.getTypeAndData(x, y, z);
    }

    @Deprecated
    @Override
    public byte getData(int x, int y, int z) {
        return rawChunkData.getData(x, y, z);
    }

    @Override
    public ChunkData getRaw() {
        return rawChunkData;
    }

    @Override
    public void setRaw(ChunkData data) {
        rawChunkData = data;
    }

    @Override
    public void inject(BiomeGrid biome) {
        if (biome3D != null) {
            biome3D.inject(biome);
        }
    }
}
