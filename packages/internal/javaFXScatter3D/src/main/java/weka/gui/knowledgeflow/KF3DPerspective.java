/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    KF3DPerspective.java
 *    Copyright (C) 2019 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.gui.knowledgeflow;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import weka.core.Instances;
import weka.gui.AbstractPerspective;
import weka.gui.PerspectiveInfo;
import weka.gui.visualize.Visualize3D;

import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/**
 * New Knowledge Flow perspective for the scatter plot 3D visualization
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@PerspectiveInfo(ID = "weka.gui.knowledgeflow.visualize3d",
  title = "Visualize 3D",
  toolTipText = "Visualize instances in a 3D scatter plot",
  iconPath = "weka/gui/knowledgeflow/icons/scatterPlot3D.png")
public class KF3DPerspective extends AbstractPerspective {

  private static final long serialVersionUID = 6515138550449103493L;

  /** The actual 3D visualization panel */
  protected Visualize3D m_vis3D;
  protected JFXPanel m_jfxPanel;

  public KF3DPerspective() {
    setLayout(new BorderLayout());
  }

  @Override
  public void instantiationComplete() {
    // make sure the toolkit is intialized
    m_jfxPanel = new JFXPanel();
    m_vis3D = new Visualize3D(m_jfxPanel);
    add(m_vis3D, BorderLayout.CENTER);
  }

  @Override
  public void setInstances(Instances inst) {
    m_vis3D.setInstances(inst);
    // prevent JavaFX from auto shutting down
    Platform.setImplicitExit(false);
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        m_vis3D.updateDisplay(true, m_jfxPanel);
      }
    });
  }

  @Override
  public boolean acceptsInstances() {
    return true;
  }
}
