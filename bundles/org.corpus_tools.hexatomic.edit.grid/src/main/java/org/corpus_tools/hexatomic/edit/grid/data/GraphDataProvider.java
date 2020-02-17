/*-
 * #%L
 * org.corpus_tools.hexatomic.edit.grid
 * %%
 * Copyright (C) 2018 - 2020 Stephan Druskat,
 *                                     Thomas Krause
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

package org.corpus_tools.hexatomic.edit.grid.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.corpus_tools.salt.SALT_TYPE;
import org.corpus_tools.salt.common.SDocumentGraph;
import org.corpus_tools.salt.common.SSpan;
import org.corpus_tools.salt.common.SStructuredNode;
import org.corpus_tools.salt.common.STextualDS;
import org.corpus_tools.salt.common.STextualRelation;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.core.SAnnotation;
import org.corpus_tools.salt.core.SRelation;
import org.corpus_tools.salt.util.DataSourceSequence;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;

/**
 * Enables the use of an {@link SDocumentGraph} as a data source for the {@link NatTable}.
 * 
 * @author Stephan Druskat (mail@sdruskat.net)
 *
 */
public class GraphDataProvider implements IDataProvider {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(GraphDataProvider.class);

  private STextualDS ds = null;
  private final SDocumentGraph graph;
  private final List<SToken> orderedDsTokens = new ArrayList<SToken>();
  private final List<SSpan> dsSpans = new ArrayList<SSpan>();

  // Alphabetically ordered maps of column titles to columns for the two annotated types
  private final TreeMap<String, Column> tokenColumns = new TreeMap<>();
  private final TreeMap<String, Column> spanColumns = new TreeMap<>();
  // To be compiled from the two tree sets, tokens first, then spans
  private final List<Column> columns = new ArrayList<>();



  /**
   * Initializes the graph to grid resolution.
   * 
   * @param graph The graph data source
   */
  public GraphDataProvider(SDocumentGraph graph) {
    this.graph = graph;
  }

  private void resolveGraph() {
    // Reset data
    orderedDsTokens.clear();
    dsSpans.clear();
    tokenColumns.clear();
    spanColumns.clear();
    columns.clear();

    log.debug("Starting to resolve SDocumentGraph of {} for data source {}.", graph.getDocument(),
        ds);
    // Resolve which tokens should be taken into account based on ds and save to dsTokens.
    List<SToken> unorderedTokens = new ArrayList<SToken>();
    for (SRelation<?, ?> inRel : ds.getInRelations()) {
      if (inRel instanceof STextualRelation) {
        // Source can only be token
        unorderedTokens.add((SToken) inRel.getSource());
      }
    }
    orderedDsTokens.addAll(graph.getSortedTokenByText(unorderedTokens));

    // Resolve which spans should be taken into account based on ds.
    for (SSpan span : graph.getSpans()) {
      List<DataSourceSequence> overlappedSequences =
          graph.getOverlappedDataSourceSequence(span, SALT_TYPE.STEXT_OVERLAPPING_RELATION);
      for (DataSourceSequence seq : overlappedSequences) {
        if (seq.getDataSource() == ds) {
          dsSpans.add(span);
        }
      }
    }

    // Add a column for the token text as the first column
    Column tokenColumn = new Column();
    tokenColumn.setTitle("Token");
    for (int i = 0; i < orderedDsTokens.size(); i++) {
      SToken token = orderedDsTokens.get(i);
      try {
        tokenColumn.setRow(i, token);
      } catch (RuntimeException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    columns.add(tokenColumn);

    resolveTokenAnnotations(orderedDsTokens);

    // Count span annotations and add to column count
    // resolveAnnotations(new ArrayList<SStructuredNode>(dsSpans));
    resolveSpanAnnotations(dsSpans, orderedDsTokens);

    // Complete the list of columns
    // Order is kept correctly, because TreeMap.values()' iterator returns the values in
    // ascending order of the corresponding keys, i.e., the collection of values is sorted.
    columns.addAll(tokenColumns.values());
    columns.addAll(spanColumns.values());
    log.debug("Finished resolving SDocumentGraph of {}.", graph.getDocument());
  }

  private void resolveSpanAnnotations(List<SSpan> spans, List<SToken> orderedTokens) {
    // TODO Try both ways: get all spans for token AND get tokens for span
    for (SSpan span : spans) {
      List<SToken> overlappedTokens = graph.getOverlappedTokens(span);
      List<Integer> tokenIndices = new ArrayList<>();
      // Build token list, i.e., row indices covered by this span
      for (SToken token : overlappedTokens) {
        tokenIndices.add(orderedTokens.indexOf(token));
      }
      for (SAnnotation annotation : span.getAnnotations()) {
        Column column = spanColumns.get(annotation.getQName());
        if (column != null) {
          boolean allCellsEmpty = true;
          for (Integer idx : tokenIndices) {
            if (column.checkRowExists(idx)) {
              allCellsEmpty = false;
              break;
            }
          }
          if (allCellsEmpty) {
            for (Integer idx : tokenIndices) {
              try {
                column.setRow(idx, annotation);
              } catch (RuntimeException e) {
                // TODO Auto-generated catch block
                // TODO Bubble?
                e.printStackTrace();
              }
            }
          } else {
            // Iterate columnName and continue
            addSpanRecursively(tokenIndices, annotation, 2);
          }
        } else {
          column = new Column();
          column.setTitle(annotation.getQName());
          for (Integer idx : tokenIndices) {
            try {
              column.setRow(idx, annotation);
            } catch (RuntimeException e) {
              // TODO Auto-generated catch block
              // TODO Bubble?
              e.printStackTrace();
            }
          }
          spanColumns.put(annotation.getQName(), column);
        }
      }
    }

  }

  private void addSpanRecursively(List<Integer> tokenIndices, SAnnotation annotation,
      Integer spanColumnIndex) {
    String columnName = annotation.getQName() + spanColumnIndex;
    Column column = spanColumns.get(columnName);
    if (column != null) {
      // Try to add, otherwise iterate and re-run
      boolean allCellsempty = true;
      for (Integer idx : tokenIndices) {
        if (column.checkRowExists(idx)) {
          allCellsempty = false;
          break;
        }
      }
      if (allCellsempty) {
        for (Integer idx : tokenIndices) {
          try {
            column.setRow(idx, annotation);
          } catch (RuntimeException e) {
            // TODO: handle exception
          }
        }
      } else {
        // Bump counter and re-run
        spanColumnIndex = spanColumnIndex + 1;
        addSpanRecursively(tokenIndices, annotation, spanColumnIndex);
      }
    } else {
      column = new Column();
      column.setTitle(columnName);
      for (Integer idx : tokenIndices) {
        try {
          column.setRow(idx, annotation);
        } catch (RuntimeException e) {
          // TODO: handle exception
        }
      }
      spanColumns.put(columnName, column);
    }
  }

  private void resolveTokenAnnotations(List<SToken> orderedTokens) {
    // There can be no two annotations of the same qname for a single token
    for (SStructuredNode token : orderedTokens) {
      Set<SAnnotation> annos = token.getAnnotations();
      for (SAnnotation anno : annos) {
        // Check if we already have a column with that key
        Column column = tokenColumns.get(anno.getQName());
        if (column != null) {
          if (column.checkRowExists(orderedTokens.indexOf(token))) {
            // TODO add recursively
          } else {
            try {
              column.setRow(orderedTokens.indexOf(token), anno);
            } catch (RuntimeException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        } else {
          column = new Column();
          try {
            column.setRow(orderedTokens.indexOf(token), anno);
          } catch (RuntimeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          tokenColumns.put(anno.getQName(), column);
        }
      }
    }
    // Add in alphabetical order the newly added qualified annotation names.
    log.debug("Resolved annotations for tokens/spans in {}.", graph.getDocument());

  }

  @Override
  public Object getDataValue(int columnIndex, int rowIndex) {
    if (ds == null) {
      if (columnIndex == 0 && rowIndex == 0) {
        return "Please select data source!";
      }
    } else {
      if (orderedDsTokens.size() == 0 && columnIndex == 0) {
        return "Data source contains no tokens!";
      } else {
        Column column = columns.get(columnIndex);
        return column.getDisplayText(rowIndex);
      }
    }
    return null;
  }

  @Override
  public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
    // TODO Auto-generated method stub

  }

  @Override
  public int getColumnCount() {
    if (ds == null) {
      return 1;
    } else if (orderedDsTokens.size() == 0) {
      return 1;
    }
    return columns.size();
  }

  @Override
  public int getRowCount() {
    if (ds == null) {
      return 1;
    } else if (orderedDsTokens.size() == 0) {
      return 1;
    }
    return orderedDsTokens.size();
  }

  /**
   * Sets the data source field.
   * 
   * @param ds the ds to set
   */
  public void setDs(STextualDS ds) {
    log.debug("Setting data source {}.", ds);
    this.ds = ds;
    resolveGraph();
  }

}
