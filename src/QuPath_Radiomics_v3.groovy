/**
 * QuPath Radiomics Feature Extraction - v3
 * Extracts up to 119 radiomics features from cell detections and annotations (103 enabled by default)
 */

import qupath.lib.images.servers.ImageServer
import qupath.lib.regions.RegionRequest
import qupath.lib.roi.interfaces.ROI
import qupath.lib.objects.PathObject
import qupath.lib.gui.scripting.QPEx
import qupath.lib.io.GsonTools
import groovy.transform.CompileStatic
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Arrays

// ============================================================================
// CONFIGURATION — modify the settings below to match your analysis
// ============================================================================

// --- Output settings ---
def outputDir = buildFilePath(PROJECT_BASE_DIR, "radiomics")
mkdirs(outputDir)
def exportCSV = true           // Save results to a CSV file
def addToMeasurements = true   // Add features to QuPath's measurement table

// --- Radiomics parameters ---
def settings = [
    binWidth: 25,              // Intensity binning width (PyRadiomics default: 25)
    voxelArrayShift: 0,        // Intensity shift before binning
    force2D: true,             // Force 2D processing (recommended for histopathology)
    distances: [1],            // GLCM pixel distance
    angles: 4                  // GLCM/GLRLM directions (fixed: 0, 45, 90, 135 degrees, matrices summed)
]

// --- Feature classes to extract (set to false to skip) ---
def enabledFeatures = [
    'firstorder': true,        // 19 intensity statistics
    'shape2D': true,           // 10 2D shape features
    'glcm': true,              // 23 GLCM texture features
    'glrlm': true,             // 16 GLRLM texture features
    'glszm': true,             // 16 GLSZM texture features
    'ngtdm': true,             // 5 NGTDM texture features
    'gldm': true,              // 14 GLDM texture features
    'shape': false             // 16 legacy 3D-named shape features (2D quantities; not recommended)
]

// --- What to process (set to true/false) ---
def processAnnotations = false // Process annotation objects (tissue regions, ROIs)
def processDetections = true   // Process detection objects (cells)
def selectedOnly = false       // Only process currently selected objects
def progressInterval = 10000   // Print progress every N objects

// ============================================================================
// HELPERS
// ============================================================================

def quradVersion() {
    return '0.4.0'
}

def metadataKeys() {
    return ['Image', 'ObjectID', 'ObjectType', 'Classification', 'Centroid_X', 'Centroid_Y', 'NumPixels', 'PixelWidth_um', 'PixelHeight_um']
}

def safeDiv(num, denom) {
    return denom != 0 ? num / denom : 0.0d
}

@CompileStatic
double percentile(double[] sorted, double p) {
    int n = sorted.length
    if (n == 1) return sorted[0]
    double pos = p / 100.0d * (n - 1)
    int lo = (int) Math.floor(pos)
    int hi = Math.min(lo + 1, n - 1)
    double frac = pos - lo
    return sorted[lo] + frac * (sorted[hi] - sorted[lo])
}

@CompileStatic
long countMaskPixels(boolean[][] mask) {
    long n = 0
    for (int y = 0; y < mask.length; y++) {
        for (int x = 0; x < mask[y].length; x++) {
            if (mask[y][x]) n++
        }
    }
    return n
}

// ============================================================================
// PYRADIOMICS-STYLE BINNING (aligned to multiples of binWidth from 0)
// ============================================================================

@CompileStatic
List<Number> calculateBinEdges(double[] intensities, int binWidth) {
    if (intensities == null || intensities.length == 0) {
        return []
    }
    
    double minimum = intensities.min()
    double maximum = intensities.max()
    
    // Align to multiple of binWidth from 0
    // lowBound = minimum - (minimum % binWidth)
    int lowBound = (int)(minimum - (minimum % binWidth))
    // highBound = maximum + 2 * binWidth (to ensure max is included)
    int highBound = (int)(maximum + 2 * binWidth)
    
    // Generate bin edges
    List<Number> binEdges = []
    for (int edge = lowBound; edge <= highBound; edge += binWidth) {
        binEdges.add(edge)
    }
    if (binEdges.size() == 1) {
        double e0 = binEdges[0].doubleValue()
        binEdges = [e0 - 0.5d, e0 + 0.5d] as List<Number>
    }
    return binEdges
}

@CompileStatic
int quantizeValue(double intensity, List<Number> binEdges) {
    if (binEdges == null || binEdges.size() < 2) {
        return 1
    }
    double low = binEdges[0].doubleValue()
    double width = binEdges[1].doubleValue() - low
    if (intensity >= binEdges[binEdges.size() - 1].doubleValue()) {
        return binEdges.size()
    }
    int bin = (int) Math.floor((intensity - low) / width) + 1
    if (bin < 1) return 1
    if (bin > binEdges.size() - 1) return binEdges.size() - 1
    return bin
}

@CompileStatic
int[][] quantizeMatrix(int[][] image, boolean[][] mask, List<Number> binEdges) {
    int h = image.length
    int w = h > 0 ? image[0].length : 0
    int[][] quantized = new int[h][w]
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            quantized[y][x] = mask[y][x] ? quantizeValue((double) image[y][x], binEdges) : 0
        }
    }
    return quantized
}

@CompileStatic
int maxGrayLevel(int[][] quantized) {
    int ng = 0
    for (int y = 0; y < quantized.length; y++) {
        for (int x = 0; x < quantized[y].length; x++) {
            if (quantized[y][x] > ng) ng = quantized[y][x]
        }
    }
    return ng
}

@CompileStatic
double log2(double v) {
    return Math.log(v) / Math.log(2)
}

// ============================================================================
// FIRST ORDER FEATURES (19 features)
// ============================================================================

@CompileStatic
Map calculateFirstOrderFeatures(double[] intensities, Map settings, List<Number> binEdges = null) {
    def features = [:]
    if (intensities == null || intensities.length == 0) return features
    
    int n = intensities.length
    double[] sorted = intensities.clone()
    Arrays.sort(sorted)
    
    int shift = ((settings.get('voxelArrayShift') ?: 0) as Number).intValue()
    
    // Mean
    double sum = 0.0d
    for (int i = 0; i < n; i++) {
        sum += intensities[i]
    }
    double mean = sum / n
    
    // Energy
    double energy = 0.0d
    for (int i = 0; i < n; i++) {
        double val = intensities[i] + shift
        energy += val * val
    }
    features['Energy'] = energy
    features['TotalEnergy'] = energy
    
    // Histogram for entropy - use PyRadiomics-style binning if binEdges provided
    int binWidth = ((settings.get('binWidth') ?: 25) as Number).intValue()
    int[] hist
    int nBins
    
    if (binEdges != null && binEdges.size() > 1) {
        // Use PyRadiomics-style binning
        nBins = binEdges.size() - 1
        hist = new int[nBins]
        for (int i = 0; i < n; i++) {
            int bin = quantizeValue(intensities[i], binEdges) - 1  // Convert to 0-based
            if (bin >= 0 && bin < nBins) {
                hist[bin]++
            }
        }
    } else {
        // Fallback to old method
        double minVal = sorted[0]
        double maxVal = sorted[n - 1]
        double range = maxVal - minVal
        nBins = range > 0 ? Math.max(1, (int) Math.ceil(range / binWidth)) : 1
        hist = new int[nBins]
        for (int i = 0; i < n; i++) {
            int bin = range > 0 ? Math.min((int)((intensities[i] - minVal) / binWidth), nBins - 1) : 0
            hist[bin]++
        }
    }
    
    // Entropy
    double entropy = 0.0d
    for (int i = 0; i < nBins; i++) {
        if (hist[i] > 0) {
            double p = (double) hist[i] / n
            entropy -= p * Math.log(p) / Math.log(2)
        }
    }
    features['Entropy'] = entropy
    
    // Basic statistics
    features['Minimum'] = sorted[0]
    features['10Percentile'] = percentile(sorted, 10)
    features['90Percentile'] = percentile(sorted, 90)
    features['Maximum'] = sorted[n - 1]
    features['Mean'] = mean
    features['Median'] = percentile(sorted, 50)
    features['InterquartileRange'] = percentile(sorted, 75) - percentile(sorted, 25)
    features['Range'] = sorted[n - 1] - sorted[0]
    
    // Mean absolute deviation
    double mad = 0.0d
    for (int i = 0; i < n; i++) {
        mad += Math.abs(intensities[i] - mean)
    }
    features['MeanAbsoluteDeviation'] = mad / n
    
    // Robust mean absolute deviation
    double prcnt10 = percentile(sorted, 10)
    double prcnt90 = percentile(sorted, 90)
    double robustSum = 0.0d
    int robustCount = 0
    for (int i = 0; i < n; i++) {
        if (intensities[i] >= prcnt10 && intensities[i] <= prcnt90) {
            robustSum += intensities[i]
            robustCount++
        }
    }
    if (robustCount > 0) {
        double robustMean = robustSum / robustCount
        double rmad = 0.0d
        for (int i = 0; i < n; i++) {
            if (intensities[i] >= prcnt10 && intensities[i] <= prcnt90) {
                rmad += Math.abs(intensities[i] - robustMean)
            }
        }
        features['RobustMeanAbsoluteDeviation'] = rmad / robustCount
    } else {
        features['RobustMeanAbsoluteDeviation'] = 0.0
    }
    
    features['RootMeanSquared'] = Math.sqrt(energy / n)
    
    // Variance and higher moments
    double variance = 0.0d
    for (int i = 0; i < n; i++) {
        double diff = intensities[i] - mean
        variance += diff * diff
    }
    variance /= n
    features['Variance'] = variance
    
    double stdDev = Math.sqrt(variance)
    features['StandardDeviation'] = stdDev
    
    if (stdDev > 0) {
        double skewness = 0.0d
        double kurtosis = 0.0d
        for (int i = 0; i < n; i++) {
            double z = (intensities[i] - mean) / stdDev
            skewness += z * z * z
            kurtosis += z * z * z * z
        }
        features['Skewness'] = skewness / n
        features['Kurtosis'] = kurtosis / n
    } else {
        features['Skewness'] = 0.0
        features['Kurtosis'] = 0.0
    }
    
    // Uniformity
    double uniformity = 0.0d
    for (int i = 0; i < nBins; i++) {
        double p = (double) hist[i] / n
        uniformity += p * p
    }
    features['Uniformity'] = uniformity
    
    return features
}

// ============================================================================
// SHAPE FEATURES - 2D (10 features)
// ============================================================================

@CompileStatic
double[] computeAxisLengths(boolean[][] mask) {
    double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0
    long n = 0
    for (int y = 0; y < mask.length; y++) {
        for (int x = 0; x < mask[y].length; x++) {
            if (mask[y][x]) {
                sx += x; sy += y
                sxx += (double) x * x; syy += (double) y * y; sxy += (double) x * y
                n++
            }
        }
    }
    if (n < 1) return [0.0d, 0.0d, 0.0d] as double[]
    double mx = sx / n, my = sy / n
    double denom = n
    double cxx = (sxx - n * mx * mx) / denom
    double cyy = (syy - n * my * my) / denom
    double cxy = (sxy - n * mx * my) / denom
    double tr = cxx + cyy
    double det = cxx * cyy - cxy * cxy
    double disc = Math.sqrt(Math.max(0.0d, tr * tr / 4.0d - det))
    double l1 = tr / 2.0d + disc
    double l2 = tr / 2.0d - disc
    if (l2 < 0) l2 = 0.0d
    double major = 4.0d * Math.sqrt(l1)
    double minor = 4.0d * Math.sqrt(l2)
    double elong = l1 > 0 ? Math.sqrt(l2 / l1) : 0.0d
    return [major, minor, elong] as double[]
}

@CompileStatic
double computeMaximumDiameter(ROI roi) {
    ROI hull = roi.getConvexHull()
    List<qupath.lib.geom.Point2> points = (hull != null && !hull.isEmpty()) ? hull.getAllPoints() : roi.getAllPoints()
    double maxD2 = 0.0d
    int n = points.size()
    for (int i = 0; i < n; i++) {
        def pi = points[i]
        for (int j = i + 1; j < n; j++) {
            def pj = points[j]
            double dx = pi.getX() - pj.getX()
            double dy = pi.getY() - pj.getY()
            double d2 = dx * dx + dy * dy
            if (d2 > maxD2) maxD2 = d2
        }
    }
    return Math.sqrt(maxD2)
}

@CompileStatic
Map calculateShape2DFeatures(ROI roi, boolean[][] mask) {
    def features = [:]
    double area = roi.getArea()
    double perimeter = roi.getLength()

    features['MeshSurface'] = area
    features['PixelSurface'] = (double) countMaskPixels(mask)
    features['Perimeter'] = perimeter
    features['PerimeterSurfaceRatio'] = area > 0 ? perimeter / area : 0.0

    double sphericity = perimeter > 0 ? 2.0d * Math.sqrt(Math.PI * area) / perimeter : 0.0d
    features['Sphericity'] = sphericity
    features['SphericalDisproportion'] = sphericity > 0 ? 1.0 / sphericity : 0.0
    features['MaximumDiameter'] = computeMaximumDiameter(roi)

    double[] axes = computeAxisLengths(mask)
    features['MajorAxisLength'] = axes[0]
    features['MinorAxisLength'] = axes[1]
    features['Elongation'] = axes[2]

    return features
}

// ============================================================================
// SHAPE FEATURES - 3D (16 features)
// ============================================================================

@CompileStatic
Map calculateShape3DFeatures(ROI roi, boolean[][] mask) {
    def features = [:]
    double area = roi.getArea()
    double perimeter = roi.getLength()

    features['VoxelVolume'] = (double) countMaskPixels(mask)
    features['MeshVolume'] = area
    features['SurfaceArea'] = perimeter
    features['SurfaceVolumeRatio'] = area > 0 ? perimeter / area : 0.0

    double sphericity = perimeter > 0 ? 2.0d * Math.sqrt(Math.PI * area) / perimeter : 0.0d
    features['Sphericity'] = sphericity
    features['Compactness1'] = perimeter > 0 ? area / Math.sqrt(Math.PI * perimeter * perimeter * perimeter) : 0.0
    features['Compactness2'] = perimeter > 0 ? 36.0 * Math.PI * area * area / (perimeter * perimeter * perimeter) : 0.0
    features['SphericalDisproportion'] = sphericity > 0 ? 1.0 / sphericity : 0.0

    double maxDiameter = computeMaximumDiameter(roi)
    double[] axes = computeAxisLengths(mask)
    double major = axes[0], minor = axes[1], elong = axes[2]

    features['Maximum3DDiameter'] = maxDiameter
    features['Maximum2DDiameterSlice'] = maxDiameter
    features['Maximum2DDiameterColumn'] = roi.getBoundsWidth()
    features['Maximum2DDiameterRow'] = roi.getBoundsHeight()
    features['MajorAxisLength'] = major
    features['MinorAxisLength'] = minor
    features['LeastAxisLength'] = minor
    features['Elongation'] = elong

    return features
}

// ============================================================================
// GLCM FEATURES (23 features)
// ============================================================================

@CompileStatic
long[][] buildGLCM(int[][] image, boolean[][] mask, int distance, List<Number> binEdges) {
    int[][] q = quantizeMatrix(image, mask, binEdges)
    int ng = maxGrayLevel(q)
    long[][] glcm = new long[ng + 1][ng + 1]
    int height = q.length
    int width = height > 0 ? q[0].length : 0
    int[][] angles = [[1,0], [1,1], [0,1], [-1,1]] as int[][]
    for (int a = 0; a < 4; a++) {
        int dx = angles[a][0] * distance
        int dy = angles[a][1] * distance
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = q[y][x]
                if (i == 0) continue
                int nx = x + dx
                int ny = y + dy
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                int j = q[ny][nx]
                if (j == 0) continue
                glcm[i][j]++
                glcm[j][i]++
            }
        }
    }
    return glcm
}

@CompileStatic
Map calculateGLCMFeatures(int[][] image, boolean[][] mask, Map settings, List<Number> binEdges) {
    def features = [:]
    int distance = ((settings.get('distances') as List)[0] as Number).intValue()
    long[][] glcm = buildGLCM(image, mask, distance, binEdges)
    int ng = glcm.length - 1
    if (ng < 1) return features

    double total = 0.0d
    for (int i = 1; i <= ng; i++) for (int j = 1; j <= ng; j++) total += glcm[i][j]
    if (total == 0) return features

    double[][] p = new double[ng + 1][ng + 1]
    double[] px = new double[ng + 1]
    double[] py = new double[ng + 1]
    for (int i = 1; i <= ng; i++) {
        for (int j = 1; j <= ng; j++) {
            p[i][j] = glcm[i][j] / total
            px[i] += p[i][j]
            py[j] += p[i][j]
        }
    }

    double ux = 0.0d, uy = 0.0d
    for (int i = 1; i <= ng; i++) { ux += i * px[i]; uy += i * py[i] }
    double varX = 0.0d, varY = 0.0d
    for (int i = 1; i <= ng; i++) { varX += (i - ux) * (i - ux) * px[i]; varY += (i - uy) * (i - uy) * py[i] }
    double sx = Math.sqrt(varX)
    double sy = Math.sqrt(varY)
    boolean degenerateCorrelation = sx * sy == 0

    double autocorr = 0.0d, clusterProm = 0.0d, clusterShade = 0.0d, clusterTend = 0.0d
    double contrast = 0.0d, correlation = 0.0d, jointEnergy = 0.0d, jointEntropy = 0.0d
    double idm = 0.0d, idmn = 0.0d, id = 0.0d, idn = 0.0d, invVar = 0.0d, maxProb = 0.0d, sumSquares = 0.0d
    double[] pxMinusY = new double[ng]
    double[] pxPlusY = new double[2 * ng + 1]
    double ngd = ng

    for (int i = 1; i <= ng; i++) {
        for (int j = 1; j <= ng; j++) {
            double prob = p[i][j]
            if (prob == 0) continue
            autocorr += i * j * prob
            double c = i + j - ux - uy
            clusterProm += c * c * c * c * prob
            clusterShade += c * c * c * prob
            clusterTend += c * c * prob
            int d = i - j
            contrast += d * d * prob
            if (!degenerateCorrelation) correlation += (i - ux) * (j - uy) * prob / (sx * sy)
            jointEnergy += prob * prob
            jointEntropy -= prob * log2(prob)
            idm += prob / (1 + d * d)
            idmn += prob / (1 + (d * d) / (ngd * ngd))
            id += prob / (1 + Math.abs(d))
            idn += prob / (1 + Math.abs(d) / ngd)
            if (d != 0) invVar += prob / (d * d)
            if (prob > maxProb) maxProb = prob
            sumSquares += (i - ux) * (i - ux) * prob
            pxMinusY[Math.abs(d)] += prob
            pxPlusY[i + j] += prob
        }
    }

    features['Autocorrelation'] = autocorr
    features['JointAverage'] = ux
    features['ClusterProminence'] = clusterProm
    features['ClusterShade'] = clusterShade
    features['ClusterTendency'] = clusterTend
    features['Contrast'] = contrast
    features['Correlation'] = degenerateCorrelation ? 1.0 : correlation
    features['JointEnergy'] = jointEnergy
    features['JointEntropy'] = jointEntropy
    features['Idm'] = idm
    features['Idmn'] = idmn
    features['Id'] = id
    features['Idn'] = idn
    features['InverseVariance'] = invVar
    features['MaximumProbability'] = maxProb
    features['SumSquares'] = sumSquares

    double diffAvg = 0.0d, diffEntropy = 0.0d, diffVar = 0.0d
    for (int k = 0; k < ng; k++) {
        double pk = pxMinusY[k]
        if (pk == 0) continue
        diffAvg += k * pk
        diffEntropy -= pk * log2(pk)
    }
    for (int k = 0; k < ng; k++) diffVar += (k - diffAvg) * (k - diffAvg) * pxMinusY[k]
    features['DifferenceAverage'] = diffAvg
    features['DifferenceEntropy'] = diffEntropy
    features['DifferenceVariance'] = diffVar

    double sumAvg = 0.0d, sumEntropy = 0.0d
    for (int k = 2; k <= 2 * ng; k++) {
        double pk = pxPlusY[k]
        if (pk == 0) continue
        sumAvg += k * pk
        sumEntropy -= pk * log2(pk)
    }
    features['SumAverage'] = sumAvg
    features['SumEntropy'] = sumEntropy

    double hx = 0.0d, hy = 0.0d, hxy1 = 0.0d, hxy2 = 0.0d
    for (int i = 1; i <= ng; i++) {
        if (px[i] > 0) hx -= px[i] * log2(px[i])
        if (py[i] > 0) hy -= py[i] * log2(py[i])
    }
    for (int i = 1; i <= ng; i++) {
        for (int j = 1; j <= ng; j++) {
            double pp = px[i] * py[j]
            if (pp <= 0) continue
            if (p[i][j] > 0) hxy1 -= p[i][j] * log2(pp)
            hxy2 -= pp * log2(pp)
        }
    }
    double maxH = Math.max(hx, hy)
    features['Imc1'] = maxH > 0 ? (jointEntropy - hxy1) / maxH : 0.0
    double imc2 = 1.0d - Math.exp(-2.0d * (hxy2 - jointEntropy))
    features['Imc2'] = imc2 > 0 ? Math.sqrt(imc2) : 0.0

    return features
}

// ============================================================================
// GLRLM FEATURES (16 features)
// ============================================================================

@CompileStatic
long[][] buildGLRLM(int[][] image, boolean[][] mask, List<Number> binEdges) {
    int[][] q = quantizeMatrix(image, mask, binEdges)
    int ng = maxGrayLevel(q)
    int height = q.length
    int width = height > 0 ? q[0].length : 0
    int maxRun = Math.max(width, height)
    long[][] glrlm = new long[ng + 1][maxRun + 1]
    int[][] directions = [[1,0], [0,1], [1,1], [1,-1]] as int[][]
    for (int d = 0; d < 4; d++) {
        int dx = directions[d][0]
        int dy = directions[d][1]
        for (int y0 = 0; y0 < height; y0++) {
            for (int x0 = 0; x0 < width; x0++) {
                int px = x0 - dx
                int py = y0 - dy
                if (px >= 0 && px < width && py >= 0 && py < height) continue
                int gl = 0
                int len = 0
                int x = x0
                int y = y0
                while (x >= 0 && x < width && y >= 0 && y < height) {
                    int g = q[y][x]
                    if (g == 0) {
                        if (len > 0) glrlm[gl][len]++
                        gl = 0
                        len = 0
                    } else if (g == gl) {
                        len++
                    } else {
                        if (len > 0) glrlm[gl][len]++
                        gl = g
                        len = 1
                    }
                    x += dx
                    y += dy
                }
                if (len > 0) glrlm[gl][len]++
            }
        }
    }
    return glrlm
}

@CompileStatic
Map calculateGLRLMFeatures(int[][] image, boolean[][] mask, Map settings, List<Number> binEdges) {
    def features = [:]
    long[][] glrlm = buildGLRLM(image, mask, binEdges)
    int ng = glrlm.length - 1
    if (ng < 1) return features
    int maxRun = glrlm[0].length - 1

    double totalRuns = 0.0d
    for (int i = 1; i <= ng; i++) for (int j = 1; j <= maxRun; j++) totalRuns += glrlm[i][j]
    if (totalRuns == 0) return features

    double sre = 0.0d, lre = 0.0d, lgre = 0.0d, hgre = 0.0d
    double srlge = 0.0d, srhge = 0.0d, lrlge = 0.0d, lrhge = 0.0d
    double glMean = 0.0d, rlMean = 0.0d, runEntropy = 0.0d, runPixels = 0.0d
    double[] glCounts = new double[ng + 1]
    double[] rlCounts = new double[maxRun + 1]

    for (int gl = 1; gl <= ng; gl++) {
        for (int rl = 1; rl <= maxRun; rl++) {
            double c = glrlm[gl][rl]
            if (c == 0) continue
            runPixels += c * rl
            sre += c / ((double) rl * rl)
            lre += c * rl * rl
            lgre += c / ((double) gl * gl)
            hgre += c * gl * gl
            srlge += c / ((double) gl * gl * rl * rl)
            srhge += c * gl * gl / ((double) rl * rl)
            lrlge += c * rl * rl / ((double) gl * gl)
            lrhge += c * gl * gl * rl * rl
            glMean += gl * c
            rlMean += rl * c
            double pr = c / totalRuns
            runEntropy -= pr * log2(pr)
            glCounts[gl] += c
            rlCounts[rl] += c
        }
    }
    glMean /= totalRuns
    rlMean /= totalRuns

    double glVar = 0.0d, rlVar = 0.0d
    for (int gl = 1; gl <= ng; gl++) {
        for (int rl = 1; rl <= maxRun; rl++) {
            double c = glrlm[gl][rl]
            if (c == 0) continue
            glVar += (gl - glMean) * (gl - glMean) * c
            rlVar += (rl - rlMean) * (rl - rlMean) * c
        }
    }
    glVar /= totalRuns
    rlVar /= totalRuns

    double glnu = 0.0d, rlnu = 0.0d
    for (int gl = 1; gl <= ng; gl++) glnu += glCounts[gl] * glCounts[gl]
    for (int rl = 1; rl <= maxRun; rl++) rlnu += rlCounts[rl] * rlCounts[rl]

    features['ShortRunEmphasis'] = sre / totalRuns
    features['LongRunEmphasis'] = lre / totalRuns
    features['GrayLevelNonUniformity'] = glnu / totalRuns
    features['GrayLevelNonUniformityNormalized'] = glnu / (totalRuns * totalRuns)
    features['RunLengthNonUniformity'] = rlnu / totalRuns
    features['RunLengthNonUniformityNormalized'] = rlnu / (totalRuns * totalRuns)
    features['RunPercentage'] = runPixels > 0 ? totalRuns / runPixels : 0.0
    features['GrayLevelVariance'] = glVar
    features['RunVariance'] = rlVar
    features['RunEntropy'] = runEntropy
    features['LowGrayLevelRunEmphasis'] = lgre / totalRuns
    features['HighGrayLevelRunEmphasis'] = hgre / totalRuns
    features['ShortRunLowGrayLevelEmphasis'] = srlge / totalRuns
    features['ShortRunHighGrayLevelEmphasis'] = srhge / totalRuns
    features['LongRunLowGrayLevelEmphasis'] = lrlge / totalRuns
    features['LongRunHighGrayLevelEmphasis'] = lrhge / totalRuns

    return features
}

// ============================================================================
// GLSZM FEATURES (16 features)
// ============================================================================

@CompileStatic
int floodFill(int[][] image, boolean[][] visited, int startX, int startY, int targetGL, int[] stack = null) {
    int h = image.length
    int w = image[0].length
    if (stack == null) stack = new int[64]
    int sp = 0
    stack[sp++] = startX
    stack[sp++] = startY
    int size = 0
    while (sp > 0) {
        int cy = stack[--sp]
        int cx = stack[--sp]
        if (cx < 0 || cx >= w || cy < 0 || cy >= h) continue
        if (visited[cy][cx] || image[cy][cx] != targetGL) continue
        visited[cy][cx] = true
        size++
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue
                int nx = cx + dx
                int ny = cy + dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                if (visited[ny][nx] || image[ny][nx] != targetGL) continue
                if (sp + 2 > stack.length) {
                    int[] bigger = new int[stack.length * 2]
                    System.arraycopy(stack, 0, bigger, 0, sp)
                    stack = bigger
                }
                stack[sp++] = nx
                stack[sp++] = ny
            }
        }
    }
    return size
}

@CompileStatic
Map<Long, Long> buildGLSZM(int[][] image, boolean[][] mask, List<Number> binEdges) {
    int[][] q = quantizeMatrix(image, mask, binEdges)
    int h = q.length
    int w = h > 0 ? q[0].length : 0
    boolean[][] visited = new boolean[h][w]
    def glszm = new HashMap<Long, Long>()
    int[] stack = new int[Math.max(64, 2 * w * h + 2)]
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (visited[y][x] || q[y][x] == 0) continue
            int gl = q[y][x]
            int size = floodFill(q, visited, x, y, gl, stack)
            if (size > 0) {
                long key = (((long) gl) << 32) | size
                Long prev = glszm.get(key)
                glszm.put(key, prev == null ? 1L : prev + 1L)
            }
        }
    }
    return glszm
}

@CompileStatic
Map calculateGLSZMFeatures(int[][] image, boolean[][] mask, Map settings, List<Number> binEdges) {
    def features = [:]
    def glszm = buildGLSZM(image, mask, binEdges)
    if (glszm.isEmpty()) return features

    double totalZones = 0.0d
    for (Long v : glszm.values()) totalZones += v
    if (totalZones == 0) return features

    double np = countMaskPixels(mask)
    if (np == 0) return features

    double sae = 0.0d, lae = 0.0d, lgze = 0.0d, hgze = 0.0d
    double salge = 0.0d, sahge = 0.0d, lalge = 0.0d, lahge = 0.0d
    double glMean = 0.0d, szMean = 0.0d, zoneEntropy = 0.0d
    Map<Integer, Double> glCounts = new HashMap<Integer, Double>()
    Map<Integer, Double> szCounts = new HashMap<Integer, Double>()

    for (Map.Entry<Long, Long> entry : glszm.entrySet()) {
        long key = entry.getKey()
        int gl = (int) (key >> 32)
        int sz = (int) (key & 0xffffffffL)
        double c = entry.getValue()
        sae += c / ((double) sz * sz)
        lae += c * sz * sz
        lgze += c / ((double) gl * gl)
        hgze += c * gl * gl
        salge += c / ((double) gl * gl * sz * sz)
        sahge += c * gl * gl / ((double) sz * sz)
        lalge += c * sz * sz / ((double) gl * gl)
        lahge += c * gl * gl * sz * sz
        glMean += gl * c
        szMean += sz * c
        double pz = c / totalZones
        zoneEntropy -= pz * log2(pz)
        Double g0 = glCounts.get(gl)
        glCounts.put(gl, (g0 == null ? 0.0d : g0) + c)
        Double s0 = szCounts.get(sz)
        szCounts.put(sz, (s0 == null ? 0.0d : s0) + c)
    }
    glMean /= totalZones
    szMean /= totalZones

    double glVar = 0.0d, szVar = 0.0d
    for (Map.Entry<Long, Long> entry : glszm.entrySet()) {
        long key = entry.getKey()
        int gl = (int) (key >> 32)
        int sz = (int) (key & 0xffffffffL)
        double c = entry.getValue()
        glVar += (gl - glMean) * (gl - glMean) * c
        szVar += (sz - szMean) * (sz - szMean) * c
    }
    glVar /= totalZones
    szVar /= totalZones

    double glnu = 0.0d, sznu = 0.0d
    for (double c : glCounts.values()) glnu += c * c
    for (double c : szCounts.values()) sznu += c * c

    features['SmallAreaEmphasis'] = sae / totalZones
    features['LargeAreaEmphasis'] = lae / totalZones
    features['GrayLevelNonUniformity'] = glnu / totalZones
    features['GrayLevelNonUniformityNormalized'] = glnu / (totalZones * totalZones)
    features['SizeZoneNonUniformity'] = sznu / totalZones
    features['SizeZoneNonUniformityNormalized'] = sznu / (totalZones * totalZones)
    features['ZonePercentage'] = totalZones / np
    features['GrayLevelVariance'] = glVar
    features['ZoneVariance'] = szVar
    features['ZoneEntropy'] = zoneEntropy
    features['LowGrayLevelZoneEmphasis'] = lgze / totalZones
    features['HighGrayLevelZoneEmphasis'] = hgze / totalZones
    features['SmallAreaLowGrayLevelEmphasis'] = salge / totalZones
    features['SmallAreaHighGrayLevelEmphasis'] = sahge / totalZones
    features['LargeAreaLowGrayLevelEmphasis'] = lalge / totalZones
    features['LargeAreaHighGrayLevelEmphasis'] = lahge / totalZones

    return features
}

// ============================================================================
// NGTDM FEATURES (5 features)
// ============================================================================

@CompileStatic
double[][] buildNGTDM(int[][] image, boolean[][] mask, List<Number> binEdges) {
    int[][] q = quantizeMatrix(image, mask, binEdges)
    int ng = maxGrayLevel(q)
    int h = q.length
    int w = h > 0 ? q[0].length : 0
    double[] n = new double[ng + 1]
    double[] s = new double[ng + 1]
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int gl = q[y][x]
            if (gl == 0) continue
            int count = 0
            double sum = 0.0d
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue
                    int ny = y + dy
                    int nx = x + dx
                    if (ny < 0 || ny >= h || nx < 0 || nx >= w) continue
                    int g = q[ny][nx]
                    if (g == 0) continue
                    sum += g
                    count++
                }
            }
            n[gl] += 1
            if (count > 0) s[gl] += Math.abs(gl - sum / count)
        }
    }
    return [n, s] as double[][]
}

@CompileStatic
Map calculateNGTDMFeatures(int[][] image, boolean[][] mask, Map settings, List<Number> binEdges) {
    def features = [:]
    double[][] ns = buildNGTDM(image, mask, binEdges)
    double[] n = ns[0]
    double[] s = ns[1]
    int ng = n.length - 1
    if (ng < 1) return features

    double nTotal = 0.0d
    int ngp = 0
    for (int i = 1; i <= ng; i++) { nTotal += n[i]; if (n[i] > 0) ngp++ }
    if (nTotal == 0) return features

    double[] p = new double[ng + 1]
    for (int i = 1; i <= ng; i++) p[i] = n[i] / nTotal

    double sumPS = 0.0d, sumS = 0.0d
    for (int i = 1; i <= ng; i++) { sumPS += p[i] * s[i]; sumS += s[i] }
    features['Coarseness'] = sumPS > 0 ? 1.0 / sumPS : 1.0e6

    double contrastSum = 0.0d, busyDenom = 0.0d, complexity = 0.0d, strengthNum = 0.0d
    for (int i = 1; i <= ng; i++) {
        if (n[i] == 0) continue
        for (int j = 1; j <= ng; j++) {
            if (n[j] == 0) continue
            double d = i - j
            contrastSum += p[i] * p[j] * d * d
            if (i != j) busyDenom += Math.abs(i * p[i] - j * p[j])
            double denom = p[i] + p[j]
            if (denom > 0) complexity += Math.abs(d) * (p[i] * s[i] + p[j] * s[j]) / denom
            strengthNum += (p[i] + p[j]) * d * d
        }
    }
    features['Contrast'] = ngp > 1 ? (1.0 / (ngp * (ngp - 1))) * contrastSum * sumS / nTotal : 0.0
    features['Busyness'] = busyDenom > 0 ? sumPS / busyDenom : 0.0
    features['Complexity'] = complexity / nTotal
    features['Strength'] = sumS > 0 ? strengthNum / sumS : 0.0

    return features
}

// ============================================================================
// GLDM FEATURES (14 features)
// ============================================================================

@CompileStatic
long[][] buildGLDM(int[][] image, boolean[][] mask, List<Number> binEdges) {
    int[][] q = quantizeMatrix(image, mask, binEdges)
    int ng = maxGrayLevel(q)
    int h = q.length
    int w = h > 0 ? q[0].length : 0
    long[][] gldm = new long[ng + 1][10]
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int gl = q[y][x]
            if (gl == 0) continue
            int dep = 0
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue
                    int ny = y + dy
                    int nx = x + dx
                    if (ny >= 0 && ny < h && nx >= 0 && nx < w && q[ny][nx] == gl) dep++
                }
            }
            gldm[gl][dep + 1]++
        }
    }
    return gldm
}

@CompileStatic
Map calculateGLDMFeatures(int[][] image, boolean[][] mask, Map settings, List<Number> binEdges) {
    def features = [:]
    long[][] gldm = buildGLDM(image, mask, binEdges)
    int ng = gldm.length - 1
    if (ng < 1) return features

    double totalDep = 0.0d
    for (int i = 1; i <= ng; i++) for (int j = 1; j <= 9; j++) totalDep += gldm[i][j]
    if (totalDep == 0) return features

    double sde = 0.0d, lde = 0.0d, lgde = 0.0d, hgde = 0.0d
    double sdlge = 0.0d, sdhge = 0.0d, ldlge = 0.0d, ldhge = 0.0d
    double glMean = 0.0d, depMean = 0.0d, depEntropy = 0.0d
    double[] glCounts = new double[ng + 1]
    double[] depCounts = new double[10]

    for (int gl = 1; gl <= ng; gl++) {
        for (int dep = 1; dep <= 9; dep++) {
            double c = gldm[gl][dep]
            if (c == 0) continue
            sde += c / ((double) dep * dep)
            lde += c * dep * dep
            lgde += c / ((double) gl * gl)
            hgde += c * gl * gl
            sdlge += c / ((double) gl * gl * dep * dep)
            sdhge += c * gl * gl / ((double) dep * dep)
            ldlge += c * dep * dep / ((double) gl * gl)
            ldhge += c * gl * gl * dep * dep
            glMean += gl * c
            depMean += dep * c
            double pd = c / totalDep
            depEntropy -= pd * log2(pd)
            glCounts[gl] += c
            depCounts[dep] += c
        }
    }
    glMean /= totalDep
    depMean /= totalDep

    double glVar = 0.0d, depVar = 0.0d
    for (int gl = 1; gl <= ng; gl++) {
        for (int dep = 1; dep <= 9; dep++) {
            double c = gldm[gl][dep]
            if (c == 0) continue
            glVar += (gl - glMean) * (gl - glMean) * c
            depVar += (dep - depMean) * (dep - depMean) * c
        }
    }
    glVar /= totalDep
    depVar /= totalDep

    double glnu = 0.0d, depnu = 0.0d
    for (int gl = 1; gl <= ng; gl++) glnu += glCounts[gl] * glCounts[gl]
    for (int dep = 1; dep <= 9; dep++) depnu += depCounts[dep] * depCounts[dep]

    features['SmallDependenceEmphasis'] = sde / totalDep
    features['LargeDependenceEmphasis'] = lde / totalDep
    features['GrayLevelNonUniformity'] = glnu / totalDep
    features['DependenceNonUniformity'] = depnu / totalDep
    features['DependenceNonUniformityNormalized'] = depnu / (totalDep * totalDep)
    features['GrayLevelVariance'] = glVar
    features['DependenceVariance'] = depVar
    features['DependenceEntropy'] = depEntropy
    features['LowGrayLevelEmphasis'] = lgde / totalDep
    features['HighGrayLevelEmphasis'] = hgde / totalDep
    features['SmallDependenceLowGrayLevelEmphasis'] = sdlge / totalDep
    features['SmallDependenceHighGrayLevelEmphasis'] = sdhge / totalDep
    features['LargeDependenceLowGrayLevelEmphasis'] = ldlge / totalDep
    features['LargeDependenceHighGrayLevelEmphasis'] = ldhge / totalDep

    return features
}

// ============================================================================
// PIXEL EXTRACTION WITH MASK
// ============================================================================

@CompileStatic
BufferedImage rasterizeRoi(ROI roi, RegionRequest request, int width, int height) {
    BufferedImage maskImg = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    java.awt.Graphics2D g2d = maskImg.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g2d.scale(1.0d / request.getDownsample(), 1.0d / request.getDownsample())
    g2d.translate(-request.getX(), -request.getY())
    g2d.setColor(Color.WHITE)
    g2d.fill(roi.getShape())
    g2d.dispose()
    return maskImg
}

@CompileStatic
List extractPixelsWithMask(BufferedImage img, ROI roi, RegionRequest request, int imageWidth = Integer.MAX_VALUE, int imageHeight = Integer.MAX_VALUE) {
    int width = Math.min(img.getWidth(), imageWidth - request.getX())
    int height = Math.min(img.getHeight(), imageHeight - request.getY())
    if (width <= 0 || height <= 0) return [new double[0], new int[0][0], new boolean[0][0]] as List

    BufferedImage maskImg = rasterizeRoi(roi, request, width, height)
    java.awt.image.Raster maskRaster = maskImg.getRaster()
    int[] rgbs = img.getRGB(0, 0, width, height, null, 0, width)
    int startX = Math.max(0, -request.getX())
    int startY = Math.max(0, -request.getY())

    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE
    int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE
    int count = 0
    for (int y = startY; y < height; y++) {
        for (int x = startX; x < width; x++) {
            if (maskRaster.getSample(x, y, 0) != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                count++
            }
        }
    }
    if (count == 0) return [new double[0], new int[0][0], new boolean[0][0]] as List

    int compactWidth = maxX - minX + 1
    int compactHeight = maxY - minY + 1
    double[] values = new double[count]
    int[][] imageMatrix = new int[compactHeight][compactWidth]
    boolean[][] mask = new boolean[compactHeight][compactWidth]

    int k = 0
    for (int y = minY; y <= maxY; y++) {
        for (int x = minX; x <= maxX; x++) {
            if (maskRaster.getSample(x, y, 0) == 0) continue
            int rgb = rgbs[y * width + x]
            int r = (rgb >> 16) & 0xFF
            int g = (rgb >> 8) & 0xFF
            int b = rgb & 0xFF
            int gray = (299 * r + 587 * g + 114 * b).intdiv(1000)
            values[k++] = gray
            imageMatrix[y - minY][x - minX] = gray
            mask[y - minY][x - minX] = true
        }
    }

    return [values, imageMatrix, mask] as List
}

// ============================================================================
// MAIN EXTRACTION FUNCTION
// ============================================================================

def extractFeatures(ImageServer server, PathObject pathObject, Map settings, Map enabledFeatures) {
    def results = [:]
    
    try {
        ROI roi = pathObject.getROI()
        if (roi == null) return results
        
        boolean needsShape = enabledFeatures['shape2D'] || enabledFeatures['shape']
        boolean needsIntensity = enabledFeatures['firstorder'] || enabledFeatures['glcm'] ||
                                enabledFeatures['glrlm'] || enabledFeatures['glszm'] ||
                                enabledFeatures['ngtdm'] || enabledFeatures['gldm']

        if (!needsShape && !needsIntensity) return results

        def request = RegionRequest.createInstance(server.getPath(), 1.0d, roi)
        def img = server.readRegion(request)
        List extracted = extractPixelsWithMask(img, roi, request, server.getWidth(), server.getHeight())
        double[] intensities = extracted[0] as double[]
        int[][] imageMatrix = extracted[1] as int[][]
        boolean[][] mask = extracted[2] as boolean[][]
        if (intensities.length == 0) return results
        results['NumPixels'] = intensities.length

        if (enabledFeatures['shape2D']) {
            calculateShape2DFeatures(roi, mask).each { k, v -> results["shape2D_${k}"] = v }
        }
        if (enabledFeatures['shape']) {
            calculateShape3DFeatures(roi, mask).each { k, v -> results["shape_${k}"] = v }
        }

        if (!needsIntensity) return results

        List<Number> binEdges = calculateBinEdges(intensities, ((settings.binWidth ?: 25) as Number).intValue())
        
        // First order features
        if (enabledFeatures['firstorder']) {
            calculateFirstOrderFeatures(intensities, settings, binEdges).each { k, v -> results["firstorder_${k}"] = v }
        }
        
        // Texture features
        if (imageMatrix != null && imageMatrix.length > 0 && imageMatrix[0].length > 0) {
            if (enabledFeatures['glcm']) {
                calculateGLCMFeatures(imageMatrix, mask, settings, binEdges).each { k, v -> results["glcm_${k}"] = v }
            }
            if (enabledFeatures['glrlm']) {
                calculateGLRLMFeatures(imageMatrix, mask, settings, binEdges).each { k, v -> results["glrlm_${k}"] = v }
            }
            if (enabledFeatures['glszm']) {
                calculateGLSZMFeatures(imageMatrix, mask, settings, binEdges).each { k, v -> results["glszm_${k}"] = v }
            }
            if (enabledFeatures['ngtdm']) {
                calculateNGTDMFeatures(imageMatrix, mask, settings, binEdges).each { k, v -> results["ngtdm_${k}"] = v }
            }
            if (enabledFeatures['gldm']) {
                calculateGLDMFeatures(imageMatrix, mask, settings, binEdges).each { k, v -> results["gldm_${k}"] = v }
            }
        }
        
    } catch (Exception e) {
        // println "Error: ${e.message}"
    }
    
    return results
}



@CompileStatic
String formatValue(Object v) {
    if (v == null) return ''
    if (v instanceof Number) return Double.toString(((Number) v).doubleValue())
    return '"' + v.toString().replace('"', '""') + '"'
}

@CompileStatic
File writeCsv(List allResults, File outputFile) {
    List<String> keys = metadataKeys() as List<String>
    Map first = allResults[0] as Map
    Set<String> available = new LinkedHashSet<String>()
    for (Object row : allResults) available.addAll(((Map) row).keySet() as Set<String>)
    List<String> featureKeys = available.findAll { !keys.contains(it) }.sort() as List<String>
    List<String> headers = keys.findAll { first.containsKey(it) } + featureKeys
    outputFile.withWriter { java.io.Writer writer ->
        writer.write(headers.join(',')); writer.write('\n')
        StringBuilder sb = new StringBuilder(4096)
        for (Object row : allResults) {
            Map result = row as Map
            sb.setLength(0)
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(',')
                sb.append(formatValue(result.get(headers.get(i))))
            }
            sb.append('\n')
            writer.write(sb.toString())
        }
    }
    return outputFile
}

def buildSettingsRecord(ImageServer server, Map settings, Map enabledFeatures, int nObjects) {
    def cal = server.getPixelCalibration()
    return [
        software          : 'QuRad',
        version           : quradVersion(),
        qupathVersion     : qupath.lib.common.GeneralTools.getVersion(),
        image             : server.getMetadata().getName(),
        imageWidth        : server.getWidth(),
        imageHeight       : server.getHeight(),
        isRGB             : server.isRGB(),
        pixelWidth_um     : cal.hasPixelSizeMicrons() ? cal.getPixelWidthMicrons() : null,
        pixelHeight_um    : cal.hasPixelSizeMicrons() ? cal.getPixelHeightMicrons() : null,
        grayscale         : 'floor((299 R + 587 G + 114 B) / 1000) on 8-bit RGB',
        binWidth          : settings.binWidth,
        voxelArrayShift   : settings.voxelArrayShift,
        force2D           : true,
        glcmDistance_px   : settings.distances[0],
        glcmAngles        : [[1, 0], [1, 1], [0, 1], [-1, 1]],
        glrlmAngles       : [[1, 0], [0, 1], [1, 1], [1, -1]],
        angleAggregation  : 'matrices summed over angles (PyRadiomics weightingNorm=no_weighting), symmetric GLCM',
        maskConvention    : 'pixel included if its centre lies inside the ROI polygon (Java2D fill, STROKE_PURE, no antialiasing)',
        enabledFeatures   : enabledFeatures,
        nObjects          : nObjects,
        timestamp         : String.format('%tFT%<tT', new Date())
    ]
}

def writeSettingsJson(File file, Map record) {
    java.nio.file.Files.writeString(file.toPath(), GsonTools.getInstance(true).toJson(record))
    return file
}

def imageMetadata(ImageServer server) {
    def cal = server.getPixelCalibration()
    return [
        Image          : server.getMetadata().getName(),
        PixelWidth_um  : cal.hasPixelSizeMicrons() ? cal.getPixelWidthMicrons() : null,
        PixelHeight_um : cal.hasPixelSizeMicrons() ? cal.getPixelHeightMicrons() : null
    ]
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================

println "=" * 80
println "QuPath Radiomics Extraction - QuRad ${quradVersion()}"
println "=" * 80
enabledFeatures.each { c, e -> if (e) println "  ✓ ${c}" }
println "=" * 80

def imageData = QPEx.getCurrentImageData()
if (!imageData) {
    println "ERROR: No image loaded"
    return
}

def server = imageData.getServer()
def hierarchy = imageData.getHierarchy()
def objectsToProcess = []

if (!server.isRGB()) {
    println "ERROR: QuRad requires an 8-bit RGB (brightfield) image; this image has ${server.nChannels()} channel(s) of type ${server.getPixelType()}"
    return
}

if (selectedOnly) {
    objectsToProcess = QPEx.getSelectedObjects()
} else {
    if (processAnnotations) objectsToProcess.addAll(QPEx.getAnnotationObjects())
    if (processDetections) objectsToProcess.addAll(QPEx.getDetectionObjects())
}

if (objectsToProcess.isEmpty()) {
    println "ERROR: No objects to process"
    return
}

println "Processing ${objectsToProcess.size()} objects\n"

def imageMeta = imageMetadata(server)
def allResults = []
def processedCount = 0
def skippedCount = 0
def startTime = System.currentTimeMillis()
def firstObjectLogged = false

objectsToProcess.eachWithIndex { pathObject, index ->
    
    if ((index + 1) % progressInterval == 0) {
        def elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        def rate = (index + 1) / elapsed
        println "Processed ${index + 1}/${objectsToProcess.size()} (${String.format('%.1f', rate)} objects/sec)"
    }
    
    try {
        def results = extractFeatures(server, pathObject, settings, enabledFeatures)
        if (results.isEmpty()) {
            skippedCount++
            return
        }
        
        if (!firstObjectLogged) {
            println "\nFirst object features: ${results.size()}"
            results.groupBy { k, v -> k.split('_')[0] }.each { category, features ->
                println "  ${category}: ${features.size()}"
            }
            println ""
            firstObjectLogged = true
        }
        
        results.putAll(imageMeta)
        results['ObjectID'] = pathObject.getID().toString()
        results['ObjectType'] = pathObject.isDetection() ? 'Detection' : 'Annotation'
        results['Classification'] = pathObject.getPathClass()?.toString() ?: 'Unclassified'
        
        def roi = pathObject.getROI()
        if (roi != null) {
            results['Centroid_X'] = roi.getCentroidX()
            results['Centroid_Y'] = roi.getCentroidY()
        }
        
        if (addToMeasurements) {
            results.each { k, v ->
                if (v instanceof Number && !(k in ['Centroid_X', 'Centroid_Y', 'PixelWidth_um', 'PixelHeight_um'])) {
                    pathObject.measurements.put(k, v.doubleValue())
                }
            }
        }
        
        allResults.add(results)
        processedCount++
        
    } catch (Exception e) {
        skippedCount++
    }
}

if (addToMeasurements) {
    hierarchy.fireHierarchyChangedEvent(this)
}

println "\n" + "=" * 80
println "Complete"
println "=" * 80
println "Processed: ${processedCount} objects"
println "Skipped: ${skippedCount} objects"
if (objectsToProcess.size() > 0) {
    println "Success rate: ${String.format('%.1f', 100.0 * processedCount / objectsToProcess.size())}%"
}

if (allResults.isEmpty()) {
    println "\nERROR: No features extracted"
    return
}

println "\nFeature summary:"
def featuresByCategory = allResults[0].groupBy { k, v -> k.split('_')[0] }
featuresByCategory.each { category, features ->
    println "  ${category}: ${features.size()}"
}

def totalFeatures = featuresByCategory.findAll { k, v ->
    !(k in ['Image', 'ObjectID', 'ObjectType', 'Classification', 'Centroid', 'NumPixels', 'PixelWidth', 'PixelHeight'])
}.collect { k, v -> v.size() }.sum()
println "\nTotal radiomics features: ${totalFeatures}"

if (exportCSV && allResults.size() > 0) {
    println "\nExporting to CSV..."
    def outputFolder = new File(outputDir)
    if (!outputFolder.exists()) outputFolder.mkdirs()
    
    def timestamp = String.format('%tY%<tm%<td_%<tH%<tM%<tS', new Date())
    def imageName = server.getMetadata().getName().replaceAll('[^a-zA-Z0-9]', '_')
    def outputFile = writeCsv(allResults, new File(outputFolder, "${imageName}_radiomics_${timestamp}.csv"))
    writeSettingsJson(new File(outputFolder, "${imageName}_radiomics_${timestamp}_settings.json"),
        buildSettingsRecord(server, settings, enabledFeatures, allResults.size()))
    
    println "File: ${outputFile.absolutePath}"
    println "Rows: ${allResults.size()}, Columns: ${allResults[0].keySet().size()}"
}

def totalTime = (System.currentTimeMillis() - startTime) / 1000.0
println "\nTotal time: ${String.format('%.1f', totalTime)}s"
println "Processing rate: ${String.format('%.1f', processedCount / totalTime)} objects/sec"

println "\n" + "=" * 80
println "Feature classes:"
println "  First Order: 19"
println "  Shape2D: 10"
println "  GLCM: 23"
println "  GLRLM: 16"
println "  GLSZM: 16"
println "  NGTDM: 5"
println "  GLDM: 14"
println "  Shape (legacy 3D-named, optional): 16"
println "  -----------------"
println "  Default: 103 features + 9 metadata columns"
println "=" * 80
