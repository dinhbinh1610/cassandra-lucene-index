/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.cassandra.lucene.schema.mapping;

import com.google.common.base.MoreObjects;
import com.spatial4j.core.shape.jts.JtsGeometry;
import com.stratio.cassandra.lucene.IndexException;
import com.stratio.cassandra.lucene.common.GeoTransformation;
import com.stratio.cassandra.lucene.util.GeospatialUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.SortField;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.composite.CompositeSpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.stratio.cassandra.lucene.util.GeospatialUtilsJTS.CONTEXT;
import static com.stratio.cassandra.lucene.util.GeospatialUtilsJTS.geometry;

/**
 * A {@link Mapper} to map geographical shapes represented according to the <a href="http://en.wikipedia.org/wiki/Well-known_text">
 * Well Known Text (WKT)</a> format. <p> This class depends on <a href="http://www.vividsolutions.com/jts">Java Topology
 * Suite (JTS)</a>. This library can't be distributed together with this project due to license compatibility problems,
 * but you can add it by putting <a href="http://search.maven.org/remotecontent?filepath=com/vividsolutions/jts-core/1.14.0/jts-core-1.14.0.jar">jts-core-1.14.0.jar</a>
 * into Cassandra lib directory. <p> Pole wrapping is not supported.
 *
 * The indexing is based on a {@link CompositeSpatialStrategy} combining a geohash search tree in front of doc values.
 * The search tree is used to quickly filtering according to a precision level, and the stored BinaryDocValues are used
 * to achieve precision discarding false positives.
 *
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */
public class GeoShapeMapper extends SingleColumnMapper<String> {

    /** The name of the mapped column. */
    public final String column;

    /** The max number of levels in the tree. */
    public final int maxLevels;

    /** The spatial strategy for radial distance searches. */
    public final SpatialStrategy strategy;

    /** The sequence of transformations to be applied to the shape before indexing. */
    public final List<GeoTransformation> transformations;

    /**
     * Builds a new {@link GeoShapeMapper}.
     *
     * @param field the name of the field
     * @param column the name of the column
     * @param validated if the field must be validated
     * @param maxLevels the maximum number of precision levels in the search tree. False positives will be discarded
     * using stored doc values, so this doesn't mean precision lost. Higher values will produce few false positives to
     * be post-filtered, at the expense of creating many terms in the search index, specially with large polygons.
     * @param transformations the sequence of operations to be applied to the indexed shapes
     */
    public GeoShapeMapper(String field,
                          String column,
                          Boolean validated,
                          Integer maxLevels,
                          List<GeoTransformation> transformations) {
        super(field, column, false, validated, null, String.class, TEXT_TYPES);

        this.column = column == null ? field : column;

        if (StringUtils.isWhitespace(column)) {
            throw new IndexException("Column must not be whitespace, but found '{}'", column);
        }

        this.maxLevels = GeospatialUtils.validateGeohashMaxLevels(maxLevels);
        SpatialPrefixTree grid = new GeohashPrefixTree(CONTEXT, this.maxLevels);

        RecursivePrefixTreeStrategy indexStrategy = new RecursivePrefixTreeStrategy(grid, field);
        SerializedDVStrategy geometryStrategy = new SerializedDVStrategy(CONTEXT, field);
        strategy = new CompositeSpatialStrategy(field, indexStrategy, geometryStrategy);

        this.transformations = transformations == null ? Collections.emptyList() : transformations;
    }

    /** {@inheritDoc} */
    @Override
    public List<IndexableField> indexableFields(String name, String value) {
        JtsGeometry shape = geometry(value);
        for (GeoTransformation transformation : transformations) {
            shape = transformation.apply(shape);
        }
        return Arrays.asList(strategy.createIndexableFields(shape));
    }

    /** {@inheritDoc} */
    @Override
    public SortField sortField(String name, boolean reverse) {
        throw new IndexException("GeoShape mapper '{}' does not support simple sorting", name);
    }

    /** {@inheritDoc} */
    @Override
    protected String doBase(String field, Object value) {
        return value.toString();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("field", field)
                          .add("column", column)
                          .add("validated", validated)
                          .add("maxLevels", maxLevels)
                          .add("transformations", transformations)
                          .toString();
    }

}
