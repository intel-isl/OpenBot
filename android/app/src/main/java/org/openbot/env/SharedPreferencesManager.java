package org.openbot.env;

import android.content.Context;
import android.content.SharedPreferences;
import org.openbot.tflite.Network;
import org.openbot.utils.Enums;

public class SharedPreferencesManager {

  private static final String PREFERENCES_NAME = "openbot_settings";
  private static final int DEFAULT_BAUD_RATE = 115200;
  private static final String BAUD_RATE = "BAUD_RATE";
  private static final int DEFAULT_LOG_MODE = Enums.LogMode.CROP_IMG.ordinal();
  private static final String LOG_MODE = "LOG_MODE";
  private static final int DEFAULT_CONTROL_MODE = Enums.ControlMode.GAMEPAD.getValue();
  private static final String CONTROL_MODE = "CONTROL_MODE";
  private static final int DEFAULT_SPEED_MODE = Enums.SpeedMode.NORMAL.getValue();
  private static final String SPEED_MODE = "SPEED_MODE";
  private static final int DEFAULT_DRIVE_MODE = Enums.DriveMode.GAME.getValue();
  private static final String DRIVE_MODE = "DRIVE_MODE";

  private static final String DEFAULT_MODEL = "DEFAULT_MODEL_NAME";
  private static final String OBJECT_NAV_MODEL = "OBJECT_NAV_MODEL_NAME";
  private static final String AUTOPILOT_MODEL = "AUTOPILOT_MODEL_NAME";

  private static final String OBJECT_TYPE = "OBJECT_TYPE";
  private static final String DEFAULT_OBJECT_TYPE = "person";

  private static final int DEFAULT_DEVICE = Network.Device.CPU.ordinal();
  private static final String DEVICE = "DEVICE";
  private static final int DEFAULT_NUM_THREAD = 4;
  private static final String NUM_THREAD = "NUM_THREAD";
  private static final String CAMERA_SWITCH = "CAMERA_SWITCH";
  private static final String SHEET_EXPANDED = "SHEET_EXPANDED";
  private static final String DELAY = "DELAY";

  private final SharedPreferences preferences;

  public SharedPreferencesManager(Context context) {
    preferences =
        context
            .getApplicationContext()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  public int getBaudrate() {
    return preferences.getInt(BAUD_RATE, DEFAULT_BAUD_RATE);
  }

  public int getDevice() {
    return preferences.getInt(DEVICE, DEFAULT_DEVICE);
  }

  public int getDriveMode() {
    return preferences.getInt(DRIVE_MODE, DEFAULT_DRIVE_MODE);
  }

  public int getLogMode() {
    return preferences.getInt(LOG_MODE, DEFAULT_LOG_MODE);
  }

  public int getControlMode() {
    return preferences.getInt(CONTROL_MODE, DEFAULT_CONTROL_MODE);
  }

  public int getSpeedMode() {
    return preferences.getInt(SPEED_MODE, DEFAULT_SPEED_MODE);
  }

  public int getNumThreads() {
    return preferences.getInt(NUM_THREAD, DEFAULT_NUM_THREAD);
  }

  public boolean getCameraSwitch() {
    return preferences.getBoolean(CAMERA_SWITCH, false);
  }

  public boolean getSheetExpanded() {
    return preferences.getBoolean(SHEET_EXPANDED, false);
  }

  public void setBaudrate(int baudRate) {
    preferences.edit().putInt(BAUD_RATE, baudRate).apply();
  }

  public void setDefaultModel(String model) {
    preferences.edit().putString(DEFAULT_MODEL, model).apply();
  }

  public String getDefaultModel() {
    return preferences.getString(DEFAULT_MODEL, "");
  }

  public void setObjectNavModel(String model) {
    preferences.edit().putString(OBJECT_NAV_MODEL, model).apply();
  }

  public String getObjectNavModel() {
    return preferences.getString(OBJECT_NAV_MODEL, "");
  }

  public void setAutopilotModel(String model) {
    preferences.edit().putString(AUTOPILOT_MODEL, model).apply();
  }

  public String getAutopilotModel() {
    return preferences.getString(AUTOPILOT_MODEL, "");
  }

  public void setObjectType(String model) {
    preferences.edit().putString(OBJECT_TYPE, model).apply();
  }

  public String getObjectType() {
    return preferences.getString(OBJECT_TYPE, DEFAULT_OBJECT_TYPE);
  }

  public void setDevice(int device) {
    preferences.edit().putInt(DEVICE, device).apply();
  }

  public void setDriveMode(int mode) {
    preferences.edit().putInt(DRIVE_MODE, mode).apply();
  }

  public void setLogMode(int mode) {
    preferences.edit().putInt(LOG_MODE, mode).apply();
  }

  public void setControlMode(int mode) {
    preferences.edit().putInt(CONTROL_MODE, mode).apply();
  }

  public void setSpeedMode(int mode) {
    preferences.edit().putInt(SPEED_MODE, mode).apply();
  }

  public void setNumThreads(int numThreads) {
    preferences.edit().putInt(NUM_THREAD, numThreads).apply();
  }

  public void setCameraSwitch(boolean isChecked) {
    preferences.edit().putBoolean(CAMERA_SWITCH, isChecked).apply();
  }

  public void setSheetExpanded(boolean expanded) {
    preferences.edit().putBoolean(SHEET_EXPANDED, expanded).apply();
  }

  public void setSensorStatus(boolean status, String sensor) {
    preferences.edit().putBoolean(sensor, status).apply();
  }

  public boolean getSensorStatus(String sensor) {
    return preferences.getBoolean(sensor, false);
  }

  public void setDelay(int delay) {
    preferences.edit().putInt(DELAY, delay).apply();
  }

  public int getDelay() {
    return preferences.getInt(DELAY, 200);
  }
}
