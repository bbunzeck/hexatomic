/*-
 * #%L
 * org.corpus_tools.hexatomic.graph
 * %%
 * Copyright (C) 2018 - 2019 Stephan Druskat, Thomas Krause
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.corpus_tools.hexatomic.graph;

import com.google.common.collect.BiMap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.corpus_tools.salt.SALT_TYPE;
import org.corpus_tools.salt.common.SDocumentGraph;
import org.corpus_tools.salt.common.STextualDS;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.core.SNode;
import org.corpus_tools.salt.core.SRelation;
import org.corpus_tools.salt.graph.IdentifiableElement;
import org.corpus_tools.salt.util.DataSourceSequence;
import org.eclipse.zest.layouts.algorithms.AbstractLayoutAlgorithm;
import org.eclipse.zest.layouts.dataStructures.InternalNode;
import org.eclipse.zest.layouts.dataStructures.InternalRelationship;

public class SaltGraphLayout extends AbstractLayoutAlgorithm {

  private static class RankSubrank implements Comparable<RankSubrank> {
    private final int rank;
    private final int subrank;

    public RankSubrank(int rank, int subrank) {
      this.rank = rank;
      this.subrank = subrank;
    }

    @Override
    public int hashCode() {
      return Objects.hash(rank, subrank);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      RankSubrank other = (RankSubrank) obj;
      return rank == other.rank && subrank == other.subrank;
    }

    @Override
    public int compareTo(RankSubrank o) {
      return ComparisonChain.start().compare(this.rank, o.rank).compare(this.subrank, o.subrank)
          .result();
    }

    @Override
    public String toString() {
      return "" + this.rank + "." + this.subrank;
    }
  }

  private double averageTokenNodeWidth;
  private double maxNodeHeight;

  private final double percentMargin = 1.8;

  private BiMap<InternalNode, SNode> nodes;
  private BiMap<InternalRelationship, SRelation<?, ?>> relations;


  public SaltGraphLayout(int styles) {
    super(styles);
  }



  @Override
  public void setLayoutArea(double x, double y, double width, double height) {
    throw new RuntimeException("Operation not implemented");
  }

  @Override
  protected boolean isValidConfiguration(boolean asynchronous, boolean continuous) {
    return true;
  }

  @Override
  protected void applyLayoutInternal(InternalNode[] entitiesToLayout,
      InternalRelationship[] relationshipsToConsider, double boundsX, double boundsY,
      double boundsWidth, double boundsHeight) {
    
    fireProgressStarted(4);
    
    List<SToken> tokens = new LinkedList<>();

    fireProgressEvent(0, 4);
    
    // 1. Assign an initial rank to each non-token
    Map<InternalNode, Integer> rankForNode = new HashMap<>();

    // Get all nodes that are root nodes when only including the considered relations
    for (SNode n : this.nodes.values()) {
      if (n instanceof SToken) {
        tokens.add((SToken) n);
      } else {
        boolean isRoot = true;
        for (SRelation<?, ?> rel : n.getInRelations()) {
          if (this.relations.containsValue(rel)) {
            isRoot = false;
            continue;
          }
        }
        if (isRoot) {
          assignRankRecursivly(this.nodes.inverse().get(n), rankForNode, 0);
        }
      }
    }
    // Group nodes with the same rank
    Multimap<Integer, InternalNode> nodesByRank = HashMultimap.create();
    for (Map.Entry<InternalNode, Integer> entry : rankForNode.entrySet()) {
      nodesByRank.put(entry.getValue(), entry.getKey());
    }
    
    fireProgressEvent(1, 4);

    // 2. Check if we can merge nodes to the same rank if they don't cover the same token
    LinkedHashMap<InternalNode, RankSubrank> mergedRanks = new LinkedHashMap<>();
    for (Integer rank : new LinkedList<>(nodesByRank.keySet())) {
      Collection<InternalNode> sameRankNodes = nodesByRank.get(rank);
      // We start with a given sub rank and a non-occupied text range
      int subrank = 0;
      Map<STextualDS, BitSet> occupiedByText = new HashMap<>();
      for (InternalNode internal : sameRankNodes) {
        SNode n = this.nodes.get(internal);
        if (n != null && n.getGraph() instanceof SDocumentGraph) {
          SDocumentGraph graph = (SDocumentGraph) n.getGraph();
          @SuppressWarnings("rawtypes")
          List<DataSourceSequence> sequences =
              graph.getOverlappedDataSourceSequence(n, SALT_TYPE.STEXT_OVERLAPPING_RELATION);
          if (sequences != null) {

            // check if any overlapped sequence is already occupied by another node in this rank
            boolean overlapsExisting = false;
            for (DataSourceSequence<?> s : sequences) {

              if (s.getDataSource() instanceof STextualDS) {
                BitSet alreadyOccupied = occupiedByText.get(s.getDataSource());
                if (alreadyOccupied == null) {
                  alreadyOccupied = new BitSet();
                  occupiedByText.put((STextualDS) s.getDataSource(), alreadyOccupied);
                }
                BitSet occupiedBySequence = new BitSet();
                occupiedBySequence.set(s.getStart().intValue(), s.getEnd().intValue());
                if (alreadyOccupied.intersects(occupiedBySequence)) {
                  overlapsExisting = true;
                  break;
                }
              }
            }

            if (overlapsExisting) {
              // assign a new sub-rank to this node
              subrank++;

              // occupied needs to be updated to include the current node only (and forget the
              // previous occupancy)
              occupiedByText.clear();
            }
          }

          // update the occupied region to include the new sequences
          for (DataSourceSequence<?> s : sequences) {
            if (s.getDataSource() instanceof STextualDS) {
              BitSet occupied = occupiedByText.get(s.getDataSource());
              if (occupied == null) {
                occupied = new BitSet();
                occupiedByText.put((STextualDS) s.getDataSource(), occupied);
              }
              occupied.set(s.getStart().intValue(), s.getEnd().intValue());
            }
          }

          // remember the generated rank and sub-rank
          mergedRanks.put(internal, new RankSubrank(rank, subrank));
        }
      }
    }

    // re-create the ranks map but include the merged sub-ranks
    rankForNode.clear();
    int flattenedRank = 0;
    RankSubrank previousRank = null;
    // Because mergedRanks is a linked hash map and the insertion order was by the original rank,
    // the iteration will be sorted by the original rank value (smallest rank first)
    for (Map.Entry<InternalNode, RankSubrank> e : mergedRanks.entrySet()) {
      InternalNode internal = e.getKey();
      RankSubrank currentRank = e.getValue();

      if (previousRank != null && !currentRank.equals(previousRank)) {
        // add a new global (or flattened) rank
        flattenedRank++;
      }

      rankForNode.put(internal, flattenedRank);

      previousRank = currentRank;
    }
    
    fireProgressEvent(2, 4);

    // 3. layout tokens and put them one rank below the number of ranks
    layoutTokenOrder(tokens, boundsX, boundsY, flattenedRank + 1);
    fireProgressEvent(3, 4);

    // 4. assign position based on (sub-) rank and the covered tokens
    for (Map.Entry<InternalNode, Integer> e : rankForNode.entrySet()) {
      int rank = e.getValue();
      InternalNode node = e.getKey();
      SNode saltNode = this.nodes.get(node);
      // Get covered tokens to get the x position
      if (saltNode.getGraph() instanceof SDocumentGraph) {
        SDocumentGraph graph = (SDocumentGraph) saltNode.getGraph();
        List<SToken> overlappedTokens = graph.getOverlappedTokens(saltNode);
        Range<Double> range = getTokenRange(overlappedTokens);

        double x = 0.0;
        if (range.hasLowerBound()) {
          if (range.hasUpperBound()) {
            // position at the center
            x = range.lowerEndpoint() + (range.upperEndpoint() - range.lowerEndpoint()) / 2.0;
          } else {
            // position left-aligned
            x = range.lowerEndpoint();
          }
        }
        node.setInternalLocation(boundsX + x,
            boundsY + (rank * (this.maxNodeHeight * this.percentMargin)));

      }
    }
    
    updateLayoutLocations(entitiesToLayout);
    fireProgressEnded(4);

  }

  private Range<Double> getTokenRange(List<SToken> tokens) {

    Optional<Double> minX = Optional.empty();
    Optional<Double> maxX = Optional.empty();

    for (SToken t : tokens) {
      InternalNode internalTokenNode = this.nodes.inverse().get(t);
      if (internalTokenNode != null) {
        if (minX.isPresent()) {
          minX = Optional.of(Math.min(internalTokenNode.getInternalX(), minX.get()));
        } else {
          minX = Optional.of(internalTokenNode.getInternalX());
        }

        if (maxX.isPresent()) {
          maxX = Optional.of(Math.max(
              internalTokenNode.getInternalX() + internalTokenNode.getInternalWidth(), maxX.get()));
        } else {
          maxX =
              Optional.of(internalTokenNode.getInternalX() + internalTokenNode.getInternalWidth());
        }
      }
    }


    if (!minX.isPresent()) {
      minX = Optional.of(0.0);
    }
    if (maxX.isPresent()) {
      return Range.closed(minX.get(), maxX.get());
    } else {
      return Range.atLeast(minX.get());
    }
  }

  private void assignRankRecursivly(InternalNode node, Map<InternalNode, Integer> ranks, int rank) {
    if (node == null || ranks.containsKey(node)) {
      return;
    }

    SNode saltNode = this.nodes.get(node);
    if (saltNode == null || saltNode instanceof SToken) {
      return;
    }

    ranks.putIfAbsent(node, rank);

    for (SRelation<?, ?> rel : saltNode.getOutRelations()) {
      if (this.relations.values().contains(rel)) {
        InternalNode outNode = this.nodes.inverse().get(rel.getTarget());
        if (outNode != null) {
          assignRankRecursivly(outNode, ranks, rank + 1);
        }
      }
    }


  }


  private void layoutTokenOrder(Collection<SToken> tokens, double boundsX, double boundsY,
      int tokenRank) {
    double x = boundsX;

    // Sort tokens
    if (!tokens.isEmpty()) {
      SDocumentGraph docGraph = tokens.iterator().next().getGraph();
      List<SToken> sortedTokens = docGraph.getSortedTokenByText(new LinkedList<SToken>(tokens));
      for (SToken t : sortedTokens) {
        InternalNode n = this.nodes.inverse().get(t);
        if (n != null) {
          n.setInternalLocation(x,
              boundsY + (tokenRank * (this.maxNodeHeight * this.percentMargin)));
          x += this.averageTokenNodeWidth / 10.0;
          x += n.getLayoutEntity().getWidthInLayout();
        }
      }
    }
  }

  @Override
  protected void preLayoutAlgorithm(InternalNode[] entitiesToLayout,
      InternalRelationship[] relationshipsToConsider, double x, double y, double width,
      double height) {

    // map all internal nodes to their Salt entity
    this.nodes = HashBiMap.create();
    for (InternalNode n : entitiesToLayout) {
      IdentifiableElement saltElement = SaltGraphContentProvider.getData(n);
      if (saltElement instanceof SNode) {
        this.nodes.put(n, (SNode) saltElement);
      }
    }
    this.relations = HashBiMap.create();
    for (InternalRelationship rel : relationshipsToConsider) {
      IdentifiableElement saltElement = SaltGraphContentProvider.getData(rel);
      if (saltElement instanceof SRelation<?, ?>) {
        this.relations.put(rel, (SRelation<?, ?>) saltElement);
      }
    }

    this.maxNodeHeight = 0;
    // Calculate the average width and height to get a good distance between the tokens
    double sumWidth = 0.0;
    int tokenCount = 0;
    for (int index = 0; index < entitiesToLayout.length; index++) {
      InternalNode n = entitiesToLayout[index++];
      IdentifiableElement element = SaltGraphContentProvider.getData(n);
      if (element instanceof SToken) {
        sumWidth += n.getLayoutEntity().getWidthInLayout();
        tokenCount++;
      } else {
        // Find the maximum existing Y: all tokens should be located at the bottom of the graph
        this.maxNodeHeight = Math.max(this.maxNodeHeight, n.getLayoutEntity().getHeightInLayout());
      }
    }

    this.averageTokenNodeWidth = sumWidth / (double) tokenCount;

  }

  @Override
  protected void postLayoutAlgorithm(InternalNode[] entitiesToLayout,
      InternalRelationship[] relationshipsToConsider) {

  }

  @Override
  protected int getTotalNumberOfLayoutSteps() {
    return 4;
  }

  @Override
  protected int getCurrentLayoutStep() {
    return 0;
  }


}
