package de.jeff_media.BestTools;

import org.bukkit.Material;

public class BestToolsCache {

    // This is quite useful right now

    boolean valid = false;

    Material lastMat = null;

    public void invalidated() {
        lastMat=null;
        valid=false;
    }

    void validate(Material material) {
        lastMat = material;
        valid=true;
    }
}
