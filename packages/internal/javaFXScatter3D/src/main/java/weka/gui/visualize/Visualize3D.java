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
 *    Visualize3D.java
 *    Copyright (C) 2019 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.gui.visualize;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import weka.core.Attribute;
import weka.core.Instances;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Random;

/**
 * Panel that displays a 3D scatter plot of the data. Uses JavaFX 3D. Has
 * widgets to allow the user to select the axes to be visualized.
 * 
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class Visualize3D extends JPanel {
  private static final long serialVersionUID = -706744029383610864L;

  /** Manages the 3D scene */
  protected ScatterScene3D m_scene3D;

  /** Combo box for selecting the x axis */
  protected JComboBox m_xCombo = new JComboBox();

  /** Combo box for selecting the y axis */
  protected JComboBox m_yCombo = new JComboBox();

  /** Combo box for selecting the z axis */
  protected JComboBox m_zCombo = new JComboBox();

  /** Combo box for selecting the coloring axis */
  protected JComboBox m_cCombo = new JComboBox();

  /** Button for upating the display after changing axis etc. */
  protected JButton m_updateBut = new JButton("Update display");

  protected boolean m_combosReady = false;
  protected boolean m_combosChanged = false;

  /** A titled panel that holds the plot */
  protected JPanel m_plotSurround = new JPanel();

  /** A reference to the current data set */
  protected Instances m_masterInstances;

  /** Field for the resampling percentage */
  protected JTextField m_resamplePercent = new JTextField(5);

  /** Field for the random seed for resampling */
  protected JTextField m_randomSeed = new JTextField(5);

  private double m_previousPercent = -1;
  private int m_previousSeed = 1;

  protected boolean m_newSetOfInstances;

  public Visualize3D(JFXPanel jfxPanel) {
    setLayout(new BorderLayout());

    m_xCombo.setEnabled(false);
    m_yCombo.setEnabled(false);
    m_zCombo.setEnabled(false);
    m_cCombo.setEnabled(false);
    m_xCombo.setLightWeightPopupEnabled(false);
    m_yCombo.setLightWeightPopupEnabled(false);
    m_zCombo.setLightWeightPopupEnabled(false);
    m_cCombo.setLightWeightPopupEnabled(false);
    m_updateBut.setEnabled(false);

    JPanel controlHolder = new JPanel();
    controlHolder.setLayout(new BorderLayout());

    JPanel comboHolder = new JPanel();
    comboHolder.setLayout(new GridLayout(2, 2));
    comboHolder.add(m_xCombo);
    comboHolder.add(m_yCombo);
    comboHolder.add(m_zCombo);
    comboHolder.add(m_cCombo);
    controlHolder.add(comboHolder, BorderLayout.NORTH);

    JPanel butHolder = new JPanel();
    butHolder.setLayout(new BorderLayout());
    // butHolder.add(m_updateBut, BorderLayout.SOUTH);
    JPanel samplingAndUpdateHolder = new JPanel();
    samplingAndUpdateHolder.setLayout(new BorderLayout());
    JPanel samplingPanel = new JPanel();
    samplingPanel.add(new JLabel("Sample %"));
    samplingPanel.add(m_resamplePercent);
    samplingPanel.add(new JLabel("Random seed"));
    m_randomSeed.setText("" + 1);
    samplingPanel.add(m_randomSeed);
    samplingAndUpdateHolder.add(samplingPanel, BorderLayout.WEST);
    samplingAndUpdateHolder.add(m_updateBut, BorderLayout.CENTER);
    butHolder.add(samplingAndUpdateHolder, BorderLayout.NORTH);
    controlHolder.add(butHolder, BorderLayout.SOUTH);

    add(controlHolder, BorderLayout.NORTH);

    m_plotSurround.setLayout(new BorderLayout());
    m_plotSurround.setBorder(BorderFactory.createTitledBorder("Plot"));

    add(m_plotSurround, BorderLayout.CENTER);

    m_xCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        m_combosChanged = true;
      }
    });

    m_yCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        m_combosChanged = true;
      }
    });

    m_zCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        m_combosChanged = true;
      }
    });

    m_cCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        m_combosChanged = true;
      }
    });

    m_updateBut.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Platform.runLater(new Runnable() {
          @Override
          public void run() {
            updateDisplay(false, null);
          }
        });
      }
    });

    m_plotSurround.add(jfxPanel, BorderLayout.CENTER);
  }

  /**
   * Tell's the panel to update the visualization.
   * 
   * @param newInstances true if a new set of instances have been set on the
   *          panel
   */
  public void updateDisplay(boolean newInstances, JFXPanel toUse) {

    if (m_scene3D == null && toUse != null) {
      m_scene3D = new ScatterScene3D();
      Scene scene = m_scene3D.buildScene();
      toUse.setScene(scene);
    }

    double currentPercent = -1;
    int currentSeed = Integer.MAX_VALUE;
    boolean doUpdate = false;
    int x = 0, y = 0, z = 0, c = 0;
    Instances inst = m_masterInstances;

    Instances toUpdateWith = newInstances ? m_masterInstances : null;
    try {
      currentPercent = Double.parseDouble(m_resamplePercent.getText());
      currentSeed = Integer.parseInt(m_randomSeed.getText());

      if (currentPercent < 100 && currentPercent > 0) {
        if (currentPercent != m_previousPercent
          || currentSeed != m_previousSeed) {

          inst =
            new Instances(m_masterInstances, 0,
              m_masterInstances.numInstances());
          inst.randomize(new Random(currentSeed));
          inst =
            new Instances(inst, 0, (int) Math.round(currentPercent / 100D
              * inst.numInstances()));
          m_previousPercent = currentPercent;
          m_previousSeed = currentSeed;
          // m_visPanel.setInstances(inst);

          doUpdate = true;
          toUpdateWith = inst;
        }
      } else {
        if (currentPercent != m_previousPercent) {
          // m_visPanel.setInstances(m_masterInstances);
          doUpdate = true;
          m_previousPercent = 100;
          m_resamplePercent.setText("" + 100);
          toUpdateWith = m_masterInstances;
        }
      }
    } catch (NumberFormatException ex) {
    }

    if (m_combosReady) {
      x = m_xCombo.getSelectedIndex();
      y = m_yCombo.getSelectedIndex();
      z = m_zCombo.getSelectedIndex();
      c = m_cCombo.getSelectedIndex();
    }

    if (m_combosChanged) {
      x = m_xCombo.getSelectedIndex();
      y = m_yCombo.getSelectedIndex();
      z = m_zCombo.getSelectedIndex();
      c = m_cCombo.getSelectedIndex();

      if (m_combosReady) {
        m_combosChanged = false;
        doUpdate = true;
      }
    }

    if (doUpdate) {
      if (toUpdateWith == null) {
        m_scene3D.updateAxes(x, y, z, c);
      } else {
        m_scene3D.setInstances(toUpdateWith, x, y, z, c);
      }
    }

    m_newSetOfInstances = false;
  }

  /**
   * Sets a new set of instances to be visualized.
   *
   * @param inst the instances to visualize.
   */
  public void setInstances(Instances inst) {
    m_masterInstances = inst;
    setPercent();

    // m_visPanel.setInstances(m_masterInstances);
    m_plotSurround.setBorder(BorderFactory.createTitledBorder("Plot: "
      + m_masterInstances.relationName()));
    setupComboBoxes(inst);
    // updateDisplay(true);
    m_newSetOfInstances = true;
  }

  private void setPercent() {
    if (m_masterInstances.numInstances() > 5000) {
      double percent = 5000D / m_masterInstances.numInstances() * 100.0;
      percent = Math.round(percent * 100.0);
      percent /= 100.0;
      m_resamplePercent.setText("" + percent);
    } else {
      m_resamplePercent.setText("100");
    }
  }

  /**
   * Sets up the combo boxes.
   *
   * @param inst the instances to use for setting combo box choices.
   */
  protected void setupComboBoxes(Instances inst) {
    m_combosReady = false;
    String[] XNames = new String[inst.numAttributes()];
    String[] YNames = new String[inst.numAttributes()];
    String[] ZNames = new String[inst.numAttributes()];
    String[] CNames = new String[inst.numAttributes()];
    for (int i = 0; i < XNames.length; i++) {
      String type = "";
      switch (inst.attribute(i).type()) {
      case Attribute.NOMINAL:
        type = " (Nom)";
        break;
      case Attribute.NUMERIC:
        type = " (Num)";
        break;
      case Attribute.STRING:
        type = " (Str)";
        break;
      case Attribute.DATE:
        type = " (Dat)";
        break;
      case Attribute.RELATIONAL:
        type = " (Rel)";
        break;
      default:
        type = " (???)";
      }
      XNames[i] = "X: " + inst.attribute(i).name() + type;
      YNames[i] = "Y: " + inst.attribute(i).name() + type;
      ZNames[i] = "Z: " + inst.attribute(i).name() + type;
      CNames[i] = "Colour: " + inst.attribute(i).name() + type;
    }

    m_xCombo.setModel(new DefaultComboBoxModel(XNames));
    m_yCombo.setModel(new DefaultComboBoxModel(YNames));
    m_zCombo.setModel(new DefaultComboBoxModel(ZNames));
    m_cCombo.setModel(new DefaultComboBoxModel(CNames));

    m_xCombo.setEnabled(true);
    m_yCombo.setEnabled(true);
    m_zCombo.setEnabled(true);
    m_cCombo.setEnabled(true);
    m_updateBut.setEnabled(true);

    int xIndex = 0;
    int yIndex = 0;
    int zIndex = 0;
    int cIndex = 0;
    if (inst.numAttributes() > 1) {
      zIndex = 1;
      yIndex = 1;
    }

    if (inst.numAttributes() > 2) {
      zIndex = 2;
    }

    cIndex = inst.numAttributes() - 1;
    m_xCombo.setSelectedIndex(xIndex);
    m_yCombo.setSelectedIndex(yIndex);
    m_zCombo.setSelectedIndex(zIndex);
    m_cCombo.setSelectedIndex(cIndex);
    m_combosReady = true;
  }

  private static void initAndShowGUI(final String[] args) {
    try {
      // This is necessary (here on the AWT event thread) for some reason in
      // order to initialize the toolkit (even though we don't use this
      // JFXPanel)
      // Think we just need to make one of these somewhere (just not on the FX
      // thread) for initialization purposes.
      final JFXPanel p = new JFXPanel();

      final Instances insts =
        new Instances(new BufferedReader(new FileReader(args[0])));

      final JFrame frame = new JFrame("Visualize 3D");
      frame.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {
          frame.dispose();
          System.exit(1);
        }
      });

      frame.setSize(800, 600);
      frame.setVisible(true);

      Platform.runLater(new Runnable() {
        @Override
        public void run() {
          // Scene creation etc. needs to happen on the FX thread...
          final Visualize3D vis = new Visualize3D(p);
          vis.setInstances(insts);
          frame.setContentPane(vis);
        }
      });
      // vis.setInstances(insts);

    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * @param args
   */
  public static void main(final String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        initAndShowGUI(args);
      }
    });
  }
}
