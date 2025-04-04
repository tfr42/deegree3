/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 and
 lat/lon GmbH

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

package org.deegree.services.wms;

import static java.util.Collections.singletonList;
import static org.deegree.commons.ows.exception.OWSException.LAYER_NOT_QUERYABLE;
import static org.deegree.commons.ows.exception.OWSException.NO_APPLICABLE_CODE;
import static org.deegree.rendering.r2d.RenderHelper.calcScaleWMS130;
import static org.deegree.rendering.r2d.context.MapOptionsHelper.insertMissingOptions;
import static org.deegree.theme.Themes.getAllLayers;
import static org.slf4j.LoggerFactory.getLogger;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import javax.imageio.ImageIO;

import org.deegree.commons.ows.exception.OWSException;
import org.deegree.commons.utils.Pair;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.feature.Feature;
import org.deegree.feature.FeatureCollection;
import org.deegree.feature.Features;
import org.deegree.feature.GenericFeatureCollection;
import org.deegree.feature.types.FeatureType;
import org.deegree.filter.OperatorFilter;
import org.deegree.layer.Layer;
import org.deegree.layer.LayerData;
import org.deegree.layer.LayerQuery;
import org.deegree.layer.LayerRef;
import org.deegree.protocol.wms.filter.EnvFunction;
import org.deegree.protocol.wms.filter.ScaleFunction;
import org.deegree.protocol.wms.ops.GetFeatureInfoSchema;
import org.deegree.protocol.wms.ops.GetLegendGraphic;
import org.deegree.rendering.r2d.Copyright;
import org.deegree.rendering.r2d.context.MapOptions;
import org.deegree.rendering.r2d.context.MapOptionsMaps;
import org.deegree.rendering.r2d.context.RenderContext;
import org.deegree.services.OWS;
import org.deegree.services.jaxb.wms.CopyrightType;
import org.deegree.services.jaxb.wms.ServiceConfigurationType;
import org.deegree.services.wms.visibility.RequestedLayerVisibilityInspector;
import org.deegree.style.StyleRef;
import org.deegree.style.se.unevaluated.Style;
import org.deegree.style.utils.ImageUtils;
import org.deegree.theme.Theme;
import org.deegree.theme.Themes;
import org.deegree.theme.persistence.ThemeProvider;
import org.deegree.workspace.ResourceMetadata;
import org.deegree.workspace.Workspace;
import org.slf4j.Logger;

/**
 * <code>MapService</code>
 *
 * @author <a href="mailto:schmitz@lat-lon.de">Andreas Schmitz</a>
 */
public class MapService {

	private static final Logger LOG = getLogger(MapService.class);

	public StyleRegistry registry;

	MapOptionsMaps layerOptions = new MapOptionsMaps();

	MapOptions defaultLayerOptions;

	private int updateSequence; // TODO how to restore this after restart?

	private final String getLegendGraphicBackgroundColor;

	private List<Theme> themes;

	private HashMap<String, org.deegree.layer.Layer> newLayers;

	HashMap<String, Theme> themeMap;

	private final GetLegendHandler getLegendHandler;

	private Copyright copyright;

	private final RequestedLayerVisibilityInspector visibilityInspector;

	/**
	 * @param conf
	 * @param workspace
	 * @param metadata
	 * @param updateSequence
	 * @param getLegendGraphicBackgroundColor
	 * @throws MalformedURLException
	 */
	public MapService(ServiceConfigurationType conf, Workspace workspace, ResourceMetadata<OWS> metadata,
			int updateSequence, String getLegendGraphicBackgroundColor) throws MalformedURLException {
		this.updateSequence = updateSequence;
		this.getLegendGraphicBackgroundColor = getLegendGraphicBackgroundColor;
		this.registry = new StyleRegistry();

		MapServiceBuilder builder = new MapServiceBuilder(conf);

		defaultLayerOptions = builder.buildMapOptions();

		if (conf != null && conf.getThemeId() != null && !conf.getThemeId().isEmpty()) {
			themes = new ArrayList<Theme>();
			newLayers = new HashMap<String, org.deegree.layer.Layer>();
			themeMap = new HashMap<String, Theme>();
			for (String id : conf.getThemeId()) {
				Theme thm = workspace.getResource(ThemeProvider.class, id);
				if (thm == null) {
					LOG.warn("Theme with id {} was not available.", id);
				}
				else {
					themes.add(thm);
					themeMap.put(thm.getLayerMetadata().getName(), thm);

					for (org.deegree.layer.Layer l : Themes.getAllLayers(thm)) {
						newLayers.put(l.getMetadata().getName(), l);
					}
					for (Theme theme : Themes.getAllThemes(thm)) {
						themeMap.put(theme.getLayerMetadata().getName(), theme);
					}
				}
			}
			copyright = parseCopyright(metadata, conf.getCopyright());
		}
		getLegendHandler = new GetLegendHandler(this);

		visibilityInspector = new RequestedLayerVisibilityInspector(conf.getVisibilityInspector(), workspace);
	}

	/**
	 * @return the list of themes if configuration is based on themes, else null
	 */
	public List<Theme> getThemes() {
		return themes;
	}

	/**
	 * @param glg GetLegendGraphic, never <code>null</code>
	 * @return an empty image conforming to the request parameters
	 */
	public BufferedImage prepareImage(GetLegendGraphic glg) {
		Color bgcolor = null;
		boolean transparent = true;
		if (getLegendGraphicBackgroundColor != null) {
			bgcolor = Color.decode(getLegendGraphicBackgroundColor);
			transparent = false;
		}

		String format = glg.getFormat();
		int width = glg.getWidth();
		int height = glg.getHeight();
		return ImageUtils.prepareImage(format, width, height, transparent, bgcolor);
	}

	public boolean hasTheme(String name) {
		return themeMap.get(name) != null;
	}

	public boolean isCrsSupported(String name, ICRS requestedCrs) {
		Theme theme = themeMap.get(name);
		if (theme == null)
			return false;
		List<ICRS> supportedCrs = theme.getLayerMetadata().getSpatialMetadata().getCoordinateSystems();
		return supportedCrs.contains(requestedCrs);
	}

	public void getMap(org.deegree.protocol.wms.ops.GetMap gm, List<String> headers, RenderContext ctx)
			throws OWSException {
		Iterator<StyleRef> styleItr = gm.getStyles().iterator();
		MapOptionsMaps options = gm.getRenderingOptions();
		List<MapOptions> mapOptions = new ArrayList<MapOptions>();
		double scale = gm.getScale();

		List<LayerQuery> queries = new ArrayList<LayerQuery>();

		Iterator<LayerRef> layerItr = gm.getLayers().iterator();
		List<OperatorFilter> filters = gm.getFilters();
		Iterator<OperatorFilter> filterItr = filters == null ? null : filters.iterator();
		while (layerItr.hasNext()) {
			LayerRef lr = layerItr.next();
			StyleRef sr = styleItr.next();
			OperatorFilter f = filterItr == null ? null : filterItr.next();

			LayerQuery query = buildQuery(sr, lr, options, mapOptions, f, gm);
			if (query != null)
				queries.add(query);
		}

		ListIterator<LayerQuery> queryIter = queries.listIterator();

		ScaleFunction.getCurrentScaleValue().set(scale);
		EnvFunction.getCurrentEnvValue()
			.set(EnvFunction.parse(gm.getParameterMap(), gm.getBoundingBox(), gm.getCoordinateSystem(), gm.getWidth(),
					gm.getHeight(), scale));

		try {
			List<LayerData> layerDataList = checkStyleValidAndBuildLayerDataList(gm, headers, scale, queryIter);
			Iterator<MapOptions> optIter = mapOptions.iterator();
			for (LayerData d : layerDataList) {
				ctx.applyOptions(optIter.next());
				try {
					d.render(ctx);
				}
				catch (InterruptedException e) {
					String msg = "Request time-out.";
					throw new OWSException(msg, NO_APPLICABLE_CODE);
				}
			}
			ctx.optimizeAndDrawLabels();
			if (copyright != null) {
				ctx.paintCopyright(copyright, gm.getHeight());
			}
		}
		finally {
			ScaleFunction.getCurrentScaleValue().remove();
			EnvFunction.getCurrentEnvValue().remove();
		}
	}

	private List<LayerData> checkStyleValidAndBuildLayerDataList(org.deegree.protocol.wms.ops.GetMap gm,
			List<String> headers, double scale, ListIterator<LayerQuery> queryIter) throws OWSException {
		List<LayerData> layerDataList = new ArrayList<LayerData>();
		for (LayerRef lr : gm.getLayers()) {
			LayerQuery query = queryIter.next();
			String layerName = lr.getName();
			// List<Layer> layers = getAllLayers( themeMap.get( lr.getName() ) );
			List<Layer> layers;
			// TODO Workaround for InlineFeature
			if (lr.getName() == null && lr.getLayer() != null) {
				layers = singletonList(lr.getLayer());
			}
			else {
				layers = getAllLayers(themeMap.get(lr.getName()));
			}
			assertStyleApplicableForAtLeastOneLayer(layers, query.getStyle(), lr.getName());
			for (org.deegree.layer.Layer layer : layers) {
				if (layer.getMetadata().getScaleDenominators().first > scale
						|| layer.getMetadata().getScaleDenominators().second < scale) {
					continue;
				}
				if (!visibilityInspector.isVisible(layerName, layer.getMetadata())) {
					continue;
				}
				if (layer.isStyleApplicable(query.getStyle())) {
					layerDataList.add(layer.mapQuery(query, headers));
				}
			}
		}
		return layerDataList;
	}

	private void assertStyleApplicableForAtLeastOneLayer(List<Layer> layers, StyleRef style, String name)
			throws OWSException {
		for (Layer layer : layers) {
			if (layer.isStyleApplicable(style)) {
				return;
			}
		}
		throw new OWSException("Style " + style.getName() + " is not defined for layer " + name + ".",
				"StyleNotDefined", "styles");
	}

	private LayerQuery buildQuery(StyleRef style, LayerRef lr, MapOptionsMaps options, List<MapOptions> mapOptions,
			OperatorFilter f, org.deegree.protocol.wms.ops.GetMap gm) {
		String layerName = lr.getName();

		if (layerName == null && lr.getLayer() != null) {
			// TODO Is it required to take the Options from the map options and merge them
			// with defaultLayerOptions ?
			mapOptions.add(defaultLayerOptions);
		}
		else {
			for (org.deegree.layer.Layer l : Themes.getAllLayers(themeMap.get(layerName))) {
				insertMissingOptions(l.getMetadata().getName(), options, l.getMetadata().getMapOptions(),
						defaultLayerOptions);
				mapOptions.add(options.get(l.getMetadata().getName()));
			}
		}

		return new LayerQuery(gm.getBoundingBox(), gm.getWidth(), gm.getHeight(), style, f, gm.getParameterMap(),
				gm.getDimensions(), gm.getPixelSize(), options, gm.getQueryBox());
	}

	public FeatureCollection getFeatures(org.deegree.protocol.wms.ops.GetFeatureInfo gfi, List<String> headers)
			throws OWSException {
		List<LayerQuery> queries = prepareGetFeatures(gfi);
		List<LayerData> list = new ArrayList<LayerData>();

		double scale = calcScaleWMS130(gfi.getWidth(), gfi.getHeight(), gfi.getEnvelope(), gfi.getCoordinateSystem(),
				gfi.getPixelSize());
		ListIterator<LayerQuery> queryIter = queries.listIterator();
		for (LayerRef n : gfi.getQueryLayers()) {
			LayerQuery query = queryIter.next();
			for (org.deegree.layer.Layer l : Themes.getAllLayers(themeMap.get(n.getName()))) {
				if (l.getMetadata().getScaleDenominators().first > scale
						|| l.getMetadata().getScaleDenominators().second < scale) {
					continue;
				}

				if (!l.getMetadata().isQueryable()) {
					throw new OWSException("GetFeatureInfo is requested on a Layer (name: " + l.getMetadata().getName()
							+ ") that is not queryable.", LAYER_NOT_QUERYABLE);
				}

				list.add(l.infoQuery(query, headers));
			}
		}

		List<Feature> feats = new ArrayList<Feature>(gfi.getFeatureCount());
		for (LayerData d : list) {
			FeatureCollection col = d.info();
			if (col != null) {
				feats.addAll(col);
			}
		}

		feats = Features.clearDuplicates(feats);

		if (feats.size() > gfi.getFeatureCount()) {
			feats = feats.subList(0, gfi.getFeatureCount());
		}
		GenericFeatureCollection col = new GenericFeatureCollection();
		col.addAll(feats);
		return col;
	}

	private List<LayerQuery> prepareGetFeatures(org.deegree.protocol.wms.ops.GetFeatureInfo gfi) {
		List<LayerQuery> queries = new ArrayList<LayerQuery>();

		Iterator<LayerRef> layerItr = gfi.getQueryLayers().iterator();
		Iterator<StyleRef> styleItr = gfi.getStyles().iterator();
		List<OperatorFilter> filters = gfi.getFilters();
		Iterator<OperatorFilter> filterItr = filters == null ? null : filters.iterator();
		while (layerItr.hasNext()) {
			LayerRef lr = layerItr.next();
			StyleRef sr = styleItr.next();
			OperatorFilter f = filterItr == null ? null : filterItr.next();
			final int layerRadius = defaultLayerOptions.getFeatureInfoRadius();
			LayerQuery query = new LayerQuery(gfi.getEnvelope(), gfi.getWidth(), gfi.getHeight(), gfi.getX(),
					gfi.getY(), gfi.getFeatureCount(), f, sr, gfi.getParameterMap(), gfi.getDimensions(),
					new MapOptionsMaps(), gfi.getEnvelope(), layerRadius);
			queries.add(query);
		}
		return queries;
	}

	private void getFeatureTypes(Collection<FeatureType> types, String name) {
		for (org.deegree.layer.Layer l : Themes.getAllLayers(themeMap.get(name))) {
			types.addAll(l.getMetadata().getFeatureTypes());
		}
	}

	/**
	 * @param fis
	 * @return an application schema object
	 */
	public List<FeatureType> getSchema(GetFeatureInfoSchema fis) {
		List<FeatureType> list = new LinkedList<FeatureType>();
		for (String l : fis.getLayers()) {
			getFeatureTypes(list, l);
		}
		return list;
	}

	/**
	 * @return the style registry
	 */
	public StyleRegistry getStyles() {
		return registry;
	}

	/**
	 * @param style
	 * @return the optimal legend size
	 */
	public Pair<Integer, Integer> getLegendSize(Style style) {
		return getLegendHandler.getLegendSize(style);
	}

	public BufferedImage getLegend(GetLegendGraphic req) throws OWSException {
		return getLegendHandler.getLegend(req);
	}

	/**
	 * @return the extensions object with default extension parameter settings
	 */
	public MapOptionsMaps getExtensions() {
		return layerOptions;
	}

	/**
	 * @return the default feature info radius
	 */
	public int getGlobalFeatureInfoRadius() {
		return defaultLayerOptions.getFeatureInfoRadius();
	}

	/**
	 * @return the global max features setting
	 */
	public int getGlobalMaxFeatures() {
		return defaultLayerOptions.getMaxFeatures();
	}

	/**
	 * @return the current update sequence
	 */
	public int getCurrentUpdateSequence() {
		return updateSequence;
	}

	private Copyright parseCopyright(ResourceMetadata<OWS> metadata, CopyrightType copyright) {
		if (copyright != null) {
			String copyrightText = copyright.getText();
			BufferedImage copyrightImage = parseCopyrightImage(metadata, copyright.getImage());
			int offsetX = copyright.getOffsetX() != null ? copyright.getOffsetX() : 8;
			int offsetY = copyright.getOffsetY() != null ? copyright.getOffsetY() : 13;
			return new Copyright(copyrightText, copyrightImage, offsetX, offsetY);
		}
		return null;
	}

	private BufferedImage parseCopyrightImage(ResourceMetadata<OWS> metadata, String image) {
		if (image != null) {
			URL copyrightImageAsUrl = parseCopyrightImageAsUrl(metadata, image);
			if (copyrightImageAsUrl != null) {
				try {
					return ImageIO.read(copyrightImageAsUrl);
				}
				catch (IOException e) {
					LOG.warn("Could not read copyright as image from {}: {}", copyrightImageAsUrl, e.getMessage());
				}
			}
		}
		return null;
	}

	private URL parseCopyrightImageAsUrl(ResourceMetadata<OWS> metadata, String image) {
		URL url = metadata.getLocation().resolveToUrl(image);
		if (url != null)
			return url;
		try {
			return new URL(image);
		}
		catch (MalformedURLException e) {
			LOG.debug("Could not resolve copyright {}.", image, e);
		}
		LOG.warn("Could not resolve copyright {}.", image);
		return null;
	}

}
