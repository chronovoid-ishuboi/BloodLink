package com.bloodlink.util;

/** Test-only bridge to the package-private {@link SvgShapeReader}. */
final class SvgShapeReaderTestAccess {
    private SvgShapeReaderTestAccess() { }
    static String read(String resourcePath) { return SvgShapeReader.pathDataFrom(resourcePath); }
}
