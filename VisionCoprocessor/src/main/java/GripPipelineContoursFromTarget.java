import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import edu.wpi.first.vision.VisionPipeline;

/**
 * GripPipelineContoursFromTarget class.
 *
 * <p>
 * An OpenCV pipeline generated by GRIP.
 *
 * @author GRIP
 */
public class GripPipelineContoursFromTarget implements VisionPipeline {
	public static class HatchVisionTarget {
		public HatchVisionTarget(RotatedRect left, RotatedRect right) {
			leftStripe = left;
			rightStripe = right;
		}
		private RotatedRect leftStripe = null;
		private RotatedRect rightStripe = null;

		public Point centerPoint() {
			return new Point((leftStripe.center.x + rightStripe.center.x) / 2.0, (leftStripe.center.y + rightStripe.center.y) / 2.0);
		}

		public double computePixelsPerInch() {
			// Compute 3 known dimensions in pixels and compare them against their known dimensions in inches.
			double yPixPerInch = (leftStripe.size.height + rightStripe.size.height) / (2.0 * STRIPE_LENGTH_IN);
			double xPixPerInch = (leftStripe.size.width  + rightStripe.size.width) / (2.0 * STRIPE_WIDTH_IN);
			double distPixPerInch = (rightStripe.center.x - leftStripe.center.x) / (STRIPE_TIP_SEPARATION_IN + STRIPE_BOTTOM_KICKOUT_IN);
			// Then average them all together
			return (yPixPerInch + xPixPerInch + distPixPerInch) / 3.0;
		}

		public double computeRangeInches(int imageWidthPx, double cameraFovWidthDeg) {
			// Ignore the effects of lens distortion
			double degreesPerPixel = cameraFovWidthDeg / imageWidthPx;
			
			double angularWidthOfTarget = Math.toRadians(rightStripe.center.x * degreesPerPixel - leftStripe.center.x * degreesPerPixel);
			// Ignore the effects of any nonzero incident angle for now
			double range = (((STRIPE_TIP_SEPARATION_IN + STRIPE_BOTTOM_KICKOUT_IN)/2.0) / Math.sin(angularWidthOfTarget / 2.0));

			return range;
		}
		public double computeBearingDegrees() {
			return 0; // TODO
		}
		public double computeIncidentAngleDegrees() {
			return 0; // TODO
		}

		public void drawOn(Mat img) {
			drawOn(img, new Scalar(255,255,255));
		}
		public void drawOn(Mat img, Scalar color) {
			LinkedList<MatOfPoint> rotboxes = new LinkedList<>();
			rotboxes.add(rrToMop(leftStripe));
			rotboxes.add(rrToMop(rightStripe));
			Imgproc.drawContours(img, rotboxes, -1, color);
			Imgproc.line(img, leftStripe.center, rightStripe.center, color);
			double avgHeight = (leftStripe.size.height + rightStripe.size.height) / 2.0;
			Imgproc.line(img, new Point(centerPoint().x, centerPoint().y - (avgHeight / 2.0)), 
			                  new Point(centerPoint().x, centerPoint().y + (avgHeight / 2.0)), color);
		}
	}

	static final double CAMERA_FOV_WIDTH_DEG = 61;//https://www.chiefdelphi.com/t/what-are-the-view-angles-of-the-microsoft-lifecam-hd-3000/149360
	// From field drawings page 143, drawing GE-19126
	static final double STRIPE_LENGTH_IN = 5.5;
	static final double STRIPE_WIDTH_IN = 2.0;
	static final double STRIPE_TIP_SEPARATION_IN = 8.0;
	static final double STRIPE_BOTTOM_KICKOUT_IN = 1.38;
	//Outputs
	private Mat blurOutput = new Mat();
	private Mat hsvThresholdOutput = new Mat();
	private ArrayList<MatOfPoint> findContoursOutput = new ArrayList<MatOfPoint>();
	private ArrayList<MatOfPoint> filterContoursOutput = new ArrayList<MatOfPoint>();
	private List<RotatedRect> rotatedBoxen = new LinkedList<>();
	private List<RotatedRect> neitherSideStripes = new LinkedList<>();
	private List<RotatedRect> leftSideStripes = new LinkedList<>();
	private List<RotatedRect> rightSideStripes = new LinkedList<>();
	private List<HatchVisionTarget> detectedTargets = new LinkedList<>();

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}

	/**
	 * Convert an OpenCV RotatedRect object, which we can't draw,
	 * to an MatOfPoint object, which we can.
	 * @param rect the rectangle to convert
	 * @return a copy of the rectangle, represented as a MatOfPoint
	 */
	public static MatOfPoint rrToMop(RotatedRect rect) {
		Point[] vertices = new Point[4];
		rect.points(vertices);
		return new MatOfPoint(vertices);
	}

	/**
	 * This is the primary method that runs the entire pipeline and updates the outputs.
	 */
	@Override	public void process(Mat source0) {
		// Step Blur0:
		Mat blurInput = source0;
		BlurType blurType = BlurType.get("Box Blur");
		double blurRadius = 2.7027027027027026;
		blur(blurInput, blurType, blurRadius, blurOutput);

		// Step HSV_Threshold0:
		Mat hsvThresholdInput = blurOutput;
		double[] hsvThresholdHue = {45.69817278554671, 93.99989504410354};
		double[] hsvThresholdSaturation = {91.72661870503596, 255.0};
		double[] hsvThresholdValue = {57.32913669064751, 255.0};
		hsvThreshold(hsvThresholdInput, hsvThresholdHue, hsvThresholdSaturation, hsvThresholdValue, hsvThresholdOutput);

		// Step Find_Contours0:
		Mat findContoursInput = hsvThresholdOutput;
		boolean findContoursExternalOnly = false;
		findContours(findContoursInput, findContoursExternalOnly, findContoursOutput);

		// Step Filter_Contours0:
		ArrayList<MatOfPoint> filterContoursContours = findContoursOutput;
		double filterContoursMinArea = 50.0;
		double filterContoursMinPerimeter = 0.0;
		double filterContoursMinWidth = 0.0;
		double filterContoursMaxWidth = 1000.0;
		double filterContoursMinHeight = 0.0;
		double filterContoursMaxHeight = 1000.0;
		double[] filterContoursSolidity = {90.28776978417267, 100.0};
		double filterContoursMaxVertices = 10000.0;
		double filterContoursMinVertices = 0.0;
		double filterContoursMinRatio = 0.0;
		double filterContoursMaxRatio = 1.0;
		filterContours(filterContoursContours, filterContoursMinArea, filterContoursMinPerimeter, filterContoursMinWidth, filterContoursMaxWidth, filterContoursMinHeight, filterContoursMaxHeight, filterContoursSolidity, filterContoursMaxVertices, filterContoursMinVertices, filterContoursMinRatio, filterContoursMaxRatio, filterContoursOutput);


		// Find rotated minimum-volume rectangles to fit all contours and filter on them
		rotatedBoxen = new LinkedList<>();
		final double nominalAspectRatio = STRIPE_LENGTH_IN / STRIPE_WIDTH_IN;
		final double minAspectRatio  = nominalAspectRatio * 0.6;
		final double maxAspectRatio  = nominalAspectRatio * 1.5;
		final double minSolidity     = 0.75;
		final double minArea         = 100.0;
		filterBoxen(findContoursOutput,  minAspectRatio, maxAspectRatio, minSolidity, minArea, rotatedBoxen);

		final double nominalAngleOffAxis = Math.toDegrees(Math.asin(STRIPE_BOTTOM_KICKOUT_IN / STRIPE_LENGTH_IN));
		final double leftStripeNominalAngle = -180 + nominalAngleOffAxis; // -165ish
		final double rightStripeNominalAngle = -0 - nominalAngleOffAxis; // -15ish
		final double angleToleranceDegVert = 10; // Angle tolerance, when rotating the stripe to be more vertical
		final double angleToleranceDegHorz = 20; // Angle tolerance, when rotating the stripe to be more horizontal
		neitherSideStripes = new LinkedList<>();
		leftSideStripes = new LinkedList<>();
		rightSideStripes = new LinkedList<>();
		classifyRectangles(rotatedBoxen, leftStripeNominalAngle - angleToleranceDegVert, leftStripeNominalAngle + angleToleranceDegHorz,
		  rightStripeNominalAngle - angleToleranceDegHorz, rightStripeNominalAngle + angleToleranceDegVert, leftSideStripes, rightSideStripes, neitherSideStripes);


		detectedTargets = findTargets(leftSideStripes, rightSideStripes);
	}

	/**
	 * This method is a generated getter for the output of a Blur.
	 * @return Mat output from Blur.
	 */
	public Mat blurOutput() {
		return blurOutput;
	}

	/**
	 * This method is a generated getter for the output of a HSV_Threshold.
	 * @return Mat output from HSV_Threshold.
	 */
	public Mat hsvThresholdOutput() {
		return hsvThresholdOutput;
	}

	/**
	 * This method is a generated getter for the output of a Find_Contours.
	 * @return ArrayList<MatOfPoint> output from Find_Contours.
	 */
	public ArrayList<MatOfPoint> findContoursOutput() {
		return findContoursOutput;
	}

	/**
	 * This method is a generated getter for the output of a Filter_Contours.
	 * @return ArrayList<MatOfPoint> output from Filter_Contours.
	 */
	public ArrayList<MatOfPoint> filterContoursOutput() {
		return filterContoursOutput;
	}

	public List<RotatedRect> getFilteredBoxes() {
		return rotatedBoxen;
	}

	public List<RotatedRect> getClassifiedLeftStripes() {
		return leftSideStripes;
	}
	public List<RotatedRect> getClassifiedRightStripes() {
		return rightSideStripes;
	}
	public List<RotatedRect> getUnclassifiedStripes() {
		return neitherSideStripes;
	}

	public List<HatchVisionTarget> getDetectedTargets() {
		return detectedTargets;
	}
	/**
	 * An indication of which type of filter to use for a blur.
	 * Choices are BOX, GAUSSIAN, MEDIAN, and BILATERAL
	 */
	enum BlurType{
		BOX("Box Blur"), GAUSSIAN("Gaussian Blur"), MEDIAN("Median Filter"),
			BILATERAL("Bilateral Filter");

		private final String label;

		BlurType(String label) {
			this.label = label;
		}

		public static BlurType get(String type) {
			if (BILATERAL.label.equals(type)) {
				return BILATERAL;
			}
			else if (GAUSSIAN.label.equals(type)) {
			return GAUSSIAN;
			}
			else if (MEDIAN.label.equals(type)) {
				return MEDIAN;
			}
			else {
				return BOX;
			}
		}

		@Override
		public String toString() {
			return this.label;
		}
	}

	/**
	 * Softens an image using one of several filters.
	 * @param input The image on which to perform the blur.
	 * @param type The blurType to perform.
	 * @param doubleRadius The radius for the blur.
	 * @param output The image in which to store the output.
	 */
	private static void blur(Mat input, BlurType type, double doubleRadius,
		Mat output) {
		int radius = (int)(doubleRadius + 0.5);
		int kernelSize;
		switch(type){
			case BOX:
				kernelSize = 2 * radius + 1;
				Imgproc.blur(input, output, new Size(kernelSize, kernelSize));
				break;
			case GAUSSIAN:
				kernelSize = 6 * radius + 1;
				Imgproc.GaussianBlur(input,output, new Size(kernelSize, kernelSize), radius);
				break;
			case MEDIAN:
				kernelSize = 2 * radius + 1;
				Imgproc.medianBlur(input, output, kernelSize);
				break;
			case BILATERAL:
				Imgproc.bilateralFilter(input, output, -1, radius, radius);
				break;
		}
	}

	/**
	 * Segment an image based on hue, saturation, and value ranges.
	 *
	 * @param input The image on which to perform the HSL threshold.
	 * @param hue The min and max hue
	 * @param sat The min and max saturation
	 * @param val The min and max value
	 * @param output The image in which to store the output.
	 */
	private static void hsvThreshold(Mat input, double[] hue, double[] sat, double[] val,
	    Mat out) {
		Imgproc.cvtColor(input, out, Imgproc.COLOR_BGR2HSV);
		Core.inRange(out, new Scalar(hue[0], sat[0], val[0]),
			new Scalar(hue[1], sat[1], val[1]), out);
	}

	/**
	 * Sets the values of pixels in a binary image to their distance to the nearest black pixel.
	 * @param input The image on which to perform the Distance Transform.
	 * @param type The Transform.
	 * @param maskSize the size of the mask.
	 * @param output The image in which to store the output.
	 */
	private static void findContours(Mat input, boolean externalOnly,
		List<MatOfPoint> contours) {
		Mat hierarchy = new Mat();
		contours.clear();
		int mode;
		if (externalOnly) {
			mode = Imgproc.RETR_EXTERNAL;
		}
		else {
			mode = Imgproc.RETR_LIST;
		}
		int method = Imgproc.CHAIN_APPROX_SIMPLE;
		Imgproc.findContours(input, contours, hierarchy, mode, method);
	}


	/**
	 * Filters out contours that do not meet certain criteria.
	 * @param inputContours is the input list of contours
	 * @param output is the the output list of contours
	 * @param minArea is the minimum area of a contour that will be kept
	 * @param minPerimeter is the minimum perimeter of a contour that will be kept
	 * @param minWidth minimum width of a contour
	 * @param maxWidth maximum width
	 * @param minHeight minimum height
	 * @param maxHeight maximimum height
	 * @param Solidity the minimum and maximum solidity of a contour
	 * @param minVertexCount minimum vertex Count of the contours
	 * @param maxVertexCount maximum vertex Count
	 * @param minRatio minimum ratio of width to height
	 * @param maxRatio maximum ratio of width to height
	 */
	private static void filterContours(List<MatOfPoint> inputContours, double minArea,
		double minPerimeter, double minWidth, double maxWidth, double minHeight, double
		maxHeight, double[] solidity, double maxVertexCount, double minVertexCount, double
		minRatio, double maxRatio, List<MatOfPoint> output) {
		final MatOfInt hull = new MatOfInt();
		output.clear();
		//operation
		for (int i = 0; i < inputContours.size(); i++) {
			final MatOfPoint contour = inputContours.get(i);
			final Rect bb = Imgproc.boundingRect(contour);
			if (bb.width < minWidth || bb.width > maxWidth) continue;
			if (bb.height < minHeight || bb.height > maxHeight) continue;
			final double area = Imgproc.contourArea(contour);
			if (area < minArea) continue;
			if (Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true) < minPerimeter) continue;
			Imgproc.convexHull(contour, hull);
			MatOfPoint mopHull = new MatOfPoint();
			mopHull.create((int) hull.size().height, 1, CvType.CV_32SC2);
			for (int j = 0; j < hull.size().height; j++) {
				int index = (int)hull.get(j, 0)[0];
				double[] point = new double[] { contour.get(index, 0)[0], contour.get(index, 0)[1]};
				mopHull.put(j, 0, point);
			}
			final double solid = 100 * area / Imgproc.contourArea(mopHull);
			if (solid < solidity[0] || solid > solidity[1]) continue;
			if (contour.rows() < minVertexCount || contour.rows() > maxVertexCount)	continue;
			final double ratio = bb.width / (double)bb.height;
			if (ratio < minRatio || ratio > maxRatio) continue;
			output.add(contour);
		}


	}

	/**
	 * 
	 * @param inputContours
	 * @param minAspectRatio minimum ratio of larger to smaller dimension - eg, a 1x3 rectangle has aspect ratio 3.
	 * @param maxAspectRatio maximum ratio of larger to smaller dimension - eg, a 1x3 rectangle has aspect ratio 3.
	 * @param minSolidity min fraction of the rectangle that is filled in by the contour
	 * @param minArea minimum area, in square pixels
	 * @param output
	 */
	private static void filterBoxen(List<MatOfPoint> inputContours,
		double minAspectRatio, double maxAspectRatio, double minSolidity, double minArea, List<RotatedRect> output) {
		output.clear();
		for (MatOfPoint contour : inputContours) {
			final double contourArea = Imgproc.contourArea(contour);

			// Compute rectangle of tightest fit
			MatOfPoint2f  contours_2f = new MatOfPoint2f( contour.toArray() );
			RotatedRect rect = Imgproc.minAreaRect(contours_2f);

			// The convention for angles coming from minAreaRect is a little odd.
			// Angle is out of the "top" of the rectangle, perpendicular to the long axis of the stripe.
			// However, it's a little arbitrary whether the long or short side is mapped to the width vs the height.
			// (It seems black-box deterministic)
			// So here we correct for that, making it so the width is always the narrow dimension.
			// That is, the equation rect.size.height > rect.size.width should always hold.
			if( rect.size.height < rect.size.width) {
				rect = new RotatedRect(rect.center, new Size(rect.size.height, rect.size.width), (rect.angle - 90));
			}
			if (rect.angle < -180) {
				rect.angle += 360;
			}


			// Big enough to be worked with?
			if(rect.size.area() < minArea) { continue; }

			// Aspect ratio within range?
			double aspectRatio = rect.size.height / rect.size.width;
			if(aspectRatio > maxAspectRatio || aspectRatio < minAspectRatio) { continue; }

			// Sufficiently filled in?
			final double cAreaToRArea = contourArea / rect.size.area();
			if(cAreaToRArea < minSolidity) { continue; }

			//System.out.println("Accepting rectangle with angle: " + rect.angle);
			
			output.add(rect);
		}
	}

	/**
	 * 
	 * In OpenCV images, note that positive X is measured rightward from the left side, and positive Y is
	 * measured downward from the top.  Therefore, 0 degrees is straight rightward, and positive angles are
	 * clockwise.
	 * 
	 * @param input a set of rectangles from minAreaRect that meet certain filtering criteria
	 * @param nominalLeftSideAngle angle in OpenCV image convention at which we expect to see the stripe above the left side of the open hatches
	 * @param nominalRightSideAngle angle in OpenCV image convention at which we expect to see the stripe above the left side of the open hatches
	 * @param angleTolerance
	 * @param leftSides the subset of input where the angle is within a range of nominalLeftSideAngle +/- angleTolerance
	 * @param rightSides the subset of input where the angle is within a range of nominalRightSideAngle +/- angleTolerance
	 */
	private static void classifyRectangles(List<RotatedRect> input,  
		double minLeftSideAngle, double maxLeftSideAngle, double minRightSideAngle, double maxRightSideAngle,
		List<RotatedRect> leftSides,  List<RotatedRect> rightSides, List<RotatedRect> neitherSideStripes) {
		leftSides.clear();
		rightSides.clear();
		for (RotatedRect rect : input) {
			if(rect.angle >= minLeftSideAngle && rect.angle < maxLeftSideAngle) {
				leftSides.add(rect);
			} else if(rect.angle >= minRightSideAngle && rect.angle < maxRightSideAngle) {
				rightSides.add(rect);
			} else {
				neitherSideStripes.add(rect);
			}
		}
	}

	public static List<HatchVisionTarget> findTargets(List<RotatedRect> leftSides,  List<RotatedRect> rightSides) {
		LinkedList<HatchVisionTarget> targets = new LinkedList<HatchVisionTarget>();

		final double VERTICAL_TOLERANCE_IN = STRIPE_LENGTH_IN * 1;
		final double HORIZONAL_TOLERANCE_IN = STRIPE_WIDTH_IN * 3;
		// For every left side stripe, see if you can find a matching right side stripe
		for (RotatedRect leftStripe : leftSides) {
			// Technically these aren't quite aligned with x or y but are 15 degrees off
			double yPixPerInch = leftStripe.size.height / STRIPE_LENGTH_IN;
			double xPixPerInch = leftStripe.size.width / STRIPE_WIDTH_IN;
			double avgPixPerInch = (yPixPerInch + xPixPerInch) / 2.0;
			// Right now we just assume the right stripe is vertically aligned with the left stripe
			double centerXOffsetPix = (STRIPE_BOTTOM_KICKOUT_IN + STRIPE_TIP_SEPARATION_IN) * avgPixPerInch;

			for(int i = 0; i < rightSides.size(); ++i) {
				RotatedRect rightStripe = rightSides.get(i);
				boolean verticalMatch = Math.abs(rightStripe.center.y - leftStripe.center.y) < (VERTICAL_TOLERANCE_IN * avgPixPerInch);
				boolean horizontalMatch =  Math.abs(rightStripe.center.x - leftStripe.center.x - centerXOffsetPix) < (HORIZONAL_TOLERANCE_IN * avgPixPerInch);
				if(horizontalMatch && verticalMatch) {
					HatchVisionTarget hvt = new HatchVisionTarget(leftStripe, rightStripe);
					targets.add(hvt);

					// Remove right stripe from future consideration
					rightSides.remove(i);
					i--;

					// Conclude our checks for this left stripe
					break;
				}
			}
		}

		return targets;
	}

	public static void main(String[] args) {

		String[] filesToProcess = {
			"test_images/Floor line/CargoAngledLine48in.jpg",
			"test_images/Floor line/CargoLine16in.jpg                                               ",
			"test_images/Floor line/CargoLine24in.jpg                                               ",
			"test_images/Floor line/CargoLine36in.jpg                                               ",
			"test_images/Floor line/CargoLine48in.jpg                                               ",
			"test_images/Floor line/CargoLine60in.jpg                                               ",
			"test_images/Occluded, single target/LoadingAngle36in.jpg                               ",
			"test_images/Occluded, single target/LoadingAngleDark36in.jpg                           ",
			"test_images/Occluded, single target/LoadingAngleDark60in.jpg                           ",
			"test_images/Occluded, single target/LoadingAngleDark96in.jpg                           ",
			"test_images/Occluded, single target/LoadingStraightDark108in.jpg                       ",
			"test_images/Occluded, single target/LoadingStraightDark10in.jpg                        ",
			"test_images/Occluded, single target/LoadingStraightDark13in.jpg                        ",
			"test_images/Occluded, single target/LoadingStraightDark21in.jpg                        ",
			"test_images/Occluded, single target/LoadingStraightDark36in.jpg                        ",
			"test_images/Occluded, single target/LoadingStraightDark48in.jpg                        ",
			"test_images/Occluded, single target/LoadingStraightDark60in.jpg                        ",
			"test_images/Occluded, single target/LoadingStraightDark84in.jpg                        ",
			"test_images/Occluded, single target/LoadingStraightDark9in.jpg                         ",
			"test_images/Occluded, two targets/CargoSideStraightDark60in.jpg                        ",
			"test_images/Occluded, two targets/CargoSideStraightDark72in.jpg                        ",
			"test_images/Unoccluded, single target/From FRC/CargoSideStraightDark36in.jpg           ",
			"test_images/Unoccluded, single target/From FRC/CargoStraightDark19in.jpg               ",
			"test_images/Unoccluded, single target/From FRC/CargoStraightDark24in.jpg               ",
			"test_images/Unoccluded, single target/From FRC/RocketBallStraightDark19in.jpg          ",
			"test_images/Unoccluded, single target/From FRC/RocketBallStraightDark24in.jpg          ",
			"test_images/Unoccluded, single target/From FRC/RocketBallStraightDark29in.jpg          ",
			"test_images/Unoccluded, single target/From FRC/RocketBallStraightDark48in.jpg          ",
			"test_images/Unoccluded, single target/From FRC/RocketPanelStraightDark12in.jpg         ",
			"test_images/Unoccluded, single target/From FRC/RocketPanelStraightDark16in.jpg         ",
			"test_images/Unoccluded, single target/From FRC/RocketPanelStraightDark24in.jpg         ",
			"test_images/Unoccluded, single target/From FRC/RocketPanelStraightDark36in.jpg         ",
			"test_images/Unoccluded, single target/Taken in classroom/19 inches.png                 ",
			"test_images/Unoccluded, single target/Taken in classroom/29 inches.png                 ",
			"test_images/Unoccluded, single target/Taken in classroom/far.png                       ",
			"test_images/Unoccluded, single target/Taken in classroom/near.png                      ",
			"test_images/Unoccluded, two targets/CargoAngledDark48in.jpg                            ",
			"test_images/Unoccluded, two targets/CargoStraightDark72in.jpg                          ",
			"test_images/Unoccluded, two targets/CargoStraightDark90in.jpg                          ",
			"test_images/Unoccluded, two targets/RocketPanelAngleDark48in.jpg                       ",
			"test_images/Unoccluded, two targets/RocketPanelAngleDark60in.jpg                       ",
			"test_images/Unoccluded, two targets/RocketPanelAngleDark84in.jpg                       ",
		};

		Scalar unfilteredContoursColor = new Scalar(0,0,255);
		Scalar filteredContoursColor = new Scalar(255, 0, 0);
		Scalar filteredRectsColor = new Scalar(0, 255, 255);
		Scalar leftStripesColor = new Scalar(0, 255, 0);
		Scalar rightStripesColor = new Scalar(0, 0, 255);
		int lineWidth = 1;
	
		GripPipelineContoursFromTarget processor = new GripPipelineContoursFromTarget();
		for (String file : filesToProcess) {
			Mat img = Imgcodecs.imread(file);
			processor.process(img);
			// Imgproc.drawContours(img, processor.findContoursOutput(), -1, unfilteredContoursColor);
			// Imgproc.drawContours(img, processor.filterContoursOutput(), -1, filteredContoursColor);
			LinkedList<MatOfPoint> rotboxes = new LinkedList<>();
			drawRotBoxes(img, processor.getUnclassifiedStripes(), filteredRectsColor, true);
			drawRotBoxes(img, processor.getClassifiedLeftStripes(), leftStripesColor, false);
			drawRotBoxes(img, processor.getClassifiedRightStripes(), rightStripesColor, false);

			for(HatchVisionTarget hvt : processor.getDetectedTargets()) {
				hvt.drawOn(img);
				System.out.println(file + " contains target at range " + hvt.computeRangeInches(img.width(), CAMERA_FOV_WIDTH_DEG));
			}

			HighGui.imshow(file, img);
			// System.out.println(file + " has " + processor.filterLines0Output().size() + " left side lines: " + processor.filterLines0Output());
			// System.out.println(file + " has " + processor.filterLines1Output().size() + " right side lines: " + processor.filterLines1Output());
		}
		HighGui.waitKey(10);
	}

	public static void drawRotBoxes(Mat img, List<RotatedRect> rects, Scalar color, boolean annotate) {
		LinkedList<MatOfPoint> rotboxes = new LinkedList<>();
		for(RotatedRect rect : rects) {
			rotboxes.add(rrToMop(rect));
			Integer angle = (int) rect.angle;
			if(annotate) {
				Imgproc.putText(img, angle.toString(), rect.center, Core.FONT_HERSHEY_SIMPLEX, 0.5, color);
				Imgproc.putText(img, String.format("%.0fx%.0f", rect.size.width, rect.size.height), new Point(rect.center.x -20, rect.center.y + 50), Core.FONT_HERSHEY_SIMPLEX, 0.5, color);
			}
		}
		Imgproc.drawContours(img, rotboxes, -1, color);

	}
}

