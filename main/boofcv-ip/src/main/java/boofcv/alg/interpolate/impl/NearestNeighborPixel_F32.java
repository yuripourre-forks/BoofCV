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

package boofcv.alg.interpolate.impl;

import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.alg.interpolate.NearestNeighborPixelS;
import boofcv.struct.border.ImageBorder_F32;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;

import javax.annotation.Generated;


/**
 * <p>
 * Performs nearest neighbor interpolation to extract values between pixels in an image.
 * </p>
 *
 *
 * <p>DO NOT MODIFY. Automatically generated code created by GenerateNearestNeighborPixel_SB</p>
 *
 * @author Peter Abeles
 */
@Generated("boofcv.alg.interpolate.impl.GenerateNearestNeighborPixel_SB")
public class NearestNeighborPixel_F32 extends NearestNeighborPixelS<GrayF32> {

	private float[] data;
	public NearestNeighborPixel_F32() {}

	public NearestNeighborPixel_F32(GrayF32 orig) {
		setImage(orig);
	}
	@Override
	public void setImage(GrayF32 image) {
		super.setImage(image);
		this.data = orig.data;
	}

	@Override
	public float get_fast(float x, float y) {
		return data[ orig.startIndex + ((int)y)*stride + (int)x];
	}

	public float get_border(float x, float y) {
		return ((ImageBorder_F32)border).get((int)Math.floor(x),(int)Math.floor(y));
	}

	@Override
	public float get(float x, float y) {
		if (x < 0 || y < 0 || x > width-1 || y > height-1 )
			return get_border(x,y);
		int xx = (int)x;
		int yy = (int)y;

		return data[ orig.startIndex + yy*stride + xx];
	}

	@Override
	public InterpolatePixelS<GrayF32> copy() {
		NearestNeighborPixel_F32 out = new NearestNeighborPixel_F32();
		out.setBorder(border);
		return out;
	}

	@Override
	public ImageType<GrayF32> getImageType() {
		return ImageType.single(GrayF32.class);
	}
}
