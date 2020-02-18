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

package boofcv.app.uchiya;

import boofcv.alg.fiducial.dots.UchiyaMarkerGeneratorImage;
import boofcv.app.fiducials.CreateFiducialDocumentImage;
import boofcv.misc.BoofMiscOps;
import georegression.struct.point.Point2D_F64;
import lombok.Getter;

import java.util.List;

/**
 * Generates the QR Code PDF Document
 *
 * @author Peter Abeles
 */
public class CreateUchiyaDocumentImage extends CreateFiducialDocumentImage {

	List<List<Point2D_F64>> markers;
	@Getter UchiyaMarkerGeneratorImage g = new UchiyaMarkerGeneratorImage();

	public double dotDiameter;

	public CreateUchiyaDocumentImage(String documentName ) {
		super(documentName);
	}


	public void render( List<List<Point2D_F64>> markers ) {
		int numDigits = BoofMiscOps.numDigits(markers.size());
		g.configure(markerWidth,markerWidth,(int)(dotDiameter));
		g.setRadius(dotDiameter/2.0);
		for (int i = 0; i < markers.size(); i++) {
			g.render(markers.get(i),markerWidth);
			save(g.getImage(),String.format("%0"+numDigits+"d",i));
		}
	}


}
