package top.lacrus.zephyrzeno.electron.display;

/** Common decoder for real four-bit inputs. -1=unpowered/unknown, 16=minus. */
public final class SevenSegment {
    private static final int[] GLYPHS={0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f,0x77,0x7c,0x39,0x5e,0x79,0x71,0x40};
    public static int mask(int value){return value>=0&&value<GLYPHS.length?GLYPHS[value]:0;}
    public static final double[][] SEGMENTS={{.2,.06,.8,.14},{.8,.15,.9,.45},{.8,.55,.9,.85},{.2,.86,.8,.94},{.1,.55,.2,.85},{.1,.15,.2,.45},{.2,.46,.8,.54}};
    private SevenSegment(){}
}
