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

package com.volmit.iris.engine.jigsaw;

import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.AxisAlignedBB;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.math.RNG;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.BlockVector;

@SuppressWarnings("ALL")
@Data
public class PlannedPiece {
    private IrisPosition position;
    private IrisObject object;
    private IrisJigsawPiece piece;
    private IrisObjectRotation rotation;
    private IrisDataManager data;
    private KList<IrisJigsawPieceConnector> connected;
    private boolean dead = false;
    private int rotationKey;
    private AxisAlignedBB box;
    private PlannedStructure structure;

    public PlannedPiece(PlannedStructure structure, IrisPosition position, IrisJigsawPiece piece) {
        this(structure, position, piece, 0, 0, 0);
    }

    public PlannedPiece(PlannedStructure structure, IrisPosition position, IrisJigsawPiece piece, int rx, int ry, int rz) {
        this.structure = structure;
        this.position = position;
        rotationKey = (rz * 100) + (rx * 10) + ry;
        this.data = piece.getLoader();
        this.rotation = IrisObjectRotation.of(rx * 90D, ry * 90D, rz * 90D);
        this.object = structure.rotated(piece, rotation);
        this.piece = rotation.rotateCopy(piece);
        this.piece.setLoadKey(piece.getLoadKey());
        this.object.setLoadKey(piece.getObject());
        this.connected = new KList<>();
    }

    public void setPosition(IrisPosition p) {
        this.position = p;
        box = null;
    }

    public String toString() {
        return piece.getLoadKey() + "@(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")[rot:" + rotationKey + "]";
    }

    public AxisAlignedBB getBox() {
        if (box != null) {
            return box;
        }

        BlockVector v = getObject().getCenter();
        box = object.getAABB().shifted(position.add(new IrisPosition(object.getCenter())));
        return box;
    }

    public boolean contains(IrisPosition p) {
        return getBox().contains(p);
    }

    public boolean collidesWith(PlannedPiece p) {
        return getBox().intersects(p.getBox());
    }

    public KList<IrisJigsawPieceConnector> getAvailableConnectors() {
        if (connected.isEmpty()) {
            return piece.getConnectors().copy();
        }

        if (connected.size() == piece.getConnectors().size()) {
            return new KList<>();
        }

        KList<IrisJigsawPieceConnector> c = new KList<>();

        for (IrisJigsawPieceConnector i : piece.getConnectors()) {
            if (!connected.contains(i)) {
                c.add(i);
            }
        }

        return c;
    }

    public boolean connect(IrisJigsawPieceConnector c) {
        if (piece.getConnectors().contains(c)) {
            return connected.addIfMissing(c);
        }

        return false;
    }

    public IrisPosition getWorldPosition(IrisJigsawPieceConnector c) {
        return getWorldPosition(c.getPosition());
    }

    public IrisPosition getWorldPosition(IrisPosition position) {
        return this.position.add(position).add(new IrisPosition(object.getCenter()));
    }

    public boolean isFull() {
        return connected.size() >= piece.getConnectors().size() || isDead();
    }

    public void place(World world) {
        IrisAccess a = IrisWorlds.access(world);

        int minY = 0;
        if (a != null) {
            minY = a.getCompound().getDefaultEngine().getMinHeight();

            if (!a.getCompound().getRootDimension().isBedrock())
                minY--; //If the dimension has no bedrock, allow it to go a block lower
        }

        getPiece().getPlacementOptions().getRotation().setEnabled(false);
        int finalMinY = minY;
        RNG rng = getStructure().getRng().nextParallelRNG(37555);
        getObject().place(position.getX() + getObject().getCenter().getBlockX(), position.getY() + getObject().getCenter().getBlockY(), position.getZ() + getObject().getCenter().getBlockZ(), new IObjectPlacer() {
            @Override
            public int getHighest(int x, int z) {
                return position.getY();
            }

            @Override
            public int getHighest(int x, int z, boolean ignoreFluid) {
                return position.getY();
            }

            @Override
            public void set(int x, int y, int z, BlockData d) {
                Block block = world.getBlockAt(x, y, z);

                //Prevent blocks being set in or bellow bedrock
                if (y <= finalMinY || block.getType() == Material.BEDROCK) return;

                block.setBlockData(d);

                if (a != null && getPiece().getPlacementOptions().getLoot().isNotEmpty() &&
                        block.getState() instanceof InventoryHolder) {

                    IrisLootTable table = getPiece().getPlacementOptions().getTable(block.getBlockData(), getData());
                    if (table == null) return;
                    Engine engine = a.getCompound().getEngineForHeight(y);
                    engine.addItems(false, ((InventoryHolder) block.getState()).getInventory(),
                            rng.nextParallelRNG(BlockPosition.toLong(x, y, z)),
                            new KList<>(table), InventorySlotType.STORAGE, x, y, z, 15);
                }
            }

            @Override
            public BlockData get(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getBlockData();
            }

            @Override
            public boolean isPreventingDecay() {
                return false;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().isSolid();
            }

            @Override
            public boolean isUnderwater(int x, int z) {
                return false;
            }

            @Override
            public int getFluidHeight() {
                return 0;
            }

            @Override
            public boolean isDebugSmartBore() {
                return false;
            }

            @Override
            public void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile) {
                BlockState state = world.getBlockAt(xx, yy, zz).getState();
                tile.toBukkitTry(state);
                state.update();
            }
        }, piece.getPlacementOptions(), rng, getData());
    }
}
