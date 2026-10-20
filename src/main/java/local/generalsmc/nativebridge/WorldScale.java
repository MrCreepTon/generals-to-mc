package local.generalsmc.nativebridge;

/** One coordinate scale for models, physics, terrain, ranges and the radar. */
public final class WorldScale {
    private WorldScale() {}
    public static final double UNITS_PER_BLOCK=readScale();
    public static final boolean PAGED_WORLD=Boolean.parseBoolean(System.getProperty("generalsmc.pagedWorld","true"));
    private static double readScale(){double scale=Double.parseDouble(System.getProperty("generalsmc.worldScale","8"));if(!Double.isFinite(scale)||scale<4||scale>32)throw new IllegalArgumentException("World scale must be finite and between 4 and 32");return scale;}
    public static final int PATH_CELL_UNITS=4;
    public static int blocksForCells(int cells){return (int)Math.ceil(cells*PATH_CELL_UNITS/UNITS_PER_BLOCK);}
}
