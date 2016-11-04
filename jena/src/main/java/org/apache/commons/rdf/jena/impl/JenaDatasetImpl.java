/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.rdf.jena.impl;

import static org.apache.jena.graph.Node.ANY;

import java.io.StringWriter;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.jena.JenaDataset;
import org.apache.commons.rdf.jena.JenaGraph;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.GraphView;

class JenaDatasetImpl implements JenaDataset {

    private DatasetGraph graph;
    private UUID salt;
    private JenaRDF factory;

    JenaDatasetImpl(DatasetGraph graph, UUID salt) {
        this.graph = graph;
        this.salt = salt;
        this.factory = new JenaRDF(salt);
    }

    @Override
    public void add(BlankNodeOrIRI graphName, BlankNodeOrIRI subject, IRI predicate, RDFTerm object) {
        graph.add(org.apache.jena.sparql.core.Quad.create(factory.asJenaNode(graphName), factory.asJenaNode(subject),
                factory.asJenaNode(predicate), factory.asJenaNode(object)));
    }

    @Override
    public void add(Quad quad) {
        graph.add(factory.asJenaQuad(quad));
    }

    @Override
    public DatasetGraph asJenaDatasetGraph() {
        return graph;
    }

    @Override
    public void clear() {
        graph.clear();
    }

    @Override
    public void close() {
        graph.close();
    }

    @Override
    public boolean contains(Optional<BlankNodeOrIRI> graphName, BlankNodeOrIRI subject, IRI predicate, RDFTerm object) {
        return graph.contains(toJenaPattern(graphName), toJenaPattern(subject), toJenaPattern(predicate),
                toJenaPattern(object));
    }

    private Node toJenaPattern(Optional<? extends RDFTerm> graphName) {
        // In theory we could have done:
        // factory.toJena(graphName.orElse(internalJenaFactory::createAnyVariable))
        // but because of generics casting rules that doesn't work :(

        if (graphName == null) {
            return ANY;
        }
        // null: default graph
        return factory.asJenaNode(graphName.orElse(null));
    }

    private Node toJenaPattern(RDFTerm term) {
        if (term == null)
            return ANY;
        return factory.asJenaNode(term);
    }

    @Override
    public boolean contains(Quad quad) {
        return graph.contains(factory.asJenaQuad(quad));
    }

    @Override
    public void remove(Optional<BlankNodeOrIRI> graphName, BlankNodeOrIRI subject, IRI predicate, RDFTerm object) {
        graph.deleteAny(toJenaPattern(graphName), toJenaPattern(subject),
                toJenaPattern(predicate), toJenaPattern(object));
    }

    @Override
    public void remove(Quad quad) {
        graph.delete(factory.asJenaQuad(quad));
    }

    @Override
    public long size() {
        long quads = Iter.asStream(graph.listGraphNodes())
                .map(graph::getGraph)
                .collect(Collectors.summingLong(org.apache.jena.graph.Graph::size));
        return quads + graph.getDefaultGraph().size();
    }

    @Override
    public Stream<? extends Quad> stream() {
        JenaRDF factory = new JenaRDF(salt);
        return Iter.asStream(graph.find(ANY, ANY, ANY, ANY), true).map(factory::asQuad);
    }

    @Override
    public Stream<? extends Quad> stream(Optional<BlankNodeOrIRI> g, BlankNodeOrIRI s, IRI p, RDFTerm o) {
        JenaRDF factory = new JenaRDF(salt);
        return Iter.asStream(graph.find(toJenaPattern(g), toJenaPattern(s), toJenaPattern(p), toJenaPattern(o)), true)
                .map(factory::asQuad);
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        RDFDataMgr.write(sw, graph, Lang.NT);
        return sw.toString();
    }

    @Override
    public Graph getGraph() {
        GraphView g = GraphView.createDefaultGraph(graph);
        return new JenaGraphImpl(g, salt);
    }

    @Override
    public JenaGraph getUnionGraph() {
        GraphView gv = GraphView.createUnionGraph(graph);
        return new JenaGraphImpl(gv, salt);
    }

    @Override
    public Optional<Graph> getGraph(BlankNodeOrIRI graphName) {
        GraphView gv = GraphView.createNamedGraph(graph, factory.asJenaNode(graphName));
        return Optional.of(new JenaGraphImpl(gv, salt));
    }

    @Override
    public Stream<BlankNodeOrIRI> getGraphNames() {
        JenaRDF factory = new JenaRDF(salt);
        return Iter.asStream(graph.listGraphNodes()).map(node -> (BlankNodeOrIRI) factory.asRDFTerm(node));
    }

    @Override
    public Iterable<Quad> iterate() {
        final JenaRDF factory = new JenaRDF(salt);
        return Iter.asStream(graph.find(), false).map(q -> (Quad) factory.asQuad(q))::iterator;
    }

}
