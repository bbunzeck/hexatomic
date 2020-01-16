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

import org.corpus_tools.salt.common.SDocumentGraph;
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


  private final SDocumentGraph graph;

  /**
   * Initializes the graph to grid resolution.
   * 
   * @param graph The graph data source
   */
  public GraphDataProvider(SDocumentGraph graph) {
    this.graph = graph;
    resolveGraph();
  }

  private void resolveGraph() {
    log.debug("Starting to resolve SDocumentGraph of {}.", graph.getDocument());
    // TODO Auto-generated method stub
    log.debug("Finished resolving SDocumentGraph of {}.", graph.getDocument());
  }

  @Override
  public Object getDataValue(int columnIndex, int rowIndex) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
    // TODO Auto-generated method stub

  }

  @Override
  public int getColumnCount() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getRowCount() {
    // TODO Auto-generated method stub
    return 0;
  }


}
