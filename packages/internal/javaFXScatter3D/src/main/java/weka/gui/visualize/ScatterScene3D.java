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
 *    ScatterScene3D.java
 *    Copyright (C) 2019 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.gui.visualize;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Handles scene creation and rendering.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 */
public class ScatterScene3D {

  protected final Group m_root = new Group();
  protected final Xform m_axisGroup = new Xform();
  protected final Xform m_world = new Xform();
  protected final Xform m_cameraXform = new Xform();
  protected final Xform m_cameraXform2 = new Xform();
  protected final Xform m_cameraXform3 = new Xform();
  protected final Xform m_dataForm = new Xform();
  protected Scene m_scene;

  protected final PerspectiveCamera m_camera = new PerspectiveCamera(true);

  private static final double CAMERA_INITIAL_DISTANCE = -1500;
  private static final double CAMERA_INITIAL_X_ANGLE = 25; // 70.0;
  private static final double CAMERA_INITIAL_Y_ANGLE = -135;// 320.0;
  private static final double CAMERA_NEAR_CLIP = 0.1;
  private static final double CAMERA_FAR_CLIP = 10000.0;
  private static final double AXIS_LENGTH = 500;
  private static final double CONTROL_MULTIPLIER = 0.5;
  private static final double SHIFT_MULTIPLIER = 10.0;
  private static final double MOUSE_SPEED = 0.1;
  private static final double ROTATION_SPEED = 2.0;
  private static final double TRACK_SPEED = 0.3;

  protected double m_mousePosX;
  protected double m_mousePosY;
  protected double m_mouseOldX;
  protected double m_mouseOldY;
  protected double m_mouseDeltaX;
  protected double m_mouseDeltaY;

  /** the data to be plotted */
  protected Instances m_data;

  protected int m_xIndex;
  protected int m_yIndex;
  protected int m_zIndex;
  protected int m_cIndex;

  protected double m_minX;
  protected double m_maxX;
  protected double m_minY;
  protected double m_maxY;
  protected double m_minZ;
  protected double m_maxZ;
  protected double m_minC;
  protected double m_maxC;

  protected static Color[] DEFAULT_COLORS =
    { Color.LIGHTBLUE, Color.RED, Color.GREEN, Color.PINK, Color.YELLOW,
      Color.PURPLE, Color.ORANGE, Color.AQUA, Color.LAVENDER, Color.BLUE,
      Color.LIGHTGREEN, Color.LIGHTYELLOW };

  private String formatNumberString(double num) {
    int whole = (int) Math.abs(num);
    double decimal = Math.abs(num) - whole;
    int nondecimal;
    nondecimal = (whole > 0) ? (int) (Math.log(whole) / Math.log(10)) : 1;

    int precision =
      (decimal > 0) ? (int) Math
        .abs(((Math.log(Math.abs(num)) / Math.log(10)))) + 2 : 1;
    if (precision > VisualizeUtils.MAX_PRECISION) {
      precision = 1;
    }

    String retString =
      Utils.doubleToString(num, nondecimal + 1 + precision, precision);

    return retString;
  }

  /**
   * Set up the camera
   */
  protected void buildCamera() {
    m_root.getChildren().add(m_cameraXform);
    m_cameraXform.getChildren().add(m_cameraXform2);
    m_cameraXform2.getChildren().add(m_cameraXform3);
    m_cameraXform3.getChildren().add(m_camera);
    m_cameraXform3.setRotateZ(180.0);

    m_camera.setNearClip(CAMERA_NEAR_CLIP);
    m_camera.setFarClip(CAMERA_FAR_CLIP);
    m_camera.setTranslateZ(CAMERA_INITIAL_DISTANCE);
    m_cameraXform.ry.setAngle(CAMERA_INITIAL_Y_ANGLE);
    m_cameraXform.rx.setAngle(CAMERA_INITIAL_X_ANGLE);
  }

  /**
   * Construct axes
   */
  protected void buildAxes() {
    if (m_data != null) {
      int numAttributes = m_data.numAttributes();

      if (m_xIndex > numAttributes - 1 || m_yIndex > numAttributes - 1
        || m_zIndex > numAttributes - 1 || m_cIndex > numAttributes - 1) {
        return;
      }

      if (m_data.attribute(m_xIndex).isNumeric()) {
        m_minX = m_data.attributeStats(m_xIndex).numericStats.min;
        m_maxX = m_data.attributeStats(m_xIndex).numericStats.max;
      } else {
        m_minX = 0;
        m_maxX = m_data.attribute(m_xIndex).numValues() - 1;
      }

      if (m_data.attribute(m_yIndex).isNumeric()) {
        m_minY = m_data.attributeStats(m_yIndex).numericStats.min;
        m_maxY = m_data.attributeStats(m_yIndex).numericStats.max;
      } else {
        m_minY = 0;
        m_maxY = m_data.attribute(m_yIndex).numValues() - 1;
      }

      if (m_data.attribute(m_zIndex).isNumeric()) {
        m_minZ = m_data.attributeStats(m_zIndex).numericStats.min;
        m_maxZ = m_data.attributeStats(m_zIndex).numericStats.max;
      } else {
        m_minZ = 0;
        m_maxZ = m_data.attribute(m_zIndex).numValues() - 1;
      }

      if (m_data.attribute(m_cIndex).isNumeric()) {
        m_minC = m_data.attributeStats(m_cIndex).numericStats.min;
        m_maxC = m_data.attributeStats(m_cIndex).numericStats.max;
      }
    }

    final PhongMaterial redMaterial = new PhongMaterial();
    redMaterial.setDiffuseColor(Color.MAGENTA);
    redMaterial.setSpecularColor(Color.MAGENTA);

    final PhongMaterial greenMaterial = new PhongMaterial();
    greenMaterial.setDiffuseColor(Color.MAGENTA);
    greenMaterial.setSpecularColor(Color.MAGENTA);

    final PhongMaterial blueMaterial = new PhongMaterial();
    blueMaterial.setDiffuseColor(Color.MAGENTA);
    blueMaterial.setSpecularColor(Color.MAGENTA);

    final Box xAxis = new Box(AXIS_LENGTH, 1, 1);
    xAxis.setTranslateX(AXIS_LENGTH / 2);
    final Box yAxis = new Box(1, AXIS_LENGTH, 1);
    yAxis.setTranslateY(AXIS_LENGTH / 2);
    final Box zAxis = new Box(1, 1, AXIS_LENGTH);
    zAxis.setTranslateZ(AXIS_LENGTH / 2);

    xAxis.setMaterial(redMaterial);
    yAxis.setMaterial(greenMaterial);
    zAxis.setMaterial(blueMaterial);

    m_axisGroup.getChildren().addAll(xAxis, yAxis, zAxis);

    if (m_data != null) {
      Text xAxisText = new Text();
      xAxisText.setFont(new Font(18));
      xAxisText.setText(m_data.attribute(m_xIndex).name());
      xAxisText.setX(AXIS_LENGTH / 2);
      xAxisText.setY(-15);
      // xAxisText.setTranslateZ(100);
      xAxisText.setFill(Color.WHITE);
      xAxisText.getTransforms().add(
        new Rotate(180, xAxisText.getX(), xAxisText.getY(), 0, Rotate.Z_AXIS));
      xAxisText.getTransforms().add(
        new Rotate(180, xAxisText.getX(), xAxisText.getY(), 0, Rotate.Y_AXIS));
      m_axisGroup.getChildren().add(xAxisText);

      if (m_data.attribute(m_xIndex).isNumeric()) {
        // x min
        Text xMinText = new Text();
        xMinText.setFont(new Font(18));
        xMinText.setText(formatNumberString(m_minX));
        xMinText.setY(-15);
        xMinText.setFill(Color.WHITE);
        xMinText.getTransforms()
          .add(
            new Rotate(180, xAxisText.getX(), xAxisText.getY(), 0,
              Rotate.Z_AXIS));
        xMinText.getTransforms()
          .add(
            new Rotate(180, xAxisText.getX(), xAxisText.getY(), 0,
              Rotate.Y_AXIS));
        m_axisGroup.getChildren().add(xMinText);

        // x max
        Text xMaxText = new Text();
        xMaxText.setFont(new Font(18));
        xMaxText.setText(formatNumberString(m_maxX));
        xMaxText.setY(-15);
        xMaxText.setX(AXIS_LENGTH);
        xMaxText.setFill(Color.WHITE);
        xMaxText.getTransforms().add(
          new Rotate(180, xMaxText.getX(), xMaxText.getY(), 0, Rotate.Z_AXIS));
        xMaxText.getTransforms().add(
          new Rotate(180, xMaxText.getX(), xMaxText.getY(), 0, Rotate.Y_AXIS));
        m_axisGroup.getChildren().add(xMaxText);
      } else {
        // nominal labels (25 max)
        int numLabels = m_data.attribute(m_xIndex).numValues();
        double xRange = m_maxX - m_minX > 0 ? m_maxX - m_minX : 1;
        for (int i = 0; i < numLabels; i++) {
          double xV = (i - m_minX) / xRange * AXIS_LENGTH;
          String label = m_data.attribute(m_xIndex).value(i);
          Text lText = new Text();
          lText.setText(label);
          lText.setFont(new Font(18));
          lText.setY(-35);
          lText.setX(xV);
          lText.setFill(Color.WHITE);
          lText.getTransforms().add(
            new Rotate(180, lText.getX(), lText.getY(), 0, Rotate.Z_AXIS));
          lText.getTransforms().add(
            new Rotate(90, lText.getX(), lText.getY(), 0, Rotate.Y_AXIS));
          lText.getTransforms().add(
            new Rotate(-90, lText.getX(), lText.getY(), 0, Rotate.X_AXIS));
          m_axisGroup.getChildren().add(lText);
        }
      }

      Text zAxisText = new Text();
      zAxisText.setFont(new Font(18));
      zAxisText.setText(m_data.attribute(m_zIndex).name());
      zAxisText.setTranslateZ(AXIS_LENGTH / 2);
      zAxisText.setX(0);
      zAxisText.setY(-15);
      zAxisText.setFill(Color.WHITE);
      zAxisText.getTransforms().add(
        new Rotate(180, zAxisText.getX(), zAxisText.getY(), 0, Rotate.Z_AXIS));
      zAxisText.getTransforms().add(
        new Rotate(90, zAxisText.getX(), zAxisText.getY(), 0, Rotate.Y_AXIS));
      m_axisGroup.getChildren().add(zAxisText);

      if (m_data.attribute(m_zIndex).isNumeric()) {
        // z min
        Text zMinText = new Text();
        zMinText.setFont(new Font(18));
        zMinText.setText(formatNumberString(m_minZ));
        zMinText.setY(-15);
        zMinText.setFill(Color.WHITE);
        zMinText.getTransforms().add(
          new Rotate(180, zMinText.getX(), zMinText.getY(), 0, Rotate.Z_AXIS));
        zMinText.getTransforms().add(
          new Rotate(90, zMinText.getX(), zMinText.getY(), 0, Rotate.Y_AXIS));
        m_axisGroup.getChildren().add(zMinText);

        // z max
        Text zMaxText = new Text();
        zMaxText.setFont(new Font(18));
        zMaxText.setText(formatNumberString(m_maxZ));
        zMaxText.setY(-15);
        zMaxText.setTranslateZ(AXIS_LENGTH);
        zMaxText.setFill(Color.WHITE);
        zMaxText.getTransforms().add(
          new Rotate(180, zMaxText.getX(), zMaxText.getY(), 0, Rotate.Z_AXIS));
        zMaxText.getTransforms().add(
          new Rotate(90, zMaxText.getX(), zMaxText.getY(), 0, Rotate.Y_AXIS));
        m_axisGroup.getChildren().add(zMaxText);
      } else {
        // nominal labels (25 max)
        int numLabels = m_data.attribute(m_zIndex).numValues();
        double zRange = m_maxZ - m_minZ > 0 ? m_maxZ - m_minZ : 1;
        for (int i = 0; i < numLabels; i++) {
          double zV = (i - m_minZ) / zRange * AXIS_LENGTH;
          String label = m_data.attribute(m_zIndex).value(i);
          Text lText = new Text();
          lText.setText(label);
          lText.setFont(new Font(18));
          int width = (int) lText.getLayoutBounds().getWidth() + 5;
          lText.setY(-55);
          lText.setX(-width);
          lText.setTranslateZ(zV);
          lText.setFill(Color.WHITE);
          lText.getTransforms().add(
            new Rotate(90, lText.getX(), lText.getY(), 0, Rotate.X_AXIS));
          m_axisGroup.getChildren().add(lText);
        }
      }

      Text yAxisText = new Text();
      yAxisText.setFont(new Font(18));
      yAxisText.setText(m_data.attribute(m_yIndex).name());
      yAxisText.setY(AXIS_LENGTH / 2);
      yAxisText.setX(0);
      yAxisText.setFill(Color.WHITE);
      yAxisText.getTransforms().add(
        new Rotate(-90, yAxisText.getX(), yAxisText.getY(), 0, Rotate.Z_AXIS));
      yAxisText.getTransforms().add(
        new Rotate(180, yAxisText.getX(), yAxisText.getY(), 0, Rotate.Y_AXIS));
      m_axisGroup.getChildren().add(yAxisText);

      if (m_data.attribute(m_yIndex).isNumeric()) {
        // y min
        Text yMinText = new Text();
        yMinText.setFont(new Font(18));
        yMinText.setText(formatNumberString(m_minY));
        yMinText.setX(-15);
        yMinText.setFill(Color.WHITE);
        yMinText.getTransforms().add(
          new Rotate(-90, yMinText.getX(), yMinText.getY(), 0, Rotate.Z_AXIS));
        yMinText.getTransforms().add(
          new Rotate(180, yMinText.getX(), yMinText.getY(), 0, Rotate.Y_AXIS));
        m_axisGroup.getChildren().add(yMinText);

        // y max
        Text yMaxText = new Text();
        yMaxText.setFont(new Font(18));
        yMaxText.setText(formatNumberString(m_maxY));
        yMaxText.setX(-15);
        yMaxText.setY(AXIS_LENGTH);
        yMaxText.setFill(Color.WHITE);
        yMaxText.getTransforms().add(
          new Rotate(-90, yMaxText.getX(), yMaxText.getY(), 0, Rotate.Z_AXIS));
        yMaxText.getTransforms().add(
          new Rotate(180, yMaxText.getX(), yMaxText.getY(), 0, Rotate.Y_AXIS));
        m_axisGroup.getChildren().add(yMaxText);
      } else {
        // nominal labels (25 max)
        int numLabels = m_data.attribute(m_yIndex).numValues();
        double yRange = m_maxY - m_minY > 0 ? m_maxY - m_minY : 1;
        for (int i = 0; i < numLabels; i++) {
          double yV = (i - m_minY) / yRange * AXIS_LENGTH;
          String label = m_data.attribute(m_yIndex).value(i);
          Text lText = new Text();
          lText.setText(label);
          lText.setFont(new Font(18));
          int width = (int) lText.getLayoutBounds().getWidth() + 20;
          lText.setY(yV);
          lText.setX(-width);
          lText.setFill(Color.WHITE);
          lText.getTransforms().add(
            new Rotate(-180, lText.getX(), lText.getY(), 0, Rotate.Z_AXIS));
          lText.getTransforms().add(
            new Rotate(180, lText.getX(), lText.getY(), 0, Rotate.Y_AXIS));
          m_axisGroup.getChildren().add(lText);
        }
      }
    }

    m_axisGroup.setVisible(true);
    m_world.getChildren().addAll(m_axisGroup);
  }

  /**
   * Map instances data into the scene
   */
  protected void buildData() {
    if (m_data == null) {
      return;
    }

    double xRange = m_maxX - m_minX > 0 ? m_maxX - m_minX : 1;
    double yRange = m_maxY - m_minY > 0 ? m_maxY - m_minY : 1;
    double zRange = m_maxZ - m_minZ > 0 ? m_maxZ - m_minZ : 1;
    double cRange = m_maxC - m_minC > 0 ? m_maxC - m_minC : 1;

    PhongMaterial whiteMaterial = new PhongMaterial();
    whiteMaterial.setDiffuseColor(Color.WHITE);
    whiteMaterial.setSpecularColor(Color.WHITE);
    for (int i = 0; i < m_data.numInstances(); i++) {
      Instance current = m_data.instance(i);
      if (current.isMissing(m_xIndex) || current.isMissing(m_yIndex)
        || current.isMissing(m_zIndex)) {
        continue;
      }
      Shape3D dataS =
        current.isMissing(m_cIndex) ? new Cylinder(2, 2) : new Sphere(2);
      dataS.setMaterial(whiteMaterial);
      if (current.attribute(m_cIndex).isNominal()
        && !current.isMissing(m_cIndex)) {
        Color temp =
          DEFAULT_COLORS[(int) current.value(m_cIndex) % DEFAULT_COLORS.length];
        PhongMaterial phongMaterial = new PhongMaterial();
        phongMaterial.setDiffuseColor(temp);
        phongMaterial.setSpecularColor(temp);
        dataS.setMaterial(phongMaterial);
      } else if (current.attribute(m_cIndex).isNumeric()
        && !current.isMissing(m_cIndex)) {
        double r = (current.value(m_cIndex) - m_minC) / cRange;
        r = (r * 240) + 15;
        Color temp = new Color(r / 255, (255 - r) / 255, 150 / 255.0, 1);
        PhongMaterial phongMaterial = new PhongMaterial();
        phongMaterial.setDiffuseColor(temp);
        phongMaterial.setSpecularColor(temp);
        dataS.setMaterial(phongMaterial);
      }
      double xV = (current.value(m_xIndex) - m_minX) / xRange * AXIS_LENGTH;
      double yV = (current.value(m_yIndex) - m_minY) / yRange * AXIS_LENGTH;
      double zV = (current.value(m_zIndex) - m_minZ) / zRange * AXIS_LENGTH;
      dataS.setTranslateX(xV);
      dataS.setTranslateY(yV);
      dataS.setTranslateZ(zV);

      String xVS =
        current.attribute(m_xIndex).isNumeric() ? "" + current.value(m_xIndex)
          : current.stringValue(m_xIndex);
      String yVS =
        current.attribute(m_yIndex).isNumeric() ? "" + current.value(m_yIndex)
          : current.stringValue(m_yIndex);
      String zVS =
        current.attribute(m_zIndex).isNumeric() ? "" + current.value(m_zIndex)
          : current.stringValue(m_zIndex);

      Tooltip t =
        new Tooltip(m_data.attribute(m_xIndex).name() + ": "
          + xVS + "\n" + m_data.attribute(m_yIndex).name()
          + ": " + yVS + "\n"
          + m_data.attribute(m_zIndex).name() + ": " + zVS);
      Tooltip.install(dataS, t);

      m_dataForm.getChildren().add(dataS);
    }

    m_world.getChildren().add(m_dataForm);
  }

  /**
   * Set the Instances to visualize
   *
   * @param data the instances to visualize
   * @param x x axis
   * @param y y axis
   * @param z z axis
   * @param c coloring axis
   */
  public void setInstances(Instances data, int x, int y, int z, int c) {
    m_data = data;
    m_xIndex = x;
    m_yIndex = y;
    m_zIndex = z;
    m_cIndex = c;
    if (m_world.getChildren().size() > 0) {
      m_world.getChildren().clear();
      m_axisGroup.getChildren().clear();
      m_dataForm.getChildren().clear();

      buildAxes();
      buildData();
      addLegend();
    }
  }

  /**
   * Update the current axes being visualized
   *
   * @param x x axis
   * @param y y axis
   * @param z z axis
   * @param c coloring axis
   */
  public void updateAxes(int x, int y, int z, int c) {
    if (m_data == null) {
      return;
    }

    m_xIndex = x;
    m_yIndex = y;
    m_zIndex = z;
    m_cIndex = c;
    if (m_world.getChildren().size() > 0) {
      m_world.getChildren().clear();
      m_axisGroup.getChildren().clear();
      m_dataForm.getChildren().clear();

      buildAxes();
      buildData();
      addLegend();
    }
  }

  /**
   * Add a legend to the scene
   */
  protected void addLegend() {
    if (m_data == null) {
      return;
    }

    Text controlsText = new Text();
    controlsText.setFont(new Font(14));
    controlsText.setText("Ctrl - zoom in/out\n" + "Alt - translate\n"
      + "Shift - slow movement");
    controlsText.setX(AXIS_LENGTH);
    controlsText.setY(-20);
    controlsText.setTranslateZ(100);
    controlsText.setFill(Color.WHITE);
    controlsText.getTransforms().add(
      new Rotate(180, controlsText.getX(), controlsText.getY(), 0,
        Rotate.Z_AXIS));
    controlsText.getTransforms().add(
      new Rotate(180, controlsText.getX(), controlsText.getY(), 0,
        Rotate.Y_AXIS));
    m_dataForm.getChildren().add(controlsText);
    if (m_data.attribute(m_cIndex).isNominal()) {
      for (int i = 0; i < m_data.attribute(m_cIndex).numValues(); i++) {
        String label = m_data.attribute(m_cIndex).value(i);

        Text labelText = new Text();
        labelText.setFont(new Font(18));
        labelText.setText(label);
        labelText.setX(AXIS_LENGTH);
        labelText.setY((AXIS_LENGTH * 0.75) - (i * 20));
        labelText.setTranslateZ(100);
        labelText.setFill(DEFAULT_COLORS[i % 12]);
        labelText.getTransforms()
          .add(
            new Rotate(180, labelText.getX(), labelText.getY(), 0,
              Rotate.Z_AXIS));
        labelText.getTransforms()
          .add(
            new Rotate(180, labelText.getX(), labelText.getY(), 0,
              Rotate.Y_AXIS));
        m_dataForm.getChildren().add(labelText);
      }
    } else {
      double ypos = AXIS_LENGTH / 2;
      for (int i = 100; i >= 0; i--) {
        double red = 2.55 * i;
        double blue = 150;
        double green = 255 - red;

        red /= 255.0;
        blue /= 255.0;
        green /= 255.0;

        Box line = new Box(20, 1, 1);
        PhongMaterial material = new PhongMaterial();
        Color barColor = new Color(red, green, blue, 1);
        material.setDiffuseColor(barColor);
        material.setSpecularColor(barColor);
        line.setMaterial(material);
        line.setTranslateX(AXIS_LENGTH);
        line.setTranslateY(ypos - i);
        line.setTranslateZ(100);
        m_dataForm.getChildren().add(line);
      }

      String minCString = formatNumberString(m_minC);
      String maxCString = formatNumberString(m_maxC);
      Text maxCText = new Text();
      maxCText.setFont(new Font(18));
      maxCText.setText(maxCString);
      maxCText.setX(AXIS_LENGTH - 20);
      maxCText.setY(ypos + 10);
      maxCText.setTranslateZ(100);
      maxCText.setFill(Color.WHITE);
      maxCText.getTransforms().add(
        new Rotate(180, maxCText.getX(), maxCText.getY(), 0, Rotate.Z_AXIS));
      maxCText.getTransforms().add(
        new Rotate(180, maxCText.getX(), maxCText.getY(), 0, Rotate.Y_AXIS));
      m_dataForm.getChildren().add(maxCText);

      Text minCText = new Text();
      minCText.setFont(new Font(18));
      minCText.setText(minCString);
      minCText.setX(AXIS_LENGTH - 20);
      minCText.setY(AXIS_LENGTH / 2 - 120);
      minCText.setTranslateZ(100);
      minCText.setFill(Color.WHITE);
      minCText.getTransforms().add(
        new Rotate(180, minCText.getX(), minCText.getY(), 0, Rotate.Z_AXIS));
      minCText.getTransforms().add(
        new Rotate(180, minCText.getX(), minCText.getY(), 0, Rotate.Y_AXIS));
      m_dataForm.getChildren().add(minCText);
    }
  }

  /**
   * Constructs the whole scene
   *
   * @return the Scene object
   */
  public Scene buildScene() {
    m_root.getChildren().add(m_world);
    m_root.setDepthTest(DepthTest.ENABLE);

    buildCamera();
    buildAxes();
    buildData();
    addLegend();

    m_scene = new Scene(m_root, 1024, 768, true);
    m_scene.setFill(Color.BLACK);
    handleMouse(m_scene, m_world);

    m_scene.setCamera(m_camera);

    return m_scene;
  }

  /**
   * Handle mouse events
   *
   * @param scene the Scene
   * @param root the root node of the scene
   */
  private void handleMouse(Scene scene, final Node root) {
    scene.setOnMousePressed(new EventHandler<MouseEvent>() {
      @Override
      public void handle(MouseEvent me) {
        m_mousePosX = me.getSceneX();
        m_mousePosY = me.getSceneY();
        m_mouseOldX = me.getSceneX();
        m_mouseOldY = me.getSceneY();
      }
    });
    scene.setOnMouseDragged(new EventHandler<MouseEvent>() {
      @Override
      public void handle(MouseEvent me) {
        m_mouseOldX = m_mousePosX;
        m_mouseOldY = m_mousePosY;
        m_mousePosX = me.getSceneX();
        m_mousePosY = me.getSceneY();
        m_mouseDeltaX = (m_mousePosX - m_mouseOldX);
        m_mouseDeltaY = (m_mousePosY - m_mouseOldY);

        double modifier = 1.0;
        double translateModifier = SHIFT_MULTIPLIER;

        boolean altDown = me.isAltDown();
        boolean cntrlDown = me.isControlDown();
//        boolean metaDown = me.isMetaDown();
        boolean shiftDown = me.isShiftDown();

        if (cntrlDown) {
          modifier = CONTROL_MULTIPLIER;
        }
        if (shiftDown) {
          modifier = 1.0 / SHIFT_MULTIPLIER;
          translateModifier = 1.0;
        }

        if (me.isPrimaryButtonDown()) {
          if (altDown) {
            m_cameraXform2.t.setX(m_cameraXform2.t.getX() + m_mouseDeltaX
              * translateModifier * TRACK_SPEED);
            m_cameraXform2.t.setY(m_cameraXform2.t.getY() + m_mouseDeltaY
              * translateModifier * TRACK_SPEED);
          } else if (cntrlDown) {
            double z = m_camera.getTranslateZ();
            double newZY = z + m_mouseDeltaY * TRACK_SPEED * translateModifier;
            double newZX = z + m_mouseDeltaX * TRACK_SPEED * translateModifier;
            double newZ =
              Math.abs(m_mouseDeltaX) > Math.abs(m_mouseDeltaY) ? newZX : newZY;
            m_camera.setTranslateZ(newZ);
          } else {
            m_cameraXform.ry.setAngle(m_cameraXform.ry.getAngle()
              - m_mouseDeltaX * MOUSE_SPEED * modifier * ROTATION_SPEED);
            m_cameraXform.rx.setAngle(m_cameraXform.rx.getAngle()
              + m_mouseDeltaY * MOUSE_SPEED * modifier * ROTATION_SPEED);
          }
        } else if (me.isSecondaryButtonDown()) {
          double z = m_camera.getTranslateZ();
          double newZ = z + m_mouseDeltaX * MOUSE_SPEED * modifier;
          m_camera.setTranslateZ(newZ);
        } else if (me.isMiddleButtonDown()) {
          m_cameraXform2.t.setX(m_cameraXform2.t.getX() + m_mouseDeltaX
            * MOUSE_SPEED * modifier * TRACK_SPEED);
          m_cameraXform2.t.setY(m_cameraXform2.t.getY() + m_mouseDeltaY
            * MOUSE_SPEED * modifier * TRACK_SPEED);
        }
      }
    });
  }

  /**
   * Gets a swing component containing the scene
   *
   * @return a swing component containing the scene
   */
  public JComponent getSwingComponent() {
    JFXPanel fxPanel = new JFXPanel();
    Scene scene = buildScene();
    fxPanel.setScene(scene);

    return fxPanel;
  }

  /**
   * Get the Scene object
   *
   * @return the Scene object
   */
  public Scene getScene() {
    if (m_scene != null) {
      return m_scene;
    }

    return buildScene();
  }

  /*
   * public FXCanvas getSWTScene() { FXCanvas fxCanvas = new FXCanvas(shell,
   * SWT.NONE) { public Point computeSize(int wHint, int hHint, boolean changed)
   * { getScene().getWindow().sizeToScene(); int width = (int)
   * getScene().getWidth(); int height = (int) getScene().getHeight(); return
   * new Point(width, height); } }; }
   */

  private static Instances loadInstances(String path) {
    try {
      return new Instances(new BufferedReader(new FileReader(path)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static ScatterScene3D initFX(JFXPanel fxPanel, String[] args) {
    // This method is invoked on the JavaFX thread
    ScatterScene3D scatterScene = new ScatterScene3D();
    Instances toPlot = loadInstances(args[0]);
    int x = Integer.parseInt(args[1]);
    int y = Integer.parseInt(args[2]);
    int z = Integer.parseInt(args[3]);
    int c = Integer.parseInt(args[4]);
    scatterScene.setInstances(toPlot, x, y, z, c);
    Scene scene = scatterScene.buildScene();
    fxPanel.setScene(scene);
    return scatterScene;
  }

  private static void initAndShowGUI(final String[] args) {
    // This method is invoked on the EDT thread
    JFrame frame = new JFrame("Swing and JavaFX");
    final JFXPanel fxPanel = new JFXPanel();
    frame.add(fxPanel);
    frame.setSize(1024, 768);
    frame.setVisible(true);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        ScatterScene3D ss = initFX(fxPanel, args);
      }
    });
  }

  public static void main(final String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        initAndShowGUI(args);
      }
    });
  }
}
