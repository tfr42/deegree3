/*----------------    FILE HEADER  ------------------------------------------
 This file is part of deegree.
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 http://www.giub.uni-bonn.de/deegree/
 lat/lon GmbH
 http://www.lat-lon.de

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 Contact:

 Andreas Poth
 lat/lon GmbH
 Aennchenstr. 19
 53177 Bonn
 Germany
 E-Mail: poth@lat-lon.de

 Prof. Dr. Klaus Greve
 Department of Geography
 University of Bonn
 Meckenheimer Allee 166
 53115 Bonn
 Germany
 E-Mail: greve@giub.uni-bonn.de
 ---------------------------------------------------------------------------*/

package org.deegree.services.wps.ap.wcts;

import java.util.Arrays;

import org.deegree.commons.ows.exception.OWSException;
import org.deegree.commons.tom.ows.CodeType;
import org.deegree.services.wps.DefaultExceptionCustomizer;

/**
 * The <code>ExceptionCustomizer</code> class TODO add class documentation here.
 *
 * @author <a href="mailto:bezema@lat-lon.de">Rutger Bezema</a>
 *
 */
public class ExceptionCustomizer extends DefaultExceptionCustomizer {

	/**
	 * @param processId
	 */
	public ExceptionCustomizer(CodeType processId) {
		super(processId);
	}

	@Override
	public OWSException inputMissingParameter(CodeType inputParameterId, String parameter) {
		return new OWSException(
				"Missing input parameter in call to TransformCoordinates. The inputparameter: " + inputParameterId
						+ " misses following required parameter: " + parameter + ".",
				OWSException.MISSING_PARAMETER_VALUE);
	}

	@Override
	public OWSException inputMissingParameters(CodeType inputParameterId, String... parameters) {
		return new OWSException(
				"Missing input parameters in call to TransformCoordinates. The inputparameter: " + inputParameterId
						+ " misses at least one of following parameters: " + Arrays.toString(parameters) + ".",
				OWSException.MISSING_PARAMETER_VALUE);
	}

}
