package org.openbot.utils;

import android.util.Size;
import java.util.EnumSet;

public class Enums {

  public enum SensorType {
    ACCELEROMETER("Accelerometer"),
    GYROSCOPE("Gyroscope"),
    PROXIMITY("Proximity"),
    GRAVITY("Gravity"),
    MAGNETIC("Magnetic"),
    LIGHT("Light"),
    PRESSURE("Pressure"),
    GPS("Gps"),
    VEHICLE("Vehicle"),
    POSE("Pose"),
    MOTION("Motion");

    private String sensor;

    SensorType(String sensor) {
      this.sensor = sensor;
    }

    public String getSensor() {
      return sensor;
    }
  }

  public enum LogMode {
    ALL_IMGS(0),
    CROP_IMG(1),
    PREVIEW_IMG(2),
    ONLY_SENSORS(3);

    private final int value;

    LogMode(final int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  public enum ControlMode {
    GAMEPAD(0),
    PHONE(1),
    WEBRTC(2);

    private final int value;

    ControlMode(final int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    public static ControlMode getByID(int value) {
      for (final ControlMode element : EnumSet.allOf(ControlMode.class)) {
        if (element.value == value) {
          return element;
        }
      }
      return null;
    }
  }

  public static ControlMode switchControlMode(ControlMode mode) {
    switch (mode) {
      case GAMEPAD:
        return ControlMode.PHONE;
      case PHONE:
        return ControlMode.GAMEPAD;
    }
    return null;
  }

  public enum SpeedMode {
    SLOW(128),
    NORMAL(192),
    FAST(255);

    private final int value;

    SpeedMode(final int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    public static SpeedMode getByID(int value) {
      for (final SpeedMode element : EnumSet.allOf(SpeedMode.class)) {
        if (element.value == value) {
          return element;
        }
      }
      return null;
    }
  }

  public enum DriveMode {
    DUAL(0),
    GAME(1),
    JOYSTICK(2);

    private final int value;

    DriveMode(final int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    public static DriveMode getByID(int value) {
      for (final DriveMode element : EnumSet.allOf(DriveMode.class)) {
        if (element.value == value) {
          return element;
        }
      }
      return null;
    }
  }

  public static DriveMode switchDriveMode(DriveMode mode) {
    switch (mode) {
      case DUAL:
        return DriveMode.GAME;
      case GAME:
        return DriveMode.JOYSTICK;
      case JOYSTICK:
        return DriveMode.DUAL;
    }
    return null;
  }

  public enum VehicleIndicator {
    LEFT(-1),
    STOP(0),
    RIGHT(1);

    private final int value;

    VehicleIndicator(final int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  public enum Direction {
    UP(+1),
    CYCLIC(0),
    DOWN(-1);

    private final int value;

    Direction(final int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  public static SpeedMode toggleSpeed(int direction, SpeedMode speedMode) {
    if (speedMode != null)
      switch (speedMode) {
        case SLOW:
          if (direction != Enums.Direction.DOWN.getValue()) return SpeedMode.NORMAL;
          break;
        case NORMAL:
          return direction == Enums.Direction.DOWN.getValue() ? SpeedMode.SLOW : SpeedMode.FAST;
        case FAST:
          if (direction == Enums.Direction.DOWN.getValue()) return SpeedMode.NORMAL;
          if (direction == Enums.Direction.CYCLIC.getValue()) return SpeedMode.SLOW;
          break;
      }
    return null;
  }

  public enum Preview {
    FULL_HD(new Size(1080, 1920)),
    HD(new Size(720, 1280)),
    SD(new Size(360, 640));

    private final Size value;

    Preview(final Size value) {
      this.value = value;
    }

    public Size getValue() {
      return value;
    }
  }
}
