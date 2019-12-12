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

package org.corpus_tools.hexatomic.console;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.corpus_tools.hexatomic.console.ConsoleCommandParser.NewNodeContext;
import org.corpus_tools.salt.common.SDocumentGraph;
import org.corpus_tools.salt.common.SStructure;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;

public class GraphAnnoConsole implements Runnable, IDocumentListener {


  private ByteArrayOutputStream out = new ByteArrayOutputStream();

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(GraphAnnoConsole.class);

  private final IDocument document;

  private final UISynchronize sync;

  private final SDocumentGraph graph;

  /**
   * Constructs a new console.
   * 
   * @param document The document that holds the content and history.
   * @param sync An Eclipse synchronization object.
   * @param graph The Salt graph to edit.
   */
  public GraphAnnoConsole(IDocument document, UISynchronize sync, SDocumentGraph graph) {
    this.document = document;
    this.sync = sync;
    this.graph = graph;

    this.document.addDocumentListener(this);

    Thread t = new Thread(this);
    t.start();
  }

  @Override
  public void run() {
    writeLine("To display a list of available commands, type \"help\".\n");

  }


  private void writeLine(String str) {
    try {
      out.write(str.getBytes(StandardCharsets.UTF_8));
      out.write('\n');

      sync.asyncExec(() -> document.set(new String(out.toByteArray(), StandardCharsets.UTF_8)));


    } catch (IOException e) {
      log.error("Could not write to console output", e);
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {

  }

  @Override
  public void documentAboutToBeChanged(DocumentEvent event) {
    if (event.getText().endsWith("\n")) {
      int nrLines = event.getDocument().getNumberOfLines();
      try {
        if (nrLines >= 1) {
          IRegion lineRegion = document.getLineInformation(nrLines - 1);
          String lastLine = document.get(lineRegion.getOffset(), lineRegion.getLength());

          // parse the line
          ConsoleCommandLexer lexer = new ConsoleCommandLexer(CharStreams.fromString(lastLine));
          CommonTokenStream tokens = new CommonTokenStream(lexer);
          ConsoleCommandParser parser = new ConsoleCommandParser(tokens);

          ParseTreeWalker walker = new ParseTreeWalker();
          CommandTreeListener listener = new CommandTreeListener();
          walker.walk(listener, parser.start());
        }
      } catch (BadLocationException e) {
        log.error("Bad location in console, no last line", e);
      }
    }

  }

  private class CommandTreeListener extends ConsoleCommandBaseListener {

    @Override
    public void exitNewNode(NewNodeContext ctx) {

      if (graph == null) {
        return;
      }
      SStructure n = graph.createStructure();
      if (n != null) {
        log.debug("Created new structure node {}", n.getId());
      }
    }
  }

}
