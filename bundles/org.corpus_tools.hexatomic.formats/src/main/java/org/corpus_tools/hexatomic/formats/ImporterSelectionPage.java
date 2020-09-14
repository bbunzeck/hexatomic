/*-
 * #%L
 * org.corpus_tools.hexatomic.formats
 * %%
 * Copyright (C) 2018 - 2020 Stephan Druskat, Thomas Krause
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

package org.corpus_tools.hexatomic.formats;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Optional;
import org.corpus_tools.pepper.common.Pepper;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class ImporterSelectionPage extends WizardPage implements IWizardPage {


  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ImporterSelectionPage.class);

  private Button btnRadioButton;
  protected ImporterSelectionPage() {
    super("Select import format");
    setTitle("Select import format");
    setDescription(
        "Corpora are stored in specific formats and you need to select the correct one.");
  }

  protected void updateRecommendFormats(File corpusPath) {

    // Init selection to default
    btnRadioButton.setSelection(false);

    Optional<Pepper> pepper = Activator.getPepper();
    File[] roots = File.listRoots();
    boolean pathIsRoot = false;
    for (File r : roots) {
      if (r.equals(corpusPath)) {
        pathIsRoot = true;
        break;
      }
    }
    if (!pathIsRoot && pepper.isPresent()) {
      try {
        for (String importerName : pepper.get()
            .findAppropriateImporters(URI.createFileURI(corpusPath.getAbsolutePath()))) {
          Optional<ImportFormat> format = ImportFormat.getFormatByName(importerName);
          if (format.isPresent() && format.get() == ImportFormat.Exmaralda) {
            btnRadioButton.setSelection(true);
            setPageComplete(true);
          }
        }
      } catch (FileNotFoundException ex) {
        log.error("Corpus path not a valid URI, can't get recommened importers", ex);
      }
    }
  }


  @Override
  public void createControl(Composite parent) {

    setPageComplete(false);

    Composite container = new Composite(parent, SWT.NULL);
    setControl(container);
    container.setLayout(new GridLayout(1, false));

    btnRadioButton = new Button(container, SWT.RADIO);
    btnRadioButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        setPageComplete(getSelectedFormat().isPresent());
      }
    });
    btnRadioButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
    btnRadioButton.setBounds(0, 0, 112, 17);
    btnRadioButton.setText("EXMARaLDA format (*.exb)");

  }

  protected Optional<ImportFormat> getSelectedFormat() {
    if (btnRadioButton.getSelection()) {
      return Optional.of(ImportFormat.Exmaralda);
    }
    return Optional.empty();
  }
}
