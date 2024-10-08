/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2012 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.geometry.validation.event;

import java.util.List;

import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.patches.PolygonPatch;

/**
 * {@link GeometryValidationEvent} that indicates that a planar surface patch
 * (={@link PolygonPatch}) has two holes (interior rings) that intersect.
 *
 * @author <a href="mailto:schneider@occamlabs.de">Markus Schneider</a>
 */
public class InteriorRingsIntersect extends AbstractGeometryValidationEvent {

	private final PolygonPatch patch;

	private final int ring1Idx;

	private final int ring2Idx;

	private final Point location;

	private final boolean singlePoint;

	/**
	 * Creates a new {@link InteriorRingsIntersect} instance.
	 * @param patch affected patch, never <code>null</code>
	 * @param ring1Idx index of the first interior ring affected (starting at 0)
	 * @param ring2Idx index of the second interior ring affected (starting at 0)
	 * @param location location of the intersection, may be <code>null</code>
	 * @param singlePoint <code>true</code> if the intersection is just a single point,
	 * <code>false</code> otherwise
	 * @param geometryParticleHierarchy list of affected geometry particles (that the
	 * patch is part of), must not be <code>null</code>
	 */
	public InteriorRingsIntersect(PolygonPatch patch, int ring1Idx, int ring2Idx, Point location, boolean singlePoint,
			List<Object> geometryParticleHierarchy) {
		super(geometryParticleHierarchy);
		this.patch = patch;
		this.ring1Idx = ring1Idx;
		this.ring2Idx = ring2Idx;
		this.location = location;
		this.singlePoint = singlePoint;
	}

	/**
	 * Returns the affected {@link PolygonPatch} geometry.
	 * @return affected patch, never <code>null</code>
	 */
	public PolygonPatch getPatch() {
		return patch;
	}

	/**
	 * Returns the index of the first affected interior ring.
	 * @return index of the first affected interior ring (starting at 0)
	 */
	public int getRing1Idx() {
		return ring1Idx;
	}

	/**
	 * Returns the index of the second affected interior ring.
	 * @return index of the second affected interior ring (starting at 0)
	 */
	public int getRing2Idx() {
		return ring2Idx;
	}

	/**
	 * Returns the location of the intersection.
	 * @return location of intersection, may be <code>null</code>
	 */
	public Point getLocation() {
		return location;
	}

	/**
	 * Returns whether the intersection is just a single point.
	 * @return <code>true</code> if the intersection is just a single point,
	 * <code>false</code> otherwise
	 */
	public boolean isSinglePoint() {
		return singlePoint;
	}

}
