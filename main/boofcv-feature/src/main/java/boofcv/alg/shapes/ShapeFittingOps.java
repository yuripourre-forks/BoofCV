/*
 * Copyright (c) 2021, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.shapes;

import boofcv.alg.shapes.polyline.splitmerge.PolylineSplitMerge;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU8;
import georegression.fitting.curves.ClosestPointEllipseAngle_F64;
import georegression.fitting.curves.FitEllipseAlgebraic_F64;
import georegression.fitting.curves.RefineEllipseEuclideanLeastSquares_F64;
import georegression.geometry.UtilEllipse_F64;
import georegression.struct.curve.EllipseRotated_F64;
import georegression.struct.point.Point2D_F32;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.trig.Circle2D_F64;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_F64;
import org.ddogleg.struct.DogArray_I32;

import java.util.ArrayList;
import java.util.List;

/**
 * Functions for fitting shapes to sequences of points. Points sequences are often found by computing a shape's
 * contour or edge.
 *
 * @author Peter Abeles
 * @see boofcv.alg.feature.detect.edge.CannyEdge
 * @see boofcv.alg.filter.binary.BinaryImageOps#contour(GrayU8, boofcv.struct.ConnectRule, GrayS32)
 */
public class ShapeFittingOps {

	/**
	 * <p>Fits a polygon to the provided sequence of connected points. The found polygon is returned as a list of
	 * vertices. Each point in the original sequence is guaranteed to be within "toleranceDist' of a line segment.</p>
	 *
	 * <p>Internally a split-and-merge algorithm is used. See referenced classes for more information. Consider
	 * using internal algorithms directly if this function is a performance bottleneck.</p>
	 *
	 * @param sequence Ordered and connected list of points.
	 * @param loop If true the sequence is a connected at both ends, otherwise it is assumed to not be.
	 * @param minimumSideLength The minimum allowed side length in pixels. Try 10
	 * @param cornerPenalty How much a corner is penalized. Try 0.25
	 * @return Vertexes in the fit polygon.
	 * @see PolylineSplitMerge
	 */
	public static List<PointIndex_I32> fitPolygon( List<Point2D_I32> sequence, boolean loop,
												   int minimumSideLength, double cornerPenalty ) {

		PolylineSplitMerge alg = new PolylineSplitMerge();
		alg.setLoops(loop);
		alg.setMinimumSideLength(minimumSideLength);
		alg.setCornerScorePenalty(cornerPenalty);

		alg.process(sequence);
		PolylineSplitMerge.CandidatePolyline best = alg.getBestPolyline();

		DogArray<PointIndex_I32> output = new DogArray<>(PointIndex_I32::new);
		if (best != null) {
			indexToPointIndex(sequence, best.splits, output);
		}

		return new ArrayList<>(output.toList());
	}

	/**
	 * Computes the best fit ellipse based on minimizing Euclidean distance. An estimate is initially provided
	 * using algebraic algorithm which is then refined using non-linear optimization. The amount of non-linear
	 * optimization can be controlled using 'iterations' parameter. Will work with partial and complete contours
	 * of objects.
	 *
	 * <p>NOTE: To improve speed, make calls directly to classes in Georegression. Look at the code for details.</p>
	 *
	 * @param points (Input) Set of unordered points. Not modified.
	 * @param iterations Number of iterations used to refine the fit. If set to zero then an algebraic solution
	 * is returned.
	 * @param computeError If true it will compute the average Euclidean distance error
	 * @param outputStorage (Output/Optional) Storage for the ellipse. Can be null.
	 * @return Found ellipse.
	 */
	public static FitData<EllipseRotated_F64> fitEllipse_F64( List<Point2D_F64> points, int iterations,
															  boolean computeError,
															  FitData<EllipseRotated_F64> outputStorage ) {

		if (outputStorage == null) {
			outputStorage = new FitData<>(new EllipseRotated_F64());
		}

		// Compute the optimal algebraic error
		FitEllipseAlgebraic_F64 algebraic = new FitEllipseAlgebraic_F64();

		if (!algebraic.process(points)) {
			// could be a line or some other weird case. Create a crude estimate instead
			FitData<Circle2D_F64> circleData = averageCircle_F64(points, null, null);
			Circle2D_F64 circle = circleData.shape;
			outputStorage.shape.setTo(circle.center.x, circle.center.y, circle.radius, circle.radius, 0);
		} else {
			UtilEllipse_F64.convert(algebraic.getEllipse(), outputStorage.shape);
		}

		// Improve the solution from algebraic into Euclidean
		if (iterations > 0) {
			RefineEllipseEuclideanLeastSquares_F64 leastSquares = new RefineEllipseEuclideanLeastSquares_F64();
			leastSquares.setMaxIterations(iterations);
			leastSquares.refine(outputStorage.shape, points);
			outputStorage.shape.setTo(leastSquares.getFound());
		}

		// compute the average Euclidean error if the user requests it
		if (computeError) {
			ClosestPointEllipseAngle_F64 closestPoint = new ClosestPointEllipseAngle_F64(1e-8, 100);
			closestPoint.setEllipse(outputStorage.shape);

			double total = 0;
			for (int pointIdx = 0; pointIdx < points.size(); pointIdx++) {
				Point2D_F64 p = points.get(pointIdx);
				closestPoint.process(p);
				total += p.distance(closestPoint.getClosest());
			}
			outputStorage.error = total/points.size();
		} else {
			outputStorage.error = 0;
		}

		return outputStorage;
	}

	/**
	 * Convenience function. Same as {@link #fitEllipse_F64(java.util.List, int, boolean, FitData)}, but converts the set of integer points
	 * into floating point points.
	 *
	 * @param points (Input) Set of unordered points. Not modified.
	 * @param iterations Number of iterations used to refine the fit. If set to zero then an algebraic solution
	 * is returned.
	 * @param computeError If true it will compute the average Euclidean distance error
	 * @param outputStorage (Output/Optional) Storage for the ellipse. Can be null
	 * @return Found ellipse.
	 */
	public static FitData<EllipseRotated_F64> fitEllipse_I32( List<Point2D_I32> points, int iterations,
															  boolean computeError,
															  FitData<EllipseRotated_F64> outputStorage ) {

		List<Point2D_F64> pointsF = convert_I32_F64(points);

		return fitEllipse_F64(pointsF, iterations, computeError, outputStorage);
	}

	/**
	 * Converts a list of I32 points into F64
	 *
	 * @param points Original points
	 * @return Converted points
	 */
	public static List<Point2D_F64> convert_I32_F64( List<Point2D_I32> points ) {
		return convert_I32_F64(points, null).toList();
	}

	public static DogArray<Point2D_F64> convert_I32_F64( List<Point2D_I32> input, DogArray<Point2D_F64> output ) {
		if (output == null)
			output = new DogArray<>(input.size(), Point2D_F64::new);
		else
			output.reset();

		for (int i = 0; i < input.size(); i++) {
			Point2D_I32 p = input.get(i);
			output.grow().setTo(p.x, p.y);
		}
		return output;
	}

	public static List<Point2D_F32> convert_I32_F32( List<Point2D_I32> points ) {
		return convert_I32_F32(points, null).toList();
	}

	public static DogArray<Point2D_F32> convert_I32_F32( List<Point2D_I32> input, DogArray<Point2D_F32> output ) {
		if (output == null)
			output = new DogArray<>(input.size(), Point2D_F32::new);
		else
			output.reset();

		for (int i = 0; i < input.size(); i++) {
			Point2D_I32 p = input.get(i);
			output.grow().setTo(p.x, p.y);
		}
		return output;
	}

	/**
	 * Computes a circle which has it's center at the mean position of the provided points and radius is equal to the
	 * average distance of each point from the center. While fast to compute the provided circle is not a best
	 * fit circle by any reasonable metric, except for special cases.
	 *
	 * @param points (Input) Set of unordered points. Not modified.
	 * @param optional (Optional) Used internally to store the distance of each point from the center. Can be null.
	 * @param outputStorage (Output/Optional) Storage for results. If null then a new circle instance will be returned.
	 * @return The found circle fit.
	 */
	public static FitData<Circle2D_F64> averageCircle_I32( List<Point2D_I32> points, DogArray_F64 optional,
														   FitData<Circle2D_F64> outputStorage ) {
		if (outputStorage == null) {
			outputStorage = new FitData<>(new Circle2D_F64());
		}
		if (optional == null) {
			optional = new DogArray_F64();
		}

		Circle2D_F64 circle = outputStorage.shape;

		int N = points.size();

		// find center of the circle by computing the mean x and y
		int sumX = 0, sumY = 0;
		for (int i = 0; i < N; i++) {
			Point2D_I32 p = points.get(i);
			sumX += p.x;
			sumY += p.y;
		}

		optional.reset();
		double centerX = circle.center.x = sumX/(double)N;
		double centerY = circle.center.y = sumY/(double)N;

		double meanR = 0;
		for (int i = 0; i < N; i++) {
			Point2D_I32 p = points.get(i);
			double dx = p.x - centerX;
			double dy = p.y - centerY;

			double r = Math.sqrt(dx*dx + dy*dy);
			optional.push(r);
			meanR += r;
		}
		meanR /= N;
		circle.radius = meanR;

		// compute radius variance
		double variance = 0;
		for (int i = 0; i < N; i++) {
			double diff = optional.get(i) - meanR;
			variance += diff*diff;
		}
		outputStorage.error = variance/N;

		return outputStorage;
	}

	/**
	 * Computes a circle which has it's center at the mean position of the provided points and radius is equal to the
	 * average distance of each point from the center. While fast to compute the provided circle is not a best
	 * fit circle by any reasonable metric, except for special cases.
	 *
	 * @param points (Input) Set of unordered points. Not modified.
	 * @param optional (Optional) Used internally to store the distance of each point from the center. Can be null.
	 * @param outputStorage (Output/Optional) Storage for results. If null then a new circle instance will be returned.
	 * @return The found circle fit.
	 */
	public static FitData<Circle2D_F64> averageCircle_F64( List<Point2D_F64> points, DogArray_F64 optional,
														   FitData<Circle2D_F64> outputStorage ) {
		if (outputStorage == null) {
			outputStorage = new FitData<>(new Circle2D_F64());
		}
		if (optional == null) {
			optional = new DogArray_F64();
		}

		Circle2D_F64 circle = outputStorage.shape;

		int N = points.size();

		// find center of the circle by computing the mean x and y
		double sumX = 0, sumY = 0;
		for (int i = 0; i < N; i++) {
			Point2D_F64 p = points.get(i);
			sumX += p.x;
			sumY += p.y;
		}

		optional.reset();
		double centerX = circle.center.x = sumX/(double)N;
		double centerY = circle.center.y = sumY/(double)N;

		double meanR = 0;
		for (int i = 0; i < N; i++) {
			Point2D_F64 p = points.get(i);
			double dx = p.x - centerX;
			double dy = p.y - centerY;

			double r = Math.sqrt(dx*dx + dy*dy);
			optional.push(r);
			meanR += r;
		}
		meanR /= N;
		circle.radius = meanR;

		// compute radius variance
		double variance = 0;
		for (int i = 0; i < N; i++) {
			double diff = optional.get(i) - meanR;
			variance += diff*diff;
		}
		outputStorage.error = variance/N;

		return outputStorage;
	}

	/**
	 * Converts the list of indexes in a sequence into a list of {@link PointIndex_I32}.
	 *
	 * @param sequence Sequence of points.
	 * @param indexes List of indexes in the sequence.
	 * @param output Output list of {@link PointIndex_I32}.
	 */
	public static void indexToPointIndex( List<Point2D_I32> sequence, DogArray_I32 indexes,
										  DogArray<PointIndex_I32> output ) {
		output.reset();

		for (int i = 0; i < indexes.size; i++) {
			int index = indexes.data[i];
			Point2D_I32 p = sequence.get(index);

			PointIndex_I32 o = output.grow();
			o.x = p.x;
			o.y = p.y;
			o.index = index;
		}
	}
}
