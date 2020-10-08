/*
 * Copyright (c) 2020, Peter Abeles. All Rights Reserved.
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

package boofcv.struct.geo;

import boofcv.testing.BoofStandardJUnit;
import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Peter Abeles
 */
public class TestQueueMatrix extends BoofStandardJUnit {

	@Test
	public void constructor_regular() {
		QueueMatrix alg = new QueueMatrix(3,4);

		assertEquals(0,alg.size);

		DMatrixRMaj M = alg.grow();

		assertEquals(3,M.numRows);
		assertEquals(4, M.numCols);
	}

}
