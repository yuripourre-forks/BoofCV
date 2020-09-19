/*
 * Copyright (c) 2011-2020, Peter Abeles. All Rights Reserved.
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

package boofcv.abst.geo.triangulate;

import boofcv.abst.geo.GeneralCheckNViewsTriangulateProjective;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point4D_F64;
import org.ejml.data.DMatrixRMaj;

import java.util.List;

/**
 * @author Peter Abeles
 */
class TestWrapNViewsTriangulateProjectiveDLT extends GeneralCheckNViewsTriangulateProjective {
	WrapNViewsTriangulateProjectiveDLT alg;

	public TestWrapNViewsTriangulateProjectiveDLT() {
		alg = new WrapNViewsTriangulateProjectiveDLT();
	}

	@Override
	public boolean triangulate( List<Point2D_F64> obsPts, List<DMatrixRMaj> motion,
								List<DMatrixRMaj> essential, Point4D_F64 found ) {
		return alg.triangulate(obsPts, motion, found);
	}
}