# Controller App

This Android app serves as a `remote controller` for the [OpenBot](https://www.openbot.org) vehicle.  Basically it preforms similar function as PS3/4 or Xbox remote controller, but running on another Android device. It allows the user to control the robot car via two sliders in `Dual Drive` mode.

<p float="left">
  <img src="../../docs/images/controller_main_screen.png" width="50%" />
</p>

## Connection

When the controller app is started, it immediately tries to connect to the robot. We see the following screen:

<p float="left">
  <img src="../../docs/images/controller_pre_connect.png" width="50%" />
</p>

To connect the controller to the robot, place the robot's app control setting into a **Phone** mode.

<p float="left">
  <img src="../../docs/images/app_controller_settings_1.png" width="25%" />
  <img src="../../docs/images/app_controller_settings_2.png" width="25%" />
</p>

You can also connect to the controller from the `FreeRoamFragment` by selecting the phone as the controller:

<p float="left">
  <img src="../../docs/images/free-roam-fragment-selection.png" width="50%" />
</p>


In a few seconds, you will hear a beep, and the controller will change its screen to:

<p float="left">
  <img src="../../docs/images/controller_command_buttons.png" width="50%" />
</p>

Here you can select to drive the robot by tilting the phone, or by using the on-screen controls.

***Note:*** This should be sufficient to connect, but if the connection cannot be established after 30 seconds, toggle the `Control` setting on the bot app to `Gamepad`
and then to `Phone` again to re-initiate the connection. If that fails, exit the controller app and start it again. Toggle the Control mode again on the robot app.

## Operation

### On-screen controls
The operation of the controller app is pretty self-explanatory. You can turn left/right by moving the slider thumb up and down on each side. You can also place the wheels
on each side in reverse by moving the thumb below the center of the slider.

You can also set right/left
<img src="../../docs/images/keyboard_arrow_left-24px.svg" height="24"/> 
<img src="../../docs/images/keyboard_arrow_right-24px.svg" height="24"/> 
turn indicators by clicking on the arrows on the top-left of the screen, and the red button between them to cancel.

### Tilt to drive
The controller can also use its accelerometer motion sensor to drive the robot. If you select this option, the controller will enter a full-screen (Zen) mode with only 
the video showing and no other controls. To exit this mode, either double-tap on the screen, or place the controler on a flat surface.

Here is a picture of the orientation access of the controller:

<p float="left">
  <img src="../../docs/images/orientation_axis.png" width="50%" />
</p>

Start with the controller in the up-right position in landscape mode. To move forward, increase the `roll` angle (rotate forward around the `Y` access). To turn left or right,
tilt the phone around the `X` access. 

If you like to control some other settings on the robot, such as `Noise`, `Logs`, etc, double-tap in the middle of the screen, and you will see the buttons on the left.
They also show the current settings, so if the `Noise` button is highlighted, this means this setting on the robot is `ON`. If you change the setting manually from
 the robot menu, this should be reflected on the controller app as well.

## Future Development

Some of the features we are looking to add are:

- Add information on the controller for more robot sensors, such as battery level and speed.
- Video Stream from the robot's camera to the controller
- Use controller's gyroscope sensor co control the robot
- Send crash and bump events from the robot to the controller for a more realistic experience

Here is a [Technical Overview](../../docs/technical/OpenBotController.pdf) of the controller app.