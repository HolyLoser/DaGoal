package com.stipasay.dagoal;

public class FeatureLayer {
    public String assetId;
    public float normX;      // Normalized X offset relative to 1080 canvas (-1f .. 1f)
    public float normY;      // Normalized Y offset relative to 1080 canvas (-1f .. 1f)
    public float scale;      // Size scale factor (default 1.0f)
    public int zIndex;       // Layer order
    public int tintColor;    // Color tint int (0 if no tinting)
    public boolean isTinted;

    public FeatureLayer(String assetId, float normX, float normY, float scale, int zIndex, int tintColor, boolean isTinted) {
        this.assetId = assetId;
        this.normX = normX;
        this.normY = normY;
        this.scale = scale;
        this.zIndex = zIndex;
        this.tintColor = tintColor;
        this.isTinted = isTinted;
    }

    public FeatureLayer(String assetId, float normX, float normY, float scale, int zIndex) {
        this(assetId, normX, normY, scale, zIndex, 0, false);
    }
}
