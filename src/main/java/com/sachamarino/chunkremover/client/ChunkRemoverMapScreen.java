package com.sachamarino.chunkremover.client;

/**
 * Implemented by the FTB Chunks large map screen through a mixin: the selection state and the actions
 * triggered by the "delete chunks" button.
 */
public interface ChunkRemoverMapScreen {
    MapSelection chunkremover$getSelection();

    /**
     * Toggles selection mode, or asks for confirmation when an area has been selected.
     */
    void chunkremover$onDeleteButtonClicked();

    /**
     * Called when the server reported the result of a deletion: leaves selection mode and rebuilds the map tiles.
     */
    void chunkremover$onDeletionFinished();
}
