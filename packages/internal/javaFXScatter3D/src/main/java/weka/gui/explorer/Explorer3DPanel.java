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
 *    Explorer3DPanel.java
 *    Copyright (C) 2019 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.gui.explorer;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import weka.core.Instances;
import weka.gui.visualize.Visualize3D;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/**
 * Explorer plugin class that provides a 3D scatter plot visualization as a
 * separate tab in the Explorer.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class Explorer3DPanel extends JPanel implements Explorer.ExplorerPanel {

  /** For serialization */
  private static final long serialVersionUID = 5333487402744784354L;

  /** Reference to the Explorer object */
  protected Explorer m_explorer;

  /** The actual 3D visualization panel */
  protected Visualize3D m_vis3D;

  protected JFXPanel m_jfxPanel;

  /**
   * Constructor
   */
  public Explorer3DPanel() {
    setLayout(new BorderLayout());
  }

  /**
   * Sets the Explorer to use as parent frame (used for sending notifications
   * about changes in the data)
   *
   * @param parent the parent frame
   */
  @Override
  public void setExplorer(Explorer parent) {
    m_explorer = parent;

    // Initialize toolkit here
    m_jfxPanel = new JFXPanel();
    m_vis3D = new Visualize3D(m_jfxPanel);
    add(m_vis3D, BorderLayout.CENTER);
  }

  /**
   * returns the parent Explorer frame
   *
   * @return the parent
   */
  @Override
  public Explorer getExplorer() {
    return m_explorer;
  }

  /**
   * Tells the panel to use a new set of instances.
   *
   * @param inst a set of Instances
   */
  @Override
  public void setInstances(final Instances inst) {
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

  /**
   * Returns the title for the tab in the Explorer
   *
   * @return the title of this tab
   */
  @Override
  public String getTabTitle() {
    return "Visualize3D";
  }

  /**
   * Returns the tooltip for the tab in the Explorer
   *
   * @return the tooltip of this tab
   */
  @Override
  public String getTabTitleToolTip() {
    return "visualize instances in a 3D scatter plot";
  }
}
