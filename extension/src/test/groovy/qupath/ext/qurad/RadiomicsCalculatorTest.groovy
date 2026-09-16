package qupath.ext.qurad

import org.junit.jupiter.api.Test
import qupath.lib.regions.ImagePlane
import qupath.lib.regions.RegionRequest
import qupath.lib.roi.ROIs

import java.awt.image.BufferedImage

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class RadiomicsCalculatorTest {

    @Test
    void csvRetainsFeaturesAbsentFromFirstObject() {
        File out = File.createTempFile('qurad-csv-', '.csv')
        try {
            calc.writeCsv([[ObjectID: 'tiny', firstorder_Mean: 5d],
                           [ObjectID: 'normal', firstorder_Mean: 8d, glcm_Contrast: 2d]], out)
            def rows = out.readLines().collect { it.split(',', -1) }
            int col = rows[0].toList().indexOf('glcm_Contrast')
            assertTrue(col >= 0)
            assertEquals('', rows[1][col])
            assertEquals('2.0', rows[2][col])
            assertEquals(rows[0].length, rows[1].length)
        } finally {
            out.delete()
        }
    }

    private static final double EPS = 1e-9

    private final RadiomicsCalculator calc = new RadiomicsCalculator()
    private final double[] intensities = [0d, 1d, 2d, 3d, 4d] as double[]
    private final Map settings = [binWidth: 25, voxelArrayShift: 0, distances: [1]]

    private static int[][] constantImage(int h, int w, int value) {
        int[][] img = new int[h][w]
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img[y][x] = value
        return img
    }

    private static boolean[][] fullMask(int h, int w) {
        boolean[][] m = new boolean[h][w]
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) m[y][x] = true
        return m
    }

    private static double[] flatten(int[][] img) {
        def out = []
        img.each { row -> row.each { out << (double) it } }
        return out as double[]
    }

    @Test
    void binEdgesAlignToMultiplesOfBinWidth() {
        def edges = calc.calculateBinEdges(intensities, 25)
        assertEquals([0, 25, 50], edges)
    }

    @Test
    void quantizationMatchesHalfOpenBins() {
        def edges = calc.calculateBinEdges([0d, 100d] as double[], 25)
        assertEquals([0, 25, 50, 75, 100, 125, 150], edges)
        assertEquals(1, calc.quantizeValue(0d, edges))
        assertEquals(1, calc.quantizeValue(24.999d, edges))
        assertEquals(2, calc.quantizeValue(25d, edges))
        assertEquals(5, calc.quantizeValue(100d, edges))
        assertEquals(5, calc.quantizeValue(124d, edges))
    }

    @Test
    void binEdgesHandleFlatRegion() {
        def edges = calc.calculateBinEdges([7d, 7d, 7d] as double[], 25)
        assertEquals([0, 25, 50], edges)
        assertTrue(calc.quantizeValue(7d, edges) >= 1)
    }

    @Test
    void percentilesInterpolateLinearlyLikeNumpy() {
        double[] sorted = [0d, 1d, 2d, 3d, 4d] as double[]
        assertEquals(0.4d, calc.percentile(sorted, 10), EPS)
        assertEquals(2.0d, calc.percentile(sorted, 50), EPS)
        assertEquals(3.0d, calc.percentile(sorted, 75), EPS)
        assertEquals(1.5d, calc.percentile([0d, 1d, 2d, 3d] as double[], 50), EPS)
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
        assertEquals(0.4d, f['10Percentile'] as double, EPS)
        assertEquals(3.6d, f['90Percentile'] as double, EPS)
    }

    @Test
    void uniformRegionGivesAnalyticValues() {
        int[][] img = constantImage(10, 10, 100)
        boolean[][] mask = fullMask(10, 10)
        double[] vals = flatten(img)
        def edges = calc.calculateBinEdges(vals, 25)

        def fo = calc.calculateFirstOrderFeatures(vals, settings, edges)
        assertEquals(0.0d, fo['Entropy'] as double, EPS)
        assertEquals(1.0d, fo['Uniformity'] as double, EPS)
        assertEquals(0.0d, fo['Skewness'] as double, EPS)
        assertEquals(0.0d, fo['Kurtosis'] as double, EPS)

        def glcm = calc.calculateGLCMFeatures(img, mask, settings, edges)
        assertEquals(23, glcm.size())
        assertEquals(1.0d, glcm['JointEnergy'] as double, EPS)
        assertEquals(0.0d, glcm['Contrast'] as double, EPS)
        assertEquals(0.0d, glcm['JointEntropy'] as double, EPS)
        assertEquals(1.0d, glcm['Correlation'] as double, EPS)
        assertEquals(0.0d, glcm['Imc2'] as double, EPS)

        def glrlm = calc.calculateGLRLMFeatures(img, mask, settings, edges)
        assertEquals(58.0d / 400.0d, glrlm['RunPercentage'] as double, EPS)
        assertEquals(3340.0d / 58.0d, glrlm['LongRunEmphasis'] as double, EPS)

        def glszm = calc.calculateGLSZMFeatures(img, mask, settings, edges)
        assertEquals(0.01d, glszm['ZonePercentage'] as double, EPS)
        assertEquals(10000.0d, glszm['LargeAreaEmphasis'] as double, EPS)

        def ngtdm = calc.calculateNGTDMFeatures(img, mask, settings, edges)
        assertEquals(1.0e6d, ngtdm['Coarseness'] as double, EPS)
        assertEquals(0.0d, ngtdm['Contrast'] as double, EPS)

        def gldm = calc.calculateGLDMFeatures(img, mask, settings, edges)
        assertEquals(14, gldm.size())
        assertEquals(0.0d, gldm['GrayLevelVariance'] as double, EPS)
    }

    @Test
    void checkerboardGlcmMatchesHandComputed() {
        int[][] img = new int[4][4]
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img[y][x] = ((x + y) % 2 == 0) ? 0 : 100
        boolean[][] mask = fullMask(4, 4)
        def edges = calc.calculateBinEdges(flatten(img), 25)
        def glcm = calc.calculateGLCMFeatures(img, mask, settings, edges)
        assertEquals(48.0d / 84.0d * 16.0d, glcm['Contrast'] as double, EPS)
        assertEquals(2.0d * Math.pow(24.0d / 84.0d, 2) + 2.0d * Math.pow(18.0d / 84.0d, 2), glcm['JointEnergy'] as double, EPS)
        assertEquals(36.0d / 84.0d + 48.0d / 84.0d / 17.0d, glcm['Idm'] as double, EPS)
    }

    @Test
    void glrlmUsesFourDirections() {
        int[][] img = constantImage(3, 3, 50)
        long[][] glrlm = calc.buildGLRLM(img, fullMask(3, 3), calc.calculateBinEdges(flatten(img), 25))
        long runs = 0
        glrlm.each { row -> row.each { runs += it } }
        assertEquals(16L, runs)
        assertEquals(8L, glrlm[1][3])
        assertEquals(4L, glrlm[1][1])
    }

    @Test
    void ngtdmCountsIsolatedPixelsWithZeroDifference() {
        int[][] img = new int[8][8]
        boolean[][] mask = new boolean[8][8]
        int[][] block = [[100, 130, 100], [130, 100, 130], [100, 130, 100]] as int[][]
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) { img[y + 1][x + 1] = block[y][x]; mask[y + 1][x + 1] = true }
        img[6][6] = 200; mask[6][6] = true
        def edges = calc.calculateBinEdges(flatten(img).findAll { it > 0 } as double[], 25)
        def f = calc.calculateNGTDMFeatures(img, mask, settings, edges)
        assertEquals(0.3931847968545215d, f['Coarseness'] as double, 1e-12)
        assertEquals(2.1194444444444445d, f['Busyness'] as double, 1e-12)
        assertEquals(0.2523555555555556d, f['Contrast'] as double, 1e-8)
    }

    @Test
    void floodFillUsesEightConnectivity() {
        int[][] image = [[5, 0], [0, 5]] as int[][]
        boolean[][] visited = new boolean[2][2]
        int size = calc.floodFill(image, visited, 0, 0, 5)
        assertEquals(2, size)
    }

    @Test
    void axisLengthsUsePopulationCovariance() {
        boolean[][] line = [[true, true, true, true]] as boolean[][]
        def axes = calc.computeAxisLengths(line)
        assertEquals(4.0d * Math.sqrt(1.25d), axes[0] as double, EPS)
        assertEquals(0.0d, axes[1] as double, EPS)
        assertEquals(0.0d, axes[2] as double, EPS)
    }

    @Test
    void rectangleRoiIsRasterizedWithPixelCentreRule() {
        def img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB)
        def roi = ROIs.createRectangleROI(2, 3, 10, 5, ImagePlane.getDefaultPlane())
        def request = RegionRequest.createInstance("test", 1.0, roi)
        def region = img.getSubimage(request.getX(), request.getY(), request.getWidth(), request.getHeight())
        def (values, matrix, mask) = calc.extractPixelsWithMask(region, roi, request)
        assertEquals(50, (values as double[]).length)
        def shape = calc.calculateShape2DFeatures(roi, mask)
        assertEquals(10, shape.size())
        assertEquals(50.0d, shape['PixelSurface'] as double, EPS)
        assertEquals(50.0d, shape['MeshSurface'] as double, EPS)
        assertEquals(30.0d, shape['Perimeter'] as double, EPS)
        assertEquals(Math.sqrt(125.0d), shape['MaximumDiameter'] as double, EPS)
        assertEquals(2.0d * Math.sqrt(Math.PI * 50.0d) / 30.0d, shape['Sphericity'] as double, EPS)
    }

    @Test
    void polygonWithHalfPixelOffsetSelectsExpectedPixels() {
        def img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
        def roi = ROIs.createRectangleROI(4.3, 4.3, 3, 3, ImagePlane.getDefaultPlane())
        def request = RegionRequest.createInstance("test", 1.0, roi)
        def region = img.getSubimage(request.getX(), request.getY(), request.getWidth(), request.getHeight())
        def (values, matrix, mask) = calc.extractPixelsWithMask(region, roi, request)
        assertEquals(9, (values as double[]).length)
        assertEquals(3, (mask as boolean[][]).length)
        assertEquals(3, (mask as boolean[][])[0].length)
    }
}
