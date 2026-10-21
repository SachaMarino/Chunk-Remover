package com.sachamarino.chunkremover.client;

import com.sachamarino.chunkremover.common.ChunkArea;

import java.util.ArrayList;
import java.util.List;

/**
 * Selection state of the "delete chunks" tool on the large map: a list of rectangles (Shift adds one) plus the
 * rectangle currently being dragged. Coordinates are chunk coordinates.
 */
public final class MapSelection {
    private boolean active;
    private boolean dragging;
    private final List<ChunkArea> areas = new ArrayList<>();
    private int startX, startZ, endX, endZ;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            clearAreas();
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean hasArea() {
        return !areas.isEmpty();
    }

    /**
     * @param additive keep the rectangles already selected (Shift held)
     */
    public void beginDrag(int chunkX, int chunkZ, boolean additive) {
        if (!additive) {
            areas.clear();
        }
        dragging = true;
        startX = endX = chunkX;
        startZ = endZ = chunkZ;
    }

    public void updateDrag(int chunkX, int chunkZ) {
        endX = chunkX;
        endZ = chunkZ;
    }

    public void endDrag(int chunkX, int chunkZ) {
        updateDrag(chunkX, chunkZ);
        dragging = false;
        areas.add(currentDrag());
    }

    public void clearAreas() {
        dragging = false;
        areas.clear();
    }

    public void reset() {
        active = false;
        clearAreas();
    }

    private ChunkArea currentDrag() {
        return ChunkArea.ofChunks(startX, startZ, endX, endZ);
    }

    /**
     * The confirmed rectangles, to send to the server.
     */
    public List<ChunkArea> areas() {
        return List.copyOf(areas);
    }

    /**
     * The rectangles to draw: confirmed ones plus the one being dragged.
     */
    public List<ChunkArea> visibleAreas() {
        List<ChunkArea> visible = new ArrayList<>(areas);
        if (dragging) {
            visible.add(currentDrag());
        }
        return visible;
    }

    /**
     * Number of distinct chunks in the visible selection.
     */
    public long chunkCount() {
        return ChunkArea.unionCount(visibleAreas());
    }
}
