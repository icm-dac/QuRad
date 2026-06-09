package qupath.ext.qurad

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class RadiomicsCalculatorTest {

    private static final double EPS = 1e-9

    private final RadiomicsCalculator calc = new RadiomicsCalculator()
    private final double[] intensities = [0d, 1d, 2d, 3d, 4d] as double[]
    private final Map settings = [binWidth: 25, voxelArrayShift: 0]

    @Test
    void binEdgesAlignToMultiplesOfBinWidth() {
        def edges = calc.calculateBinEdges(intensities, 25)
        assertEquals([0, 25, 50], edges)
    }

    @Test
    void binEdgesHandleFlatRegion() {
        def edges = calc.calculateBinEdges([7d, 7d, 7d] as double[], 25)
        assertEquals([0, 25, 50], edges)
        assertEquals(calc.quantizeValue(7d, edges), calc.quantizeValue(7d, edges))
        assertTrue(calc.quantizeValue(7d, edges) >= 1)
    }

    @Test
    void firstOrderProduces19Features() {
        def f = calc.calculateFirstOrderFeatures(intensities, settings, calc.calculateBinEdges(intensities, 25))
        assertEquals(19, f.size())
    }

    @Test
    void firstOrderValuesMatchHandComputed() {
        def binEdges = calc.calculateBinEdges(intensities, 25)
        def f = calc.calculateFirstOrderFeatures(intensities, settings, binEdges)

        assertEquals(30.0d, f['Energy'] as double, EPS)
        assertEquals(2.0d, f['Mean'] as double, EPS)
        assertEquals(0.0d, f['Minimum'] as double, EPS)
        assertEquals(4.0d, f['Maximum'] as double, EPS)
        assertEquals(4.0d, f['Range'] as double, EPS)
        assertEquals(2.0d, f['Median'] as double, EPS)
        assertEquals(2.0d, f['Variance'] as double, EPS)
        assertEquals(Math.sqrt(2.0d), f['StandardDeviation'] as double, EPS)
        assertEquals(Math.sqrt(6.0d), f['RootMeanSquared'] as double, EPS)
        assertEquals(1.2d, f['MeanAbsoluteDeviation'] as double, EPS)
        assertEquals(2.0d, f['InterquartileRange'] as double, EPS)
    }
}
