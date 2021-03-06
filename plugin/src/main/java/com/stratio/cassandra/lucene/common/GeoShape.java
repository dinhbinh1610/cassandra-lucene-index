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
package com.stratio.cassandra.lucene.common;

import com.google.common.base.MoreObjects;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.jts.JtsGeometry;
import com.vividsolutions.jts.geom.Geometry;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonTypeInfo;

import java.util.Arrays;
import java.util.List;

import static com.stratio.cassandra.lucene.util.GeospatialUtilsJTS.CONTEXT;
import static com.stratio.cassandra.lucene.util.GeospatialUtilsJTS.geometry;

/**
 * Class representing the transformation of a JTS geographical shape into a new shape.
 *
 * @author Andres de la Pena {@literal <adelapena@stratio.com>}
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = GeoShape.WKT.class)
@JsonSubTypes({@JsonSubTypes.Type(value = GeoShape.WKT.class, name = "wkt"),
               @JsonSubTypes.Type(value = GeoShape.BBox.class, name = "bbox"),
               @JsonSubTypes.Type(value = GeoShape.Buffer.class, name = "buffer"),
               @JsonSubTypes.Type(value = GeoShape.Centroid.class, name = "centroid"),
               @JsonSubTypes.Type(value = GeoShape.ConvexHull.class, name = "convex_hull"),
               @JsonSubTypes.Type(value = GeoShape.Difference.class, name = "difference"),
               @JsonSubTypes.Type(value = GeoShape.Intersection.class, name = "intersection"),
               @JsonSubTypes.Type(value = GeoShape.Union.class, name = "union")})
public interface GeoShape {

    /**
     * Returns the {@link JtsGeometry} resulting of applying this transformation to the specified {@link JtsGeometry}.
     *
     * @return the transformed JTS shape
     */
    JtsGeometry apply();

    /**
     * {@link GeoShape} that returns the bounding box of a JTS geographical shape. The bounding box of shape is
     * the minimal rectangle containing the shape.
     */
    class WKT implements GeoShape {

        /** The WKT shape to be added. */
        @JsonProperty("value")
        public final String value;

        /**
         * Constructor receiving a WKT string.
         *
         * @param value the geometry to be added
         */
        @JsonCreator
        public WKT(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * Returns the {@link JtsGeometry} represented by the WKT string.
         *
         * @return the shape represented by the WKT string
         */
        @Override
        public JtsGeometry apply() {
            return geometry(value);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("value", value).toString();
        }
    }

    /**
     * {@link GeoShape} that returns the bounding box of a JTS geographical shape. The bounding box of shape is
     * the minimal rectangle containing the shape.
     */
    class BBox implements GeoShape {

        /** The shape to be transformed. */
        @JsonProperty("shape")
        public final GeoShape shape;

        /**
         * Constructor receiving the {@link GeoShape} to be transformed.
         *
         * @param shape the shape to be transformed
         */
        @JsonCreator
        public BBox(@JsonProperty("shape") GeoShape shape) {
            this.shape = shape;
        }

        /**
         * Returns the bounding box of the specified {@link JtsGeometry}.
         *
         * @return the convex hull
         */
        @Override
        public JtsGeometry apply() {
            Rectangle rectangle = shape.apply().getBoundingBox();
            Geometry geometry = CONTEXT.getGeometryFrom(rectangle);
            return CONTEXT.makeShape(geometry);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).toString();
        }
    }

    /**
     * {@link GeoShape} that returns the bounding shape of a JTS geographical shape.
     */
    class Buffer implements GeoShape {

        /** The shape to be transformed. */
        @JsonProperty("shape")
        public final GeoShape shape;

        /** The max allowed distance. */
        @JsonProperty("max_distance")
        public final GeoDistance maxDistance;

        /** The min allowed distance. */
        @JsonProperty("min_distance")
        public final GeoDistance minDistance;

        /**
         * Constructor take the shape and the distance range.
         *
         * @param shape the shape to be transformed
         * @param minDistance the min allowed distance
         * @param maxDistance the max allowed distance
         */
        @JsonCreator
        public Buffer(@JsonProperty("shape") GeoShape shape,
                      @JsonProperty("min_distance") GeoDistance minDistance,
                      @JsonProperty("max_distance") GeoDistance maxDistance) {
            this.shape = shape;
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
        }

        /**
         * Returns the buffer of the specified {@link JtsGeometry}.
         *
         * @return the buffer
         */
        @Override
        public JtsGeometry apply() {

            JtsGeometry jts = shape.apply();
            JtsGeometry max = maxDistance == null
                              ? CONTEXT.makeShape(jts.getGeom())
                              : jts.getBuffered(maxDistance.getDegrees(), CONTEXT);

            if (minDistance != null) {
                JtsGeometry min = jts.getBuffered(minDistance.getDegrees(), CONTEXT);
                Geometry difference = max.getGeom().difference(min.getGeom());
                return CONTEXT.makeShape(difference);
            }
            return max;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                              .add("minDistance", minDistance)
                              .add("maxDistance", maxDistance)
                              .toString();
        }
    }

    /**
     * {@link GeoShape} that returns the center point of a JTS geographical shape.
     */
    class Centroid implements GeoShape {

        /** The shape to be transformed. */
        @JsonProperty("shape")
        public final GeoShape shape;

        /**
         * Constructor receiving the {@link GeoShape} to be transformed.
         *
         * @param shape the shape to be transformed
         */
        @JsonCreator
        public Centroid(@JsonProperty("shape") GeoShape shape) {
            this.shape = shape;
        }

        /**
         * Returns the center of the specified {@link JtsGeometry}.
         *
         * @return the center
         */
        @Override
        public JtsGeometry apply() {
            Geometry centroid = shape.apply().getGeom().getCentroid();
            return CONTEXT.makeShape(centroid);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).toString();
        }
    }

    /**
     * {@link GeoShape} that returns the convex hull of a JTS geographical shape.
     */
    class ConvexHull implements GeoShape {

        /** The shape to be transformed. */
        @JsonProperty("shape")
        public final GeoShape shape;

        /**
         * Constructor receiving the {@link GeoShape} to be transformed.
         *
         * @param shape the shape to be transformed
         */
        @JsonCreator
        public ConvexHull(@JsonProperty("shape") GeoShape shape) {
            this.shape = shape;
        }

        /**
         * Returns the convex hull of the specified {@link JtsGeometry}.
         *
         * @return the convex hull
         */
        @Override
        public JtsGeometry apply() {
            Geometry centroid = shape.apply().getGeom().convexHull();
            return CONTEXT.makeShape(centroid);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).toString();
        }
    }

    /**
     * {@link GeoShape} that returns the difference of two JTS geographical shapes.
     */
    class Difference implements GeoShape {

        /** The shapes to be subtracted. */
        @JsonProperty("shapes")
        public final List<GeoShape> shapes;

        /**
         * Constructor receiving the shapes to be subtracted.
         *
         * @param shapes the shapes to be subtracted
         */
        @JsonCreator
        public Difference(@JsonProperty("shapes") GeoShape... shapes) {
            this.shapes = Arrays.asList(shapes);
        }

        /**
         * Returns the difference of the specified shapes.
         *
         * @return the difference
         */
        @Override
        public JtsGeometry apply() {
            Geometry result = shapes.get(0).apply().getGeom();
            for (int i = 1; i < shapes.size(); i++) {
                result = result.difference(shapes.get(i).apply().getGeom());
            }
            return CONTEXT.makeShape(result);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("shapes", shapes).toString();
        }
    }

    /**
     * {@link GeoShape} that returns the intersection of two JTS geographical shapes.
     */
    class Intersection implements GeoShape {

        /** The shapes to be intersected. */
        @JsonProperty("shapes")
        public final List<GeoShape> shapes;

        /**
         * Constructor receiving the shapes to be intersected.
         *
         * @param shapes the shapes to be intersected
         */
        @JsonCreator
        public Intersection(@JsonProperty("shapes") GeoShape... shapes) {
            this.shapes = Arrays.asList(shapes);
        }

        /**
         * Returns the intersection of the specified shapes.
         *
         * @return the intersection
         */
        @Override
        public JtsGeometry apply() {
            Geometry result = shapes.get(0).apply().getGeom();
            for (int i = 1; i < shapes.size(); i++) {
                result = result.intersection(shapes.get(i).apply().getGeom());
            }
            return CONTEXT.makeShape(result);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("shapes", shapes).toString();
        }
    }

    /**
     * {@link GeoShape} that returns the union of two JTS geographical shapes.
     */
    class Union implements GeoShape {

        /** The shapes to be united. */
        @JsonProperty("shapes")
        public final List<GeoShape> shapes;

        /**
         * Constructor receiving the shapes to be united.
         *
         * @param shapes the shapes to be united
         */
        @JsonCreator
        public Union(@JsonProperty("shapes") GeoShape... shapes) {
            this.shapes = Arrays.asList(shapes);
        }

        /**
         * Returns the intersection of the specified shapes.
         *
         * @return the intersection
         */
        @Override
        public JtsGeometry apply() {
            Geometry result = shapes.get(0).apply().getGeom();
            for (int i = 1; i < shapes.size(); i++) {
                result = result.union(shapes.get(i).apply().getGeom());
            }
            return CONTEXT.makeShape(result);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("shapes", shapes).toString();
        }
    }
}
