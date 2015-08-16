/*
 * Copyright (c) 2011-2015, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.feature.detect.grid;

import boofcv.alg.feature.detect.squares.ClustersIntoGrids;
import boofcv.alg.feature.detect.squares.SquareGrid;
import boofcv.alg.feature.detect.squares.SquareNode;
import boofcv.alg.feature.detect.squares.SquaresIntoClusters;
import boofcv.alg.shapes.polygon.BinaryPolygonConvexDetector;
import boofcv.struct.image.ImageSingleBand;
import georegression.metric.Area2D_F64;
import georegression.metric.ClosestPoint2D_F64;
import georegression.metric.UtilAngle;
import georegression.struct.line.LineParametric2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;
import org.ddogleg.struct.FastQueue;

import java.util.ArrayList;
import java.util.List;

/**
 * Detect a square grid calibration target and returns the corner points of each square.  This calibration grid is
 * specified by a set of squares which are organized in a grid pattern.  All squares are the same size.  The entire
 * grid must be visible.  Space between the squares is specified as a ratio of the square size. The grid will be
 * oriented so that returned points are in clock-wise order.
 *
 * @author Peter Abeles
 */
// TODO tell the polygon detector that there should be no inner contour
public class DetectSquareGridCalibration<T extends ImageSingleBand> {

	// dimension of square grid.  This only refers to black squares and not the white space
	int numCols;
	int numRows;

	SquaresIntoClusters s2c;
	ClustersIntoGrids c2g;

	BinaryPolygonConvexDetector<T> detectorSquare;

	// output results
	List<Point2D_F64> calibrationPoints = new ArrayList<Point2D_F64>();
	int calibRows;
	int calibCols;

	/**
	 * Confiogures detector
	 *
	 * @param numCols Number of black squares in the grid columns
	 * @param numRows Number of black squares in the grid rows
	 * @param spaceToSquareRatio Ratio of spacing between the squares and the squares width
	 */
	public DetectSquareGridCalibration(int numCols, int numRows, double spaceToSquareRatio,
									   BinaryPolygonConvexDetector<T> detectorSquare ) {
		this.numCols = numCols;
		this.numRows = numRows;
		this.detectorSquare = detectorSquare;

		s2c = new SquaresIntoClusters(spaceToSquareRatio);
		c2g = new ClustersIntoGrids(numCols*numRows);
	}

	public boolean process( T image ) {
		detectorSquare.process(image);

		FastQueue<Polygon2D_F64> found = detectorSquare.getFound();

		List<List<SquareNode>> clusters = s2c.process(found.toList());
		c2g.process(clusters);
		List<SquareGrid> grids = c2g.getGrids();

		SquareGrid match = null;
		double matchSize = 0;
		for( SquareGrid g : grids ) {
			SquareGrid candidate = null;
			if (g.columns != numCols || g.rows != numRows) {
				if( g.columns == numRows && g.rows == numCols ) {
					transpose(g);
				} else {
					continue;
				}
			}

			double size = computeSize(g);
			if( size > matchSize ) {
				matchSize = size;
				match = g;
			}
		}

		if( match != null ) {
			if( checkFlip(match) ) {
				flipRows(match);
			}
			return extractCalibrationPoints(match);
		} else {
			return false;
		}
	}

	Polygon2D_F64 poly = new Polygon2D_F64(4);
	double computeSize( SquareGrid grid ) {
		poly.vertexes.data[0] = grid.get(0,0).center;
		poly.vertexes.data[1] = grid.get(0,grid.columns-1).center;
		poly.vertexes.data[2] = grid.get(grid.rows-1,grid.columns-1).center;
		poly.vertexes.data[3] = grid.get(grid.rows-1,0).center;

		return Area2D_F64.polygonConvex(poly);
	}

	boolean checkFlip( SquareGrid grid ) {
		Point2D_F64 a = grid.get(0,0).center;
		Point2D_F64 b = grid.get(0,grid.columns-1).center;
		Point2D_F64 c = grid.get(grid.rows-1,0).center;

		double angleAB = Math.atan2( b.y-a.y, b.x-a.x);
		double angleAC = Math.atan2( c.y-a.y, c.x-a.x);

		return UtilAngle.distanceCW(angleAB, angleAC) > Math.PI * 0.7;
	}

	/**
	 * Transposes the grid
	 */
	void transpose( SquareGrid grid ) {
		List<SquareNode> tmp = new ArrayList<SquareNode>();

		for (int row = 0; row < grid.rows; row++) {
			for (int col = 0; col < grid.columns; col++) {
				tmp.add( grid.nodes.get( col*grid.rows + row));
			}
		}

		grid.nodes.clear();
		grid.nodes.addAll(tmp);
	}

	/**
	 * Flips the order of rows
	 */
	void flipRows( SquareGrid grid ) {
		List<SquareNode> tmp = new ArrayList<SquareNode>();

		for (int row = 0; row < grid.rows; row++) {
			for (int col = 0; col < grid.columns; col++) {
				tmp.add( grid.nodes.get( (grid.rows - row - 1)*grid.columns + col));
			}
		}

		grid.nodes.clear();
		grid.nodes.addAll(tmp);
	}


	// local storage for extractCalibrationPoints
	LineParametric2D_F64 axisX = new LineParametric2D_F64();
	LineParametric2D_F64 axisY = new LineParametric2D_F64();
	Point2D_F64 sorted[] = new Point2D_F64[4];
	List<Point2D_F64> row0 = new ArrayList<Point2D_F64>();
	List<Point2D_F64> row1 = new ArrayList<Point2D_F64>();

	/**
	 * Converts the grid into a list of calibration points.  Uses a local axis around the square
	 * to figure out the order.  The local axis is computed from the center of the square in question and
	 * it's adjacent squares.
	 *
	 * @return true if valid grid or false if not
	 */
	boolean extractCalibrationPoints( SquareGrid grid ) {

		// the first pass interleaves every other row
		for (int row = 0; row < grid.rows; row++) {

			row0.clear();
			row1.clear();
			for (int col = 0; col < grid.columns; col++) {
				selectAxis(grid, row, col);

				Polygon2D_F64 square = grid.get(row,col).corners;
				sortCorners(square);

				for (int i = 0; i < 4; i++) {
					if( sorted[i] == null) {
						return false;
					} else {
						if( i < 2 )
							row0.add(sorted[i]);
						else
							row1.add(sorted[i]);
					}
				}
			}
			calibrationPoints.addAll(row0);
			calibrationPoints.addAll(row1);
		}

		return true;
	}

	/**
	 * Puts the corners into a specified order so that it can be placed into the grid.
	 * Uses local coordiant systems defined buy axisX and axisY.
	 */
	private void sortCorners(Polygon2D_F64 square) {
		for (int i = 0; i < 4; i++) {
			sorted[i] = null;
		}
		for (int i = 0; i < 4; i++) {
			Point2D_F64 p = square.get(i);
			double coorX = ClosestPoint2D_F64.closestPointT(axisX, p);
			double coorY = ClosestPoint2D_F64.closestPointT(axisY, p);

			if( coorX < 0 ) {
				if( coorY < 0 ) {
					sorted[0] = p;
				} else {
					sorted[2] = p;
				}
			} else {
				if( coorY < 0 ) {
					sorted[1] = p;
				} else {
					sorted[3] = p;
				}
			}
		}
	}

	/**
	 * Select the local X and Y axis around the specified grid element.
	 */
	void selectAxis( SquareGrid grid, int row , int col ) {
		int r0,r1;
		int c0,c1;

		if( row == grid.rows-1 ) {
			r0 = row-1;
			r1 = row;
		} else {
			r0 = row;
			r1 = row+1;
		}

		if( col == grid.columns-1 ) {
			c0 = col-1;
			c1 = col;
		} else {
			c0 = col;
			c1 = col+1;
		}

		Point2D_F64 c = grid.get(r0,c0).center;
		Point2D_F64 a = grid.get(r0,c1).center;
		Point2D_F64 b = grid.get(r1,c0).center;


		axisX.p.set(c);
		axisX.slope.set(a.x-c.x,a.y-c.y);
		axisY.p.set(c);
		axisY.slope.set(b.y-c.y,b.y-c.y);
	}

	public List<Point2D_F64> getCalibrationPoints() {
		return calibrationPoints;
	}

	public int getCalibRows() {
		return calibRows;
	}

	public int getCalibCols() {
		return calibCols;
	}
}
