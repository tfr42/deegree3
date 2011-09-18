//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
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
package org.deegree.services.csw.exporthandling;

import static org.deegree.protocol.csw.CSWConstants.CSW_202_NS;
import static org.deegree.protocol.csw.CSWConstants.CSW_PREFIX;
import static org.deegree.protocol.csw.CSWConstants.VERSION_202;
import static org.deegree.protocol.ows.exception.OWSException.NO_APPLICABLE_CODE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.axiom.om.OMElement;
import org.deegree.commons.tom.ows.Version;
import org.deegree.commons.utils.kvp.InvalidParameterValueException;
import org.deegree.commons.utils.time.DateUtils;
import org.deegree.commons.xml.XMLAdapter;
import org.deegree.commons.xml.schema.SchemaValidator;
import org.deegree.commons.xml.stax.SchemaLocationXMLStreamWriter;
import org.deegree.commons.xml.stax.TrimmingXMLStreamWriter;
import org.deegree.filter.Filter;
import org.deegree.filter.sort.SortProperty;
import org.deegree.metadata.MetadataRecord;
import org.deegree.metadata.ebrim.RegistryObject;
import org.deegree.metadata.persistence.MetadataQuery;
import org.deegree.metadata.persistence.MetadataResultSet;
import org.deegree.metadata.persistence.MetadataStore;
import org.deegree.protocol.csw.CSWConstants;
import org.deegree.protocol.csw.CSWConstants.OutputSchema;
import org.deegree.protocol.csw.CSWConstants.ResultType;
import org.deegree.protocol.csw.CSWConstants.ReturnableElement;
import org.deegree.protocol.csw.MetadataStoreException;
import org.deegree.protocol.ows.exception.OWSException;
import org.deegree.services.controller.utils.HttpResponseBuffer;
import org.deegree.services.csw.CSWController;
import org.deegree.services.csw.getrecords.GetRecords;
import org.deegree.services.csw.getrecords.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the export functionality for a {@link GetRecords} request
 * 
 * @see CSWController
 * 
 * @author <a href="mailto:thomas@lat-lon.de">Steffen Thomas</a>
 * @author last edited by: $Author: thomas $
 * 
 * @version $Revision: $, $Date: $
 */
public class GetRecordsHandler {

    private static Logger LOG = LoggerFactory.getLogger( GetRecordsHandler.class );

    private final int maxMatches;

    private final String schemaLocation;

    private final MetadataStore store;

    public GetRecordsHandler( int maxMatches, String schemaLocation, MetadataStore store ) {
        this.maxMatches = maxMatches;
        this.schemaLocation = schemaLocation;
        this.store = store;
    }

    /**
     * Preprocessing for the export of a {@link GetRecords} request
     * 
     * @param getRec
     * @param response
     * @throws XMLStreamException
     * @throws IOException
     * @throws SQLException
     * @throws OWSException
     */
    public void doGetRecords( GetRecords getRec, HttpResponseBuffer response, MetadataStore<?> store )
                            throws XMLStreamException, IOException, OWSException {

        LOG.debug( "doGetRecords: " + getRec );

        Version version = getRec.getVersion();
        response.setContentType( getRec.getOutputFormat() );

        XMLStreamWriter xmlWriter = getXMLResponseWriter( response, schemaLocation );
        try {
            export( xmlWriter, getRec, version, store );
        } catch ( OWSException e ) {
            LOG.debug( e.getMessage() );
            throw new InvalidParameterValueException( e.getMessage() );
        } catch ( MetadataStoreException e ) {
            LOG.debug( e.getMessage() );
            throw new OWSException( e.getMessage(), NO_APPLICABLE_CODE );
        }
        xmlWriter.flush();
    }

    /**
     * Exports the correct recognized request and determines to which version export it should delegate the request
     * 
     * @param xmlWriter
     * @param getRec
     * @param response
     * @param version
     * @throws XMLStreamException
     * @throws SQLException
     * @throws OWSException
     * @throws MetadataStoreException
     */
    private void export( XMLStreamWriter xmlWriter, GetRecords getRec, Version version, MetadataStore<?> store )
                            throws XMLStreamException, OWSException, MetadataStoreException {
        if ( VERSION_202.equals( version ) ) {
            export202( xmlWriter, getRec, store );
        } else {
            throw new IllegalArgumentException( "Version '" + version + "' is currently not supported." );
        }
    }

    /**
     * 
     * Exporthandling for the version 2.0.2
     * 
     * @param xmlWriter
     * @param getRec
     * @throws XMLStreamException
     * @throws SQLException
     * @throws OWSException
     * @throws MetadataStoreException
     */
    private void export202( XMLStreamWriter writer, GetRecords getRec, MetadataStore<?> store )
                            throws XMLStreamException, OWSException, MetadataStoreException {

        if ( getRec.getResultType() != ResultType.validate ) {
            writer.writeStartElement( CSW_PREFIX, "GetRecordsResponse", CSW_202_NS );
            writer.writeNamespace( CSW_PREFIX, CSW_202_NS );
            writer.writeNamespace( "xsi", "http://www.w3.org/2001/XMLSchema-instance" );
            exportSearchStatus202( writer );
            exportSearchResults202( writer, getRec, store );
        } else {
            if ( validate( getRec.getXMLRequest() ).size() != 0 ) {
                String errorMessage = "VALIDATION-ERROR: ";
                for ( String error : validate( getRec.getXMLRequest() ) ) {
                    errorMessage += error + "; \n";
                }
                throw new IllegalArgumentException( errorMessage );
            }
            writer.writeStartElement( CSW_PREFIX, "Acknowledgement", CSW_202_NS );
            writer.writeAttribute( "timeStamp", DateUtils.formatISO8601Date( new Date() ) );
            writer.writeStartElement( CSW_202_NS, "EchoedRequest" );
            readXMLFragment( getRec.getXMLRequest().toString(), writer );
            writer.writeEndElement();
            writer.writeEndElement();
        }
        writer.writeEndDocument();
    }

    private void exportSearchStatus202( XMLStreamWriter writer )
                            throws XMLStreamException {
        writer.writeStartElement( CSW_202_NS, "SearchStatus" );
        writer.writeAttribute( "timestamp", DateUtils.formatISO8601Date( new Date() ) );
        // SearchStatus
        writer.writeEndElement();
    }

    /**
     * Exports the result container that contains the requested elements
     * 
     * @param writer
     * @param getRec
     * @throws XMLStreamException
     * @throws OWSException
     * @throws MetadataStoreException
     */
    protected void exportSearchResults202( XMLStreamWriter writer, GetRecords getRec, MetadataStore<?> store )
                            throws XMLStreamException, OWSException, MetadataStoreException {

        boolean asDC = false;
        if ( OutputSchema.determineOutputSchema( OutputSchema.DC ).equals( getRec.getOutputSchema() ) ) {
            asDC = true;
        }

        ReturnableElement elementSetName = null;
        String[] returnElements = null;
        MetadataQuery query = null;

        if ( getRec.getQuery() != null ) {
            int maxRecords = maxMatches > 0 ? maxMatches : getRec.getMaxRecords();
            int startPosition = getRec.getStartPosition();
            Query cswQuery = getRec.getQuery();
            elementSetName = cswQuery.getElementSetName();
            returnElements = cswQuery.getElementName();
            Filter constraints = cswQuery.getConstraint();
            SortProperty[] sortProps = cswQuery.getSortProps();
            QName[] queryTypeNames = cswQuery.getQueryTypeNames();
            QName[] returnTypeNames = cswQuery.getReturnTypeNames();
            query = new MetadataQuery( queryTypeNames, returnTypeNames, constraints, sortProps, startPosition,
                                       maxRecords );
        } else {
            // must be an AdhocQUery
            int maxRecords = maxMatches > 0 ? maxMatches : getRec.getMaxRecords();
            int startPosition = getRec.getStartPosition();
            AdhocQueryAnalyzer fb = new AdhocQueryAnalyzer( getRec.getAdhocQuery(), startPosition, maxRecords,
                                                            (MetadataStore<RegistryObject>) store );
            query = fb.getMetadataQuery();
            elementSetName = fb.getGetRecordsQuery().getElementSetName();
            returnElements = fb.getGetRecordsQuery().getElementName();
        }

        String elementSetValue = elementSetName != null ? elementSetName.name() : "custom";

        int returnedRecords = 0;
        int counter = 0;

        boolean isResultTypeHits = getRec.getResultType().name().equals( CSWConstants.ResultType.hits.name() ) ? true
                                                                                                              : false;

        MetadataResultSet<?> rs = null;

        writer.writeStartElement( CSW_202_NS, "SearchResults" );
        try {
            if ( maxMatches <= 0 ) {
                LOG.debug( "Max matches not configured, performing 2 queries: count + data" );
                int countRows = 0;
                int nextRecord = 0;

                try {
                    countRows = store.getRecordCount( query );
                    returnedRecords = 0;
                    nextRecord = 1;
                    if ( !isResultTypeHits ) {
                        rs = store.getRecords( query );
                        returnedRecords = computeReturned( countRows, getRec.getMaxRecords(), getRec.getStartPosition() );
                        nextRecord = computeNext( countRows, getRec.getMaxRecords(), getRec.getStartPosition() );
                    }
                } catch ( MetadataStoreException e ) {
                    throw new OWSException( e.getMessage(), OWSException.INVALID_PARAMETER_VALUE );
                }

                writer.writeAttribute( "elementSet", elementSetValue );
                writer.writeAttribute( "recordSchema", getRec.getOutputSchema().toString() );
                writer.writeAttribute( "numberOfRecordsMatched", Integer.toString( countRows ) );
                writer.writeAttribute( "numberOfRecordsReturned", Integer.toString( returnedRecords ) );
                writer.writeAttribute( "nextRecord", Integer.toString( nextRecord ) );
                writer.writeAttribute( "expires", DateUtils.formatISO8601Date( new Date() ) );

                if ( rs != null ) {
                    while ( rs.next() ) {
                        if ( counter < returnedRecords ) {
                            writeRecord( writer, rs.getRecord(), asDC, elementSetName, returnElements );
                        }
                        counter++;
                    }
                }
            } else {
                LOG.debug( "Max matches configured: {}, performing single data query", maxMatches );
                rs = store.getRecords( query );
                List<MetadataRecord> records = new ArrayList<MetadataRecord>( maxMatches );
                int maxRecords = getRec.getMaxRecords();
                if ( maxMatches > 0 && maxMatches < maxRecords ) {
                    maxRecords = maxMatches;
                }
                int matches = 0;
                if ( getRec.getStartPosition() > 1 ) {
                    // lg: Why this? It's handled by SQL!?
                    // rs.skip( getRec.getStartPosition() - 1 );
                    matches += getRec.getStartPosition() - 1;
                }
                for ( int i = 0; i < maxRecords; i++ ) {
                    if ( rs.next() ) {
                        records.add( rs.getRecord() );
                    }
                }
                int remaining = rs.getRemaining();
                int nextRecord = 0;
                if ( remaining > 0 ) {
                    nextRecord = records.size() + 1;
                    if ( getRec.getStartPosition() > 1 ) {
                        nextRecord += ( getRec.getStartPosition() - 1 );
                    }
                }
                matches += records.size() + remaining;

                writer.writeAttribute( "elementSet", elementSetValue );
                writer.writeAttribute( "recordSchema", getRec.getOutputSchema().toString() );
                writer.writeAttribute( "numberOfRecordsMatched", Integer.toString( matches ) );
                writer.writeAttribute( "numberOfRecordsReturned", Integer.toString( records.size() ) );
                writer.writeAttribute( "nextRecord", Integer.toString( nextRecord ) );
                writer.writeAttribute( "expires", DateUtils.formatISO8601Date( new Date() ) );
                for ( MetadataRecord record : records ) {
                    writeRecord( writer, record, asDC, elementSetName, returnElements );
                }
            }
        } finally {
            if ( rs != null ) {
                rs.close();
            }
        }

        // SearchResult
        writer.writeEndElement();
    }

    /**
     * Computes the header-attribute <i>nextRecord</i>
     * 
     * @param countRows
     * @param max
     * @param start
     * @return a number for <i>nextRecord</i>
     */
    private int computeNext( int countRows, int max, int start ) {
        int comPre = countRows - ( start + max );
        if ( comPre < 0 ) {
            return 0;
        } else {
            return start + max;
        }
    }

    /**
     * Computes the header-attribute <i>numberOfRecordsReturned</i>
     * 
     * @param countRows
     * @param max
     * @param start
     * @return a number for <i>numberOfRecordsReturned</i>
     */
    private int computeReturned( int countRows, int max, int start ) {
        int comPre = countRows - ( start + max );
        if ( comPre >= 0 ) {
            return max;
        } else {
            int comp = countRows - ( start - 1 );
            if ( comp < 0 ) {
                comp = 0;
            }
            return comp;
        }

    }

    /**
     * Returns an <code>XMLStreamWriter</code> for writing an XML response document.
     * 
     * @param writer
     *            writer to write the XML to, must not be null
     * @param schemaLocation
     *            allows to specify a value for the 'xsi:schemaLocation' attribute in the root element, must not be null
     * @return {@link XMLStreamWriter}
     * @throws XMLStreamException
     * @throws IOException
     */
    static XMLStreamWriter getXMLResponseWriter( HttpResponseBuffer writer, String schemaLocation )
                            throws XMLStreamException, IOException {

        if ( schemaLocation == null ) {
            return writer.getXMLWriter();
        }
        XMLStreamWriter fWriter = new TrimmingXMLStreamWriter( writer.getXMLWriter() );
        return new SchemaLocationXMLStreamWriter( fWriter, schemaLocation );
    }

    /**
     * Validates the client request
     * 
     * @param elem
     * @return a list of error messages
     */
    private List<String> validate( OMElement elem ) {
        StringWriter s = new StringWriter();
        try {
            elem.serialize( s );
        } catch ( XMLStreamException e ) {
            e.printStackTrace();
        }
        InputStream is = new ByteArrayInputStream( s.toString().getBytes() );
        return SchemaValidator.validate( is, "http://schemas.opengis.net/csw/2.0.2/CSW-discovery.xsd" );

    }

    /**
     * Reads a valid XML fragment
     * 
     * @param elementString
     * @param xmlWriter
     */
    private void readXMLFragment( String elementString, XMLStreamWriter xmlWriter ) {

        StringReader r = new StringReader( elementString );

        XMLStreamReader xmlReader;
        try {
            xmlReader = XMLInputFactory.newInstance().createXMLStreamReader( r );

            // skip START_DOCUMENT
            xmlReader.nextTag();

            XMLAdapter.writeElement( xmlWriter, xmlReader );
            xmlReader.close();

        } catch ( XMLStreamException e ) {
            e.printStackTrace();
        } catch ( FactoryConfigurationError e ) {
            e.printStackTrace();
        }
    }

    private void writeRecord( XMLStreamWriter writer, MetadataRecord record, boolean asDC,
                              ReturnableElement elementSetName, String[] returnElements )
                            throws MetadataStoreException, XMLStreamException {
        MetadataRecord rec = record;
        if ( asDC ) {
            rec = rec.toDublinCore();
        }
        if ( returnElements.length > 0 ) {
            rec.serialize( writer, returnElements );
        } else {
            rec.serialize( writer, elementSetName );
        }
    }
}