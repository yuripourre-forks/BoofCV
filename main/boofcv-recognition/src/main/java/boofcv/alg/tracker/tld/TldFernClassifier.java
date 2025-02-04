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

package boofcv.alg.tracker.tld;

import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.struct.ImageRectangle;
import boofcv.struct.image.ImageGray;
import georegression.struct.point.Point2D_F32;

import java.util.Random;

/**
 * Manages ferns, creates their descriptions, compute their values, and handles their probabilities.
 *
 * @author Peter Abeles
 */
public class TldFernClassifier<T extends ImageGray<T>> {

	// maximum value of a fern for P and N. Used for re-normalization
	protected int maxP = 0;
	protected int maxN = 0;

	// random number generator used while learning
	private Random rand;
	// number of random ferns it learns
	private int numLearnRandom;
	// standard deviation of noise used while learning
	private float fernLearnNoise;

	// List of randomly generated ferns
	protected TldFernDescription[] ferns;
	// used to look up fern values
	protected TldFernManager[] managers;

	// provides sub-pixel interpolation to improve quality at different scales
	private InterpolatePixelS<T> interpolate;

	/**
	 * Configures fern algorithm
	 *
	 * @param rand Random number generated used for creating ferns
	 * @param numFerns Number of ferns to created. Typically 10
	 * @param descriptorSize Size of each fern's descriptor. Typically 10
	 * @param numLearnRandom Number of ferns which will be generated by adding noise to the image
	 * @param fernLearnNoise The noise's standard deviation
	 * @param interpolate Interpolation function for the image
	 */
	public TldFernClassifier( Random rand, int numFerns, int descriptorSize,
							  int numLearnRandom, float fernLearnNoise,
							  InterpolatePixelS<T> interpolate ) {

		this.rand = rand;
		this.interpolate = interpolate;
		this.numLearnRandom = numLearnRandom;
		this.fernLearnNoise = fernLearnNoise;

		ferns = new TldFernDescription[numFerns];
		managers = new TldFernManager[numFerns];

		// create random ferns
		for (int i = 0; i < numFerns; i++) {
			ferns[i] = new TldFernDescription(rand, descriptorSize);
			managers[i] = new TldFernManager(descriptorSize);
		}
	}

	protected TldFernClassifier() {
	}

	/**
	 * Discard all information on fern values and their probabilities
	 */
	public void reset() {
		for (int i = 0; i < managers.length; i++)
			managers[i].reset();
	}

	/**
	 * Call before any other functions. Provides the image that is being sampled.
	 *
	 * @param gray Input image.
	 */
	public void setImage( T gray ) {
		interpolate.setImage(gray);
	}

	/**
	 * Learns a fern from the specified region. No noise is added.
	 */
	public void learnFern( boolean positive, ImageRectangle r ) {

		float rectWidth = r.getWidth();
		float rectHeight = r.getHeight();

		float c_x = r.x0 + (rectWidth - 1)/2f;
		float c_y = r.y0 + (rectHeight - 1)/2f;

		for (int i = 0; i < ferns.length; i++) {

			// first learn it with no noise
			int value = computeFernValue(c_x, c_y, rectWidth, rectHeight, ferns[i]);
			TldFernFeature f = managers[i].lookupFern(value);
			increment(f, positive);
		}
	}

	/**
	 * Computes the value for each fern inside the region and update's their P and N value. Noise is added
	 * to the image measurements to take in account the variability.
	 */
	public void learnFernNoise( boolean positive, ImageRectangle r ) {

		float rectWidth = r.getWidth();
		float rectHeight = r.getHeight();

		float c_x = r.x0 + (rectWidth - 1)/2.0f;
		float c_y = r.y0 + (rectHeight - 1)/2.0f;

		for (int i = 0; i < ferns.length; i++) {

			// first learn it with no noise
			int value = computeFernValue(c_x, c_y, rectWidth, rectHeight, ferns[i]);
			TldFernFeature f = managers[i].lookupFern(value);
			increment(f, positive);

			for (int j = 0; j < numLearnRandom; j++) {
				value = computeFernValueRand(c_x, c_y, rectWidth, rectHeight, ferns[i]);
				f = managers[i].lookupFern(value);
				increment(f, positive);
			}
		}
	}

	/**
	 * Increments the P and N value for a fern. Also updates the maxP and maxN statistics so that it
	 * knows when to re-normalize data structures.
	 */
	private void increment( TldFernFeature f, boolean positive ) {
		if (positive) {
			f.incrementP();
			if (f.numP > maxP)
				maxP = f.numP;
		} else {
			f.incrementN();
			if (f.numN > maxN)
				maxN = f.numN;
		}
	}

	/**
	 * For the specified regions, computes the values of each fern inside of it and then retrives their P and N values.
	 * The sum of which is stored inside of info.
	 *
	 * @param info (Input) Location/Rectangle (output) P and N values
	 * @return true if a known value for any of the ferns was observed in this region
	 */
	public boolean lookupFernPN( TldRegionFernInfo info ) {

		ImageRectangle r = info.r;

		float rectWidth = r.getWidth();
		float rectHeight = r.getHeight();

		float c_x = r.x0 + (rectWidth - 1)/2.0f;
		float c_y = r.y0 + (rectHeight - 1)/2.0f;

		int sumP = 0;
		int sumN = 0;

		for (int i = 0; i < ferns.length; i++) {
			TldFernDescription fern = ferns[i];

			int value = computeFernValue(c_x, c_y, rectWidth, rectHeight, fern);

			TldFernFeature f = managers[i].table[value];
			if (f != null) {
				sumP += f.numP;
				sumN += f.numN;
			}
		}

		info.sumP = sumP;
		info.sumN = sumN;

		return sumN != 0 || sumP != 0;
	}

	/**
	 * Computes the value of the specified fern at the specified location in the image.
	 */
	protected int computeFernValue( float c_x, float c_y, float rectWidth, float rectHeight, TldFernDescription fern ) {

		rectWidth -= 1;
		rectHeight -= 1;

		int desc = 0;
		for (int i = 0; i < fern.pairs.length; i++) {
			Point2D_F32 p_a = fern.pairs[i].a;
			Point2D_F32 p_b = fern.pairs[i].b;

			float valA = interpolate.get_fast(c_x + p_a.x*rectWidth, c_y + p_a.y*rectHeight);
			float valB = interpolate.get_fast(c_x + p_b.x*rectWidth, c_y + p_b.y*rectHeight);

			desc *= 2;

			if (valA < valB) {
				desc += 1;
			}
		}

		return desc;
	}

	/**
	 * Computes the value of a fern after adding noise to the image being sampled.
	 */
	protected int computeFernValueRand( float c_x, float c_y, float rectWidth, float rectHeight, TldFernDescription fern ) {

		rectWidth -= 1;
		rectHeight -= 1;

		int desc = 0;
		for (int i = 0; i < fern.pairs.length; i++) {
			Point2D_F32 p_a = fern.pairs[i].a;
			Point2D_F32 p_b = fern.pairs[i].b;

			float valA = interpolate.get_fast(c_x + p_a.x*rectWidth, c_y + p_a.y*rectHeight);
			float valB = interpolate.get_fast(c_x + p_b.x*rectWidth, c_y + p_b.y*rectHeight);

			valA += (float)rand.nextGaussian()*fernLearnNoise;
			valB += (float)rand.nextGaussian()*fernLearnNoise;

			desc *= 2;

			if (valA < valB) {
				desc += 1;
			}
		}

		return desc;
	}

	/**
	 * Renormalizes fern.numP to avoid overflow
	 */
	public void renormalizeP() {
		int targetMax = maxP/20;

		for (int i = 0; i < managers.length; i++) {
			TldFernManager m = managers[i];
			for (int j = 0; j < m.table.length; j++) {
				TldFernFeature f = m.table[j];
				if (f == null)
					continue;
				f.numP = targetMax*f.numP/maxP;
			}
		}
		maxP = targetMax;
	}

	/**
	 * Renormalizes fern.numN to avoid overflow
	 */
	public void renormalizeN() {
		int targetMax = maxN/20;

		for (int i = 0; i < managers.length; i++) {
			TldFernManager m = managers[i];
			for (int j = 0; j < m.table.length; j++) {
				TldFernFeature f = m.table[j];
				if (f == null)
					continue;
				f.numN = targetMax*f.numN/maxN;
			}
		}
		maxN = targetMax;
	}

	public int getMaxP() {
		return maxP;
	}

	public int getMaxN() {
		return maxN;
	}
}
