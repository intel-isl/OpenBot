package org.openbot.robot;

import static org.openbot.utils.Enums.ControlMode;
import static org.openbot.utils.Enums.DriveMode;
import static org.openbot.utils.Enums.SpeedMode;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;
import com.github.anastr.speedviewlib.components.Section;
import com.google.android.material.internal.ViewUtils;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.openbot.R;
import org.openbot.common.ControlsFragment;
import org.openbot.databinding.FragmentFreeRoamBinding;
import org.openbot.env.PhoneController;
import org.openbot.utils.Constants;
import org.openbot.utils.Enums;
import org.openbot.utils.PermissionUtils;
import timber.log.Timber;

public class FreeRoamFragment extends ControlsFragment {

  private FragmentFreeRoamBinding binding;
  private PhoneController phoneController;

  @Override
  public View onCreateView(
      @NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentFreeRoamBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @SuppressLint("RestrictedApi")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    phoneController = PhoneController.getInstance(requireContext());

    binding.voltageInfo.setText(getString(R.string.voltageInfo, "--.-"));
    binding.controllerContainer.speedInfo.setText(getString(R.string.speedInfo, "---,---"));
    binding.sonarInfo.setText(getString(R.string.distanceInfo, "---"));

    setSpeedMode(SpeedMode.getByID(preferencesManager.getSpeedMode()));
    setControlMode(ControlMode.getByID(preferencesManager.getControlMode()));
    setDriveMode(DriveMode.getByID(preferencesManager.getDriveMode()));

    binding.controllerContainer.controlMode.setOnClickListener(
        v -> {
          ControlMode controlMode = ControlMode.getByID(preferencesManager.getControlMode());
          if (controlMode != null) setControlMode(Enums.switchControlMode(controlMode));
        });
    binding.controllerContainer.driveMode.setOnClickListener(
        v -> setDriveMode(Enums.switchDriveMode(vehicle.getDriveMode())));

    binding.controllerContainer.speedMode.setOnClickListener(
        v ->
            setSpeedMode(
                Enums.toggleSpeed(
                    Enums.Direction.CYCLIC.getValue(),
                    SpeedMode.getByID(preferencesManager.getSpeedMode()))));

    binding.speed.getSections().clear();
    binding.speed.addSections(
        new Section(
            0f,
            0.7f,
            getResources().getColor(R.color.green),
            ViewUtils.dpToPx(requireContext(), 24)),
        new Section(
            0.7f,
            0.8f,
            getResources().getColor(R.color.yellow),
            ViewUtils.dpToPx(requireContext(), 24)),
        new Section(
            0.8f,
            1.0f,
            getResources().getColor(R.color.red),
            ViewUtils.dpToPx(requireContext(), 24)));

    mViewModel
        .getUsbStatus()
        .observe(getViewLifecycleOwner(), status -> binding.usbToggle.setChecked(status));

    binding.usbToggle.setChecked(vehicle.isUsbConnected());

    binding.usbToggle.setOnClickListener(
        v -> {
          binding.usbToggle.setChecked(vehicle.isUsbConnected());
          Navigation.findNavController(requireView()).navigate(R.id.open_settings_fragment);
        });
  }

  @Override
  protected void processUSBData(String data) {
    // Data has the following form: voltage, lWheel, rWheel, obstacle
    //      sendVehicleDataToSensorService(timestamp, data);

    binding.controllerContainer.speedInfo.setText(
        getString(
            R.string.speedInfo,
            String.format(
                Locale.US, "%3.0f,%3.0f", vehicle.getLeftWheelRPM(), vehicle.getRightWheelRPM())));

    binding.voltageInfo.setText(
        getString(
            R.string.voltageInfo, String.format(Locale.US, "%2.1f", vehicle.getBatteryVoltage())));
    binding.battery.setProgress(vehicle.getBatteryPercentage());
    if (vehicle.getBatteryPercentage() < 15) {
      binding.battery.setProgressTintList(
          ColorStateList.valueOf(getResources().getColor(R.color.red)));
      binding.battery.setProgressBackgroundTintList(
          ColorStateList.valueOf(getResources().getColor(R.color.red)));
    } else {
      binding.battery.setProgressTintList(
          ColorStateList.valueOf(getResources().getColor(R.color.green)));
      binding.battery.setProgressBackgroundTintList(
          ColorStateList.valueOf(getResources().getColor(R.color.green)));
    }

    binding.sonar.setProgress((int) (vehicle.getSonarReading() / 3));
    if (vehicle.getSonarReading() / 3 < 15) {
      binding.sonar.setProgressTintList(
          ColorStateList.valueOf(getResources().getColor(R.color.red)));
    } else if (vehicle.getSonarReading() / 3 < 45) {
      binding.sonar.setProgressTintList(
          ColorStateList.valueOf(getResources().getColor(R.color.yellow)));
    } else {
      binding.sonar.setProgressTintList(
          ColorStateList.valueOf(getResources().getColor(R.color.green)));
    }

    binding.sonarInfo.setText(
        getString(
            R.string.distanceInfo, String.format(Locale.US, "%3.0f", vehicle.getSonarReading())));
  }

  private void toggleIndicator(int value) {
    binding.indicatorRight.clearAnimation();
    binding.indicatorLeft.clearAnimation();
    binding.indicatorRight.setVisibility(View.INVISIBLE);
    binding.indicatorLeft.setVisibility(View.INVISIBLE);

    if (value == Enums.VehicleIndicator.RIGHT.getValue()) {
      binding.indicatorRight.startAnimation(startAnimation);
      binding.indicatorRight.setVisibility(View.VISIBLE);
    } else if (value == Enums.VehicleIndicator.LEFT.getValue()) {
      binding.indicatorLeft.startAnimation(startAnimation);
      binding.indicatorLeft.setVisibility(View.VISIBLE);
    }
  }

  protected void handleDriveCommand() {
    float left = vehicle.getLeftSpeed();
    float right = vehicle.getRightSpeed();
    binding.controllerContainer.controlInfo.setText(
        String.format(Locale.US, "%.0f,%.0f", left, right));

    binding.speed.speedPercentTo(vehicle.getSpeedPercent());

    binding.steering.setRotation(vehicle.getRotation());

    binding.driveGear.setText(vehicle.getDriveGear());
  }

  private void setSpeedMode(SpeedMode speedMode) {
    if (speedMode != null) {
      switch (speedMode) {
        case SLOW:
          binding.controllerContainer.speedMode.setImageResource(R.drawable.ic_speed_low);
          break;
        case NORMAL:
          binding.controllerContainer.speedMode.setImageResource(R.drawable.ic_speed_medium);
          break;
        case FAST:
          binding.controllerContainer.speedMode.setImageResource(R.drawable.ic_speed_high);
          break;
      }

      Timber.d("Updating  controlSpeed: %s", speedMode);
      preferencesManager.setSpeedMode(speedMode.getValue());
      vehicle.setSpeedMultiplier(speedMode.getValue());
    }
  }

  private void setControlMode(ControlMode controlMode) {
    if (controlMode != null) {
      switch (controlMode) {
        case GAMEPAD:
          binding.controllerContainer.controlMode.setImageResource(R.drawable.ic_controller);
          disconnectPhoneController();
          break;
        case PHONE:
          binding.controllerContainer.controlMode.setImageResource(R.drawable.ic_phone);
          if (!PermissionUtils.hasControllerPermissions(requireActivity()))
            requestPermissionLauncher.launch(Constants.PERMISSIONS_CONTROLLER);
          else connectPhoneController();
          break;
      }
      Timber.d("Updating  controlMode: %s", controlMode);
      preferencesManager.setControlMode(controlMode.getValue());
    }
  }

  protected void setDriveMode(DriveMode driveMode) {
    if (driveMode != null) {
      switch (driveMode) {
        case DUAL:
          binding.controllerContainer.driveMode.setImageResource(R.drawable.ic_dual);
          break;
        case GAME:
          binding.controllerContainer.driveMode.setImageResource(R.drawable.ic_game);
          break;
        case JOYSTICK:
          binding.controllerContainer.driveMode.setImageResource(R.drawable.ic_joystick);
          break;
      }

      Timber.d("Updating  driveMode: %s", driveMode);
      vehicle.setDriveMode(driveMode);
      preferencesManager.setDriveMode(driveMode.getValue());
    }
  }

  private void connectPhoneController() {
    phoneController.connect(requireContext());
    DriveMode oldDriveMode = currentDriveMode;
    // Currently only dual drive mode supported
    setDriveMode(DriveMode.DUAL);
    binding.controllerContainer.driveMode.setAlpha(0.5f);
    binding.controllerContainer.driveMode.setEnabled(false);
    preferencesManager.setDriveMode(oldDriveMode.getValue());
  }

  private void disconnectPhoneController() {
    phoneController.disconnect();
    setDriveMode(DriveMode.getByID(preferencesManager.getDriveMode()));
    binding.controllerContainer.driveMode.setEnabled(true);
    binding.controllerContainer.driveMode.setAlpha(1.0f);
  }

  @Override
  protected void processControllerKeyData(String commandType) {
    switch (commandType) {
      case Constants.CMD_DRIVE:
        handleDriveCommand();
        break;

      case Constants.CMD_INDICATOR_LEFT:
        toggleIndicator(Enums.VehicleIndicator.LEFT.getValue());
        break;

      case Constants.CMD_INDICATOR_RIGHT:
        toggleIndicator(Enums.VehicleIndicator.RIGHT.getValue());
        break;

      case Constants.CMD_INDICATOR_STOP:
        toggleIndicator(Enums.VehicleIndicator.STOP.getValue());
        break;

      case Constants.CMD_DRIVE_MODE:
        setDriveMode(Enums.switchDriveMode(vehicle.getDriveMode()));
        break;

      case Constants.CMD_DISCONNECTED:
        handleDriveCommand();
        setControlMode(ControlMode.GAMEPAD);
        break;

      case Constants.CMD_SPEED_DOWN:
        setSpeedMode(
            Enums.toggleSpeed(
                Enums.Direction.DOWN.getValue(),
                Enums.SpeedMode.getByID(preferencesManager.getSpeedMode())));
        break;

      case Constants.CMD_SPEED_UP:
        setSpeedMode(
            Enums.toggleSpeed(
                Enums.Direction.UP.getValue(),
                Enums.SpeedMode.getByID(preferencesManager.getSpeedMode())));
        break;
    }
  }
}
